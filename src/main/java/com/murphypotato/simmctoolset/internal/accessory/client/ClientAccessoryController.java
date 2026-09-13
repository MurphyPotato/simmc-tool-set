package com.murphypotato.simmctoolset.internal.accessory.client;

import com.murphypotato.simmctoolset.internal.accessory.domain.AccessoryCalculator;
import com.murphypotato.simmctoolset.internal.accessory.domain.AccessoryFingerprint;
import com.murphypotato.simmctoolset.internal.accessory.domain.AccessoryLibrary;
import com.murphypotato.simmctoolset.internal.accessory.domain.AccessoryMetadata;
import com.murphypotato.simmctoolset.internal.accessory.domain.AccessoryRecord;
import com.murphypotato.simmctoolset.internal.accessory.domain.AccessoryScore;
import com.murphypotato.simmctoolset.internal.accessory.domain.AccessoryScorer;
import com.murphypotato.simmctoolset.internal.accessory.domain.AccessorySlot;
import com.murphypotato.simmctoolset.internal.accessory.domain.AccessorySource;
import com.murphypotato.simmctoolset.internal.accessory.domain.ContainerLocation;
import com.murphypotato.simmctoolset.internal.accessory.domain.IconSnapshot;
import com.murphypotato.simmctoolset.internal.accessory.domain.LoadoutAnalysis;
import com.murphypotato.simmctoolset.internal.accessory.domain.LoadoutResult;
import com.murphypotato.simmctoolset.internal.accessory.domain.LoadoutScore;
import com.murphypotato.simmctoolset.internal.accessory.domain.LocationState;
import com.murphypotato.simmctoolset.internal.accessory.domain.PlanVariant;
import com.murphypotato.simmctoolset.internal.accessory.domain.StabilityProfile;
import com.murphypotato.simmctoolset.internal.accessory.domain.WeaponMode;
import com.murphypotato.simmctoolset.internal.accessory.parser.ParseResult;
import com.murphypotato.simmctoolset.internal.accessory.parser.ParseState;
import com.murphypotato.simmctoolset.internal.accessory.parser.TooltipParser;
import com.murphypotato.simmctoolset.internal.accessory.storage.AccessoryStorage;
import com.murphypotato.simmctoolset.internal.accessory.storage.AccessoryStore;
import com.murphypotato.simmctoolset.internal.accessory.storage.StorageLoadResult;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.world.World;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ClientAccessoryController implements AutoCloseable {
    public static final String DATA_DIRECTORY = "simmc-travel-hunter-accessory-tool";
    public static final String DATA_FILE = "accessories-v5-fabric.json";

    private final MinecraftClient client;
    private final TooltipParser parser = new TooltipParser();
    private final AccessoryStorage storage;
    private final AccessoryLibrary library;
    private final ContainerSourceRegistry sourceRegistry = new ContainerSourceRegistry();
    private final ContainerInteractionTracker interactionTracker = new ContainerInteractionTracker();
    private final List<ReviewEntry> reviewQueue = new ArrayList<>();
    /** Confirmations are intentionally session-scoped; a new server session must review again. */
    private final SessionReviewConfirmation sessionConfirmedReviews = new SessionReviewConfirmation();
    private final Map<String, ItemStack> transientStacks = new HashMap<>();
    private final Map<String, String> transientSourceTitles = new HashMap<>();
    private final Map<String, String> transientSourceGroups = new HashMap<>();
    private final Map<String, AccessoryMetadata> metadata = new LinkedHashMap<>();
    private final Map<String, AccessoryMetadata> pendingMetadata = new HashMap<>();
    private final Map<String, CapturedStack> pendingCaptured = new HashMap<>();
    private final Map<String, SourceConflict> sourceConflicts = new HashMap<>();
    private final EnumMap<WeaponMode, LoadoutAnalysis> analyses = new EnumMap<>(WeaponMode.class);
    private final EnumMap<WeaponMode, Map<String, AccessoryScore>> scores = new EnumMap<>(WeaponMode.class);
    private final EnumMap<WeaponMode, Map<PlanVariant, LoadoutScore>> loadoutScores = new EnumMap<>(WeaponMode.class);
    private final EnumMap<WeaponMode, List<String>> changeLogs = new EnumMap<>(WeaponMode.class);
    private final ExecutorService calculationExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "travel-hunter-loadout-calculator");
        thread.setDaemon(true);
        return thread;
    });

    private List<CapturedStack> capturedContainer = List.of();
    private Set<String> capturedContainerLocationKeys = Set.of();
    private boolean capturedContainerAvailable;
    private boolean alwaysReview;
    private boolean saveContainerLocations;
    private String locationSalt;
    private boolean calculating;
    private boolean plansDirty = true;
    private boolean storageCorrupted;
    private String storageError = "";
    private String status = "等待扫描饰品";
    private long calculationGeneration;
    private int delayedInventoryIconRefresh = -1;
    private int inventoryIconRefreshRetries;
    private int inventoryIconRefreshTicksRemaining;
    private int inventoryStableTicks;
    private List<ItemStack> inventorySyncSnapshot = List.of();
    private Runnable guidanceInvalidator = () -> { };

    public ClientAccessoryController(MinecraftClient client) {
        this.client = client;
        Path file = FabricLoader.getInstance().getConfigDir().resolve(DATA_DIRECTORY).resolve(DATA_FILE);
        this.storage = new AccessoryStorage(file);
        StorageLoadResult loaded = storage.load();
        this.library = new AccessoryLibrary(loaded.store().accessories());
        this.alwaysReview = loaded.store().settings().alwaysReview();
        this.saveContainerLocations = loaded.store().settings().saveContainerLocations();
        this.locationSalt = loaded.store().settings().locationSalt();
        Set<String> existingIds = library.items().stream().map(AccessoryRecord::id).collect(java.util.stream.Collectors.toSet());
        loaded.store().extensions().metadata().forEach((id, value) -> {
            if (existingIds.contains(id) && value != null) metadata.put(id, value);
        });
        this.storageCorrupted = loaded.corrupted();
        this.storageError = loaded.error();
        if (storageCorrupted) status = "饰品库损坏，自动保存已锁定";
        else if (!library.items().isEmpty()) status = "已恢复 " + library.items().size() + " 件饰品";
    }

    public List<AccessoryRecord> accessories() {
        return library.items();
    }

    public List<ReviewEntry> reviewQueue() {
        return List.copyOf(reviewQueue);
    }

    public boolean alwaysReview() {
        return alwaysReview;
    }

    public boolean saveContainerLocations() {
        return saveContainerLocations;
    }

    public boolean hasContainerLocations() {
        return metadata.values().stream().anyMatch(value -> value.location() != null && value.location().locatedBlock());
    }

    public boolean calculating() {
        return calculating;
    }

    public boolean plansDirty() {
        return plansDirty;
    }

    public boolean storageCorrupted() {
        return storageCorrupted;
    }

    public String storageError() {
        return storageError;
    }

    public String status() {
        return status;
    }

    public Path storageFile() {
        return storage.file();
    }

    public boolean hasCapturedContainer() {
        return capturedContainerAvailable;
    }

    public int capturedContainerSize() {
        return capturedContainer.size();
    }

    public Optional<LoadoutResult> loadout(WeaponMode weapon) {
        return analysis(weapon).map(LoadoutAnalysis::expected);
    }

    public Optional<LoadoutResult> loadout(WeaponMode weapon, PlanVariant variant) {
        return analysis(weapon).map(value -> value.result(variant));
    }

    public Optional<LoadoutAnalysis> analysis(WeaponMode weapon) {
        return Optional.ofNullable(analyses.get(weapon));
    }

    public Optional<StabilityProfile> stability(WeaponMode weapon, PlanVariant variant) {
        return analysis(weapon).map(value -> value.stability(variant));
    }

    public Optional<AccessoryScore> score(AccessoryRecord accessory, WeaponMode weapon) {
        return Optional.ofNullable(scores.getOrDefault(weapon, Map.of()).get(accessory.id()));
    }

    public boolean scoresReady() {
        return !plansDirty && scores.size() == WeaponMode.values().length
            && loadoutScores.size() == WeaponMode.values().length;
    }

    public Optional<LoadoutScore> loadoutScore(WeaponMode weapon, PlanVariant variant) {
        return Optional.ofNullable(loadoutScores.getOrDefault(weapon, Map.of()).get(variant));
    }

    public List<String> changeLog(WeaponMode weapon) {
        return changeLogs.getOrDefault(weapon, List.of());
    }

    public boolean selectedBy(AccessoryRecord accessory, WeaponMode weapon) {
        return selectedBy(accessory, weapon, PlanVariant.EXPECTED);
    }

    public boolean selectedBy(AccessoryRecord accessory, WeaponMode weapon, PlanVariant variant) {
        LoadoutResult result = loadout(weapon, variant).orElse(null);
        return result != null && result.accessories().values().stream().anyMatch(item -> item.id().equals(accessory.id()));
    }

    public ItemStack displayStack(AccessoryRecord accessory) {
        ItemStack transientStack = transientStacks.get(accessory.id());
        if (transientStack != null && !transientStack.isEmpty()) return transientStack.copy();
        AccessoryMetadata value = metadata.get(accessory.id());
        if (value != null && value.icon() != null) {
            Optional<ItemStack> rebuilt = IconSnapshotCodec.rebuild(value.icon());
            if (rebuilt.isPresent()) return rebuilt.get();
        }
        return Items.GRAY_DYE.getDefaultStack();
    }

    public boolean iconPending(AccessoryRecord accessory) {
        if (accessory == null || accessory.isBlank()) return false;
        ItemStack transientStack = transientStacks.get(accessory.id());
        if (transientStack != null && !transientStack.isEmpty()) return false;
        AccessoryMetadata value = metadata.get(accessory.id());
        return value == null || value.icon() == null || IconSnapshotCodec.rebuild(value.icon()).isEmpty();
    }

    public String sourceLabel(AccessoryRecord accessory) {
        if (accessory.isBlank()) return "未上传该部位，使用空白占位";
        AccessoryMetadata value = metadata.get(accessory.id());
        if (value != null && value.location() != null) {
            String location = locationLabel(value);
            String slot = safeSourceText(accessory.source().slotLabel(), "槽位 " + accessory.source().slotIndex());
            return location + " · " + slot;
        }
        String title = safeSourceText(
            transientSourceTitles.getOrDefault(accessory.id(), accessory.source().containerTitle()),
            accessory.source().kind().equals("inventory") ? "玩家物品栏" : "历史容器"
        );
        String slot = safeSourceText(accessory.source().slotLabel(), "槽位 " + accessory.source().slotIndex());
        return title + " · " + slot;
    }

    public String sourceGroupLabel(AccessoryRecord accessory) {
        if (accessory.isBlank()) return "空白部位";
        AccessoryMetadata value = metadata.get(accessory.id());
        if (value != null && value.location() != null) return locationLabel(value);
        return safeSourceText(
            transientSourceGroups.getOrDefault(accessory.id(), accessory.source().containerTitle()),
            accessory.source().kind().equals("inventory") ? "玩家物品栏" : "历史容器"
        );
    }

    public void setGuidanceInvalidator(Runnable invalidator) {
        guidanceInvalidator = invalidator == null ? () -> { } : invalidator;
    }

    public Map<String, Set<String>> guidanceSources(LoadoutResult loadout) {
        LinkedHashMap<String, Set<String>> result = new LinkedHashMap<>();
        if (loadout != null) {
            for (AccessoryRecord accessory : loadout.accessories().values()) {
                if (accessory.isBlank()) continue;
                AccessoryMetadata value = metadata.get(accessory.id());
                String source = value != null && value.location() != null && value.locationState() == LocationState.VERIFIED
                    ? SourceTextSanitizer.sanitize(value.location().displayLabel())
                    : SourceTextSanitizer.sanitize(transientSourceGroups.getOrDefault(accessory.id(), ""));
                if (!source.isBlank()) result.put(accessory.fingerprint(), Set.of(source));
            }
        }
        return Map.copyOf(result);
    }

    public FingerprintRead readFingerprint(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return new FingerprintRead(true, true, Optional.empty());
        if (client.player == null || client.world == null) return new FingerprintRead(false, false, Optional.empty());
        try {
            List<String> lines = stack.getTooltip(
                Item.TooltipContext.create(client.world), client.player, TooltipType.BASIC
            ).stream().map(Text::getString).toList();
            if (!TooltipParser.mayBeAccessory(lines)) return new FingerprintRead(true, true, Optional.empty());
            String itemId = Registries.ITEM.getId(stack.getItem()).toString();
            AccessorySource source = new AccessorySource("highlight", "当前页面", -1, "当前槽位");
            ParseResult parsed = parser.parse(lines, itemId, source, false);
            return new FingerprintRead(
                true,
                parsed.accepted(),
                Optional.of(parsed.accessory().fingerprint())
            );
        } catch (RuntimeException error) {
            return new FingerprintRead(false, false, Optional.empty());
        }
    }

    public void setAlwaysReview(boolean value) {
        alwaysReview = value;
        persist("已更新复核设置");
    }

    public void setSaveContainerLocations(boolean value) {
        if (saveContainerLocations == value) return;
        saveContainerLocations = value;
        if (!value) clearContainerLocationsInternal();
        persist(value ? "已开启本地容器位置记录" : "已关闭并清除容器坐标记录");
    }

    public void clearContainerLocations() {
        clearContainerLocationsInternal();
        persist("已清除容器坐标记录，饰品库未受影响");
    }

    public void recordBlockInteraction(World world, BlockHitResult hit) {
        if (!saveContainerLocations) return;
        interactionTracker.record(client, world, hit, currentServerScope());
    }

    public void observeHandledScreen(HandledScreen<?> screen) {
        if (screen == null) return;
        Object identity = screen.getScreenHandler();
        if (screen instanceof InventoryScreen) {
            interactionTracker.bind(identity, ContainerLocation.inventory(currentServerScope()));
            return;
        }
        Optional<Integer> preset = detectedPreset(screen);
        if (preset.isPresent()) {
            interactionTracker.bind(identity, ContainerLocation.preset(currentServerScope(), preset.get()));
            return;
        }
        if (!hasContainerSlots(screen)) return;
        if (saveContainerLocations) interactionTracker.consumeForScreen(client, identity);
    }

    public void onClientTick() {
        if (delayedInventoryIconRefresh < 0 || client.player == null || client.world == null) return;
        if (inventoryIconRefreshTicksRemaining-- <= 0) {
            finishInventoryIconRefresh();
            return;
        }
        List<ItemStack> current = inventoryStackSnapshot();
        if (sameStacks(inventorySyncSnapshot, current)) inventoryStableTicks++;
        else {
            inventorySyncSnapshot = current;
            inventoryStableTicks = 0;
        }
        if (delayedInventoryIconRefresh > 0) {
            delayedInventoryIconRefresh--;
            return;
        }
        if (inventoryStableTicks < 5) return;
        boolean retry = refreshInventoryIcons();
        if (retry && inventoryIconRefreshRetries-- > 0) {
            delayedInventoryIconRefresh = 20;
            inventoryStableTicks = 0;
        } else {
            finishInventoryIconRefresh();
        }
    }

    public ScanSummary scanInventory() {
        if (client.player == null || client.world == null) {
            status = "进入世界后才能扫描物品栏";
            return new ScanSummary(0, 0, 0, 0);
        }
        ContainerLocation inventory = ContainerLocation.inventory(currentServerScope());
        return scan(captureInventoryStacks(), Set.of(inventory.key()));
    }

    public void captureContainer(HandledScreen<?> screen) {
        if (client.player == null) return;
        observeHandledScreen(screen);
        PlayerInventory playerInventory = client.player.getInventory();
        ContainerSourceRegistry.PageSource pageSource = resolvePageSource(screen);
        ContainerLocation location = resolvePageLocation(screen);
        List<CapturedStack> captured = new ArrayList<>();
        for (Slot slot : screen.getScreenHandler().slots) {
            if (slot.inventory == playerInventory || !slot.isEnabled() || !slot.hasStack()) continue;
            captured.add(new CapturedStack(
                slot.getStack(),
                new AccessorySource("container", pageSource.persistedTitle(), slot.id, "槽位 " + slot.id),
                pageSource.displayTitle(),
                pageSource.displayTitle(),
                location
            ));
        }
        capturedContainer = List.copyOf(captured);
        capturedContainerLocationKeys = reliableLocation(location) ? Set.of(location.key()) : Set.of();
        capturedContainerAvailable = true;
    }

    public ScanSummary scanCapturedContainer() {
        if (!capturedContainerAvailable) {
            status = "当前没有已捕获的容器物品";
            return new ScanSummary(0, 0, 0, 0);
        }
        List<CapturedStack> combined = new ArrayList<>(capturedContainer);
        combined.addAll(captureInventoryStacks());
        Set<String> scannedLocationKeys = new java.util.HashSet<>(capturedContainerLocationKeys);
        scannedLocationKeys.add(ContainerLocation.inventory(currentServerScope()).key());
        return scan(combined, scannedLocationKeys);
    }

    public ContainerSourceRegistry.PageSource resolvePageSource(HandledScreen<?> screen) {
        observeHandledScreen(screen);
        if (screen instanceof InventoryScreen) {
            return new ContainerSourceRegistry.PageSource("玩家物品栏", "玩家物品栏", true);
        }
        ContainerLocation location = resolvePageLocation(screen);
        if (location.kind().equals("preset") || location.kind().equals("block")) {
            return new ContainerSourceRegistry.PageSource(
                location.kind().equals("preset") ? location.displayLabel() : "容器界面",
                location.displayLabel(),
                true
            );
        }
        return sourceRegistry.resolve(
            screen.getScreenHandler(),
            screen.getTitle().getString(),
            presetControls(screen)
        );
    }

    public ContainerLocation resolvePageLocation(HandledScreen<?> screen) {
        if (screen instanceof InventoryScreen) return ContainerLocation.inventory(currentServerScope());
        Optional<Integer> preset = detectedPreset(screen);
        if (preset.isPresent()) {
            ContainerLocation location = ContainerLocation.preset(currentServerScope(), preset.get());
            interactionTracker.bind(screen.getScreenHandler(), location);
            return location;
        }
        return interactionTracker.location(screen.getScreenHandler())
            .orElseGet(() -> ContainerLocation.unlocated("未定位容器"));
    }

    public void resetClientSession() {
        Set<String> conflictReviewIds = Set.copyOf(sourceConflicts.keySet());
        reviewQueue.removeIf(review -> conflictReviewIds.contains(review.id()));
        capturedContainer = List.of();
        capturedContainerLocationKeys = Set.of();
        capturedContainerAvailable = false;
        transientStacks.clear();
        transientSourceTitles.clear();
        transientSourceGroups.clear();
        sourceRegistry.clear();
        interactionTracker.clear();
        sourceConflicts.clear();
        sessionConfirmedReviews.clear();
        pendingCaptured.clear();
        pendingMetadata.clear();
        boolean changed = false;
        for (Map.Entry<String, AccessoryMetadata> entry : new ArrayList<>(metadata.entrySet())) {
            AccessoryMetadata value = entry.getValue();
            if (value.location() != null && value.locationState() == LocationState.VERIFIED) {
                metadata.put(entry.getKey(), value.withLocation(value.location(), LocationState.UNVERIFIED, value.lastSeenAt()));
                changed = true;
            }
        }
        delayedInventoryIconRefresh = 40;
        inventoryIconRefreshRetries = 8;
        inventoryIconRefreshTicksRemaining = 240;
        inventoryStableTicks = 0;
        inventorySyncSnapshot = inventoryStackSnapshot();
        if (changed) persist(null);
    }

    public void confirmReview(String reviewId, AccessoryRecord edited) {
        ReviewEntry entry = findReview(reviewId).orElse(null);
        if (entry == null) return;
        if (sourceConflicts.containsKey(reviewId)) {
            status = "请先在来源确认页选择移动记录或作为新副本";
            return;
        }
        AccessoryRecord normalized = edited.withIdentity(edited.id(), AccessoryFingerprint.compute(edited));
        String pendingId = entry.result().accessory().id();
        CapturedStack captured = pendingCaptured.get(pendingId);
        AccessoryLibrary.UpsertResult result = reconcileReviewed(reviewId, normalized, captured);
        if (result == null) return;
        ItemStack stack = transientStacks.remove(pendingId);
        String displayTitle = transientSourceTitles.remove(pendingId);
        String sourceGroup = transientSourceGroups.remove(pendingId);
        if (stack != null) transientStacks.put(result.accessory().id(), stack);
        if (displayTitle != null) transientSourceTitles.put(result.accessory().id(), displayTitle);
        if (sourceGroup != null) transientSourceGroups.put(result.accessory().id(), sourceGroup);
        transferPendingMetadata(pendingId, result.accessory().id(), captured, stack);
        pendingCaptured.remove(pendingId);
        pendingMetadata.remove(pendingId);
        reviewQueue.removeIf(review -> review.id().equals(reviewId));
        sessionConfirmedReviews.remember(entry.result(), result.accessory(), captured);
        switch (result.disposition()) {
            case ADDED -> {
                markPlansDirty();
                persist("已确认并入库：" + result.accessory().name());
            }
            case SOURCE_ENRICHED -> persist("饰品已在库内，已补充来源：" + result.accessory().name());
            case UNCHANGED -> persist("饰品已在库内，已移除待复核项：" + result.accessory().name());
        }
    }

    public void discardReview(String reviewId) {
        findReview(reviewId).ifPresent(entry -> {
            String pendingId = entry.result().accessory().id();
            transientStacks.remove(pendingId);
            transientSourceTitles.remove(pendingId);
            transientSourceGroups.remove(pendingId);
            pendingMetadata.remove(pendingId);
            pendingCaptured.remove(pendingId);
        });
        sourceConflicts.remove(reviewId);
        reviewQueue.removeIf(review -> review.id().equals(reviewId));
        status = "已丢弃待复核记录";
    }

    public Optional<ReviewEntry> findReview(String id) {
        return reviewQueue.stream().filter(review -> review.id().equals(id)).findFirst();
    }

    public Optional<AccessoryRecord> findAccessory(String id) {
        return library.find(id);
    }

    public Optional<SourceConflict> sourceConflict(String reviewId) {
        return Optional.ofNullable(sourceConflicts.get(reviewId));
    }

    public List<AccessoryRecord> sourceConflictCandidates(String reviewId) {
        SourceConflict conflict = sourceConflicts.get(reviewId);
        if (conflict == null) return List.of();
        return conflict.candidateIds().stream().map(library::find).flatMap(Optional::stream).toList();
    }

    public void resolveSourceConflict(String reviewId, String targetAccessoryId) {
        SourceConflict conflict = sourceConflicts.get(reviewId);
        ReviewEntry entry = findReview(reviewId).orElse(null);
        if (conflict == null || entry == null) return;
        String pendingId = entry.result().accessory().id();
        CapturedStack captured = pendingCaptured.get(pendingId);
        ParseResult parsedResult = entry.result();
        AccessoryRecord parsed = parsedResult.accessory();
        AccessoryRecord retained;
        boolean added;
        if (targetAccessoryId == null || targetAccessoryId.isBlank()) {
            retained = library.addDistinct(parsed);
            added = true;
        } else {
            AccessoryRecord target = library.find(targetAccessoryId).orElse(null);
            if (target == null || !target.fingerprint().equals(parsed.fingerprint())) return;
            retained = target.withSource(parsed.source());
            library.update(retained);
            retained = library.find(target.id()).orElse(retained);
            added = false;
        }
        ItemStack stack = transientStacks.remove(pendingId);
        transientSourceTitles.remove(pendingId);
        transientSourceGroups.remove(pendingId);
        if (stack != null) transientStacks.put(retained.id(), stack);
        transferPendingMetadata(pendingId, retained.id(), captured, stack);
        pendingCaptured.remove(pendingId);
        pendingMetadata.remove(pendingId);
        reviewQueue.removeIf(value -> value.id().equals(reviewId));
        sourceConflicts.remove(reviewId);
        sessionConfirmedReviews.remember(parsedResult, retained, captured);
        if (added) markPlansDirty();
        persist(added ? "已作为新副本入库：" + retained.name() : "已更新饰品最近确认来源：" + retained.name());
    }

    public void updateAccessory(AccessoryRecord edited) {
        AccessoryRecord previous = library.find(edited.id()).orElse(null);
        if (!library.update(edited)) return;
        AccessoryRecord updated = library.find(edited.id()).orElse(edited);
        if (previous != null) sessionConfirmedReviews.replaceConfirmed(previous.id(), updated);
        markPlansDirty();
        persist("已保存饰品修改");
    }

    public void deleteAccessory(String id) {
        AccessoryRecord removed = library.find(id).orElse(null);
        if (!library.remove(id)) return;
        if (removed != null) sessionConfirmedReviews.removeConfirmed(removed.id());
        transientStacks.remove(id);
        transientSourceTitles.remove(id);
        transientSourceGroups.remove(id);
        metadata.remove(id);
        markPlansDirty();
        persist("已删除饰品");
    }

    public void clearAll() {
        library.clear();
        sessionConfirmedReviews.clear();
        reviewQueue.clear();
        transientStacks.clear();
        transientSourceTitles.clear();
        transientSourceGroups.clear();
        metadata.clear();
        pendingMetadata.clear();
        pendingCaptured.clear();
        sourceConflicts.clear();
        analyses.clear();
        scores.clear();
        loadoutScores.clear();
        changeLogs.clear();
        markPlansDirty();
        persist("已清空待选饰品库和复核队列");
    }

    public void resetCorruptStorage() {
        try {
            Path backup = storage.backupCorruptAndReset();
            library.clear();
            reviewQueue.clear();
            transientStacks.clear();
            transientSourceTitles.clear();
            transientSourceGroups.clear();
            metadata.clear();
            pendingMetadata.clear();
            pendingCaptured.clear();
            sourceConflicts.clear();
            sessionConfirmedReviews.clear();
            analyses.clear();
            scores.clear();
            loadoutScores.clear();
            changeLogs.clear();
            alwaysReview = false;
            saveContainerLocations = true;
            locationSalt = new AccessoryStore.Settings(false).locationSalt();
            markPlansDirty();
            storageCorrupted = false;
            storageError = "";
            status = "损坏文件已备份为 " + backup.getFileName();
        } catch (IOException error) {
            status = "重建失败：" + safeMessage(error);
        }
    }

    public void calculateAsync() {
        if (calculating) return;
        guidanceInvalidator.run();
        List<AccessoryRecord> snapshot = library.items();
        long generation = ++calculationGeneration;
        calculating = true;
        status = "正在后台计算剑套和弓套";
        calculationExecutor.submit(() -> {
            try {
                LoadoutAnalysis bow = AccessoryCalculator.analyze(snapshot, WeaponMode.BOW);
                LoadoutAnalysis sword = AccessoryCalculator.analyze(snapshot, WeaponMode.SWORD);
                Map<String, AccessoryScore> bowScores = AccessoryScorer.scorePool(snapshot, bow.expected(), WeaponMode.BOW);
                Map<String, AccessoryScore> swordScores = AccessoryScorer.scorePool(snapshot, sword.expected(), WeaponMode.SWORD);
                Map<PlanVariant, LoadoutScore> bowLoadoutScores = scoreLoadoutVariants(bow, WeaponMode.BOW);
                Map<PlanVariant, LoadoutScore> swordLoadoutScores = scoreLoadoutVariants(sword, WeaponMode.SWORD);
                client.execute(() -> applyCalculation(
                    generation, bow, sword, bowScores, swordScores, bowLoadoutScores, swordLoadoutScores
                ));
            } catch (RuntimeException error) {
                client.execute(() -> applyCalculationFailure(generation, error));
            }
        });
    }

    private ScanSummary scan(List<CapturedStack> stacks, Set<String> explicitScannedLocations) {
        if (client.player == null || client.world == null) return new ScanSummary(0, 0, 0, 0);
        int matched = 0;
        int added = 0;
        int existing = 0;
        int review = 0;
        int failed = 0;
        boolean persistentChange = false;
        Set<String> seenExistingIds = new java.util.HashSet<>();
        Map<String, Set<String>> seenIdsByLocation = new LinkedHashMap<>();
        Set<String> scannedLocations = new java.util.HashSet<>(
            explicitScannedLocations == null ? Set.of() : explicitScannedLocations
        );
        Set<String> locationsWithReview = new java.util.HashSet<>();
        for (CapturedStack captured : stacks) {
            if (reliableLocation(captured.location())) scannedLocations.add(captured.location().key());
            try {
                List<String> lines = captured.stack()
                    .getTooltip(Item.TooltipContext.create(client.world), client.player, TooltipType.BASIC)
                    .stream()
                    .map(Text::getString)
                    .toList();
                if (!TooltipParser.mayBeAccessory(lines)) continue;
                matched++;
                String itemId = Registries.ITEM.getId(captured.stack().getItem()).toString();
                ParseResult parsed = parser.parse(lines, itemId, captured.source(), alwaysReview);
                Optional<AccessoryRecord> confirmation = sessionConfirmedReviews.find(parsed, captured);
                boolean accepted = parsed.accepted() || confirmation.isPresent();
                if (confirmation.isPresent()) {
                    // Reuse the edited record, not the parser's raw draft. This keeps
                    // a manual stat/type correction intact on every later scan.
                    AccessoryRecord trusted = confirmation.get().withSource(captured.source());
                    parsed = new ParseResult(
                        trusted, ParseState.ACCEPTED, parsed.detectedLevel(), parsed.allowedAffixCount(),
                        parsed.reasons(), parsed.rawLines(), parsed.suspiciousLines()
                    );
                }
                if (accepted) {
                    removeMatchingReviews(parsed.accessory(), captured.location());
                    AccessoryLibrary.UpsertResult result = reconcileAccepted(parsed, captured, seenExistingIds);
                    if (result == null) {
                        review++;
                        if (reliableLocation(captured.location())) locationsWithReview.add(captured.location().key());
                        continue;
                    }
                    transientStacks.put(result.accessory().id(), captured.stack().copy());
                    transientSourceTitles.put(result.accessory().id(), captured.displayContainerTitle());
                    transientSourceGroups.put(result.accessory().id(), captured.sourceGroupLabel());
                    boolean metadataChanged = updateMetadata(
                        result.accessory().id(), captured, LocationState.VERIFIED, true
                    );
                    persistentChange |= metadataChanged;
                    seenExistingIds.add(result.accessory().id());
                    if (reliableLocation(captured.location())) {
                        seenIdsByLocation.computeIfAbsent(captured.location().key(), ignored -> new java.util.HashSet<>())
                            .add(result.accessory().id());
                    }
                    switch (result.disposition()) {
                        case ADDED -> {
                            added++;
                            persistentChange = true;
                        }
                        case SOURCE_ENRICHED -> {
                            existing++;
                            persistentChange = true;
                        }
                        case UNCHANGED -> existing++;
                    }
                } else {
                    putReview(parsed, captured);
                    review++;
                    if (reliableLocation(captured.location())) locationsWithReview.add(captured.location().key());
                }
            } catch (RuntimeException error) {
                failed++;
                if (reliableLocation(captured.location())) locationsWithReview.add(captured.location().key());
            }
        }
        persistentChange |= markUnseenLocations(scannedLocations, locationsWithReview, seenIdsByLocation);
        ScanSummary scanSummary = new ScanSummary(matched, added, existing, review);
        String summary = scanSummary.message() + (failed > 0 ? "，读取失败 " + failed + " 件" : "");
        if (added > 0) {
            markPlansDirty();
        }
        if (persistentChange) {
            if (!persist(null)) {
                status = status + "；" + summary;
                return new ScanSummary(matched, added, existing, review);
            }
        }
        status = storageCorrupted ? "饰品库损坏，自动保存已锁定；" + summary : summary;
        return new ScanSummary(matched, added, existing, review);
    }

    private AccessoryLibrary.UpsertResult reconcileAccepted(
        ParseResult parsed,
        CapturedStack captured,
        Set<String> seenExistingIds
    ) {
        AccessoryRecord scanned = parsed.accessory();
        List<AccessoryRecord> matches = accessoriesWithFingerprint(scanned.fingerprint());
        if (!reliableLocation(captured.location())) return library.upsertScanned(scanned);

        List<AccessoryRecord> exact = matches.stream()
            .filter(existing -> sameLocation(existing.id(), captured.location()))
            .filter(existing -> existing.source().slotIndex() == scanned.source().slotIndex())
            .toList();
        if (exact.size() == 1) return refreshExistingSource(exact.getFirst(), scanned.source());
        if (exact.size() > 1) {
            createSourceConflict(parsed, captured, exact);
            return null;
        }
        if (matches.isEmpty()) return library.upsertScanned(scanned);
        if (matches.size() == 1) {
            AccessoryRecord existing = matches.getFirst();
            if (seenExistingIds.contains(existing.id())) return library.upsertScanned(scanned);
            return refreshExistingSource(existing, scanned.source());
        }
        createSourceConflict(parsed, captured, matches);
        return null;
    }

    private AccessoryLibrary.UpsertResult reconcileReviewed(
        String reviewId,
        AccessoryRecord scanned,
        CapturedStack captured
    ) {
        if (captured == null || !reliableLocation(captured.location())) return library.upsertScanned(scanned);
        List<AccessoryRecord> matches = accessoriesWithFingerprint(scanned.fingerprint());
        List<AccessoryRecord> exact = matches.stream()
            .filter(existing -> sameLocation(existing.id(), captured.location()))
            .filter(existing -> existing.source().slotIndex() == scanned.source().slotIndex())
            .toList();
        if (exact.size() == 1) return refreshExistingSource(exact.getFirst(), scanned.source());
        if (matches.isEmpty()) return library.upsertScanned(scanned);
        if (matches.size() == 1) return refreshExistingSource(matches.getFirst(), scanned.source());

        sourceConflicts.put(reviewId, new SourceConflict(
            reviewId,
            scanned.name(),
            matches.stream().map(AccessoryRecord::id).toList()
        ));
        replaceReviewReason(reviewId, "source-confirmation-required");
        status = "同款饰品有多个副本，请确认本次来源对应哪条记录";
        return null;
    }

    private AccessoryLibrary.UpsertResult refreshExistingSource(AccessoryRecord existing, AccessorySource source) {
        AccessoryRecord refreshed = existing.withSource(source);
        boolean changed = !existing.source().equals(source);
        if (changed) library.update(refreshed);
        AccessoryRecord retained = library.find(existing.id()).orElse(refreshed);
        return new AccessoryLibrary.UpsertResult(
            retained,
            changed ? AccessoryLibrary.UpsertDisposition.SOURCE_ENRICHED : AccessoryLibrary.UpsertDisposition.UNCHANGED
        );
    }

    private void createSourceConflict(ParseResult parsed, CapturedStack captured, List<AccessoryRecord> candidates) {
        List<String> reasons = new ArrayList<>(parsed.reasons());
        reasons.add("source-confirmation-required");
        ParseResult conflictResult = new ParseResult(
            parsed.accessory(), ParseState.NEEDS_REVIEW, parsed.detectedLevel(), parsed.allowedAffixCount(),
            reasons, parsed.rawLines(), parsed.suspiciousLines()
        );
        ReviewEntry entry = putReview(conflictResult, captured);
        sourceConflicts.put(entry.id(), new SourceConflict(
            entry.id(),
            parsed.accessory().name(),
            candidates.stream().map(AccessoryRecord::id).toList()
        ));
    }

    private void replaceReviewReason(String reviewId, String reason) {
        for (int index = 0; index < reviewQueue.size(); index++) {
            ReviewEntry entry = reviewQueue.get(index);
            if (!entry.id().equals(reviewId)) continue;
            ParseResult old = entry.result();
            List<String> reasons = new ArrayList<>(old.reasons());
            if (!reasons.contains(reason)) reasons.add(reason);
            reviewQueue.set(index, new ReviewEntry(reviewId, new ParseResult(
                old.accessory(), ParseState.NEEDS_REVIEW, old.detectedLevel(), old.allowedAffixCount(),
                reasons, old.rawLines(), old.suspiciousLines()
            )));
            return;
        }
    }

    private List<AccessoryRecord> accessoriesWithFingerprint(String fingerprint) {
        return library.items().stream().filter(item -> item.fingerprint().equals(fingerprint)).toList();
    }

    private boolean sameLocation(String accessoryId, ContainerLocation location) {
        AccessoryMetadata value = metadata.get(accessoryId);
        return value != null && value.location() != null && value.location().key().equals(location.key());
    }

    private boolean updateMetadata(
        String accessoryId,
        CapturedStack captured,
        LocationState locationState,
        boolean allowIconDowngrade
    ) {
        AccessoryMetadata old = metadata.getOrDefault(accessoryId, AccessoryMetadata.empty());
        IconSnapshot candidate = IconSnapshotCodec.capture(captured.stack()).orElse(null);
        IconSnapshot icon = IconSnapshotCodec.preferSnapshot(old.icon(), candidate, allowIconDowngrade);
        ContainerLocation location = old.location();
        LocationState state = old.locationState();
        if (reliableLocation(captured.location())
            && (saveContainerLocations || !captured.location().locatedBlock())) {
            location = captured.location();
            state = locationState;
        }
        AccessoryMetadata comparable = new AccessoryMetadata(icon, location, state, old.lastSeenAt());
        if (comparable.equals(old)) return false;
        metadata.put(accessoryId, new AccessoryMetadata(icon, location, state, Instant.now().toString()));
        return true;
    }

    private void transferPendingMetadata(
        String pendingId,
        String targetId,
        CapturedStack captured,
        ItemStack transientStack
    ) {
        AccessoryMetadata pending = pendingMetadata.remove(pendingId);
        if (pending != null && pending.icon() != null) {
            AccessoryMetadata old = metadata.getOrDefault(targetId, AccessoryMetadata.empty());
            metadata.put(targetId, old.withIcon(pending.icon()));
        }
        if (captured != null) updateMetadata(targetId, captured, LocationState.VERIFIED, true);
        else if (transientStack != null) {
            IconSnapshotCodec.capture(transientStack).ifPresent(icon -> {
                AccessoryMetadata old = metadata.getOrDefault(targetId, AccessoryMetadata.empty());
                metadata.put(targetId, old.withIcon(icon));
            });
        }
    }

    private boolean markUnseenLocations(
        Set<String> scannedLocations,
        Set<String> locationsWithReview,
        Map<String, Set<String>> seenIdsByLocation
    ) {
        boolean changed = false;
        for (Map.Entry<String, AccessoryMetadata> entry : new ArrayList<>(metadata.entrySet())) {
            AccessoryMetadata value = entry.getValue();
            if (value.location() == null || value.locationState() != LocationState.VERIFIED) continue;
            String key = value.location().key();
            if (!scannedLocations.contains(key) || locationsWithReview.contains(key)) continue;
            if (seenIdsByLocation.getOrDefault(key, Set.of()).contains(entry.getKey())) continue;
            metadata.put(entry.getKey(), value.withLocation(value.location(), LocationState.UNVERIFIED, value.lastSeenAt()));
            transientSourceTitles.remove(entry.getKey());
            transientSourceGroups.remove(entry.getKey());
            changed = true;
        }
        return changed;
    }

    private List<CapturedStack> captureInventoryStacks() {
        if (client.player == null) return List.of();
        PlayerInventory inventory = client.player.getInventory();
        List<CapturedStack> stacks = new ArrayList<>();
        for (int index = 0; index < inventory.size(); index++) {
            ItemStack stack = inventory.getStack(index);
            if (stack.isEmpty()) continue;
            stacks.add(new CapturedStack(
                stack,
                new AccessorySource("inventory", "玩家物品栏", index, inventorySlotLabel(index)),
                "玩家物品栏",
                "玩家物品栏",
                ContainerLocation.inventory(currentServerScope())
            ));
        }
        return stacks;
    }

    private ReviewEntry putReview(ParseResult parsed, CapturedStack captured) {
        for (int index = 0; index < reviewQueue.size(); index++) {
            ReviewEntry existing = reviewQueue.get(index);
            AccessoryRecord old = existing.result().accessory();
            AccessoryRecord next = parsed.accessory();
            CapturedStack oldCapture = pendingCaptured.get(old.id());
            boolean sameReliableSource = oldCapture != null
                && reliableLocation(oldCapture.location())
                && reliableLocation(captured.location())
                && oldCapture.location().key().equals(captured.location().key())
                && old.source().slotIndex() == next.source().slotIndex();
            boolean bothReliable = oldCapture != null
                && reliableLocation(oldCapture.location())
                && reliableLocation(captured.location());
            boolean sameSource = bothReliable
                ? sameReliableSource
                : old.source().stableKey().equals(next.source().stableKey());
            if (old.fingerprint().equals(next.fingerprint())
                && sameSource) {
                AccessoryRecord retained = next.withIdentity(old.id(), next.fingerprint());
                ParseResult refreshed = new ParseResult(
                    retained,
                    parsed.state(),
                    parsed.detectedLevel(),
                    parsed.allowedAffixCount(),
                    parsed.reasons(),
                    parsed.rawLines(),
                    parsed.suspiciousLines()
                );
                reviewQueue.set(index, new ReviewEntry(existing.id(), refreshed));
                transientStacks.put(retained.id(), captured.stack().copy());
                transientSourceTitles.put(retained.id(), captured.displayContainerTitle());
                transientSourceGroups.put(retained.id(), captured.sourceGroupLabel());
                pendingCaptured.put(retained.id(), captured);
                IconSnapshotCodec.capture(captured.stack()).ifPresent(icon ->
                    pendingMetadata.put(retained.id(), AccessoryMetadata.empty().withIcon(icon))
                );
                return reviewQueue.get(index);
            }
        }
        String reviewId = UUID.randomUUID().toString();
        ReviewEntry created = new ReviewEntry(reviewId, parsed);
        reviewQueue.add(created);
        transientStacks.put(parsed.accessory().id(), captured.stack().copy());
        transientSourceTitles.put(parsed.accessory().id(), captured.displayContainerTitle());
        transientSourceGroups.put(parsed.accessory().id(), captured.sourceGroupLabel());
        pendingCaptured.put(parsed.accessory().id(), captured);
        IconSnapshotCodec.capture(captured.stack()).ifPresent(icon ->
            pendingMetadata.put(parsed.accessory().id(), AccessoryMetadata.empty().withIcon(icon))
        );
        return created;
    }

    private void removeMatchingReviews(AccessoryRecord accepted, ContainerLocation location) {
        reviewQueue.removeIf(review -> {
            AccessoryRecord pending = review.result().accessory();
            CapturedStack pendingCapture = pendingCaptured.get(pending.id());
            boolean sameLocation = pendingCapture != null
                && reliableLocation(pendingCapture.location())
                && reliableLocation(location)
                && pendingCapture.location().key().equals(location.key())
                && pending.source().slotIndex() == accepted.source().slotIndex();
            boolean bothReliable = pendingCapture != null
                && reliableLocation(pendingCapture.location())
                && reliableLocation(location);
            boolean matches = pending.fingerprint().equals(accepted.fingerprint())
                && (bothReliable ? sameLocation : pending.source().stableKey().equals(accepted.source().stableKey()));
            if (matches) {
                transientStacks.remove(pending.id());
                transientSourceTitles.remove(pending.id());
                transientSourceGroups.remove(pending.id());
                pendingMetadata.remove(pending.id());
                pendingCaptured.remove(pending.id());
            }
            return matches;
        });
    }

    public void observeVisiblePage(
        HandledScreen<?> screen,
        Map<Integer, String> visibleFingerprints,
        Set<Integer> failedFingerprintSlots
    ) {
        if (screen == null || visibleFingerprints == null) return;
        ContainerLocation location = resolvePageLocation(screen);
        if (!reliableLocation(location)) return;
        ContainerSourceRegistry.PageSource pageSource = resolvePageSource(screen);
        PlayerInventory playerInventory = client.player == null ? null : client.player.getInventory();
        ContainerLocation inventoryLocation = ContainerLocation.inventory(currentServerScope());
        ContainerSourceRegistry.PageSource inventorySource = new ContainerSourceRegistry.PageSource(
            "玩家物品栏", "玩家物品栏", true
        );
        Map<Integer, Slot> slots = new HashMap<>();
        for (Slot slot : screen.getScreenHandler().slots) slots.put(slot.id, slot);
        boolean changed = false;

        for (Map.Entry<Integer, String> visible : visibleFingerprints.entrySet()) {
            Slot slot = slots.get(visible.getKey());
            if (slot == null || !slot.hasStack()) continue;
            boolean inventorySlot = playerInventory != null && slot.inventory == playerInventory;
            ContainerLocation slotLocation = inventorySlot ? inventoryLocation : location;
            ContainerSourceRegistry.PageSource slotPageSource = inventorySlot ? inventorySource : pageSource;
            int sourceSlotIndex = inventorySlot ? slot.getIndex() : slot.id;
            List<AccessoryRecord> matches = accessoriesWithFingerprint(visible.getValue());
            List<AccessoryRecord> exactMatches = matches.stream()
                .filter(item -> sameLocation(item.id(), slotLocation))
                .filter(item -> item.source().slotIndex() == sourceSlotIndex)
                .toList();
            AccessoryRecord target = exactMatches.size() == 1
                ? exactMatches.getFirst()
                : exactMatches.isEmpty() && matches.size() == 1 ? matches.getFirst() : null;
            if (target == null) continue;
            if (failedFingerprintSlots != null && failedFingerprintSlots.contains(slot.id)) continue;
            String kind = inventorySlot ? "inventory" : "container";
            AccessorySource source = new AccessorySource(
                kind,
                slotPageSource.persistedTitle(),
                sourceSlotIndex,
                kind.equals("inventory") ? inventorySlotLabel(sourceSlotIndex) : "槽位 " + sourceSlotIndex
            );
            AccessoryLibrary.UpsertResult refreshed = refreshExistingSource(target, source);
            changed |= refreshed.disposition() == AccessoryLibrary.UpsertDisposition.SOURCE_ENRICHED;
            CapturedStack captured = new CapturedStack(
                slot.getStack(), source, slotPageSource.displayTitle(), slotPageSource.displayTitle(), slotLocation
            );
            boolean iconDowngrade = iconRefreshBlocked(target.id(), captured.stack());
            changed |= updateMetadata(target.id(), captured, LocationState.VERIFIED, false);
            if (!iconDowngrade) transientStacks.put(target.id(), slot.getStack().copy());
            transientSourceTitles.put(target.id(), slotPageSource.displayTitle());
            transientSourceGroups.put(target.id(), slotPageSource.displayTitle());
        }

        if (changed) persist(null);
    }

    public Optional<String> guidanceSourceForAccessory(String accessoryId) {
        AccessoryMetadata value = metadata.get(accessoryId);
        String label = value != null && value.location() != null && value.locationState() == LocationState.VERIFIED
            ? SourceTextSanitizer.sanitize(value.location().displayLabel())
            : SourceTextSanitizer.sanitize(transientSourceGroups.getOrDefault(accessoryId, ""));
        return label.isBlank() ? Optional.empty() : Optional.of(label);
    }

    private boolean refreshInventoryIcons() {
        if (client.player == null || client.world == null) return true;
        boolean changed = false;
        boolean retry = false;
        Set<String> refreshedIds = new java.util.HashSet<>();
        for (CapturedStack captured : captureInventoryStacks()) {
            try {
                List<String> lines = captured.stack().getTooltip(
                    Item.TooltipContext.create(client.world), client.player, TooltipType.BASIC
                ).stream().map(Text::getString).toList();
                if (!TooltipParser.mayBeAccessory(lines)) continue;
                String itemId = Registries.ITEM.getId(captured.stack().getItem()).toString();
                AccessoryRecord parsed = parser.parse(lines, itemId, captured.source(), false).accessory();
                List<AccessoryRecord> matches = accessoriesWithFingerprint(parsed.fingerprint());
                List<AccessoryRecord> exactMatches = matches.stream()
                    .filter(item -> item.source().kind().equals("inventory"))
                    .filter(item -> item.source().slotIndex() == parsed.source().slotIndex())
                    .toList();
                AccessoryRecord target = exactMatches.size() == 1
                    ? exactMatches.getFirst()
                    : exactMatches.isEmpty() && matches.size() == 1 ? matches.getFirst() : null;
                if (target == null) {
                    retry |= !matches.isEmpty();
                    continue;
                }
                refreshedIds.add(target.id());
                AccessoryLibrary.UpsertResult refreshed = refreshExistingSource(target, parsed.source());
                changed |= refreshed.disposition() == AccessoryLibrary.UpsertDisposition.SOURCE_ENRICHED;
                boolean iconDowngrade = iconRefreshBlocked(target.id(), captured.stack());
                changed |= updateMetadata(target.id(), captured, LocationState.VERIFIED, false);
                if (iconDowngrade) retry = true;
                else transientStacks.put(target.id(), captured.stack().copy());
            } catch (RuntimeException ignored) {
                // A delayed refresh is best effort and must not disturb the current session.
                retry = true;
            }
        }
        for (Map.Entry<String, AccessoryMetadata> entry : metadata.entrySet()) {
            ContainerLocation location = entry.getValue().location();
            if (location != null && location.kind().equals("inventory") && !refreshedIds.contains(entry.getKey())) {
                retry = true;
            }
        }
        if (changed) persist(null);
        return retry;
    }

    private boolean iconRefreshBlocked(String accessoryId, ItemStack stack) {
        IconSnapshot previous = metadata.getOrDefault(accessoryId, AccessoryMetadata.empty()).icon();
        IconSnapshot candidate = IconSnapshotCodec.capture(stack).orElse(null);
        return previous != null
            && previous.hasVisualComponents()
            && !previous.equals(candidate);
    }

    private List<ItemStack> inventoryStackSnapshot() {
        if (client.player == null) return List.of();
        PlayerInventory inventory = client.player.getInventory();
        List<ItemStack> result = new ArrayList<>(inventory.size());
        for (int index = 0; index < inventory.size(); index++) result.add(inventory.getStack(index).copy());
        return List.copyOf(result);
    }

    private static boolean sameStacks(List<ItemStack> left, List<ItemStack> right) {
        if (left.size() != right.size()) return false;
        for (int index = 0; index < left.size(); index++) {
            if (!ItemStack.areEqual(left.get(index), right.get(index))) return false;
        }
        return true;
    }

    private void finishInventoryIconRefresh() {
        delayedInventoryIconRefresh = -1;
        inventoryIconRefreshRetries = 0;
        inventoryIconRefreshTicksRemaining = 0;
        inventoryStableTicks = 0;
        inventorySyncSnapshot = List.of();
    }

    private List<PresetSourceDetector.Control> presetControls(HandledScreen<?> screen) {
        List<PresetSourceDetector.Control> controls = new ArrayList<>();
        PlayerInventory playerInventory = client.player == null ? null : client.player.getInventory();
        for (Slot slot : screen.getScreenHandler().slots) {
            if (slot.inventory == playerInventory || !slot.isEnabled() || !slot.hasStack()) continue;
            ItemStack stack = slot.getStack();
            controls.add(new PresetSourceDetector.Control(
                Registries.ITEM.getId(stack.getItem()).toString(),
                stack.getName().getString()
            ));
        }
        return controls;
    }

    private Optional<Integer> detectedPreset(HandledScreen<?> screen) {
        java.util.OptionalInt detected = PresetSourceDetector.detect(presetControls(screen)).presetNumber();
        return detected.isPresent() ? Optional.of(detected.getAsInt()) : Optional.empty();
    }

    private boolean hasContainerSlots(HandledScreen<?> screen) {
        if (client.player == null) return false;
        PlayerInventory inventory = client.player.getInventory();
        return screen.getScreenHandler().slots.stream().anyMatch(slot -> slot.inventory != inventory);
    }

    private String currentServerScope() {
        return ServerScopeHasher.currentScope(client, locationSalt);
    }

    private static boolean reliableLocation(ContainerLocation location) {
        return location != null && !location.kind().equals("unlocated");
    }

    private String locationLabel(AccessoryMetadata value) {
        String label = safeSourceText(value.location().displayLabel(), "来源待确认");
        return switch (value.locationState()) {
            case VERIFIED -> label;
            case UNVERIFIED -> "来源待确认（上次记录：" + label + "）";
            case AMBIGUOUS -> "来源待确认";
        };
    }

    private void clearContainerLocationsInternal() {
        for (Map.Entry<String, AccessoryMetadata> entry : new ArrayList<>(metadata.entrySet())) {
            metadata.put(entry.getKey(), entry.getValue().withoutBlockLocation());
        }
        transientSourceTitles.clear();
        transientSourceGroups.clear();
        interactionTracker.clear();
    }

    private void applyCalculation(
        long generation,
        LoadoutAnalysis bow,
        LoadoutAnalysis sword,
        Map<String, AccessoryScore> bowScores,
        Map<String, AccessoryScore> swordScores,
        Map<PlanVariant, LoadoutScore> bowLoadoutScores,
        Map<PlanVariant, LoadoutScore> swordLoadoutScores
    ) {
        if (generation != calculationGeneration) return;
        EnumMap<WeaponMode, LoadoutAnalysis> previous = new EnumMap<>(analyses);
        analyses.put(WeaponMode.BOW, bow);
        analyses.put(WeaponMode.SWORD, sword);
        scores.put(WeaponMode.BOW, Map.copyOf(bowScores));
        scores.put(WeaponMode.SWORD, Map.copyOf(swordScores));
        loadoutScores.put(WeaponMode.BOW, Map.copyOf(bowLoadoutScores));
        loadoutScores.put(WeaponMode.SWORD, Map.copyOf(swordLoadoutScores));
        changeLogs.put(WeaponMode.BOW, buildChangeLog(previous.containsKey(WeaponMode.BOW) ? previous.get(WeaponMode.BOW).expected() : null, bow.expected()));
        changeLogs.put(WeaponMode.SWORD, buildChangeLog(previous.containsKey(WeaponMode.SWORD) ? previous.get(WeaponMode.SWORD).expected() : null, sword.expected()));
        calculating = false;
        plansDirty = false;
        status = "计算完成：弓套 " + format(bow.expected().breakdown().expected())
            + "，剑套 " + format(sword.expected().breakdown().expected());
    }

    private void applyCalculationFailure(long generation, RuntimeException error) {
        if (generation != calculationGeneration) return;
        calculating = false;
        plansDirty = true;
        status = "计算失败：" + safeMessage(error);
    }

    private List<String> buildChangeLog(LoadoutResult previous, LoadoutResult current) {
        if (previous == null) return List.of("首次生成方案");
        List<String> changes = new ArrayList<>();
        for (AccessorySlot slot : AccessorySlot.values()) {
            AccessoryRecord old = previous.accessories().get(slot);
            AccessoryRecord next = current.accessories().get(slot);
            if (!old.id().equals(next.id())) {
                changes.add(slot.label() + "：" + changeLabel(old) + " -> " + changeLabel(next));
            }
        }
        return changes.isEmpty() ? List.of("本次方案没有变化") : List.copyOf(changes);
    }

    private String changeLabel(AccessoryRecord accessory) {
        if (accessory.isBlank()) return accessory.name();
        return accessory.name() + " [" + sourceLabel(accessory) + "]";
    }

    private void markPlansDirty() {
        plansDirty = true;
        calculationGeneration++;
        calculating = false;
        guidanceInvalidator.run();
    }

    private static Map<PlanVariant, LoadoutScore> scoreLoadoutVariants(LoadoutAnalysis analysis, WeaponMode weapon) {
        EnumMap<PlanVariant, LoadoutScore> result = new EnumMap<>(PlanVariant.class);
        result.put(PlanVariant.EXPECTED, AccessoryScorer.scoreLoadout(analysis.expected(), weapon));
        analysis.stable().ifPresent(loadout -> result.put(
            PlanVariant.STABLE,
            AccessoryScorer.scoreLoadout(loadout, weapon)
        ));
        return Map.copyOf(result);
    }

    private boolean persist(String successStatus) {
        if (storageCorrupted) {
            status = "饰品库损坏，自动保存已锁定";
            return false;
        }
        try {
            storage.save(
                new AccessoryStore.Settings(alwaysReview, saveContainerLocations, locationSalt),
                library.items(),
                metadata
            );
            if (successStatus != null) status = successStatus;
            return true;
        } catch (IOException error) {
            status = "保存失败：" + safeMessage(error);
            return false;
        }
    }

    private static String inventorySlotLabel(int index) {
        if (index < 9) return "快捷栏 " + (index + 1);
        if (index < 36) return "物品栏 " + (index - 8);
        if (index == 40) return "副手";
        return "装备槽位 " + (index - 35);
    }

    private static String safeMessage(Exception error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }

    private static String safeSourceText(String value, String fallback) {
        String sanitized = SourceTextSanitizer.sanitize(value);
        return sanitized.isBlank() ? fallback : sanitized;
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }

    @Override
    public void close() {
        calculationGeneration++;
        calculating = false;
        guidanceInvalidator.run();
        calculationExecutor.shutdownNow();
    }

    public record ScanSummary(int matched, int added, int existing, int needsReview) {
        public String message() {
            return "识别 " + matched + " 件：新增 " + added + " 件，库内已有 " + existing
                + " 件，待复核 " + needsReview + " 件";
        }
    }

    public record SourceConflict(String reviewId, String accessoryName, List<String> candidateIds) {
        public SourceConflict {
            candidateIds = candidateIds == null ? List.of() : List.copyOf(candidateIds);
        }
    }

    public record FingerprintRead(boolean succeeded, boolean reliable, Optional<String> fingerprint) {
        public FingerprintRead {
            fingerprint = fingerprint == null ? Optional.empty() : fingerprint;
        }
    }
}
