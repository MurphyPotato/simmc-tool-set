package com.murphypotato.simmctoolset.internal.scroll.client;

import com.murphypotato.simmctoolset.internal.scroll.config.ArcaneSettings;
import com.murphypotato.simmctoolset.internal.scroll.config.SettingsStorage;
import com.murphypotato.simmctoolset.internal.scroll.domain.ArcaneSolver;
import com.murphypotato.simmctoolset.internal.scroll.domain.CraftPlan;
import com.murphypotato.simmctoolset.internal.scroll.domain.GameData;
import com.murphypotato.simmctoolset.internal.scroll.domain.Material;
import com.murphypotato.simmctoolset.internal.scroll.domain.ScrollRecipe;
import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public final class ArcaneController implements AutoCloseable {
    private final GameData data;
    private final SettingsStorage storage;
    private final ExecutorService executor;
    private final AtomicLong generation = new AtomicLong();
    private volatile ArcaneSettings settings;
    private volatile Future<?> running;
    private volatile boolean calculating;
    private volatile String status = "就绪";

    public ArcaneController(GameData data, SettingsStorage storage) {
        this.data = data;
        this.storage = storage;
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

    public synchronized void updateSettings(ArcaneSettings next) {
        settings = next;
        try {
            storage.save(next);
        } catch (IOException error) {
            status = "设置保存失败：" + safeMessage(error);
        }
    }

    public synchronized void calculate(MinecraftClient client, Consumer<CalculationResult> onResult) {
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
                List<CraftPlan> plans = ArcaneSolver.findCraftPlans(
                    recipe,
                    enabled,
                    ArcaneSolver.DEFAULT_IMPURITY_LIMIT,
                    ArcaneSolver.DEFAULT_MAX_PLANS,
                    () -> generation.get() != token
                );
                long elapsed = System.nanoTime() - started;
                client.execute(() -> {
                    if (generation.get() != token) return;
                    calculating = false;
                    status = plans.isEmpty() ? "当前材料范围内无解" : "完成：" + plans.size() + " 个方案";
                    onResult.accept(new CalculationResult(
                        recipe, plans, snapshot.quantity(), snapshot.includeMainMaterial(), snapshot.repeatThreshold(), elapsed
                    ));
                });
            } catch (ArcaneSolver.CalculationCancelledException ignored) {
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
