package com.murphypotato.simmctoolset.internal.accessory.parser;

import com.murphypotato.simmctoolset.internal.accessory.domain.AccessoryRecord;

import java.util.List;
import java.util.ArrayList;

public record ParseResult(
    AccessoryRecord accessory,
    ParseState state,
    Integer detectedLevel,
    int allowedAffixCount,
    List<String> reasons,
    List<String> rawLines,
    List<String> suspiciousLines
) {
    public ParseResult {
        reasons = List.copyOf(reasons);
        rawLines = List.copyOf(rawLines);
        suspiciousLines = List.copyOf(suspiciousLines);
    }

    public boolean accepted() {
        return state == ParseState.ACCEPTED;
    }

    public List<String> reasonMessages() {
        return reasons.stream().map(ParseResult::reasonMessage).toList();
    }

    public List<String> reviewMessages() {
        List<String> messages = new ArrayList<>();
        accessory.affixes().stream()
            .map(affix -> affix.warning())
            .filter(warning -> warning != null && !warning.isBlank())
            .forEach(messages::add);
        reasons.stream()
            .filter(reason -> !reason.equals("value-outside-advisory-range") && !reason.equals("unknown-combat-affix"))
            .map(this::detailedReasonMessage)
            .forEach(messages::add);
        return messages.stream().distinct().toList();
    }

    private String detailedReasonMessage(String reason) {
        if (reason.equals("invalid-affix-line") && !suspiciousLines.isEmpty()) {
            return "无法解析：" + String.join("、", suspiciousLines);
        }
        if (reason.equals("affix-count-mismatch")) {
            return "已识别 " + accessory.affixes().size() + "/应有 " + allowedAffixCount;
        }
        return reasonMessage(reason);
    }

    private static String reasonMessage(String reason) {
        return switch (reason) {
            case "invalid-title-format" -> "标题格式不完整";
            case "missing-title-quality" -> "未识别品质";
            case "missing-title-slot" -> "未识别部位";
            case "missing-title-level" -> "未识别强化等级";
            case "level-out-of-range" -> "强化等级超出品质上限";
            case "invalid-affix-line" -> "存在无法解析的词条行";
            case "invalid-affix-value" -> "存在非法词条数值";
            case "unknown-combat-affix" -> "存在未知疑似战斗词条";
            case "value-outside-advisory-range" -> "词条值超出经验范围";
            case "affix-count-over-limit" -> "词条数超过当前上限";
            case "affix-count-mismatch" -> "词条数与强化等级不一致";
            case "missing-affixes" -> "未识别到词条";
            case "always-review-enabled" -> "已开启始终复核";
            case "source-confirmation-required" -> "同款饰品有多个副本，来源待确认";
            default -> reason;
        };
    }
}
