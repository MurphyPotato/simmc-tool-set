package com.murphypotato.simmctoolset.internal.simes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure state machine for Simes casting, duration and level boss bars. */
final class ArcaneStatusState {
    static final long EXIT_NANOS = 220_000_000L;
    private static final long PENDING_TIMEOUT_NANOS = 1_000_000_000L;
    private static final Pattern CASTING = Pattern.compile("^\\s*正在吟唱\\s+(.+?)\\s*$");
    private static final Pattern DURATION = Pattern.compile(
            "^\\s*(.+?)剩余\\s*[:：]\\s*(\\d+)\\s*tick\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ARCANE_LEVEL = Pattern.compile(
            "^\\s*(.+?)\\s+Lv\\s*5(?:\\s*(?:MAX(?:\\s*/\\s*MAX)?|/\\s*MAX))?\\s*$",
            Pattern.CASE_INSENSITIVE);

    enum Kind {
        PENDING,
        LEVEL,
        CASTING,
        DURATION
    }

    record Decision(boolean recognized, boolean cancel) {
        private static final Decision NONE = new Decision(false, false);
    }

    record Snapshot(UUID id, Kind kind, String name, float progress, int remainingTicks, int totalTicks,
                    long createdAt, long updatedAt, boolean exiting, boolean interrupted, boolean suppressed) {
    }

    private final Set<String> knownNames;
    private final Map<UUID, Entry> entries = new LinkedHashMap<>();
    private final SuppressedBossBarIds suppressedIds = new SuppressedBossBarIds();

    ArcaneStatusState(Set<String> knownNames) {
        this.knownNames = Set.copyOf(knownNames);
    }

    Decision add(UUID id, String rawName, float progress, boolean notchedTen, long now, boolean hide) {
        if (id == null) return Decision.NONE;
        if (suppressedIds.contains(id)) {
            Parsed parsed = classify(rawName);
            Entry entry = entries.get(id);
            if (entry != null && parsed != null) activate(entry, parsed, progress, now);
            return new Decision(true, true);
        }

        Parsed parsed = classify(rawName);
        if (parsed == null) {
            entries.remove(id);
            if (rawName == null || rawName.isBlank()) {
                if (notchedTen && clamp(progress) >= 0.99f) {
                    entries.put(id, Entry.pending(id, now));
                }
            }
            return Decision.NONE;
        }

        Entry entry = Entry.from(id, parsed, progress, now, hide);
        entries.put(id, entry);
        if (hide) suppressedIds.suppress(id);
        return new Decision(true, hide);
    }

    Decision updateProgress(UUID id, float progress, long now, boolean hide) {
        if (id == null) return Decision.NONE;
        Entry entry = entries.get(id);
        if (suppressedIds.contains(id)) {
            if (entry != null) {
                entry.progress = clamp(progress);
                entry.updatedAt = now;
            }
            return new Decision(true, true);
        }
        if (entry != null && entry.exitAt != 0L) return Decision.NONE;
        if (entry == null || entry.kind == Kind.PENDING) return Decision.NONE;
        entry.progress = clamp(progress);
        entry.updatedAt = now;
        return markSuppressed(entry, id, hide);
    }

    Decision updateName(UUID id, String rawName, long now, boolean hide) {
        if (id == null) return Decision.NONE;
        Entry entry = entries.get(id);
        if (suppressedIds.contains(id)) {
            if (entry != null) {
                Parsed parsed = classify(rawName);
                if (parsed != null) activate(entry, parsed, entry.progress, now);
                else entries.remove(id);
            }
            return new Decision(true, true);
        }
        if (entry == null) return Decision.NONE;
        if (entry.exitAt != 0L) return Decision.NONE;

        Parsed parsed = classify(rawName);
        if (parsed == null) {
            entries.remove(id);
            return entry.kind == Kind.PENDING ? Decision.NONE : new Decision(true, false);
        }
        activate(entry, parsed, entry.progress, now);
        return markSuppressed(entry, id, hide);
    }

    Decision updateStyle(UUID id, long now, boolean hide) {
        return updateMetadata(id, now, hide);
    }

    Decision updateProperties(UUID id, long now, boolean hide) {
        return updateMetadata(id, now, hide);
    }

    private Decision updateMetadata(UUID id, long now, boolean hide) {
        if (id == null) return Decision.NONE;
        if (suppressedIds.contains(id)) return new Decision(true, true);
        Entry entry = entries.get(id);
        if (entry != null && entry.exitAt != 0L) return Decision.NONE;
        if (entry == null || entry.kind == Kind.PENDING) return Decision.NONE;
        entry.updatedAt = now;
        return markSuppressed(entry, id, hide);
    }

