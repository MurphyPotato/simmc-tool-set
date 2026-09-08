package com.murphypotato.simmctoolset.internal.accessory.client;

import com.murphypotato.simmctoolset.internal.accessory.domain.AccessoryRecord;
import com.murphypotato.simmctoolset.internal.accessory.domain.AccessorySource;
import com.murphypotato.simmctoolset.internal.accessory.domain.ContainerLocation;
import com.murphypotato.simmctoolset.internal.accessory.parser.ParseResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Session-only memory of a manually reviewed tooltip and its source. */
final class SessionReviewConfirmation {
    private final List<Entry> entries = new ArrayList<>();

    void clear() {
        entries.clear();
    }

    void remember(ParseResult parsed, AccessoryRecord confirmed, CapturedStack captured) {
        if (captured == null) return;
        remember(parsed, confirmed, captured.source(), captured.location());
    }

    void remember(
        ParseResult parsed,
        AccessoryRecord confirmed,
        AccessorySource source,
        ContainerLocation location
    ) {
        if (parsed == null || parsed.accessory() == null || parsed.accessory().fingerprint() == null
            || parsed.accessory().fingerprint().isBlank() || confirmed == null || source == null) return;
        Entry next = new Entry(parsed.accessory().fingerprint(), parsed.rawLines(), confirmed,
            new ReviewSource(source, location));
        for (int index = 0; index < entries.size(); index++) {
            Entry existing = entries.get(index);
            if (existing.rawFingerprint().equals(next.rawFingerprint())
                && existing.rawLines().equals(next.rawLines())
                && sameSource(existing.source(), next.source())) {
                entries.set(index, next);
                return;
            }
        }
        entries.add(next);
    }

    Optional<AccessoryRecord> find(ParseResult parsed, CapturedStack captured) {
        if (captured == null) return Optional.empty();
        return find(parsed, captured.source(), captured.location());
    }

    Optional<AccessoryRecord> find(
        ParseResult parsed,
        AccessorySource source,
        ContainerLocation location
    ) {
        if (parsed == null || parsed.accessory() == null || source == null) return Optional.empty();
        ReviewSource currentSource = new ReviewSource(source, location);
        for (Entry entry : entries) {
            if (entry.rawFingerprint().equals(parsed.accessory().fingerprint())
                && entry.rawLines().equals(parsed.rawLines())
                && sameSource(entry.source(), currentSource)) {
                return Optional.of(entry.confirmed().withSource(source));
            }
        }
        return Optional.empty();
    }

    void replaceConfirmed(String accessoryId, AccessoryRecord updated) {
        if (accessoryId == null || updated == null) return;
        for (int index = 0; index < entries.size(); index++) {
            Entry entry = entries.get(index);
            if (entry.confirmed().id().equals(accessoryId)) {
                entries.set(index, new Entry(entry.rawFingerprint(), entry.rawLines(), updated, entry.source()));
            }
        }
    }

    void removeConfirmed(String accessoryId) {
        if (accessoryId != null) entries.removeIf(entry -> entry.confirmed().id().equals(accessoryId));
    }

    private static boolean sameSource(ReviewSource left, ReviewSource right) {
        if (left == null || right == null) return false;
        boolean bothReliable = reliableLocation(left.location()) && reliableLocation(right.location());
        if (bothReliable) {
            return left.location().key().equals(right.location().key())
                && left.source().slotIndex() == right.source().slotIndex();
        }
        // Without a reliable block/preset location, the container title is the
        // only remaining source discriminator. Matching only kind+slot could
        // silently reuse a confirmation for another container page.
        return left.source().stableKey().equals(right.source().stableKey())
            && left.source().containerTitle().equals(right.source().containerTitle());
    }

    private static boolean reliableLocation(ContainerLocation location) {
        return location != null && !location.kind().equals("unlocated");
    }

    private record Entry(String rawFingerprint, List<String> rawLines, AccessoryRecord confirmed, ReviewSource source) {
        private Entry {
            rawLines = List.copyOf(rawLines == null ? List.of() : rawLines);
        }
    }

    private record ReviewSource(AccessorySource source, ContainerLocation location) {
    }
}
