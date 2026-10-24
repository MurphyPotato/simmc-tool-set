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
    public static final String TARGET_BACKUP_HOST = "110.42.44.191";
    public static final int TARGET_BACKUP_PORT = 7777;
    public static final String TARGET_MIRROR_HOST = "simmc.silkysoftware.com";
    public static final String TARGET_SERVER_LABEL =
            TARGET_HOST + "、" + TARGET_BACKUP_HOST + ":" + TARGET_BACKUP_PORT
                    + " 或 " + TARGET_MIRROR_HOST;

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
        while (value.endsWith(".")) value = value.substring(0, value.length() - 1);

        int colon = value.lastIndexOf(':');
        if (colon > 0 && value.indexOf(':') == colon) {
            String host = value.substring(0, colon);
            String port = value.substring(colon + 1);
            if (TARGET_BACKUP_HOST.equals(host)) {
                return Integer.toString(TARGET_BACKUP_PORT).equals(port);
            }
            return TARGET_HOST.equals(host) || TARGET_MIRROR_HOST.equals(host);
        }
        return TARGET_HOST.equals(value) || TARGET_MIRROR_HOST.equals(value);
    }
}
