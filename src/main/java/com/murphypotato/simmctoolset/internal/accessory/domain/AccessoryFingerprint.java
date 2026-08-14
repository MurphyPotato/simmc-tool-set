package com.murphypotato.simmctoolset.internal.accessory.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class AccessoryFingerprint {
    private AccessoryFingerprint() {
    }

    public static String compute(AccessoryRecord accessory) {
        return compute(accessory, true);
    }

    public static String computeLegacy(AccessoryRecord accessory) {
        return compute(accessory, false);
    }

    private static String compute(AccessoryRecord accessory, boolean includeUnit) {
        StringBuilder canonical = new StringBuilder()
            .append(accessory.name()).append('\u001f')
            .append(accessory.slot().id()).append('\u001f')
            .append(accessory.quality().id()).append('\u001f')
            .append(accessory.level()).append('\u001f')
            .append(accessory.itemId());
        for (AffixRecord affix : accessory.affixes()) {
            canonical.append('\u001e')
                .append(includeUnit ? affix.stat().id() : legacyStatId(affix)).append('\u001f')
                .append(Double.toString(affix.value())).append('\u001f');
            if (includeUnit) canonical.append(affix.unit().name()).append('\u001f');
            canonical.append(affix.label());
        }

        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    private static String legacyStatId(AffixRecord affix) {
        if (affix.stat() != AffixStat.OTHER) return affix.stat().id();
        String label = affix.label().strip();
        if (label.equals("百分比伤害减免") || label.equals("伤害减免")) return "damageReduction";
        if (label.equals("潜行速度")) return "sneakSpeed";
        return affix.stat().id();
    }
}
