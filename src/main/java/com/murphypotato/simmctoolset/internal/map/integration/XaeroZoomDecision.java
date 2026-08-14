package com.murphypotato.simmctoolset.internal.map.integration;

/** Pure scale-field update used by Home fit and native-floor restoration. */
public final class XaeroZoomDecision {
    public record Update(double userScale, double destScale, double renderScale,
                         boolean clearAnimation) { }

    private XaeroZoomDecision() { }

    public static Update fit(double fittedViewScale, double screenScale, double framebufferMultiplier) {
        double userScale = XaeroScaleMath.desiredUserScale(
                fittedViewScale, screenScale, framebufferMultiplier);
        return new Update(userScale, userScale,
                XaeroScaleMath.renderScale(userScale, framebufferMultiplier), true);
    }

    public static Update clamp(double userScale, double destScale, double framebufferMultiplier) {
        double clampedUser = XaeroScaleMath.clampNativeUserScale(userScale);
        double clampedDestination = XaeroScaleMath.clampNativeUserScale(destScale);
        return new Update(clampedUser, clampedDestination,
                XaeroScaleMath.renderScale(clampedUser, framebufferMultiplier), true);
    }
}
