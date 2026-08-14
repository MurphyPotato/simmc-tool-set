package com.murphypotato.simmctoolset.internal.simes;

import com.murphypotato.simmctoolset.client.DiagnosticLog;
import com.murphypotato.simmctoolset.client.ToolSetSettings;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
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
        if (registered) return;
        registered = true;
        SimesArcaneHud.initialize();
        SimesBrewingCookwareHud.initialize();
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
        SimesBrewingCookwareHud.reset();
    }

    public static boolean active() {
        return active && SimesServerGate.isTarget(MinecraftClient.getInstance());
    }

    public static boolean arcaneEnabled() {
        return active() && ToolSetSettings.arcaneHudEnabled();
    }

    public static boolean brewingEnabled() {
        return active() && ToolSetSettings.brewingEnabled();
    }
}
