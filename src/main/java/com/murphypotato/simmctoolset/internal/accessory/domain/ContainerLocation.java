package com.murphypotato.simmctoolset.internal.accessory.domain;

import java.util.Objects;

public record ContainerLocation(
    String kind,
    String serverScope,
    String dimension,
    Integer x,
    Integer y,
    Integer z,
    String containerType,
    int presetNumber,
    String displayLabel
) {
    public ContainerLocation {
        kind = clean(kind, "unlocated");
        serverScope = clean(serverScope, "");
        dimension = clean(dimension, "");
        containerType = clean(containerType, "容器");
        displayLabel = clean(displayLabel, defaultLabel(kind, containerType, presetNumber));
        if (!kind.equals("block")) {
            x = null;
            y = null;
            z = null;
        }
        if (!kind.equals("preset")) presetNumber = 0;
    }

    public static ContainerLocation inventory(String serverScope) {
        return new ContainerLocation("inventory", serverScope, "", null, null, null, "物品栏", 0, "玩家物品栏");
    }

    public static ContainerLocation preset(String serverScope, int presetNumber) {
        return new ContainerLocation("preset", serverScope, "", null, null, null,
            "服务器预设", presetNumber, "服务器预设 " + presetNumber);
    }

    public static ContainerLocation unlocated(String label) {
        return new ContainerLocation("unlocated", "", "", null, null, null, "容器", 0, label);
    }

    public boolean locatedBlock() {
        return kind.equals("block") && x != null && y != null && z != null
            && !serverScope.isBlank() && !dimension.isBlank();
    }

    public String key() {
        return switch (kind) {
            case "block" -> "block\u001f" + serverScope + "\u001f" + dimension + "\u001f" + x + "\u001f" + y + "\u001f" + z;
            case "preset" -> "preset\u001f" + serverScope + "\u001f" + presetNumber;
            case "inventory" -> "inventory\u001f" + serverScope;
            default -> "unlocated\u001f" + displayLabel;
        };
    }

    private static String defaultLabel(String kind, String type, int preset) {
        return switch (kind) {
            case "inventory" -> "玩家物品栏";
            case "preset" -> "服务器预设 " + preset;
            case "block" -> type;
            default -> "未定位容器";
        };
    }

    private static String clean(String value, String fallback) {
        String result = Objects.requireNonNullElse(value, "").strip();
        return result.isEmpty() ? fallback : result;
    }
}
