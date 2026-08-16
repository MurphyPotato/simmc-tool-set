package com.murphypotato.simmctoolset.internal.simes;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure helpers for projecting a server-authoritative fermentation time. */
final class FermentationCountdown {
    private static final Pattern DAYS = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*天");
    private static final Pattern HOURS = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(?:小时|时)");
    private static final Pattern MINUTES = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(?:分钟|分)");
    private static final Pattern SECONDS = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(?:秒)");
    private static final Pattern MILLIS = Pattern.compile("(\\d+)\\s*(?:毫秒|ms)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ANY_UNIT = Pattern.compile(
            "\\d+(?:\\.\\d+)?\\s*(?:天|小时|时|分钟|分|秒|毫秒|ms)",
            Pattern.CASE_INSENSITIVE);

    private FermentationCountdown() {
    }

    static long parseMillis(String value) {
        if (value == null || value.isBlank() || !ANY_UNIT.matcher(value).find()) return -1L;
        double millis = unit(value, DAYS) * 86_400_000D
                + unit(value, HOURS) * 3_600_000D
                + unit(value, MINUTES) * 60_000D
                + unit(value, SECONDS) * 1_000D
                + unit(value, MILLIS);
        return Math.max(0L, Math.round(millis));
    }

    static boolean isServerComplete(String value) {
        if (value == null || value.isBlank()) return false;
        String normalized = value.replaceAll("\\s+", "");
        return normalized.contains("腌制已完成")
                || normalized.contains("腌制完成")
                || normalized.matches("^(?:已)?完成[！!。.]?$")
                || (normalized.contains("剩余时间") && normalized.contains("完成"));
    }

    static long remainingMillis(long calibratedMillis, long calibratedAtNanos, long nowNanos) {
        if (calibratedMillis < 0L || calibratedAtNanos <= 0L) return -1L;
        long elapsedMillis = Math.max(0L, (nowNanos - calibratedAtNanos) / 1_000_000L);
        return Math.max(0L, calibratedMillis - elapsedMillis);
    }

    static String formatMillis(long millis) {
        long safe = Math.max(0L, millis);
        long hours = safe / 3_600_000L;
        long minutes = safe % 3_600_000L / 60_000L;
        double seconds = safe % 60_000L / 1_000D;
        if (hours > 0L) return String.format(Locale.ROOT, "%d 小时 %d 分 %.1f 秒", hours, minutes, seconds);
        if (minutes > 0L) return String.format(Locale.ROOT, "%d 分 %.1f 秒", minutes, seconds);
        return String.format(Locale.ROOT, "%.1f 秒", seconds);
    }

    private static double unit(String value, Pattern pattern) {
        Matcher matcher = pattern.matcher(value);
        return matcher.find() ? Double.parseDouble(matcher.group(1)) : 0D;
    }
}
