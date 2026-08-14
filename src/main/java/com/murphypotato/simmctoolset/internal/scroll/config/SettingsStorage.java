package com.murphypotato.simmctoolset.internal.scroll.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.murphypotato.simmctoolset.internal.scroll.domain.GameData;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

public final class SettingsStorage {
    public static final String SCHEMA = "simmc-arcane-scroll-calculator:v1.1.1-fabric/settings";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path file;
    private final GameData data;
    private Path lastCorruptBackup;

    public SettingsStorage(Path file, GameData data) {
        this.file = file;
        this.data = data;
    }

    public Path file() {
        return file;
    }

    public Path lastCorruptBackup() {
        return lastCorruptBackup;
    }

    public ArcaneSettings load() {
        if (!Files.exists(file)) return ArcaneSettings.defaults(data);
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!SCHEMA.equals(requiredString(root, "schema"))) throw new IllegalArgumentException("schema 不匹配");
            JsonArray excluded = root.getAsJsonArray("excludedMaterials");
            if (excluded == null || excluded.size() > data.materials().size()) throw new IllegalArgumentException("排除材料列表无效");
            Set<String> validMaterials = data.materials().stream().map(value -> value.name()).collect(java.util.stream.Collectors.toSet());
            Set<String> names = new LinkedHashSet<>();
            for (JsonElement element : excluded) {
                if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                    throw new IllegalArgumentException("排除材料名称无效");
                }
                String name = element.getAsString();
                if (validMaterials.contains(name)) names.add(name);
            }
            String requestedRecipe = requiredString(root, "selectedRecipe");
            String selectedRecipe = data.recipes().stream().anyMatch(recipe -> recipe.name().equals(requestedRecipe))
                ? requestedRecipe
                : data.recipes().getFirst().name();
            return new ArcaneSettings(
                names,
                selectedRecipe,
                requiredInt(root, "quantity"),
                requiredBoolean(root, "includeMainMaterial"),
                requiredInt(root, "repeatThreshold")
            );
        } catch (Exception error) {
            protectCorruptFile();
            return ArcaneSettings.defaults(data);
        }
    }

    public void save(ArcaneSettings settings) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("schema", SCHEMA);
        JsonArray excluded = new JsonArray();
        settings.excludedMaterials().stream().sorted().forEach(excluded::add);
        root.add("excludedMaterials", excluded);
        root.addProperty("selectedRecipe", settings.selectedRecipe());
        root.addProperty("quantity", settings.quantity());
        root.addProperty("includeMainMaterial", settings.includeMainMaterial());
        root.addProperty("repeatThreshold", settings.repeatThreshold());
        writeAtomically(GSON.toJson(root) + System.lineSeparator());
    }

    private void protectCorruptFile() {
        try {
            if (!Files.exists(file)) return;
            Files.createDirectories(file.getParent());
            Path backup = file.resolveSibling(file.getFileName() + ".corrupt-" + Instant.now().toEpochMilli() + ".bak");
            Files.copy(file, backup, StandardCopyOption.COPY_ATTRIBUTES);
            lastCorruptBackup = backup;
        } catch (IOException ignored) {
            lastCorruptBackup = null;
        }
    }

    private void writeAtomically(String json) throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), file.getFileName().toString(), ".tmp");
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

    private static String requiredString(JsonObject root, String name) {
        JsonElement value = root.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(name + " 必须是字符串");
        }
        return value.getAsString();
    }

    private static int requiredInt(JsonObject root, String name) {
        JsonElement value = root.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(name + " 必须是整数");
        }
        return value.getAsInt();
    }

    private static boolean requiredBoolean(JsonObject root, String name) {
        JsonElement value = root.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException(name + " 必须是布尔值");
        }
        return value.getAsBoolean();
    }
}
