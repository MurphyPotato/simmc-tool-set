package com.murphypotato.simmctoolset.internal.simes;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;

import java.util.Locale;

/**
 * Derived from Simes 1.4.0 by 7imes.
 * Source: https://github.com/Nov7imes/Simes
 * Modified for simMC Tool Set: isolated target-server gate; no market or account features.
 */
public final class SimesServerGate {
    public static final String TARGET_HOST = "play.simmc.cn";

    private SimesServerGate() {
    }

    public static boolean isTarget(MinecraftClient client) {
        if (client == null) return false;
        ServerInfo server = client.getCurrentServerEntry();
        return server != null && isTargetAddress(server.address);
    }

    public static boolean isTargetAddress(String address) {
        if (address == null || address.isBlank()) return false;
        String value = address.trim().toLowerCase(Locale.ROOT);
        int slash = value.indexOf('/');
        if (slash >= 0) value = value.substring(0, slash);
        int colon = value.lastIndexOf(':');
        if (colon > 0 && value.indexOf(':') == colon) value = value.substring(0, colon);
        while (value.endsWith(".")) value = value.substring(0, value.length() - 1);
        return TARGET_HOST.equals(value);
    }
}
