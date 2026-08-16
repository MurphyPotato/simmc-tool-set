package com.murphypotato.simmctoolset.map;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/** Small JVM-only verification for the optional map compatibility decision table. */
public final class MapCompatibilitySmoke {
    private MapCompatibilitySmoke() {
    }

    public static void main(String[] args) {
        MapCompatibility.Status verified = MapCompatibility.evaluate("1.39.13", "25.2.16", false);
        require(verified.mode() == MapCompatibility.Mode.VERIFIED, "verified pair mode");
        require(verified.shouldLoadMap(), "verified pair loads");
        require(!verified.canEnableExperimental(), "verified pair needs no experimental mode");

        MapCompatibility.Status incompatible = MapCompatibility.evaluate("1.39.12", "25.2.16", false);
        require(incompatible.mode() == MapCompatibility.Mode.INCOMPATIBLE, "incompatible pair mode");
        require(incompatible.shouldLoadMap(), "incompatible pair is attempted after warning");
        require(incompatible.detail().contains("版本不完全兼容"), "compatibility warning text");
        require(incompatible.detail().contains("可能导致游戏崩溃"), "crash warning text");

        MapCompatibility.Status missing = MapCompatibility.evaluate(null, "25.2.16", false);
        require(missing.mode() == MapCompatibility.Mode.MISSING_WORLD_MAP, "missing world map mode");
        require(!missing.shouldLoadMap(), "missing world map does not load");
        require(!missing.detail().isBlank(), "missing dependency detail");

        try {
            nativeMapSmoke();
        } catch (Exception exception) {
            throw new AssertionError("native map smoke", exception);
        }
        System.out.println("MapCompatibilitySmoke: 3 scenarios passed");
    }

    private static void nativeMapSmoke() throws Exception {
        try {
            Class.forName("com.murphypotato.simmctoolset.internal.map.config.SimmcMapConfig");
        } catch (ClassNotFoundException | NoClassDefFoundError unavailable) {
            System.out.println("MapNativeSmoke: skipped (optional map implementation not compiled)");
            return;
        }
        Path temp = Files.createTempDirectory("simmc-map-smoke-");
        try {
            configMigration(temp);
            cacheMigration(temp);
            Class<?> snapshotClass = Class.forName(
                    "com.murphypotato.simmctoolset.internal.map.model.MapSnapshot");
            Object snapshot = snapshotClass.getConstructor(List.class).newInstance(List.of());
            Class<?> overlayClass = Class.forName(
                    "com.murphypotato.simmctoolset.internal.map.render.WorldMapOverlayRenderer");
            Object overlay = overlayClass.getConstructor(snapshotClass).newInstance(snapshot);
            Class<?> viewClass = Class.forName(
                    "com.murphypotato.simmctoolset.internal.map.render.WorldMapOverlayRenderer$View");
            Object hit = overlayClass.getMethod("hitTest", double.class, double.class, viewClass)
                    .invoke(overlay, 10.0, 10.0, null);
            require((Boolean) hit.getClass().getMethod("isEmpty").invoke(hit), "null view hit test is empty");
        } finally {
            try (var paths = Files.walk(temp)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException exception) {
                        throw new java.io.UncheckedIOException(exception);
                    }
                });
            } catch (java.io.UncheckedIOException exception) {
                throw exception.getCause();
            }
        }
        System.out.println("MapNativeSmoke: config/cache/null-view scenarios passed");
    }

    private static void configMigration(Path temp) throws Exception {
        Path legacy = temp.resolve("simmcmap.json");
        byte[] original = "{\"markerRefreshSeconds\":42,\"worldMapEnabled\":false}".getBytes(StandardCharsets.UTF_8);
        Files.write(legacy, original);
        Path target = temp.resolve("config").resolve("simmc-tool-set").resolve("map.json");
        Class<?> configClass = Class.forName(
                "com.murphypotato.simmctoolset.internal.map.config.SimmcMapConfig");
        var load = configClass.getMethod("loadOrImport", Path.class, boolean.class, Path[].class);
        Object config = load.invoke(null, target, false, (Object) new Path[]{legacy});
        require((Integer) configClass.getMethod("markerRefreshSeconds").invoke(config) == 42,
                "legacy config refresh value");
        require(!(Boolean) configClass.getMethod("worldMapEnabled").invoke(config),
                "legacy config visibility value");
        require(Files.exists(target), "new config created");
        require(java.util.Arrays.equals(original, Files.readAllBytes(legacy)), "legacy config unchanged");

        Path externalTarget = temp.resolve("external").resolve("map.json");
        Object external = load.invoke(null, externalTarget, true, (Object) new Path[]{legacy});
        require((Boolean) configClass.getMethod("worldMapEnabled").invoke(external),
                "external map skips import");
        require(Files.notExists(externalTarget), "external map leaves new config absent");
    }

    private static void cacheMigration(Path temp) throws Exception {
        Path legacy = temp.resolve("simmcmap-cache");
        Path legacyFile = legacy.resolve("tiles").resolve("z1.bin");
        Files.createDirectories(legacyFile.getParent());
        Files.writeString(legacyFile, "cached", StandardCharsets.UTF_8);
        Path target = temp.resolve("simmc-tool-set-map-cache");
        Class<?> migrationClass = Class.forName(
                "com.murphypotato.simmctoolset.internal.map.cache.MapCacheMigration");
        migrationClass.getMethod("importIfAbsent", Path.class, Path.class).invoke(null, target, legacy);
        require(Files.readString(target.resolve("tiles").resolve("z1.bin"), StandardCharsets.UTF_8).equals("cached"),
                "legacy cache copied");
        require(Files.readString(legacyFile, StandardCharsets.UTF_8).equals("cached"), "legacy cache unchanged");
    }

    private static void require(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
    }
}
