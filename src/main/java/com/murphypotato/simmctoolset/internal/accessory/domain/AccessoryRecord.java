package com.murphypotato.simmctoolset.internal.accessory.domain;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record AccessoryRecord(
    String id,
    String fingerprint,
    String name,
    AccessorySlot slot,
    AccessoryQuality quality,
    int level,
    List<AffixRecord> affixes,
    String itemId,
    AccessorySource source
) {
    public AccessoryRecord {
        id = clean(id, UUID.randomUUID().toString());
        fingerprint = Objects.requireNonNullElse(fingerprint, "");
        name = clean(name, "未命名饰品");
        slot = Objects.requireNonNullElse(slot, AccessorySlot.MAIN_RING);
        quality = Objects.requireNonNullElse(quality, AccessoryQuality.DIM);
        level = Math.max(0, level);
        affixes = affixes == null ? List.of() : List.copyOf(affixes);
        itemId = clean(itemId, "minecraft:air");
        if (source == null) source = AccessorySource.blank(slot);
    }

    public static AccessoryRecord blank(AccessorySlot slot) {
        return new AccessoryRecord(
            "blank-" + slot.id(),
            "blank-" + slot.id(),
            "空白" + slot.label(),
            slot,
            AccessoryQuality.DIM,
            0,
            List.of(),
            "minecraft:air",
            AccessorySource.blank(slot)
        );
    }

    public boolean isBlank() {
        return source.kind().equals("blank") || id.startsWith("blank-");
    }

    public AccessoryRecord withIdentity(String nextId, String nextFingerprint) {
        return new AccessoryRecord(nextId, nextFingerprint, name, slot, quality, level, affixes, itemId, source);
    }

    public AccessoryRecord withSource(AccessorySource nextSource) {
        return new AccessoryRecord(id, fingerprint, name, slot, quality, level, affixes, itemId, nextSource);
    }

    public AccessoryRecord withEditedFields(
        String nextName,
        AccessorySlot nextSlot,
        AccessoryQuality nextQuality,
        int nextLevel,
        List<AffixRecord> nextAffixes
    ) {
        AccessoryRecord edited = new AccessoryRecord(
            id,
            "",
            nextName,
            nextSlot,
            nextQuality,
            nextLevel,
            nextAffixes,
            itemId,
            source
        );
        return edited.withIdentity(id, AccessoryFingerprint.compute(edited));
    }

    private static String clean(String value, String fallback) {
        String result = Objects.requireNonNullElse(value, "").strip();
        return result.isEmpty() ? fallback : result;
    }
}
