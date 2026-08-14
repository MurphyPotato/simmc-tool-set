package com.murphypotato.simmctoolset.internal.accessory.domain;

import java.util.Objects;

public record AccessorySource(
    String kind,
    String containerTitle,
    int slotIndex,
    String slotLabel
) {
    public AccessorySource {
        kind = clean(kind, "inventory");
        containerTitle = clean(containerTitle, kind.equals("blank") ? "空白" : "未知来源");
        slotLabel = clean(slotLabel, "槽位 " + slotIndex);
    }

    public static AccessorySource blank(AccessorySlot slot) {
        return new AccessorySource("blank", "空白", -1, slot.label());
    }

    public String stableKey() {
        return kind + "\u001f" + slotIndex;
    }

    public boolean canBeEnrichedBy(AccessorySource candidate) {
        return candidate != null
            && stableKey().equals(candidate.stableKey())
            && specificity(containerTitle) == 0
            && specificity(candidate.containerTitle()) > 0;
    }

    private static int specificity(String title) {
        if (title == null || title.isBlank() || title.equals("未知来源") || title.equals("容器界面")) return 0;
        if (title.matches("本次容器页 \\d+")) return 0;
        if (title.matches("服务器预设 [123]")) return 3;
        if (title.equals("玩家物品栏")) return 3;
        return 2;
    }

    private static String clean(String value, String fallback) {
        String result = Objects.requireNonNullElse(value, "").strip();
        return result.isEmpty() ? fallback : result;
    }
}
