package com.murphypotato.simmctoolset.internal.accessory.client;

import com.murphypotato.simmctoolset.internal.accessory.domain.PlanVariant;
import com.murphypotato.simmctoolset.internal.accessory.domain.WeaponMode;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GuidanceSession {
    private WeaponMode weapon = WeaponMode.BOW;
    private PlanVariant variant = PlanVariant.EXPECTED;
    private List<String> recommendedFingerprints = List.of();
    private final Map<String, LinkedHashSet<String>> discoveredSources = new LinkedHashMap<>();
    private boolean active;
    private long revision;

    public void start(
        WeaponMode weapon,
        PlanVariant variant,
        List<String> fingerprints,
        Map<String, ? extends Set<String>> knownSources
    ) {
        this.weapon = weapon == null ? WeaponMode.BOW : weapon;
        this.variant = variant == null ? PlanVariant.EXPECTED : variant;
        this.recommendedFingerprints = List.copyOf(fingerprints == null ? List.of() : fingerprints);
        discoveredSources.clear();
        if (knownSources != null) {
            knownSources.forEach((fingerprint, sources) -> {
                if (fingerprint == null || fingerprint.isBlank() || sources == null) return;
                LinkedHashSet<String> clean = new LinkedHashSet<>();
                sources.stream()
                    .map(SourceTextSanitizer::sanitize)
                    .filter(value -> !value.isBlank())
                    .forEach(clean::add);
                if (!clean.isEmpty()) discoveredSources.put(fingerprint, clean);
            });
        }
        active = !recommendedFingerprints.isEmpty();
        revision++;
    }

    public void end() {
        if (!active && recommendedFingerprints.isEmpty()) return;
        active = false;
        recommendedFingerprints = List.of();
        discoveredSources.clear();
        revision++;
    }

    public boolean active() {
        return active;
    }

    public WeaponMode weapon() {
        return weapon;
    }

    public PlanVariant variant() {
        return variant;
    }

    public String planLabel() {
        return weapon.label().replace("套", "") + "·" + variant.label();
    }

    public int requiredAccessoryCount() {
        return recommendedFingerprints.size();
    }

    public long revision() {
        return revision;
    }

    public Set<String> recommendedFingerprintSet() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(recommendedFingerprints));
    }

    public PageMatch match(Map<Integer, String> visibleSlots) {
        if (!active) return new PageMatch(Set.of(), Set.of(), 0, 0);
        HighlightMatcher.Match match = HighlightMatcher.match(recommendedFingerprintSet(), visibleSlots);
        LinkedHashSet<String> found = new LinkedHashSet<>();
        visibleSlots.forEach((slot, fingerprint) -> {
            if (match.slotIds().contains(slot)) found.add(fingerprint);
        });
        return new PageMatch(
            match.slotIds(),
            Collections.unmodifiableSet(found),
            match.foundAccessoryCount(),
            requiredAccessoryCount()
        );
    }

    public void replaceSource(String fingerprint, String sourceLabel) {
        if (!active || !recommendedFingerprintSet().contains(fingerprint)) return;
        String clean = SourceTextSanitizer.sanitize(sourceLabel);
        LinkedHashSet<String> replacement = new LinkedHashSet<>();
        if (!clean.isBlank()) replacement.add(clean);
        LinkedHashSet<String> previous = discoveredSources.get(fingerprint);
        if (replacement.isEmpty()) {
            if (previous != null) {
                discoveredSources.remove(fingerprint);
                revision++;
            }
        } else if (!replacement.equals(previous)) {
            discoveredSources.put(fingerprint, replacement);
            revision++;
        }
    }

    public Map<String, Integer> otherSourceCounts(Set<String> visibleFingerprints, String currentSource) {
        if (!active) return Map.of();
        Set<String> visible = visibleFingerprints == null ? Set.of() : visibleFingerprints;
        String current = SourceTextSanitizer.sanitize(currentSource);
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        for (String fingerprint : recommendedFingerprints) {
            if (visible.contains(fingerprint)) continue;
            for (String source : discoveredSources.getOrDefault(fingerprint, new LinkedHashSet<>())) {
                if (!source.equals(current)) counts.merge(source, 1, Integer::sum);
            }
        }
        return Collections.unmodifiableMap(counts);
    }

    public Map<String, Set<String>> sourceSnapshot() {
        LinkedHashMap<String, Set<String>> copy = new LinkedHashMap<>();
        discoveredSources.forEach((fingerprint, sources) ->
            copy.put(fingerprint, Collections.unmodifiableSet(new LinkedHashSet<>(sources)))
        );
        return Collections.unmodifiableMap(copy);
    }

    public record PageMatch(
        Set<Integer> slotIds,
        Set<String> foundFingerprints,
        int foundAccessoryCount,
        int requiredAccessoryCount
    ) {
    }
}
