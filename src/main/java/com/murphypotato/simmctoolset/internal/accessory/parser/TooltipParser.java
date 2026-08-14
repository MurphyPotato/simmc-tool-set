package com.murphypotato.simmctoolset.internal.accessory.parser;

import com.murphypotato.simmctoolset.internal.accessory.domain.AccessoryFingerprint;
import com.murphypotato.simmctoolset.internal.accessory.domain.AccessoryQuality;
import com.murphypotato.simmctoolset.internal.accessory.domain.AccessoryRecord;
import com.murphypotato.simmctoolset.internal.accessory.domain.AccessorySlot;
import com.murphypotato.simmctoolset.internal.accessory.domain.AccessorySource;
import com.murphypotato.simmctoolset.internal.accessory.domain.AffixRecord;
import com.murphypotato.simmctoolset.internal.accessory.domain.AffixStat;
import com.murphypotato.simmctoolset.internal.accessory.domain.AffixUnit;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TooltipParser {
    private static final Pattern COMPLETE_TITLE = Pattern.compile(
        "^(?<name>.+?)『(?<slot>主戒指|副戒指|主护符|副护符)』\\s*(?<level>\\+[0-9]+)$"
    );
    private static final Pattern SLOT_IN_TITLE = Pattern.compile("『(?<slot>主戒指|副戒指|主护符|副护符)』");
    private static final Pattern TITLE_LEVEL = Pattern.compile("\\+(?<level>[0-9]+)$");
    private static final String VALUE_EXPRESSION = "\\+[0-9]+(?:\\.[0-9]+)?%?";
    private static final Pattern BRACKETED_VALUE_LAST = Pattern.compile(
        "^\\s*『(?<label>[^』]+)』\\s*(?<value>" + VALUE_EXPRESSION + ")\\s*$"
    );
    private static final Pattern PLAIN_VALUE_LAST = Pattern.compile(
        "^\\s*(?<label>\\S(?:.*?\\S)?)\\s+(?<value>" + VALUE_EXPRESSION + ")\\s*$"
    );
    private static final Pattern VALUE_FIRST = Pattern.compile(
        "^\\s*(?<value>" + VALUE_EXPRESSION + ")\\s+(?<label>\\S(?:.*?\\S)?)\\s*$"
    );
    private static final Pattern NUMERIC_VALUE = Pattern.compile("^\\+[0-9]+(?:\\.[0-9]+)?%?$");
    private static final Pattern SUSPICIOUS_COMBAT = Pattern.compile("旅猎|暴击|弓|剑|专精|最终|额外|伤害");
    private static final Pattern NARRATIVE_LABEL = Pattern.compile(
        "[，。！？：；,!?;:]|^(?:每|当|若|如果|在).*$|.*(?:提高|增加|获得|触发|持续|最多|生效).*$"
    );

    public ParseResult parse(
        List<String> tooltipLines,
        String itemId,
        AccessorySource source,
        boolean alwaysReview
    ) {
        List<String> lines = tooltipLines == null
            ? List.of()
            : tooltipLines.stream().map(value -> value == null ? "" : value.strip()).toList();
        List<String> reasons = new ArrayList<>();
        List<String> suspiciousLines = new ArrayList<>();

        String title = lines.isEmpty() ? "" : lines.getFirst();
        Matcher completeTitle = COMPLETE_TITLE.matcher(title);
        AccessoryQuality quality = AccessoryQuality.fromTitle(title).orElse(AccessoryQuality.DIM);
        Optional<AccessoryQuality> detectedQuality = AccessoryQuality.fromTitle(title);
        AccessorySlot slot = detectSlot(title).orElse(AccessorySlot.MAIN_RING);
        Integer detectedLevel = detectTitleLevel(title).orElse(null);
        String name = extractName(title);

        if (!completeTitle.matches()) reasons.add("invalid-title-format");
        if (detectedQuality.isEmpty()) reasons.add("missing-title-quality");
        if (detectSlot(title).isEmpty()) reasons.add("missing-title-slot");
        if (detectedLevel == null) reasons.add("missing-title-level");
        if (detectedLevel != null && detectedLevel > quality.maxLevel()) reasons.add("level-out-of-range");

        List<AffixRecord> affixes = new ArrayList<>();
        for (int index = 1; index < lines.size(); index++) {
            String line = lines.get(index);
            if (line.isBlank()) continue;
            Optional<ParsedAffixLine> parsedLine = parseAffixLine(line);
            if (parsedLine.isEmpty()) {
                if (looksLikeCombat(line) || (!looksLikeNumericLore(line) && looksLikePotentialAttribute(line))) {
                    reasons.add("invalid-affix-line");
                    suspiciousLines.add(line);
                }
                continue;
            }

            String label = parsedLine.get().label();
            String numeric = parsedLine.get().numericValue();
            if (!isValidNumericValue(numeric)) {
                reasons.add("invalid-affix-value");
                suspiciousLines.add(line);
                continue;
            }

            AffixUnit unit = numeric.endsWith("%") ? AffixUnit.PERCENT : AffixUnit.NONE;
            String numericBody = unit == AffixUnit.PERCENT
                ? numeric.substring(1, numeric.length() - 1)
                : numeric.substring(1);
            double value;
            try {
                value = Double.parseDouble(numericBody);
            } catch (NumberFormatException error) {
                reasons.add("invalid-affix-value");
                suspiciousLines.add(line);
                continue;
            }
            if (!Double.isFinite(value)) {
                reasons.add("invalid-affix-value");
                suspiciousLines.add(line);
                continue;
            }

            Optional<AffixStat> detectedStat = AffixStat.fromLabel(label);
            AffixStat stat = detectedStat.orElse(AffixStat.OTHER);
            String warning = "";
            String displayedValue = "+" + format(value) + unit.suffix();
            if (detectedStat.isEmpty() && looksLikeCombat(label) && !AffixStat.isKnownNonDamageLabel(label)) {
                warning = label + " " + displayedValue + "：疑似战斗词条，必须人工选择正确类型";
                reasons.add("unknown-combat-affix");
                suspiciousLines.add(line);
            } else if (!stat.isWithinAdvisoryRange(value)) {
                warning = label + " " + displayedValue + "：超出原经验范围 "
                    + stat.advisoryRangeLabel() + "，仅作提示";
                reasons.add("value-outside-advisory-range");
            }

            affixes.add(new AffixRecord(
                UUID.randomUUID().toString(),
                stat,
                value,
                unit,
                label,
                line,
                warning
            ));
        }

        int levelForDraft = detectedLevel == null ? 0 : detectedLevel;
        int allowedAffixCount = quality.totalAffixSlots(levelForDraft);
        if (affixes.size() > allowedAffixCount) reasons.add("affix-count-over-limit");
        else if (detectedLevel != null && affixes.size() != allowedAffixCount) reasons.add("affix-count-mismatch");
        if (affixes.isEmpty()) reasons.add("missing-affixes");
        if (alwaysReview) reasons.add("always-review-enabled");

        AccessoryRecord draft = new AccessoryRecord(
            UUID.randomUUID().toString(),
            "",
            name,
            slot,
            quality,
            levelForDraft,
            affixes,
            itemId,
            source
        );
        draft = draft.withIdentity(draft.id(), AccessoryFingerprint.compute(draft));

        ParseState state = reasons.isEmpty() ? ParseState.ACCEPTED : ParseState.NEEDS_REVIEW;
        return new ParseResult(
            draft,
            state,
            detectedLevel,
            allowedAffixCount,
            unique(reasons),
            lines,
            unique(suspiciousLines)
        );
    }

    public static boolean isValidNumericValue(String value) {
        return value != null && NUMERIC_VALUE.matcher(value).matches();
    }

    public static boolean looksLikeCombat(String value) {
        return value != null && SUSPICIOUS_COMBAT.matcher(value).find();
    }

    public static boolean mayBeAccessory(List<String> tooltipLines) {
        if (tooltipLines == null || tooltipLines.isEmpty()) return false;
        String title = tooltipLines.getFirst() == null ? "" : tooltipLines.getFirst();
        return detectSlot(title).isPresent();
    }

    private static Optional<AccessorySlot> detectSlot(String title) {
        Matcher matcher = SLOT_IN_TITLE.matcher(title);
        return matcher.find() ? AccessorySlot.fromLabel(matcher.group("slot")) : Optional.empty();
    }

    private static Optional<Integer> detectTitleLevel(String title) {
        Matcher matcher = TITLE_LEVEL.matcher(title);
        if (!matcher.find()) return Optional.empty();
        try {
            return Optional.of(Integer.parseInt(matcher.group("level")));
        } catch (NumberFormatException error) {
            return Optional.empty();
        }
    }

    private static Optional<ParsedAffixLine> parseAffixLine(String line) {
        if (line == null || line.isBlank()) return Optional.empty();
        for (Pattern pattern : List.of(BRACKETED_VALUE_LAST, PLAIN_VALUE_LAST, VALUE_FIRST)) {
            Matcher matcher = pattern.matcher(line);
            if (!matcher.matches()) continue;
            String label = cleanAffixLabel(matcher.group("label"));
            String value = matcher.group("value");
            boolean explicitProperty = pattern == BRACKETED_VALUE_LAST;
            if (!label.isEmpty()
                && (explicitProperty || looksLikePropertyLabel(label) || looksLikeCombat(label))
                && isValidNumericValue(value)) {
                return Optional.of(new ParsedAffixLine(label, value));
            }
        }
        return Optional.empty();
    }

    private static String cleanAffixLabel(String label) {
        String result = label == null ? "" : label.strip();
        if (result.length() >= 2 && result.charAt(0) == '『' && result.charAt(result.length() - 1) == '』') {
            result = result.substring(1, result.length() - 1).strip();
        }
        return result;
    }

    private static boolean looksLikePotentialAttribute(String line) {
        if (line == null || line.isBlank()) return false;
        String stripped = line.strip();
        if (stripped.matches("^" + VALUE_EXPRESSION + "$")) return false;
        return stripped.contains("『") || stripped.contains("』")
            || stripped.matches("^" + VALUE_EXPRESSION + "\\s+.+$")
            || stripped.matches("^.+\\s+" + VALUE_EXPRESSION + "$");
    }

    private static boolean looksLikePropertyLabel(String label) {
        String value = label == null ? "" : label.strip();
        return !value.isEmpty() && value.length() <= 24 && !NARRATIVE_LABEL.matcher(value).find();
    }

    private static boolean looksLikeNumericLore(String line) {
        if (line == null) return false;
        for (Pattern pattern : List.of(PLAIN_VALUE_LAST, VALUE_FIRST)) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.matches()) {
                String label = cleanAffixLabel(matcher.group("label"));
                if (!looksLikeCombat(label) && !looksLikePropertyLabel(label)) return true;
            }
        }
        return false;
    }

    private static String extractName(String title) {
        int bracket = title.indexOf('『');
        String result = bracket >= 0 ? title.substring(0, bracket) : title;
        return result.strip().isEmpty() ? "未识别饰品" : result.strip();
    }

    private static List<String> unique(List<String> values) {
        return values.stream().distinct().toList();
    }

    private static String format(double value) {
        return java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private record ParsedAffixLine(String label, String numericValue) {
    }
}
