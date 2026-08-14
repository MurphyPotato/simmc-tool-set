package com.murphypotato.simmctoolset.internal.map.render;

import java.util.Objects;
import java.util.Optional;

/** Task 9 press routing: selection may update, while click-vs-drag ownership remains with Xaero. */
public final class WorldMapInputRouting {
    public record MousePress(Optional<WorldMapOverlayRenderer.HitResult> selection, boolean consume) {
        public MousePress { Objects.requireNonNull(selection, "selection"); }
    }

    private WorldMapInputRouting() { }

    public static MousePress route(WorldMapOverlayRenderer overlay, double mouseX, double mouseY, int button,
                                   WorldMapOverlayRenderer.View view) {
        Objects.requireNonNull(overlay, "overlay");
        Objects.requireNonNull(view, "view");
        if (button != 0) return new MousePress(Optional.empty(), false);
        return new MousePress(overlay.hitTest(mouseX, mouseY, view), false);
    }

    public static RequestContext requestContext(boolean leftMouseButtonDown) {
        return leftMouseButtonDown ? RequestContext.WORLD_MAP_MOVING : RequestContext.WORLD_MAP_STILL;
    }
}
