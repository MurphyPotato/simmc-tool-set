package com.murphypotato.simmctoolset.internal.accessory.client;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class HighlightMatcher {
    private HighlightMatcher() {
    }

    public static Match match(Set<String> selectedFingerprints, Map<Integer, String> slotFingerprints) {
        Set<Integer> slotIds = new HashSet<>();
        Set<String> foundFingerprints = new HashSet<>();
        for (Map.Entry<Integer, String> entry : slotFingerprints.entrySet()) {
            if (!selectedFingerprints.contains(entry.getValue())) continue;
            slotIds.add(entry.getKey());
            foundFingerprints.add(entry.getValue());
        }
        return new Match(slotIds, foundFingerprints.size());
    }

    public record Match(Set<Integer> slotIds, int foundAccessoryCount) {
        public Match {
            slotIds = Set.copyOf(slotIds);
        }
    }
}
