package com.murphypotato.simmctoolset.internal.map.integration;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/** Fail-closed health state for the optional Xaero surface. */
public final class XaeroCompatibility {
    private static final Logger LOGGER = LoggerFactory.getLogger("SIMMC/Xaero");
    private static final AtomicBoolean ENABLED = new AtomicBoolean(true);
    private static final AtomicBoolean WARNED = new AtomicBoolean();
    private static final AtomicBoolean UI_WARNED = new AtomicBoolean();

    private XaeroCompatibility() { }

    public static boolean worldMapEnabled() {
        return ENABLED.get();
    }

    public static void disable(String reason, Throwable failure) {
        if (!ENABLED.compareAndSet(true, false)) return;
        if (failure == null) LOGGER.warn("SIMMC 世界地图模块已关闭：{}", reason);
        else LOGGER.warn("SIMMC 世界地图模块已关闭：{}", reason, failure);
        warnOnce();
    }

    /** Reports a UI-frame failure without disabling the rest of the map surface. */
    public static void reportUiFailure(Throwable failure) {
        if (!UI_WARNED.compareAndSet(false, true)) return;
        LOGGER.warn("SIMMC 世界地图界面本帧渲染失败，已跳过本帧 UI", failure);
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        client.execute(() -> {
            if (client.inGameHud != null) {
                client.inGameHud.getChatHud().addMessage(Text.literal(
                        "§e[SIMMC 地图] 地图界面本帧渲染失败，已跳过本帧；地图模块仍保持运行。"));
            }
        });
    }

    private static void warnOnce() {
        if (!WARNED.compareAndSet(false, true)) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        client.execute(() -> {
            if (client.inGameHud != null) {
                client.inGameHud.getChatHud().addMessage(Text.literal(
                        "§c[SIMMC 地图] 地图子模块启动或运行失败，可能与 Xaero 版本不完全兼容；请确保 Xaero World Map 1.39.13 + Xaero Minimap 25.2.16。"));
            }
        });
    }
}
