package com.murphypotato.simmctoolset.internal.accessory.client;

public record GuidanceBannerLayout(
    int mainCenterX,
    int mainY,
    int buttonLeft,
    int buttonTop,
    int buttonRight,
    int buttonBottom,
    int detailsCenterX,
    int detailsY,
    int backgroundBottom,
    boolean wrapped
) {
    private static final int EDGE = 4;
    private static final int GAP = 6;

    public static GuidanceBannerLayout compute(
        int screenWidth,
        int mainTextWidth,
        int buttonWidth,
        boolean hasDetails
    ) {
        int width = Math.max(120, screenWidth);
        int center = width / 2;
        int visibleMainWidth = Math.min(Math.max(0, mainTextWidth), width - EDGE * 2);
        int safeButtonWidth = Math.min(Math.max(24, buttonWidth), width - EDGE * 2);
        int mainLeft = center - visibleMainWidth / 2;
        int adjacentLeft = mainLeft + visibleMainWidth + GAP;
        boolean wrapped = adjacentLeft + safeButtonWidth > width - EDGE;
        int buttonLeft = wrapped ? center - safeButtonWidth / 2 : adjacentLeft;
        int buttonTop = wrapped ? 17 : 5;
        int detailsY = hasDetails ? (wrapped ? 33 : 19) : -1;
        int bottom = hasDetails ? (wrapped ? 45 : 31) : (wrapped ? 31 : 18);
        return new GuidanceBannerLayout(
            center,
            7,
            buttonLeft,
            buttonTop,
            buttonLeft + safeButtonWidth,
            buttonTop + 13,
            center,
            detailsY,
            bottom,
            wrapped
        );
    }
}
