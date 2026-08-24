package com.murphypotato.simmctoolset.internal.map.integration;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Objects;

/** World surface bridge with no static link to either Xaero rendering utility. */
public final class WorldSurfaceAdapter {
    private static final String LEGACY_FLUSH = "xaero.map.render.util.GuiRenderUtil";
    private static final String PROFILED_FLUSH = "xaero.lib.client.render.util.GuiRenderUtil";
    private final WorldAdapterSelection.Surface kind;
    private final MethodHandle flush;

    private WorldSurfaceAdapter(WorldAdapterSelection.Surface kind, MethodHandle flush) {
        this.kind = kind;
        this.flush = flush;
    }

    public static WorldSurfaceAdapter resolve(WorldAdapterSelection selection, ClassLoader loader) {
        Objects.requireNonNull(selection, "selection");
        Objects.requireNonNull(loader, "loader");
        if (!selection.hasSurface()) return null;
        String owner = selection.surface() == WorldAdapterSelection.Surface.LEGACY
                ? LEGACY_FLUSH : PROFILED_FLUSH;
        try {
            MethodHandle flush = MethodHandles.publicLookup().findStatic(
                    Class.forName(owner, false, loader), "flushGUI", MethodType.methodType(void.class));
            return new WorldSurfaceAdapter(selection.surface(), flush);
        } catch (ReflectiveOperationException | LinkageError failure) {
            return null;
        }
    }

    public WorldAdapterSelection.Surface kind() { return kind; }
    public String arrowAnchor() {
        return kind == WorldAdapterSelection.Surface.LEGACY
                ? "Lxaero/map/settings/ModSettings;renderArrow:Z"
                : "Lxaero/map/common/config/option/WorldMapProfiledConfigOptions;ARROW";
    }

    public void flushGui() {
        try { flush.invokeExact(); }
        catch (Throwable failure) { throw new IllegalStateException("Xaero World GUI flush failed", failure); }
    }
}