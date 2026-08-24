package com.murphypotato.simmctoolset.internal.map.integration;

import com.murphypotato.simmctoolset.map.XaeroCapabilitySnapshot;
import java.lang.reflect.Method;
import static com.murphypotato.simmctoolset.map.XaeroCapabilitySnapshot.Capability.MINIMAP_SHAPE_LEGACY;
import static com.murphypotato.simmctoolset.map.XaeroCapabilitySnapshot.Capability.MINIMAP_SHAPE_PROFILE;

/** Reads only the shape ABI selected by the capability probe. */
public final class MinimapShapeAdapter {
    public enum Kind { LEGACY, PROFILE, NONE }
    private final Kind kind;
    private final ClassLoader loader;
    private MinimapShapeAdapter(Kind kind, ClassLoader loader) { this.kind = kind; this.loader = loader; }
    public static MinimapShapeAdapter resolve(XaeroCapabilitySnapshot snapshot, ClassLoader loader) {
        Kind kind = snapshot != null && snapshot.has(MINIMAP_SHAPE_LEGACY) ? Kind.LEGACY
                : snapshot != null && snapshot.has(MINIMAP_SHAPE_PROFILE) ? Kind.PROFILE : Kind.NONE;
        return new MinimapShapeAdapter(kind, loader);
    }
    public Kind kind() { return kind; }
    public boolean circular() {
        try { return switch (kind) { case LEGACY -> readLegacy() == 0; case PROFILE -> readProfile() == 0; case NONE -> false; }; }
        catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            throw new IllegalStateException("Xaero minimap shape unavailable", failure);
        }
    }
    private Object hud() throws ReflectiveOperationException {
        Class<?> type = Class.forName("xaero.common.HudMod", false, loader);
        Object instance = type.getField("INSTANCE").get(null);
        if (instance == null) throw new IllegalStateException("HudMod.INSTANCE is null");
        return instance;
    }
    private int readLegacy() throws ReflectiveOperationException {
        Object hud = hud();
        Object settings = hud.getClass().getMethod("getSettings").invoke(hud);
        return settings.getClass().getField("minimapShape").getInt(settings);
    }
    private int readProfile() throws ReflectiveOperationException {
        Object hud = hud();
        Object channel = hud.getClass().getMethod("getHudConfigs").invoke(hud);
        Object manager = channel.getClass().getMethod("getClientConfigManager").invoke(channel);
        Class<?> options = Class.forName("xaero.hud.minimap.common.config.option.MinimapProfiledConfigOptions", false, loader);
        Object option = options.getField("SHAPE").get(null);
        Class<?> configOption = Class.forName("xaero.lib.common.config.option.ConfigOption", false, loader);
        Method effective = manager.getClass().getMethod("getEffective", configOption);
        return ((Number) effective.invoke(manager, option)).intValue();
    }
}
