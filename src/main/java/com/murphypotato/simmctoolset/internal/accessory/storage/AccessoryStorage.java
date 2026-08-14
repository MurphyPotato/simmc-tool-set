package com.murphypotato.simmctoolset.internal.accessory.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.murphypotato.simmctoolset.internal.accessory.domain.AccessoryRecord;
import com.murphypotato.simmctoolset.internal.accessory.domain.AccessoryFingerprint;
import com.murphypotato.simmctoolset.internal.accessory.domain.AccessoryMetadata;
import com.murphypotato.simmctoolset.internal.accessory.domain.AccessorySource;
import com.murphypotato.simmctoolset.internal.accessory.domain.AffixRecord;
import com.murphypotato.simmctoolset.internal.accessory.domain.ContainerLocation;
import com.murphypotato.simmctoolset.internal.accessory.domain.IconSnapshot;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class AccessoryStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path file;
    private boolean writesBlocked;
    private boolean needsRc7Backup;

    public AccessoryStorage(Path file) {
        this.file = file;
    }

    public Path file() {
        return file;
    }

    public boolean writesBlocked() {
        return writesBlocked;
    }

    public StorageLoadResult load() {
        if (!Files.exists(file)) {
            writesBlocked = false;
            return new StorageLoadResult(AccessoryStore.empty(), false, "");
        }

        try {
            JsonElement original = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            needsRc7Backup = original.isJsonObject() && !original.getAsJsonObject().has("extensions");
            JsonElement migrated = migrateRaw(original);
            validateRaw(migrated);
            AccessoryStore parsed = GSON.fromJson(migrated, AccessoryStore.class);
            validate(parsed);
            writesBlocked = false;
            return new StorageLoadResult(parsed, false, "");
        } catch (IOException | RuntimeException error) {
            writesBlocked = true;
            return new StorageLoadResult(AccessoryStore.empty(), true, safeMessage(error));
        }
    }

    public void save(AccessoryStore.Settings settings, List<AccessoryRecord> accessories) throws IOException {
        save(settings, accessories, Map.of());
    }

    public void save(
        AccessoryStore.Settings settings,
        List<AccessoryRecord> accessories,
        Map<String, AccessoryMetadata> metadata
    ) throws IOException {
        if (writesBlocked) {
            throw new IOException("饰品库文件已损坏；确认备份并重建前禁止覆盖原文件");
        }
        List<AccessoryRecord> sanitized = accessories.stream()
            .map(AccessoryStorage::sanitizeForStorage)
            .toList();
        Set<String> ids = sanitized.stream().map(AccessoryRecord::id).collect(java.util.stream.Collectors.toSet());
        LinkedHashMap<String, AccessoryMetadata> sanitizedMetadata = new LinkedHashMap<>();
        if (metadata != null) metadata.forEach((id, value) -> {
            if (ids.contains(id) && value != null) sanitizedMetadata.put(id, sanitizeMetadata(value, settings.saveContainerLocations()));
        });
        AccessoryStore store = AccessoryStore.create(settings, sanitized, sanitizedMetadata);
        validate(store);
        ensureRc7Backup();
        writeAtomically(GSON.toJson(store));
    }

    public Path backupCorruptAndReset() throws IOException {
        if (!writesBlocked) throw new IOException("当前饰品库未处于损坏锁定状态");
        Files.createDirectories(file.getParent());
        Path backup = file.resolveSibling(file.getFileName() + ".corrupt-" + Instant.now().toEpochMilli() + ".bak");
        if (Files.exists(file)) Files.copy(file, backup, StandardCopyOption.COPY_ATTRIBUTES);
        writesBlocked = false;
        try {
            save(new AccessoryStore.Settings(false), List.of());
        } catch (IOException error) {
            writesBlocked = true;
            throw error;
        }
        return backup;
    }

    private void writeAtomically(String json) throws IOException {
        Path parent = file.getParent();
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, file.getFileName().toString(), ".tmp");
        try {
            Files.writeString(temporary, json, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException error) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void ensureRc7Backup() throws IOException {
        if (!needsRc7Backup || !Files.exists(file)) return;
        Path backup = file.resolveSibling(file.getFileName() + ".pre-rc7.bak");
        if (!Files.exists(backup)) {
            Files.copy(file, backup, StandardCopyOption.COPY_ATTRIBUTES);
        } else if (Files.mismatch(file, backup) != -1) {
            long timestamp = Instant.now().toEpochMilli();
            Path repeated = file.resolveSibling(file.getFileName() + ".pre-rc7-" + timestamp + ".bak");
            while (Files.exists(repeated)) {
                timestamp++;
                repeated = file.resolveSibling(file.getFileName() + ".pre-rc7-" + timestamp + ".bak");
            }
            Files.copy(file, repeated, StandardCopyOption.COPY_ATTRIBUTES);
        }
        needsRc7Backup = false;
    }

    private static void validate(AccessoryStore store) {
        if (store == null) throw new IllegalArgumentException("JSON 根对象无效");
        if (!AccessoryStore.SCHEMA.equals(store.schema())) throw new IllegalArgumentException("schema 不匹配");
        if (store.version() != AccessoryStore.VERSION) throw new IllegalArgumentException("version 不匹配");
        if (store.settings() == null || store.accessories() == null || store.extensions() == null) throw new IllegalArgumentException("缺少必要字段");
        if (store.accessories().size() > 10000) throw new IllegalArgumentException("饰品数量异常");
        for (AccessoryRecord accessory : store.accessories()) validate(accessory);
        if (store.extensions().version() != 1 || store.extensions().metadata().size() > 10000) {
            throw new IllegalArgumentException("Fabric 扩展数据无效");
        }
        for (Map.Entry<String, AccessoryMetadata> entry : store.extensions().metadata().entrySet()) {
            if (entry.getKey() == null || entry.getKey().length() > 200 || entry.getValue() == null) {
                throw new IllegalArgumentException("饰品扩展数据无效");
            }
            validate(entry.getValue());
        }
    }

    private static void validateRaw(JsonElement rootElement) {
        if (!rootElement.isJsonObject()) throw new IllegalArgumentException("JSON 根对象无效");
        JsonObject root = rootElement.getAsJsonObject();
        requireString(root, "schema");
        requireNumber(root, "version");
        requireString(root, "savedAt");
        if (!root.has("settings") || !root.get("settings").isJsonObject()) throw new IllegalArgumentException("settings 无效");
        JsonObject settings = root.getAsJsonObject("settings");
        if (!settings.has("alwaysReview") || !settings.get("alwaysReview").isJsonPrimitive() || !settings.getAsJsonPrimitive("alwaysReview").isBoolean()) {
            throw new IllegalArgumentException("alwaysReview 无效");
        }
        if (settings.has("saveContainerLocations")
            && (!settings.get("saveContainerLocations").isJsonPrimitive() || !settings.getAsJsonPrimitive("saveContainerLocations").isBoolean())) {
            throw new IllegalArgumentException("saveContainerLocations 无效");
        }
        if (settings.has("locationSalt")) requireString(settings, "locationSalt");
        if (!root.has("accessories") || !root.get("accessories").isJsonArray()) throw new IllegalArgumentException("accessories 无效");
        for (JsonElement element : root.getAsJsonArray("accessories")) {
            if (!element.isJsonObject()) throw new IllegalArgumentException("饰品对象无效");
            JsonObject item = element.getAsJsonObject();
            for (String field : List.of("id", "fingerprint", "name", "slot", "quality", "itemId")) requireString(item, field);
            requireNumber(item, "level");
            if (!item.has("affixes") || !item.get("affixes").isJsonArray()) throw new IllegalArgumentException("affixes 无效");
            for (JsonElement affixElement : item.getAsJsonArray("affixes")) {
                if (!affixElement.isJsonObject()) throw new IllegalArgumentException("词条对象无效");
                JsonObject affix = affixElement.getAsJsonObject();
                for (String field : List.of("id", "stat", "label", "rawText", "warning")) requireString(affix, field);
                requireNumber(affix, "value");
                if (affix.has("unit")) requireString(affix, "unit");
            }
            if (!item.has("source") || !item.get("source").isJsonObject()) throw new IllegalArgumentException("source 无效");
            JsonObject source = item.getAsJsonObject("source");
            for (String field : List.of("kind", "containerTitle", "slotLabel")) requireString(source, field);
            requireNumber(source, "slotIndex");
        }
        if (root.has("extensions") && !root.get("extensions").isJsonObject()) {
            throw new IllegalArgumentException("extensions 无效");
        }
    }

    private static JsonElement migrateRaw(JsonElement rootElement) {
        if (!rootElement.isJsonObject()) return rootElement;
        JsonObject root = rootElement.getAsJsonObject();
        if (root.has("settings") && root.get("settings").isJsonObject()) {
            JsonObject settings = root.getAsJsonObject("settings");
            if (!settings.has("saveContainerLocations")) settings.addProperty("saveContainerLocations", true);
        }
        if (!root.has("accessories") || !root.get("accessories").isJsonArray()) return root;
        for (JsonElement itemElement : root.getAsJsonArray("accessories")) {
            if (!itemElement.isJsonObject()) continue;
            JsonObject item = itemElement.getAsJsonObject();
            if (!item.has("affixes") || !item.get("affixes").isJsonArray()) continue;
            for (JsonElement affixElement : item.getAsJsonArray("affixes")) {
                if (!affixElement.isJsonObject()) continue;
                JsonObject affix = affixElement.getAsJsonObject();
                if (affix.has("stat") && affix.get("stat").isJsonPrimitive()) {
                    String stat = affix.get("stat").getAsString();
                    if (stat.equals("damageReduction") || stat.equals("sneakSpeed")) {
                        affix.addProperty("stat", "OTHER");
                    }
                }
                if (!affix.has("unit")) {
                    String raw = affix.has("rawText") && affix.get("rawText").isJsonPrimitive()
                        ? affix.get("rawText").getAsString().strip()
                        : "";
                    affix.addProperty("unit", raw.matches(".*\\+[0-9]+(?:\\.[0-9]+)?%.*") ? "percent" : "none");
                }
            }
        }
        return root;
    }

    private static void requireString(JsonObject object, String field) {
        if (!object.has(field)
            || !object.get(field).isJsonPrimitive()
            || !object.getAsJsonPrimitive(field).isString()) {
            throw new IllegalArgumentException(field + " 必须是字符串");
        }
    }

    private static void requireNumber(JsonObject object, String field) {
        if (!object.has(field)
            || !object.get(field).isJsonPrimitive()
            || !object.getAsJsonPrimitive(field).isNumber()) {
            throw new IllegalArgumentException(field + " 必须是数字");
        }
    }

    private static void validate(AccessoryRecord accessory) {
        if (accessory == null
            || accessory.id() == null
            || accessory.fingerprint() == null
            || accessory.name() == null
            || accessory.slot() == null
            || accessory.quality() == null
            || accessory.affixes() == null
            || accessory.itemId() == null
            || accessory.source() == null) {
            throw new IllegalArgumentException("饰品字段不完整");
        }
        if (accessory.id().length() > 200 || accessory.name().length() > 500 || accessory.itemId().length() > 300) {
            throw new IllegalArgumentException("饰品文本过长");
        }
        if (accessory.level() < 0 || accessory.level() > 100 || accessory.affixes().size() > 100) {
            throw new IllegalArgumentException("饰品数值异常");
        }
        for (AffixRecord affix : accessory.affixes()) {
            if (affix == null || affix.stat() == null || affix.unit() == null || !Double.isFinite(affix.value())) {
                throw new IllegalArgumentException("词条字段无效");
            }
        }
    }

    private static void validate(AccessoryMetadata metadata) {
        IconSnapshot icon = metadata.icon();
        if (icon != null && !icon.valid()) throw new IllegalArgumentException("图标快照无效");
        ContainerLocation location = metadata.location();
        if (location != null) {
            if (location.key().length() > 1000 || location.displayLabel().length() > 200) {
                throw new IllegalArgumentException("容器位置无效");
            }
            if (!location.serverScope().isBlank() && !location.serverScope().matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("服务器范围哈希无效");
            }
        }
        if (metadata.lastSeenAt().length() > 100) throw new IllegalArgumentException("来源时间无效");
    }

    private static AccessoryMetadata sanitizeMetadata(AccessoryMetadata metadata, boolean saveLocation) {
        IconSnapshot icon = metadata.icon() != null && metadata.icon().valid() ? metadata.icon() : null;
        ContainerLocation location = metadata.location();
        if (!saveLocation && location != null && location.locatedBlock()) location = null;
        return new AccessoryMetadata(icon, location, metadata.locationState(), metadata.lastSeenAt());
    }

    private static AccessoryRecord sanitizeForStorage(AccessoryRecord accessory) {
        if (accessory == null) return null;
        AccessorySource source = accessory.source();
        String kind = switch (source.kind()) {
            case "inventory", "container", "blank" -> source.kind();
            default -> "local";
        };
        String title = switch (kind) {
            case "inventory" -> "玩家物品栏";
            case "container" -> "容器界面";
            case "blank" -> "空白";
            default -> "本地来源";
        };
        String slotLabel = switch (kind) {
            case "inventory" -> inventorySlotLabel(source.slotIndex());
            case "container" -> "容器槽位 " + source.slotIndex();
            case "blank" -> accessory.slot().label();
            default -> "来源槽位 " + source.slotIndex();
        };
        return new AccessoryRecord(
            accessory.id(),
            AccessoryFingerprint.computeLegacy(accessory),
            accessory.name(),
            accessory.slot(),
            accessory.quality(),
            accessory.level(),
            accessory.affixes(),
            accessory.itemId(),
            new AccessorySource(kind, title, source.slotIndex(), slotLabel)
        );
    }

    private static String inventorySlotLabel(int index) {
        if (index < 9) return "快捷栏 " + (index + 1);
        if (index < 36) return "物品栏 " + (index + 1);
        return "装备槽位 " + index;
    }

    private static String safeMessage(Exception error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }
}
