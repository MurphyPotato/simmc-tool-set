package com.murphypotato.simmctoolset.internal.map.integration;

/** Unit conversions between Xaero user scale, framebuffer render scale, and SIMMC view scale. */
public final class XaeroScaleMath {
    public static final double NATIVE_USER_FLOOR = 0.0625;

    private XaeroScaleMath() { }

    public static double desiredUserScale(double fittedViewScale, double screenScale, double framebufferMultiplier) {
        positive(fittedViewScale, "fittedViewScale");
        positive(screenScale, "screenScale");
        positive(framebufferMultiplier, "framebufferMultiplier");
        return fittedViewScale * screenScale / framebufferMultiplier;
    }

    public static double renderScale(double userScale, double framebufferMultiplier) {
        positive(userScale, "userScale");
        positive(framebufferMultiplier, "framebufferMultiplier");
        return userScale * framebufferMultiplier;
    }

    public static double viewScale(double renderScale, double screenScale) {
        positive(renderScale, "renderScale");
        positive(screenScale, "screenScale");
        return renderScale / screenScale;
    }

    public static double clampNativeUserScale(double userScale) {
        if (!Double.isFinite(userScale)) return NATIVE_USER_FLOOR;
        return Math.max(NATIVE_USER_FLOOR, userScale);
    }

    private static void positive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0) throw new IllegalArgumentException(name + " must be positive");
    }
}
