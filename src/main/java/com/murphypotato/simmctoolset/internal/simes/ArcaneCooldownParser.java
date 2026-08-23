package com.murphypotato.simmctoolset.internal.simes;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses the server-authored cooldown fragments without consuming unrelated text. */
final class ArcaneCooldownParser {
    private static final Pattern COOLDOWN = Pattern.compile(
            "(?:^|[|｜])\\s*(?<name>[^|｜]{1,32}?)\\s*冷却\\s*剩余\\s*[:：]\\s*"
                    + "(?<seconds>[0-9]+(?:\\.[0-9]+)?)\\s*(?:[sS]|秒)");

    private ArcaneCooldownParser() {
    }

    static Result parse(String rawText) {
        String raw = rawText == null ? "" : rawText.trim();
        if (raw.isEmpty()) return new Result(List.of(), "");

        Matcher matcher = COOLDOWN.matcher(raw);
        List<Value> values = new ArrayList<>();
        List<String> residual = new ArrayList<>();
        int end = 0;
        while (matcher.find()) {
            addResidual(residual, raw.substring(end, matcher.start()));
            double seconds;
            try {
                seconds = Double.parseDouble(matcher.group("seconds"));
            } catch (NumberFormatException ignored) {
                addResidual(residual, matcher.group());
                end = matcher.end();
                continue;
            }
            if (!Double.isFinite(seconds) || seconds < 0.0) {
                addResidual(residual, matcher.group());
                end = matcher.end();
                continue;
            }
            values.add(new Value(ArcaneColors.canonicalName(matcher.group("name")), seconds));
            end = matcher.end();
        }
        addResidual(residual, raw.substring(end));
        return new Result(List.copyOf(values), String.join(" | ", residual));
    }

    private static void addResidual(List<String> residual, String raw) {
        String value = raw.trim();
        while (value.startsWith("|") || value.startsWith("｜")) value = value.substring(1).trim();
        while (value.endsWith("|") || value.endsWith("｜")) value = value.substring(0, value.length() - 1).trim();
        if (!value.isEmpty()) residual.add(value);
    }

    record Value(String name, double remaining) {
    }

    record Result(List<Value> values, String residual) {
    }
}
