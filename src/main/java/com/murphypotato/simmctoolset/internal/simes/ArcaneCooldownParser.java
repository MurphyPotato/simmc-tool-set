package com.murphypotato.simmctoolset.internal.simes;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses the server-authored cooldown fragments without consuming unrelated text. */
final class ArcaneCooldownParser {
    private static final Pattern COOLDOWN = Pattern.compile(
            "^\\s*(?<name>[^|｜]{1,48}?)\\s*冷却\\s*剩余\\s*[:：]\\s*"
                    + "(?<seconds>[0-9]+(?:\\.[0-9]+)?)\\s*(?:[sS]|秒)\\s*$");

    private ArcaneCooldownParser() {
    }

    static Result parse(String rawText) {
        String raw = rawText == null ? "" : rawText.trim();
        if (raw.isEmpty()) return new Result(List.of(), "");

        List<Value> values = new ArrayList<>();
        List<String> residual = new ArrayList<>();
        for (String segment : raw.split("[|｜]")) {
            String value = segment.trim();
            if (value.isEmpty()) continue;
            Matcher matcher = COOLDOWN.matcher(value);
            if (!matcher.matches()) {
                residual.add(value);
                continue;
            }
            double seconds;
            try {
                seconds = Double.parseDouble(matcher.group("seconds"));
            } catch (NumberFormatException ignored) {
                residual.add(value);
                continue;
            }
            if (!Double.isFinite(seconds) || seconds < 0.0) {
                residual.add(value);
                continue;
            }
            values.add(new Value(ArcaneColors.canonicalName(matcher.group("name")), seconds));
        }
        return new Result(List.copyOf(values), String.join(" | ", residual));
    }

    record Value(String name, double remaining) {
    }

    record Result(List<Value> values, String residual) {
    }
}
