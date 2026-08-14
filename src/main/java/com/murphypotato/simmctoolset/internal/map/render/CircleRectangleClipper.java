package com.murphypotato.simmctoolset.internal.map.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class CircleRectangleClipper {
    private CircleRectangleClipper() {
    }

    public static List<PolygonScanlineRasterizer.FillRectangle> clip(
            PolygonScanlineRasterizer.FillRectangle rectangle,
            MinimapOverlayRenderer.Clip clip) {
        Objects.requireNonNull(rectangle, "rectangle");
        Objects.requireNonNull(clip, "clip");
        int top = Math.max(rectangle.top(), clip.top());
        int bottom = Math.min(rectangle.bottom(), clip.bottom());
        int left = Math.max(rectangle.left(), clip.left());
        int right = Math.min(rectangle.right(), clip.right());
        if (right <= left || bottom <= top) {
            return List.of();
        }
        if (!clip.circular()) {
            return List.of(new PolygonScanlineRasterizer.FillRectangle(left, top, right, bottom));
        }

        double radius = clip.size() / 2.0;
        double centerX = clip.left() + radius;
        double centerY = clip.top() + radius;
        List<PolygonScanlineRasterizer.FillRectangle> clipped = new ArrayList<>(bottom - top);
        for (int row = top; row < bottom; row++) {
            double dy = row + 0.5 - centerY;
            double halfWidth = Math.sqrt(Math.max(0, radius * radius - dy * dy));
            int rowLeft = Math.max(left, (int) Math.ceil(centerX - halfWidth));
            int rowRight = Math.min(right, (int) Math.floor(centerX + halfWidth));
            if (rowRight > rowLeft) {
                clipped.add(new PolygonScanlineRasterizer.FillRectangle(
                        rowLeft, row, rowRight, row + 1));
            }
        }
        return List.copyOf(clipped);
    }
}
