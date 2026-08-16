package com.murphypotato.simmctoolset.internal.simes;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses the server-visible cooking-clock conversation without claiming missing data. */
final class SimesBrewingClockParser {
    private static final Pattern INGREDIENT = Pattern.compile("^[-－]\\s*\\[([^]]+)]\\s*[xX×]\\s*(\\d+).*$");
    private static final Pattern REMAINING = Pattern.compile(
            ".*剩余时间[：:]\\s*(.+?)(?=\\s*(?:。|！|!|\\.(?!\\d)|正在腌制|$)).*");
    private static final Pattern PRODUCT = Pattern.compile(".*正在腌制[：:]?\\s*\\[([^]]+)].*");

    private SimesBrewingClockParser() {
    }

    static Event parse(String raw) {
        if (raw == null) return new Ignore();
        String line = raw.strip();
        if (line.isEmpty()) return new Ignore();
        if (line.contains("腌制已中断")) return new Invalidate("已中断");
        if (line.contains("腌制已开始")) return new Invalidate("发酵中 · 待校准");
        if (line.contains("当前桶内的材料")) return new MaterialsHeader();

        Matcher ingredient = INGREDIENT.matcher(line);
        if (ingredient.matches()) {
            return new Ingredient(ingredient.group(1), Integer.parseInt(ingredient.group(2)));
        }

        Matcher remaining = REMAINING.matcher(line);
        if (remaining.matches()) {
            Matcher product = PRODUCT.matcher(line);
            return new Remaining(remaining.group(1).strip(), product.matches() ? product.group(1).strip() : "");
        }
        if (FermentationCountdown.isServerComplete(line)) return new Completed();
        return new Ignore();
    }

    sealed interface Event permits Ignore, MaterialsHeader, Ingredient, Invalidate, Remaining, Completed {
    }

    record Ignore() implements Event {
    }

    record MaterialsHeader() implements Event {
    }

    record Ingredient(String name, int count) implements Event {
    }

    record Invalidate(String status) implements Event {
    }

    record Remaining(String remaining, String product) implements Event {
    }

    record Completed() implements Event {
    }
}
