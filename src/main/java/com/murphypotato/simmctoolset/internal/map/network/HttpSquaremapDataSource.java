package com.murphypotato.simmctoolset.internal.map.network;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** HTTP-backed squaremap resources under one validated map base path. */
public final class HttpSquaremapDataSource implements SquaremapDataSource {
    private static final java.time.Duration MARKERS_TIMEOUT = java.time.Duration.ofMinutes(12);
    private final SquaremapHttpClient http;
    private final URI settings;
    private final URI markers;
    private final URI players;

    public HttpSquaremapDataSource(URI baseUrl, String worldKey, SquaremapHttpClient http) {
        URI base = validateBase(baseUrl);
        this.http = Objects.requireNonNull(http, "http");
        String encodedWorld = encodeWorld(worldKey);
        String prefix = base.toASCIIString();
        while (prefix.endsWith("/")) prefix = prefix.substring(0, prefix.length() - 1);
        this.settings = URI.create(prefix + "/tiles/" + encodedWorld + "/settings.json");
        this.markers = URI.create(prefix + "/tiles/" + encodedWorld + "/markers.json");
        this.players = URI.create(prefix + "/tiles/players.json");
    }

    @Override public CompletableFuture<HttpResult> fetchSettings(HttpValidators validators) {
        return http.get(settings, validators);
    }

    @Override public CompletableFuture<HttpResult> fetchMarkers(HttpValidators validators) {
        return http.get(markers, validators, MARKERS_TIMEOUT);
    }

    @Override public CompletableFuture<HttpResult> fetchPlayers(HttpValidators validators) {
        return http.get(players, validators);
    }

    private static URI validateBase(URI base) {
        Objects.requireNonNull(base, "baseUrl");
        String scheme = base.getScheme() == null ? "" : base.getScheme().toLowerCase(Locale.ROOT);
        if (!(scheme.equals("http") || scheme.equals("https")) || base.getHost() == null
                || base.getUserInfo() != null || base.getQuery() != null || base.getFragment() != null) {
            throw new IllegalArgumentException("unsafe squaremap base URL");
        }
        String path = base.getPath();
        if (path != null) {
            for (String segment : path.split("/", -1)) {
                if (segment.equals(".") || segment.equals("..")) throw new IllegalArgumentException("base path may not traverse");
            }
        }
        return base;
    }

    private static String encodeWorld(String worldKey) {
        Objects.requireNonNull(worldKey, "worldKey");
        if (worldKey.isBlank() || worldKey.length() > 256 || worldKey.equals(".") || worldKey.equals("..")) {
            throw new IllegalArgumentException("unsafe world key");
        }
        for (int i = 0; i < worldKey.length(); i++) {
            char c = worldKey.charAt(i);
            if (Character.isISOControl(c) || "/\\?#%".indexOf(c) >= 0) {
                throw new IllegalArgumentException("unsafe world key");
            }
        }
        StringBuilder encoded = new StringBuilder();
        for (byte value : worldKey.getBytes(StandardCharsets.UTF_8)) {
            int c = value & 0xff;
            if (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9'
                    || c == '-' || c == '_' || c == '~') {
                encoded.append((char) c);
            } else {
                encoded.append('%').append(String.format(Locale.ROOT, "%02X", c));
            }
        }
        return encoded.toString();
    }
}
