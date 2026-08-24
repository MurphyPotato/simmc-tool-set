package com.murphypotato.simmctoolset.internal.map.integration;

import com.murphypotato.simmctoolset.internal.map.render.WorldMapOverlayRenderer;

import java.util.Optional;

/** Primitive World view conversion; invalid state deliberately produces no hit-test view. */
public final class WorldViewAdapter {
    private WorldViewAdapter() { }
    public static Optional<WorldMapOverlayRenderer.View> view(double cameraX, double cameraZ,
                                                               double scale, double screenScale,
                                                               int width, int height) {
        double factor = Double.isFinite(screenScale) && screenScale > 0 ? screenScale : 1.0;
        double mapScale = scale / factor;
        if (!Double.isFinite(mapScale) || mapScale <= 0 || width <= 0 || height <= 0) return Optional.empty();
        return Optional.of(new WorldMapOverlayRenderer.View(cameraX, cameraZ, mapScale, width, height));
    }
    public static boolean shouldHitTest(WorldMapOverlayRenderer.View view) { return view != null; }
}