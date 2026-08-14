package com.murphypotato.simmctoolset.internal.accessory.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

public final class ServerScopeHasher {
    private ServerScopeHasher() {
    }

    public static String currentScope(MinecraftClient client, String salt) {
        if (client == null) return hash(salt, "no-session");
        ServerInfo server = client.getCurrentServerEntry();
        String localIdentity;
        if (server != null) localIdentity = server.address;
        else if (client.isInSingleplayer()) localIdentity = "singleplayer";
        else localIdentity = "local-session";
        return hash(salt, localIdentity);
    }

    public static String hash(String salt, String localIdentity) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String input = Objects.requireNonNullElse(salt, "") + '\u0000'
                + Objects.requireNonNullElse(localIdentity, "");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("当前 Java 运行时缺少 SHA-256", impossible);
        }
    }
}
