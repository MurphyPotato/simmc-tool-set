package com.murphypotato.simmctoolset.map;

import com.murphypotato.simmctoolset.client.ToolSetSettings;
import net.fabricmc.loader.api.FabricLoader;

import java.util.Optional;

/** Resolves optional Xaero versions before the internal map lifecycle starts. */
public final class MapCompatibility {
    public static final String WORLD_MAP_ID = "xaeroworldmap";
    public static final String MINIMAP_ID = "xaerominimap";
    public static final String EXTERNAL_MAP_ID = "simmcmap";
    public static final String VERIFIED_WORLD_MAP_VERSION = "1.39.13";
    public static final String VERIFIED_MINIMAP_VERSION = "25.2.16";
    private static final String VERSION_WARNING =
            "版本不完全兼容，可能启动失败，且可能导致游戏崩溃，如遇上述情况，请确保Xaero World Map 1.39.13 + Xaero Minimap 25.2.16。";

    private MapCompatibility() {
    }

    public static Status status() {
        FabricLoader loader = FabricLoader.getInstance();
        if (loader.isModLoaded(EXTERNAL_MAP_ID)) return Status.externalMap();
        Optional<String> worldMap = versionOf(WORLD_MAP_ID);
        Optional<String> minimap = versionOf(MINIMAP_ID);
        return evaluate(worldMap.orElse(null), minimap.orElse(null), ToolSetSettings.mapExperimentalEnabled());
    }

    public static boolean shouldInitializeInternalMap() {
        return status().shouldLoadMap();
    }

    public static boolean shouldApplyInternalMapMixins() {
        return status().shouldLoadMap();
    }

    static Status evaluate(String worldMapVersion, String minimapVersion, boolean experimental) {
        if (worldMapVersion == null && minimapVersion == null) return Status.missingBoth();
        if (worldMapVersion == null) return Status.missingWorldMap(minimapVersion);
        if (minimapVersion == null) return Status.missingMinimap(worldMapVersion);
        if (VERIFIED_WORLD_MAP_VERSION.equals(worldMapVersion)
                && VERIFIED_MINIMAP_VERSION.equals(minimapVersion)) {
            return Status.verified(worldMapVersion, minimapVersion);
        }
        return experimental ? Status.experimental(worldMapVersion, minimapVersion)
                : Status.incompatible(worldMapVersion, minimapVersion);
    }

    private static Optional<String> versionOf(String modId) {
        return FabricLoader.getInstance().getModContainer(modId)
                .map(container -> container.getMetadata().getVersion().getFriendlyString());
    }

    public enum Mode {
        VERIFIED, EXPERIMENTAL, INCOMPATIBLE, MISSING_WORLD_MAP, MISSING_MINIMAP, MISSING_BOTH, EXTERNAL_MAP
    }

    public record Status(Mode mode, String worldMapVersion, String minimapVersion, boolean experimentalEnabled) {
        static Status verified(String worldMapVersion, String minimapVersion) {
            return new Status(Mode.VERIFIED, worldMapVersion, minimapVersion, false);
        }

        static Status experimental(String worldMapVersion, String minimapVersion) {
            return new Status(Mode.EXPERIMENTAL, worldMapVersion, minimapVersion, true);
        }

        static Status incompatible(String worldMapVersion, String minimapVersion) {
            return new Status(Mode.INCOMPATIBLE, worldMapVersion, minimapVersion, false);
        }

        static Status missingWorldMap(String minimapVersion) {
            return new Status(Mode.MISSING_WORLD_MAP, null, minimapVersion, false);
        }

        static Status missingMinimap(String worldMapVersion) {
            return new Status(Mode.MISSING_MINIMAP, worldMapVersion, null, false);
        }

        static Status missingBoth() {
            return new Status(Mode.MISSING_BOTH, null, null, false);
        }

        static Status externalMap() {
            return new Status(Mode.EXTERNAL_MAP, null, null, false);
        }

        public boolean shouldLoadMap() {
            // A version mismatch is explicitly attempted after warning the player.
            return mode == Mode.VERIFIED || mode == Mode.EXPERIMENTAL || mode == Mode.INCOMPATIBLE;
        }

        public boolean canEnableExperimental() {
            return mode == Mode.INCOMPATIBLE;
        }

        public String displayName() {
            return switch (mode) {
                case VERIFIED -> "Verified compatibility";
                case EXPERIMENTAL -> "Experimental compatibility enabled; restart required";
                case INCOMPATIBLE -> "Compatibility warning";
                case MISSING_WORLD_MAP -> "Missing Xaero World Map";
                case MISSING_MINIMAP -> "Missing Xaero Minimap";
                case MISSING_BOTH -> "Missing Xaero World Map and Xaero Minimap";
                case EXTERNAL_MAP -> "Standalone SIMMC Map detected; internal map is disabled";
            };
        }

        public String detail() {
            return switch (mode) {
                case VERIFIED -> "Xaero World Map " + worldMapVersion + " + Xaero Minimap " + minimapVersion + ".";
                case EXPERIMENTAL -> VERSION_WARNING + "将在重启后尝试加载地图子模块。";
                case INCOMPATIBLE -> "检测到 Xaero World Map " + worldMapVersion + " + Xaero Minimap "
                        + minimapVersion + "。" + VERSION_WARNING + "当前仍会尝试运行地图子模块。";
                case MISSING_WORLD_MAP -> "检测到 Xaero Minimap " + minimapVersion + "，但缺少 Xaero World Map "
                        + VERIFIED_WORLD_MAP_VERSION + "；地图子模块不会启动。";
                case MISSING_MINIMAP -> "检测到 Xaero World Map " + worldMapVersion + "，但缺少 Xaero Minimap "
                        + VERIFIED_MINIMAP_VERSION + "；地图子模块不会启动。";
                case MISSING_BOTH -> "缺少 Xaero World Map " + VERIFIED_WORLD_MAP_VERSION + " 和 Xaero Minimap "
                        + VERIFIED_MINIMAP_VERSION + "，地图子模块不会启动。";
                case EXTERNAL_MAP -> "已检测到独立 SIMMC Map；由独立地图管理生命周期和设置，整合包不会加载 Xaero 地图实现。";
            };
        }
    }
}
