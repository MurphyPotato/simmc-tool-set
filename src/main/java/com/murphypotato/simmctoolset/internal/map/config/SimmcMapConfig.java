package com.murphypotato.simmctoolset.internal.map.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class SimmcMapConfig {
    private static final int SAVE_LOCK_STRIPE_COUNT = 64;
    private static final Object[] SAVE_LOCKS = createSaveLocks();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Logger LOGGER = LoggerFactory.getLogger(SimmcMapConfig.class);

    public static final String DEFAULT_SERVER_HOST = "play.simmc.cn";
    public static final String DEFAULT_MAP_URL = "https://map.simmc.cn";
    public static final String DEFAULT_WORLD_KEY = "minecraft_overworld";

    private boolean customServerEnabled = false;
    private String customServerHost = "";
    private String customMapUrl = "";
    private String customWorldKey = DEFAULT_WORLD_KEY;
    private int markerRefreshSeconds = 15;
    private int playerRefreshSeconds = 2;
    private int tileDiskCacheMiB = 512;
    private int tileRevalidateSeconds = 300;
    private List<String> favoriteKeys = List.of();
    private boolean worldMapEnabled = true;
    private boolean worldMapBackgroundEnabled = true;
    private boolean minimapBackgroundEnabled = false;
    private List<String> hiddenLayerIds = List.of();

    private SimmcMapConfig() {
    }

    public static SimmcMapConfig defaults() {
        return new SimmcMapConfig();
    }

    public static SimmcMapConfig load(Path path) {
        if (Files.notExists(path)) {
            return defaults();
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            SimmcMapConfig config = GSON.fromJson(reader, SimmcMapConfig.class);
            return config == null ? defaults() : config.sanitized();
        } catch (IOException | JsonParseException exception) {
            LOGGER.warn(
                    "Failed to load SIMMC map configuration from {} ({})",
                    path,
                    exception.getClass().getSimpleName()
            );
            return defaults();
        }
    }

    public static String normalizeBaseUrl(String baseUrl) {
        requireValue(baseUrl, "map base URL");
        String normalized = baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        URI uri = URI.create(normalized);
        String scheme = uri.getScheme();
        if (scheme == null
                || !(scheme.toLowerCase(Locale.ROOT).equals("http") || scheme.toLowerCase(Locale.ROOT).equals("https"))
                || uri.getHost() == null
                || !hasSafeAuthority(uri)
                || uri.getUserInfo() != null
                || uri.getQuery() != null
                || uri.getFragment() != null
                || uri.getPath().contains("..")) {
            throw new IllegalArgumentException("Unsafe map base URL: " + baseUrl);
        }
        return normalized;
    }

    private static boolean hasSafeAuthority(URI uri) {
        String authority = uri.getRawAuthority();
        String host = uri.getHost();
        if (authority == null || authority.contains("[") || authority.contains("]") || host.contains(":")) {
            return false;
        }
        int colon = authority.indexOf(':');
        if (colon < 0) {
            return authority.equalsIgnoreCase(host);
        }
        if (colon != authority.lastIndexOf(':') || !authority.substring(0, colon).equalsIgnoreCase(host)) {
            return false;
        }
        String port = authority.substring(colon + 1);
        if (!port.matches("[0-9]+")) {
            return false;
        }
        try {
            int parsedPort = Integer.parseInt(port);
            return parsedPort >= 1 && parsedPort <= 65535;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    public SimmcMapConfig withCustomServer(boolean enabled, String host, String mapUrl, String worldKey) {
        requireValue(host, "custom server host");
        requireValue(mapUrl, "map base URL");
        requireValue(worldKey, "world key");
        var config = copy();
        config.customServerEnabled = enabled;
        if (!enabled && host.isEmpty() && mapUrl.isEmpty()) {
            config.customServerHost = "";
            config.customMapUrl = "";
            config.customWorldKey = normalizeWorldKey(worldKey);
        } else {
            config.customServerHost = normalizeCustomHost(host);
            config.customMapUrl = normalizeBaseUrl(mapUrl);
            config.customWorldKey = normalizeWorldKey(worldKey);
        }
        return config;
    }

    public SimmcMapConfig withRefreshAndCache(int markerSeconds, int playerSeconds, int cacheMiB) {
        var config = copy();
        config.markerRefreshSeconds = clamp(markerSeconds, 5, 300);
        config.playerRefreshSeconds = clamp(playerSeconds, 1, 30);
        config.tileDiskCacheMiB = clamp(cacheMiB, 64, 4096);
        return config;
    }

    public SimmcMapConfig withTileRevalidateSeconds(int seconds) {
        var config = copy();
        config.tileRevalidateSeconds = clamp(seconds, 30, 3600);
        return config;
    }

    public SimmcMapConfig withFavoriteKeys(Collection<String> stableKeys) {
        var config = copy();
        config.favoriteKeys = sanitizeFavoriteKeys(stableKeys);
        return config;
    }

    public SimmcMapConfig withMapVisibility(boolean worldMapEnabled, boolean worldMapBackgroundEnabled,
                                             boolean minimapBackgroundEnabled, Collection<String> hiddenLayerIds) {
        var config = copy();
        config.worldMapEnabled = worldMapEnabled;
        config.worldMapBackgroundEnabled = worldMapBackgroundEnabled;
        config.minimapBackgroundEnabled = minimapBackgroundEnabled;
        config.hiddenLayerIds = sanitizeLayerIds(hiddenLayerIds);
        return config;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public void save(Path path) {
        Path targetPath = path.toAbsolutePath().normalize();
        Object saveLock = SAVE_LOCKS[Math.floorMod(targetPath.hashCode(), SAVE_LOCKS.length)];
        synchronized (saveLock) {
            saveLocked(targetPath);
        }
    }

    private void saveLocked(Path targetPath) {
        Path temporaryPath = null;
        try {
            Path parent = targetPath.getParent();
            Files.createDirectories(parent);
            String safePrefix = "simmc-" + targetPath.getFileName().toString().replaceAll("[^A-Za-z0-9._-]", "_") + "-";
            temporaryPath = Files.createTempFile(parent, safePrefix, ".tmp");
            try (Writer writer = Files.newBufferedWriter(
                    temporaryPath,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.WRITE
            )) {
                GSON.toJson(this, writer);
            }
            try {
                Files.move(
                        temporaryPath,
                        targetPath,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporaryPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
            temporaryPath = null;
        } catch (IOException | RuntimeException exception) {
            if (temporaryPath != null) {
                try {
                    Files.deleteIfExists(temporaryPath);
                } catch (IOException cleanupException) {
                    exception.addSuppressed(cleanupException);
                }
            }
            throw new IllegalStateException("Unable to save SIMMC map configuration", exception);
        }
    }

    private static Object[] createSaveLocks() {
        Object[] locks = new Object[SAVE_LOCK_STRIPE_COUNT];
        for (int index = 0; index < locks.length; index++) {
            locks[index] = new Object();
        }
        return locks;
    }

    private static String normalizeWorldKey(String worldKey) {
        requireValue(worldKey, "world key");
        if (!worldKey.matches("[A-Za-z0-9._-]+")
                || !worldKey.matches(".*[A-Za-z0-9].*")
                || worldKey.contains("..")) {
            throw new IllegalArgumentException("Invalid world key: " + worldKey);
        }
        return worldKey;
    }

    private static String normalizeCustomHost(String host) {
        requireValue(host, "custom server host");
        String normalized = host.trim().toLowerCase(Locale.ROOT);
        while (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.isEmpty() || normalized.length() > 253 || normalized.startsWith("[") || normalized.contains(":")) {
            throw new IllegalArgumentException("Invalid custom server host: " + host);
        }

        String[] labels = normalized.split("\\.", -1);
        boolean numericAddress = normalized.matches("[0-9.]+");
        if (numericAddress) {
            if (labels.length != 4) {
                throw new IllegalArgumentException("Invalid IPv4 address: " + host);
            }
            for (String label : labels) {
                if (label.isEmpty() || label.length() > 3 || Integer.parseInt(label) > 255) {
                    throw new IllegalArgumentException("Invalid IPv4 address: " + host);
                }
            }
            return normalized;
        }

        for (String label : labels) {
            if (!label.matches("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?")) {
                throw new IllegalArgumentException("Invalid DNS host: " + host);
            }
        }
        return normalized;
    }

    private static void requireValue(String value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
    }

    private SimmcMapConfig copy() {
        var config = new SimmcMapConfig();
        config.customServerEnabled = customServerEnabled;
        config.customServerHost = customServerHost;
        config.customMapUrl = customMapUrl;
        config.customWorldKey = customWorldKey;
        config.markerRefreshSeconds = markerRefreshSeconds;
        config.playerRefreshSeconds = playerRefreshSeconds;
        config.tileDiskCacheMiB = tileDiskCacheMiB;
        config.tileRevalidateSeconds = tileRevalidateSeconds;
        config.favoriteKeys = List.copyOf(favoriteKeys);
        config.worldMapEnabled = worldMapEnabled;
        config.worldMapBackgroundEnabled = worldMapBackgroundEnabled;
        config.minimapBackgroundEnabled = minimapBackgroundEnabled;
        config.hiddenLayerIds = List.copyOf(hiddenLayerIds);
        return config;
    }

    private SimmcMapConfig sanitized() {
        SimmcMapConfig safe = defaults().withRefreshAndCache(
                markerRefreshSeconds,
                playerRefreshSeconds,
                tileDiskCacheMiB
        ).withTileRevalidateSeconds(tileRevalidateSeconds)
                .withFavoriteKeys(favoriteKeys).withMapVisibility(worldMapEnabled,
                worldMapBackgroundEnabled, minimapBackgroundEnabled, hiddenLayerIds);
        if (!customServerEnabled && "".equals(customServerHost) && "".equals(customMapUrl)) {
            return safe;
        }
        try {
            return safe.withCustomServer(customServerEnabled, customServerHost, customMapUrl, customWorldKey);
        } catch (IllegalArgumentException exception) {
            return safe;
        }
    }

    public boolean customServerEnabled() {
        return customServerEnabled;
    }

    public String customServerHost() {
        return customServerHost;
    }

    public String customMapUrl() {
        return customMapUrl;
    }

    public String customWorldKey() {
        return customWorldKey;
    }

    public int markerRefreshSeconds() {
        return markerRefreshSeconds;
    }

    public int playerRefreshSeconds() {
        return playerRefreshSeconds;
    }

    public int tileDiskCacheMiB() {
        return tileDiskCacheMiB;
    }

    public int tileRevalidateSeconds() {
        return tileRevalidateSeconds;
    }

    public List<String> favoriteKeys() {
        return favoriteKeys;
    }

    public boolean worldMapEnabled() { return worldMapEnabled; }
    public boolean worldMapBackgroundEnabled() { return worldMapBackgroundEnabled; }
    public boolean minimapBackgroundEnabled() { return minimapBackgroundEnabled; }
    public Set<String> hiddenLayerIds() { return Set.copyOf(hiddenLayerIds); }

    private static List<String> sanitizeFavoriteKeys(Collection<String> stableKeys) {
        if (stableKeys == null || stableKeys.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> cleaned = new LinkedHashSet<>();
        for (String stableKey : stableKeys) {
            if (stableKey == null) {
                continue;
            }
            String trimmed = stableKey.trim();
            if (!trimmed.isEmpty()) {
                cleaned.add(trimmed);
            }
        }
        return List.copyOf(cleaned);
    }

    private static List<String> sanitizeLayerIds(Collection<String> layerIds) {
        if (layerIds == null) return List.of();
        return layerIds.stream().filter(java.util.Objects::nonNull).map(String::trim)
                .filter(id -> id.matches("[a-z0-9_-]+"))
                .distinct().toList();
    }
}
