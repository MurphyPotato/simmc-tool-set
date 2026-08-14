package com.murphypotato.simmctoolset.internal.accessory.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class AccessoryLibrary {
    private final List<AccessoryRecord> accessories = new ArrayList<>();

    public AccessoryLibrary(List<AccessoryRecord> initial) {
        if (initial != null) initial.forEach(item -> accessories.add(
            item.withIdentity(item.id(), AccessoryFingerprint.compute(item))
        ));
    }

    public List<AccessoryRecord> items() {
        return List.copyOf(accessories);
    }

    public UpsertResult upsertScanned(AccessoryRecord scanned) {
        String fingerprint = AccessoryFingerprint.compute(scanned);
        AccessoryRecord normalized = scanned.withIdentity(scanned.id(), fingerprint);

        for (int index = 0; index < accessories.size(); index++) {
            AccessoryRecord existing = accessories.get(index);
            if (existing.fingerprint().equals(fingerprint)
                && existing.source().stableKey().equals(normalized.source().stableKey())) {
                if (existing.source().canBeEnrichedBy(normalized.source())) {
                    AccessoryRecord enriched = existing.withSource(normalized.source());
                    accessories.set(index, enriched);
                    return new UpsertResult(enriched, UpsertDisposition.SOURCE_ENRICHED);
                }
                return new UpsertResult(existing, UpsertDisposition.UNCHANGED);
            }
        }

        accessories.add(normalized);
        return new UpsertResult(normalized, UpsertDisposition.ADDED);
    }

    public Optional<AccessoryRecord> find(String id) {
        return accessories.stream().filter(item -> item.id().equals(id)).findFirst();
    }

    public AccessoryRecord addDistinct(AccessoryRecord accessory) {
        String fingerprint = AccessoryFingerprint.compute(accessory);
        String id = accessory.id();
        while (find(id).isPresent()) id = UUID.randomUUID().toString();
        AccessoryRecord added = accessory.withIdentity(id, fingerprint);
        accessories.add(added);
        return added;
    }

    public boolean update(AccessoryRecord edited) {
        for (int index = 0; index < accessories.size(); index++) {
            if (accessories.get(index).id().equals(edited.id())) {
                String fingerprint = AccessoryFingerprint.compute(edited);
                accessories.set(index, edited.withIdentity(edited.id(), fingerprint));
                return true;
            }
        }
        return false;
    }

    public boolean remove(String id) {
        return accessories.removeIf(item -> item.id().equals(id));
    }

    public void clear() {
        accessories.clear();
    }

    public enum UpsertDisposition {
        ADDED,
        SOURCE_ENRICHED,
        UNCHANGED
    }

    public record UpsertResult(AccessoryRecord accessory, UpsertDisposition disposition) {
        public boolean added() {
            return disposition == UpsertDisposition.ADDED;
        }

        public boolean sourceEnriched() {
            return disposition == UpsertDisposition.SOURCE_ENRICHED;
        }

        public boolean unchanged() {
            return disposition == UpsertDisposition.UNCHANGED;
        }
    }
}
