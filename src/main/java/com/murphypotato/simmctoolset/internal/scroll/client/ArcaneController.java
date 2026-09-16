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
import com.murphypotato.simmctoolset.internal.scroll.domain.EvaluatedPlan;
import com.murphypotato.simmctoolset.internal.scroll.domain.RotationBatch;
import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import java.time.LocalDate;
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
    private final Map<UUID, com.murphypotato.simmctoolset.internal.scroll.domain.TemporaryPlanUsage> temporary = new java.util.HashMap<>();

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

    /** Re-evaluates a preset against the current player's daily M without saving. */
    public synchronized EvaluatedPlan evaluatePreset(UUID playerId, PresetPlan preset) {
        if (playerId == null || preset == null) throw new IllegalArgumentException("预设上下文无效");
        ScrollRecipe recipe = data.recipe(preset.recipe());
        var usage = planningUsage(playerId);
        List<Material> materials = data.materials().stream()
            .filter(material -> !settings.excludedMaterials().contains(material.name())).toList();
        return com.murphypotato.simmctoolset.internal.scroll.domain.DecayPlanner.evaluate(
            preset.batches(), recipe, materials, usage, Map.of(), settings.excludedMaterials());
    }

    public synchronized EvaluatedPlan evaluateBatches(UUID playerId, String recipeName, List<com.murphypotato.simmctoolset.internal.scroll.domain.RotationBatch> batches) {
        return evaluateBatches(playerId, recipeName, batches, true);
    }

    /** Evaluates against only committed daily M; used for the current preview itself. */
    public synchronized EvaluatedPlan evaluateCommittedBatches(UUID playerId, String recipeName,
                                                                 List<com.murphypotato.simmctoolset.internal.scroll.domain.RotationBatch> batches) {
        return evaluateBatches(playerId, recipeName, batches, false);
    }

    private EvaluatedPlan evaluateBatches(UUID playerId, String recipeName,
                                          List<com.murphypotato.simmctoolset.internal.scroll.domain.RotationBatch> batches,
                                          boolean includeTemporary) {
        if (playerId == null || recipeName == null || batches == null) throw new IllegalArgumentException("方案上下文无效");
        ScrollRecipe recipe = data.recipe(recipeName);
        var usage = includeTemporary ? planningUsage(playerId) : usageStore.snapshot(playerId).totals();
        List<Material> materials = data.materials().stream()
            .filter(material -> !settings.excludedMaterials().contains(material.name())).toList();
        return com.murphypotato.simmctoolset.internal.scroll.domain.DecayPlanner.evaluate(
            batches, recipe, materials, usage, Map.of(), settings.excludedMaterials());
    }

    public synchronized ScrollUsageStore.CommitResult commitPreset(UUID playerId, PresetPlan preset, UUID transactionId)
        throws IOException {
        return commitPreset(playerId, preset, transactionId, false);
    }

    public synchronized ScrollUsageStore.CommitResult commitPreset(UUID playerId, PresetPlan preset, UUID transactionId,
                                                                     boolean allowInfeasible)
        throws IOException {
        EvaluatedPlan evaluated = evaluatePreset(playerId, preset);
        if (!evaluated.feasible() && !allowInfeasible) throw new IllegalArgumentException("当前 M 下预设不可行");
        var snapshot = usageStore.snapshot(playerId);
        var inputs = evaluated.batches().stream()
            .map(b -> new UsagePlanInput(b.crafts(), b.plan().materials())).toList();
        Map<String,Integer> actual = new java.util.LinkedHashMap<>();
        evaluated.batches().forEach(b -> b.plan().materials().forEach((name, amount) ->
            actual.merge(name, Math.multiplyExact(amount, b.crafts()), Math::addExact)));
        int after = evaluated.afterUsage().values().stream().mapToInt(Integer::intValue)
            .max().orElse(snapshot.currentM());
        return commitUsage(new UsageCommitRequest(transactionId, playerId, snapshot.beijingDate(),
            snapshot.revision(), preset.recipe(), inputs, evaluated.plannedCrafts(), actual,
            snapshot.currentM(), after, false, false, !evaluated.feasible()));
    }

    public Optional<UUID> playerId(MinecraftClient client) {
        return client != null && client.player != null ? Optional.of(client.player.getUuid()) : Optional.empty();
    }

    /** Commits only user-confirmed material usage; previews never call this. */
    public synchronized ScrollUsageStore.CommitResult commitUsage(UsageCommitRequest request) throws IOException {
        ScrollUsageStore.CommitResult result = usageStore.commit(request);
        temporary.remove(request.playerId());
        status = result.duplicate() ? "使用记录已保存（重复确认已忽略）" : "使用记录已保存";
        return result;
    }

    public synchronized void setTemporaryUsage(UUID playerId, Map<String, Integer> usage) {
        if (playerId == null) return;
        var snapshot = usageStore.snapshot(playerId);
        var preview = temporary.computeIfAbsent(playerId,
            com.murphypotato.simmctoolset.internal.scroll.domain.TemporaryPlanUsage::new);
        preview.replace(usage, snapshot.beijingDate(), snapshot.revision());
        if (preview.usage().isEmpty()) temporary.remove(playerId);
    }

    public synchronized Map<String,Integer> temporaryUsage(UUID playerId) {
        var value = temporary.get(playerId);
        if (value != null) {
            var snapshot = usageStore.snapshot(playerId);
            if (!value.matches(snapshot.beijingDate(), snapshot.revision())) {
                value.clear();
                temporary.remove(playerId);
                return Map.of();
            }
        }
        return value == null ? Map.of() : value.usage();
    }

    public synchronized Map<String,Integer> planningUsage(UUID playerId) {
        var result = new java.util.LinkedHashMap<String,Integer>();
        if (playerId == null) return result;
        result.putAll(usageStore.snapshot(playerId).totals());
        temporaryUsage(playerId).forEach((name, amount) -> result.merge(name, amount, Math::addExact));
        return Map.copyOf(result);
    }

    private Map<String, Integer> committedAfterUsage(UUID playerId, Map<String, Integer> actual) {
        var result = new java.util.LinkedHashMap<>(usageStore.snapshot(playerId).totals());
        actual.forEach((name, amount) -> result.merge(name, amount, Math::addExact));
        return Map.copyOf(result);
    }

    public synchronized void updateSettings(ArcaneSettings next) {
        if (next == null) return;
        if (!next.selectedRecipe().equals(settings.selectedRecipe())
            || !next.excludedMaterials().equals(settings.excludedMaterials())
            || next.quantity() != settings.quantity()) {
            temporary.clear();
        }
        settings = next;
        try {
            storage.save(next);
        } catch (IOException error) {
            status = "设置保存失败：" + safeMessage(error);
        }
    }

    public synchronized String presetEvaluationMessage(UUID playerId, PresetPlan preset) {
        EvaluatedPlan plan = evaluatePreset(playerId, preset);
        if (plan.feasible()) return "预设在当前 M 下可行";
        return "预设当前不可行：制作 " + plan.plannedCrafts() + "/" + plan.desiredCrafts()
            + "，杂质 " + plan.impurity() + "，溢出 " + plan.excess()
            + "。当前 M 变化后可能需要更多材料或产生衰减。";
    }

    /** Checks the plan's operational no-extra-material boundary for one more craft. */
    public synchronized boolean noDecayForNextCraft(UUID playerId, EvaluatedPlan plan, String recipeName) {
        if (playerId == null || plan == null || plan.batches().isEmpty()) return false;
        List<RotationBatch> batches = new ArrayList<>();
        for (var batch : plan.batches()) batches.add(new RotationBatch(batch.plan(), batch.crafts()));
        int last = batches.size() - 1;
        RotationBatch tail = batches.get(last);
        batches.set(last, new RotationBatch(tail.plan(), Math.addExact(tail.crafts(), 1)));
        ScrollRecipe recipe = data.recipe(recipeName);
        List<Material> materials = data.materials().stream()
            .filter(material -> !settings.excludedMaterials().contains(material.name())).toList();
        return com.murphypotato.simmctoolset.internal.scroll.domain.DecayPlanner.evaluate(
            batches, recipe, materials, planningUsage(playerId), Map.of(), settings.excludedMaterials()).feasible();
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
                Map<String, Integer> currentUsage = playerId == null ? Map.of() : planningUsage(playerId);
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
                    status = planning.plan().complete()
                        ? (planning.status() == com.murphypotato.simmctoolset.internal.scroll.domain.PlanningStatus.TIMED_OUT
                            ? "达到时间预算，已保留完整可行方案" : "完成：" + planning.plan().batches().size() + " 个批次")
                        : planning.status() == com.murphypotato.simmctoolset.internal.scroll.domain.PlanningStatus.TIMED_OUT
                            ? (planning.plan().batches().isEmpty() ? "预算内未找到可执行方案（未证明无解）"
                                : "达到时间预算，已保留部分可执行批次")
                            : "未找到完整可行方案（当前搜索范围）";
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
        int afterM = committedAfterUsage(playerId, actual).values().stream()
            .mapToInt(Integer::intValue).max().orElse(snapshot.currentM());
        return new UsageCommitRequest(transactionId, playerId, snapshot.beijingDate(), snapshot.revision(),
            result.recipe().name(), inputs, evaluated.plannedCrafts(), actual,
            snapshot.currentM(), afterM, autoMode, modified, false);
    }

    public UsageCommitRequest commitRequest(UUID playerId, CalculationResult result, EvaluatedPlan evaluated,
                                            boolean autoMode, boolean modified, UUID transactionId) {
        if (evaluated == null) throw new IllegalArgumentException("缺少评估方案");
        CalculationResult base = result;
        var inputs = new ArrayList<UsagePlanInput>();
        evaluated.batches().forEach(batch -> inputs.add(new UsagePlanInput(batch.crafts(), batch.plan().materials())));
        Map<String, Integer> actual = new java.util.LinkedHashMap<>();
        for (var batch : evaluated.batches()) batch.plan().materials().forEach((name, amount) ->
            actual.merge(name, Math.multiplyExact(amount, batch.crafts()), Math::addExact));
        var snapshot = usageStore.snapshot(playerId);
        int afterM = committedAfterUsage(playerId, actual).values().stream()
            .mapToInt(Integer::intValue).max().orElse(snapshot.currentM());
        return new UsageCommitRequest(transactionId, playerId, snapshot.beijingDate(), snapshot.revision(),
            base.recipe().name(), inputs, evaluated.plannedCrafts(), actual,
            snapshot.currentM(), afterM, autoMode, modified, false);
    }

    public synchronized void invalidate() {
        cancelLocked("输入已变化，请重新计算");
        generation.incrementAndGet();
        temporary.clear();
    }

    public synchronized void cancel() {
        cancelLocked("计算已取消");
        generation.incrementAndGet();
        temporary.clear();
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
