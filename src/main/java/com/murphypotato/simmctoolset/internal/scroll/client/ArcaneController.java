package com.murphypotato.simmctoolset.internal.scroll.client;

import com.murphypotato.simmctoolset.internal.scroll.config.ArcaneSettings;
import com.murphypotato.simmctoolset.internal.scroll.config.SettingsStorage;
import com.murphypotato.simmctoolset.internal.scroll.config.ScrollUsageStore;
import com.murphypotato.simmctoolset.internal.scroll.config.PresetStore;
import com.murphypotato.simmctoolset.internal.scroll.domain.PresetPlan;
import com.murphypotato.simmctoolset.internal.scroll.domain.ArcaneSolver;
import com.murphypotato.simmctoolset.internal.scroll.domain.CraftPlan;
import com.murphypotato.simmctoolset.internal.scroll.domain.GameData;
import com.murphypotato.simmctoolset.internal.scroll.domain.Material;
import com.murphypotato.simmctoolset.internal.scroll.domain.ScrollRecipe;
import com.murphypotato.simmctoolset.internal.scroll.domain.ScrollPlanningRequest;
import com.murphypotato.simmctoolset.internal.scroll.domain.PlanningResult;
import com.murphypotato.simmctoolset.internal.scroll.domain.UsageCommitRequest;
import com.murphypotato.simmctoolset.internal.scroll.domain.UsagePlanInput;
import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public final class ArcaneController implements AutoCloseable {
    private final GameData data;
    private final SettingsStorage storage;
    private final ScrollUsageStore usageStore;
    private final PresetStore presetStore;
    private final ExecutorService executor;
    private final AtomicLong generation = new AtomicLong();
    private volatile ArcaneSettings settings;
    private volatile Future<?> running;
    private volatile boolean calculating;
    private volatile String status = "就绪";

    public ArcaneController(GameData data, SettingsStorage storage) {
        this(data, storage, storage.file().resolveSibling("scroll-usage.json"));
    }

    public ArcaneController(GameData data, SettingsStorage storage, java.nio.file.Path usageFile) {
        this.data = data;
        this.storage = storage;
        this.usageStore = new ScrollUsageStore(usageFile);
        this.presetStore = new PresetStore(usageFile.resolveSibling("scroll-presets.json"));
        this.settings = storage.load();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "simmc-arcane-scroll-solver");
            thread.setDaemon(true);
            return thread;
        };
        executor = Executors.newSingleThreadExecutor(factory);
        if (storage.lastCorruptBackup() != null) status = "设置文件损坏，已备份并恢复默认值";
    }

    public GameData data() {
        return data;
    }

    public ArcaneSettings settings() {
        return settings;
    }

    public boolean calculating() {
        return calculating;
    }

    public String status() {
        return status;
    }

    public ScrollUsageStore usageStore() {
        return usageStore;
    }

    public PresetStore presetStore() {
        return presetStore;
    }

    public synchronized void savePreset(PresetPlan preset) {
        try { presetStore.save(preset); status = "预设已保存"; }
        catch (IOException error) { status = "预设保存失败：" + safeMessage(error); }
    }

    public synchronized void deletePreset(String name) {
        try { presetStore.delete(name); status = "预设已删除"; }
        catch (IOException error) { status = "预设删除失败：" + safeMessage(error); }
    }

    public synchronized void renamePreset(String oldName, String newName) {
        try { presetStore.rename(oldName, newName); status = "预设已改名"; }
        catch (IOException | RuntimeException error) { status = "预设改名失败：" + safeMessage(error); }
    }

    public Optional<UUID> playerId(MinecraftClient client) {
        return client != null && client.player != null ? Optional.of(client.player.getUuid()) : Optional.empty();
    }

    /** Commits only user-confirmed material usage; previews never call this. */
    public synchronized ScrollUsageStore.CommitResult commitUsage(UsageCommitRequest request) throws IOException {
        ScrollUsageStore.CommitResult result = usageStore.commit(request);
        status = result.duplicate() ? "使用记录已保存（重复确认已忽略）" : "使用记录已保存";
        return result;
    }

    public synchronized void updateSettings(ArcaneSettings next) {
        settings = next;
        try {
            storage.save(next);
        } catch (IOException error) {
            status = "设置保存失败：" + safeMessage(error);
        }
    }

    public synchronized void calculate(MinecraftClient client, Consumer<CalculationResult> onResult) {
        calculate(client, client != null && client.player != null ? client.player.getUuid() : null, onResult);
    }

    public synchronized void calculate(MinecraftClient client, UUID playerId, Consumer<CalculationResult> onResult) {
        cancelLocked("已取消过期计算");
        long token = generation.incrementAndGet();
        ArcaneSettings snapshot = settings;
        ScrollRecipe recipe = data.recipe(snapshot.selectedRecipe());
        List<Material> enabled = data.materials().stream()
            .filter(material -> !snapshot.excludedMaterials().contains(material.name()))
            .toList();
        calculating = true;
        status = "计算中…";
        running = executor.submit(() -> {
            long started = System.nanoTime();
            try {
                Map<String, Integer> currentUsage = playerId == null ? Map.of() : usageStore.snapshot(playerId).totals();
                ScrollPlanningRequest request = new ScrollPlanningRequest(
                    recipe, enabled, snapshot.quantity(), currentUsage, Map.of(),
                    snapshot.excludedMaterials(), snapshot.searchBudget(), () -> generation.get() != token
                );
                PlanningResult planning = com.murphypotato.simmctoolset.internal.scroll.domain.DecayPlanner.plan(request);
                List<CraftPlan> plans = planning.candidatePlans();
                long elapsed = System.nanoTime() - started;
                client.execute(() -> {
                    if (generation.get() != token) return;
                    calculating = false;
                    status = planning.plan().complete() ? "完成：" + planning.plan().batches().size() + " 个批次"
                        : planning.status() == com.murphypotato.simmctoolset.internal.scroll.domain.PlanningStatus.TIMED_OUT
                        ? "达到时间预算，已返回当前最佳方案" : "未找到完整可行方案";
                    onResult.accept(new CalculationResult(
                        recipe, plans, snapshot.quantity(), snapshot.includeMainMaterial(), snapshot.repeatThreshold(), elapsed, planning
                    ));
                });
            } catch (ArcaneSolver.CalculationCancelledException ignored) {
                // New input owns the next UI update.
            } catch (com.murphypotato.simmctoolset.internal.scroll.domain.DecayPlanner.PlanningCancelledException ignored) {
                // New input owns the next UI update.
            } catch (RuntimeException error) {
                client.execute(() -> {
                    if (generation.get() != token) return;
                    calculating = false;
                    status = "计算失败：" + safeMessage(error);
                });
            }
        });
    }

    public UsageCommitRequest commitRequest(UUID playerId, CalculationResult result, boolean autoMode,
                                            boolean modified, UUID transactionId) {
        if (playerId == null || result == null || result.planning() == null) {
            throw new IllegalArgumentException("缺少玩家或计划上下文");
        }
        var evaluated = result.planning().plan();
        List<UsagePlanInput> inputs = new ArrayList<>();
        evaluated.batches().forEach(batch -> inputs.add(new UsagePlanInput(batch.crafts(), batch.plan().materials())));
        Map<String, Integer> actual = new java.util.LinkedHashMap<>();
        for (var batch : evaluated.batches()) {
            batch.plan().materials().forEach((name, amount) ->
                actual.merge(name, Math.multiplyExact(amount, batch.crafts()), Math::addExact));
        }
        var snapshot = usageStore.snapshot(playerId);
        int afterM = evaluated.afterUsage().values().stream().mapToInt(Integer::intValue).max().orElse(snapshot.currentM());
        return new UsageCommitRequest(transactionId, playerId, snapshot.beijingDate(), snapshot.revision(),
            result.recipe().name(), inputs, evaluated.plannedCrafts(), actual,
            snapshot.currentM(), afterM, autoMode, modified);
    }

    public synchronized void invalidate() {
        cancelLocked("输入已变化，请重新计算");
        generation.incrementAndGet();
    }

    public synchronized void cancel() {
        cancelLocked("计算已取消");
        generation.incrementAndGet();
    }

    private void cancelLocked(String nextStatus) {
        Future<?> current = running;
        if (current != null && !current.isDone()) current.cancel(true);
        running = null;
        calculating = false;
        status = nextStatus;
    }

    @Override
    public synchronized void close() {
        generation.incrementAndGet();
        cancelLocked("已关闭");
        executor.shutdownNow();
    }

    private static String safeMessage(Exception error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }
}
