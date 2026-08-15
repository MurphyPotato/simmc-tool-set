package com.murphypotato.simmctoolset.map;

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

        System.out.println("MapCompatibilitySmoke: 3 scenarios passed");
    }

    private static void require(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
    }
}