    Decision remove(UUID id, long now) {
        if (id == null) return Decision.NONE;
        boolean cancel = suppressedIds.release(id);
        Entry entry = entries.get(id);
        if (entry == null) return cancel ? new Decision(true, true) : Decision.NONE;
        if (entry.kind == Kind.PENDING || entry.kind == Kind.LEVEL) {
            entries.remove(id);
            return new Decision(true, cancel);
        }
        entry.exitAt = now;
        entry.interrupted = entry.kind == Kind.CASTING && entry.progress < 0.995f;
        entry.updatedAt = now;
        entry.suppressed = false;
        return new Decision(true, cancel);
    }

    void tick(long now) {
        entries.entrySet().removeIf(item -> {
            Entry entry = item.getValue();
            if (entry.kind == Kind.PENDING) return now - entry.createdAt > PENDING_TIMEOUT_NANOS;
            return entry.exitAt != 0L && now - entry.exitAt > EXIT_NANOS;
        });
    }

    void reset() {
        entries.clear();
        suppressedIds.clear();
    }

    Snapshot snapshot(UUID id) {
        Entry entry = entries.get(id);
        return entry == null ? null : entry.snapshot();
    }

    List<Snapshot> snapshots(long now) {
        List<Snapshot> result = new ArrayList<>();
        for (Entry entry : entries.values()) {
            if (entry.kind == Kind.PENDING || entry.kind == Kind.LEVEL) continue;
            result.add(entry.snapshot());
        }
        return List.copyOf(result);
    }

    boolean isSuppressed(UUID id) {
        return suppressedIds.contains(id);
    }

    private Decision markSuppressed(Entry entry, UUID id, boolean hide) {
        if (!hide) return new Decision(true, entry.suppressed);
        entry.suppressed = true;
        suppressedIds.suppress(id);
        return new Decision(true, true);
    }

    private void activate(Entry entry, Parsed parsed, float progress, long now) {
        entry.kind = parsed.kind;
        entry.name = parsed.name;
        entry.exitAt = 0L;
        entry.interrupted = false;
        entry.updatedAt = now;
        if (parsed.kind == Kind.DURATION) {
            entry.totalTicks = parsed.ticks;
            entry.remainingTicks = parsed.ticks;
            entry.progress = 1.0f;
        } else {
            entry.totalTicks = 0;
            entry.remainingTicks = 0;
            entry.progress = clamp(progress);
        }
    }

    private Parsed classify(String rawName) {
        String raw = rawName == null ? "" : rawName.trim();
        Matcher casting = CASTING.matcher(raw);
        if (casting.matches()) {
            String name = canonical(casting.group(1));
            return known(name) ? new Parsed(Kind.CASTING, name, 0) : null;
        }
        Matcher duration = DURATION.matcher(raw);
        if (duration.matches()) {
            String name = canonical(duration.group(1));
            if (!known(name)) return null;
            try {
                return new Parsed(Kind.DURATION, name, Integer.parseInt(duration.group(2)));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        Matcher level = ARCANE_LEVEL.matcher(raw);
        if (level.matches()) {
            String name = canonical(level.group(1));
            return known(name) ? new Parsed(Kind.LEVEL, name, 0) : null;
        }
        return null;
    }

    private boolean known(String name) {
        return knownNames.contains(name);
    }

    private static String canonical(String raw) {
        String name = raw == null ? "" : raw.trim();
        return switch (name) {
            case "蛛化术", "蛛化" -> "蜘化术";
            case "腾云" -> "腾云术";
            case "凌步" -> "凌步术";
            case "御风" -> "御风术";
            case "跳跃" -> "跳跃术";
            case "激流" -> "激流术";
            case "斥力" -> "斥力术";
            case "引力" -> "引力术";
            case "火陨" -> "火陨术";
            default -> name;
        };
    }

    private static float clamp(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private record Parsed(Kind kind, String name, int ticks) {
    }

    private static final class Entry {
        private final UUID id;
        private Kind kind;
        private String name;
        private float progress;
        private int remainingTicks;
        private int totalTicks;
        private final long createdAt;
        private long updatedAt;
        private long exitAt;
        private boolean interrupted;
        private boolean suppressed;

        private Entry(UUID id, Kind kind, String name, float progress, long now, boolean suppressed) {
            this.id = id;
            this.kind = kind;
            this.name = name;
            this.progress = clamp(progress);
            this.createdAt = now;
            this.updatedAt = now;
            this.suppressed = suppressed;
        }

        private static Entry pending(UUID id, long now) {
            return new Entry(id, Kind.PENDING, "", 1.0f, now, false);
        }

        private static Entry from(UUID id, Parsed parsed, float progress, long now, boolean suppressed) {
            Entry entry = new Entry(id, parsed.kind, parsed.name, progress, now, suppressed);
            if (parsed.kind == Kind.DURATION) {
                entry.totalTicks = parsed.ticks;
                entry.remainingTicks = parsed.ticks;
                entry.progress = 1.0f;
            }
            return entry;
        }

        private Snapshot snapshot() {
            return new Snapshot(id, kind, name, progress, remainingTicks, totalTicks, createdAt, updatedAt,
                    exitAt != 0L, interrupted, suppressed);
        }
    }
}
