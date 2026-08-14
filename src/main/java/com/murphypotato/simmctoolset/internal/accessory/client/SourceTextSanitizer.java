package com.murphypotato.simmctoolset.internal.accessory.client;

import java.util.Objects;

public final class SourceTextSanitizer {
    private SourceTextSanitizer() {
    }

    public static String sanitize(String input) {
        String value = Objects.requireNonNullElse(input, "");
        StringBuilder result = new StringBuilder(value.length());
        boolean skipFormattingCode = false;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (skipFormattingCode) {
                skipFormattingCode = false;
                continue;
            }
            if (codePoint == '§') {
                skipFormattingCode = true;
                continue;
            }
            int type = Character.getType(codePoint);
            if (codePoint == 0xFFFD
                || type == Character.CONTROL
                || type == Character.FORMAT
                || type == Character.PRIVATE_USE
                || type == Character.SURROGATE
                || type == Character.UNASSIGNED) {
                continue;
            }
            result.appendCodePoint(codePoint);
        }

        String collapsed = result.toString().replaceAll("\\s+", " ").strip();
        return containsReadableCharacter(collapsed) ? collapsed : "";
    }

    private static boolean containsReadableCharacter(String value) {
        return value.codePoints().anyMatch(codePoint -> Character.isLetterOrDigit(codePoint));
    }
}
