package com.murphypotato.simmctoolset.internal.simes;

import com.murphypotato.simmctoolset.client.DiagnosticLog;
import com.murphypotato.simmctoolset.client.ToolSetSettings;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

/**
 * Derived from Simes 1.4.0 by 7imes.
 * Source: https://github.com/Nov7imes/Simes
 * Modified for simMC Tool Set: owns only the licensed HUD, fermentation and cookware features.
 */
public final class SimesFeatureController {
    private static boolean registered;
    private static volatile boolean active;

    private SimesFeatureController() {
    }

    public static synchronized void initialize() {
        if (FabricLoader.getInstance().isModLoaded("simes")) {
            DiagnosticLog.info("检测到外置 Simes，内置 Simes 功能不初始化");
            return;
        }
        if (registered) return;
        registered = true;
        SimesArcaneHud.initialize();
        SimesArcaneStatusHud.initialize();
        SimesBrewingCookwareHud.initialize();
        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((client, world) -> {
            SimesArcaneHud.reset();
            SimesBrewingCookwareHud.reset();
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            active = SimesServerGate.isTarget(client);
            SimesArcaneHud.reset();
            SimesBrewingCookwareHud.reset();
            DiagnosticLog.info("Simes features active=" + active + " server=" + SimesServerGate.TARGET_HOST);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
    }

    public static synchronized void reset() {
        active = false;
        SimesArcaneHud.reset();
        SimesArcaneStatusHud.reset();
        SimesBrewingCookwareHud.reset();
    }

    public static boolean active() {
        return active && SimesServerGate.isTarget(MinecraftClient.getInstance());
    }

    public static boolean arcaneEnabled() {
        return active() && ToolSetSettings.arcaneHudEnabled();
    }

    public static void setArcaneEnabled(boolean enabled) {
        if (!enabled) SimesArcaneStatusHud.clearVisualState();
        ToolSetSettings.setArcaneHudEnabled(enabled);
    }

    public static boolean brewingEnabled() {
        return active() && ToolSetSettings.brewingEnabled();
    }

    public static boolean fermentationEnabled() {
        return active() && ToolSetSettings.fermentationEnabled();
    }

    public static boolean cookwareEnabled() {
        return active() && ToolSetSettings.cookwareEnabled();
    }

    public static String arcaneStatus() {
        if (FabricLoader.getInstance().isModLoaded("simes")) return "检测到外置 Simes，已由外置版接管";
        if (!active()) return "未连接目标服务器 play.simmc.cn";
        return ToolSetSettings.arcaneHudEnabled() ? "已激活，等待服务器奥术状态" : "已连接但已关闭";
    }

    public static String brewingStatus() {
        if (FabricLoader.getInstance().isModLoaded("simes")) return "检测到外置 Simes，已由外置版接管";
        if (!active()) return "未连接目标服务器 play.simmc.cn";
        return "已激活；发酵=" + (ToolSetSettings.fermentationEnabled() ? "开" : "关")
                + "，厨具=" + (ToolSetSettings.cookwareEnabled() ? "开" : "关")
                + "；" + SimesBrewingCookwareHud.statusSummary();
    }
}
