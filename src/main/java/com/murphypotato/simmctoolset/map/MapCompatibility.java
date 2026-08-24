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
    private static final String SAFE_DISABLE_WARNING =
            "检测到未经验证的 Xaero 版本组合。地图已安全停用，未应用 MixinGuiMap 和 "
                    + "MixinMinimapModuleRenderer；其他 Tool Set 模块继续启动。";
    private static volatile XaeroCapabilitySnapshot capabilitySnapshot;

    private MapCompatibility() {
    }

    public static Status status() {
        FabricLoader loader = FabricLoader.getInstance();
        if (loader.isModLoaded(EXTERNAL_MAP_ID)) return Status.externalMap();
        Optional<String> worldMap = versionOf(WORLD_MAP_ID);
        Optional<String> minimap = versionOf(MINIMAP_ID);
        String worldVersion = worldMap.orElse(null);
        String minimapVersion = minimap.orElse(null);
        Status versions = evaluate(worldVersion, minimapVersion, ToolSetSettings.mapExperimentalEnabled());
        if (worldVersion != null && minimapVersion != null) {
            XaeroCapabilitySnapshot capabilities = capabilities();
            if (supportsRuntime(capabilities)) return Status.capability(worldVersion, minimapVersion, capabilities);
        }
        return versions;
    }

    public static boolean shouldInitializeInternalMap() {
        return shouldInitializeInternalMap(status());
    }

    public static boolean shouldApplyInternalMapMixins() {
        return shouldApplyInternalMapMixins(status());
    }

    /** Selects a single ABI-specific mixin after the read-only bytecode probe. */
    public static boolean shouldApplyInternalMapMixin(String mixinClassName) {
        if (mixinClassName == null || FabricLoader.getInstance().isModLoaded(EXTERNAL_MAP_ID)) return false;
        if (!status().shouldLoadMap()) return false;
        XaeroCapabilitySnapshot capabilities = capabilities();
        if (mixinClassName.endsWith("MixinGuiMap")) {
            return capabilities.has(XaeroCapabilitySnapshot.Capability.WORLD_VIEW)
                    && capabilities.has(XaeroCapabilitySnapshot.Capability.WORLD_NAVIGATION);
        }
        if (mixinClassName.endsWith("MixinWorldSurfaceLegacy")) {
            return capabilities.has(XaeroCapabilitySnapshot.Capability.WORLD_SURFACE_LEGACY);
        }
        if (mixinClassName.endsWith("MixinWorldSurfaceProfiled")) {
            return capabilities.has(XaeroCapabilitySnapshot.Capability.WORLD_SURFACE_PROFILED);
        }
        if (mixinClassName.endsWith("MixinWorldZoomLegacy")) {
            return capabilities.has(XaeroCapabilitySnapshot.Capability.WORLD_ZOOM_LEGACY);
        }
        if (mixinClassName.endsWith("MixinWorldZoomProfiled")) {
            return capabilities.has(XaeroCapabilitySnapshot.Capability.WORLD_ZOOM_PROFILED);
        }
        if (mixinClassName.endsWith("MixinMinimapModuleRenderer")) {
            return capabilities.has(XaeroCapabilitySnapshot.Capability.MINIMAP_RENDER_COMMON)
                    && capabilities.has(XaeroCapabilitySnapshot.Capability.MINIMAP_HOOK_PIP)
                    && (capabilities.has(XaeroCapabilitySnapshot.Capability.MINIMAP_SHAPE_LEGACY)
                    || capabilities.has(XaeroCapabilitySnapshot.Capability.MINIMAP_SHAPE_PROFILE));
        }
        return false;
    }

    public static XaeroCapabilitySnapshot capabilities() {
        XaeroCapabilitySnapshot cached = capabilitySnapshot;
        if (cached != null) return cached;
        XaeroCapabilitySnapshot detected = XaeroCapabilityProbe.probe(MapCompatibility.class.getClassLoader());
        capabilitySnapshot = detected;
        return detected;
    }

    static boolean shouldInitializeInternalMap(Status status) {
        return status.shouldLoadMap();
    }

    static boolean shouldApplyInternalMapMixins(Status status) {
        return status.shouldApplyMixins();
    }

    private static boolean supportsRuntime(XaeroCapabilitySnapshot capabilities) {
        boolean world = capabilities.has(XaeroCapabilitySnapshot.Capability.WORLD_VIEW)
                && capabilities.has(XaeroCapabilitySnapshot.Capability.WORLD_NAVIGATION)
                && (capabilities.has(XaeroCapabilitySnapshot.Capability.WORLD_SURFACE_LEGACY)
                || capabilities.has(XaeroCapabilitySnapshot.Capability.WORLD_SURFACE_PROFILED));
        boolean minimap = capabilities.has(XaeroCapabilitySnapshot.Capability.MINIMAP_RENDER_COMMON)
                && capabilities.has(XaeroCapabilitySnapshot.Capability.MINIMAP_HOOK_PIP)
                && (capabilities.has(XaeroCapabilitySnapshot.Capability.MINIMAP_SHAPE_LEGACY)
                || capabilities.has(XaeroCapabilitySnapshot.Capability.MINIMAP_SHAPE_PROFILE));
        return world && minimap;
    }

    static Status evaluate(String worldMapVersion, String minimapVersion, boolean ignoredExperimental) {
        if (worldMapVersion == null && minimapVersion == null) return Status.missingBoth();
        if (worldMapVersion == null) return Status.missingWorldMap(minimapVersion);
        if (minimapVersion == null) return Status.missingMinimap(worldMapVersion);
        if (VERIFIED_WORLD_MAP_VERSION.equals(worldMapVersion)
                && VERIFIED_MINIMAP_VERSION.equals(minimapVersion)) {
            return Status.verified(worldMapVersion, minimapVersion);
        }
        return Status.incompatible(worldMapVersion, minimapVersion);
    }

    private static Optional<String> versionOf(String modId) {
        return FabricLoader.getInstance().getModContainer(modId)
                .map(container -> container.getMetadata().getVersion().getFriendlyString());
    }

    public enum Mode {
        VERIFIED, CAPABILITY, EXPERIMENTAL, INCOMPATIBLE, MISSING_WORLD_MAP, MISSING_MINIMAP, MISSING_BOTH, EXTERNAL_MAP
    }

    public record Status(Mode mode, String worldMapVersion, String minimapVersion, boolean experimentalEnabled,
                         XaeroCapabilitySnapshot capabilities) {
        static Status capability(String worldMapVersion, String minimapVersion,
                                 XaeroCapabilitySnapshot capabilities) {
            return new Status(Mode.CAPABILITY, worldMapVersion, minimapVersion, false, capabilities);
        }

        static Status verified(String worldMapVersion, String minimapVersion) {
            return new Status(Mode.VERIFIED, worldMapVersion, minimapVersion, false, null);
        }

        static Status incompatible(String worldMapVersion, String minimapVersion) {
            return new Status(Mode.INCOMPATIBLE, worldMapVersion, minimapVersion, false, null);
        }

        static Status missingWorldMap(String minimapVersion) {
            return new Status(Mode.MISSING_WORLD_MAP, null, minimapVersion, false, null);
        }

        static Status missingMinimap(String worldMapVersion) {
            return new Status(Mode.MISSING_MINIMAP, worldMapVersion, null, false, null);
        }

        static Status missingBoth() {
            return new Status(Mode.MISSING_BOTH, null, null, false, null);
        }

        static Status externalMap() {
            return new Status(Mode.EXTERNAL_MAP, null, null, false, null);
        }

        public boolean shouldLoadMap() {
            return mode == Mode.VERIFIED || mode == Mode.CAPABILITY;
        }

        public boolean shouldApplyMixins() {
            return mode == Mode.VERIFIED || mode == Mode.CAPABILITY;
        }

        public boolean canEnableExperimental() {
            return false;
        }

        public String displayName() {
            return switch (mode) {
                case VERIFIED -> "已验证兼容";
                case CAPABILITY -> "已识别能力兼容";
                case EXPERIMENTAL -> "实验兼容已被安全门控停用";
                case INCOMPATIBLE -> "兼容性警告：地图已安全停用";
                case MISSING_WORLD_MAP -> "缺少 Xaero 世界地图";
                case MISSING_MINIMAP -> "缺少 Xaero 小地图";
                case MISSING_BOTH -> "缺少 Xaero 世界地图和小地图";
                case EXTERNAL_MAP -> "检测到独立 SIMMC 地图，已关闭内置实现";
            };
        }

        public String detail() {
            return switch (mode) {
                case VERIFIED -> "Xaero World Map " + worldMapVersion + " + Xaero Minimap " + minimapVersion + ".";
                case CAPABILITY -> "Xaero World Map " + worldMapVersion + " + Xaero Minimap " + minimapVersion
                        + "；已按能力探测选择适配器，未知子能力会单独停用。";
                case EXPERIMENTAL -> SAFE_DISABLE_WARNING + "旧实验兼容设置不会绕过此安全门控。";
                case INCOMPATIBLE -> "检测到 Xaero World Map " + worldMapVersion + " + Xaero Minimap "
                        + minimapVersion + "。" + SAFE_DISABLE_WARNING
                        + "旧实验兼容设置不会绕过此安全门控。";
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
