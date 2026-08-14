package com.murphypotato.simmctoolset.internal.accessory.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PresetSourceDetector {
    private static final Pattern PRESET_NUMBER = Pattern.compile("预设\\s*([123])");

    private PresetSourceDetector() {
    }

    public static Result detect(List<Control> controls) {
        List<Integer> enabled = new ArrayList<>();
        List<Integer> lime = new ArrayList<>();
        for (Control control : controls == null ? List.<Control>of() : controls) {
            String name = SourceTextSanitizer.sanitize(control.displayName());
            OptionalInt number = presetNumber(name);
            if (name.contains("已启用") && number.isPresent()) enabled.add(number.getAsInt());
            if (control.itemId().equals("minecraft:lime_dye") && number.isPresent()) lime.add(number.getAsInt());
        }

        boolean enabledConflict = enabled.size() > 1;
        boolean limeConflict = lime.size() > 1;
        if (enabledConflict || limeConflict) return Result.none(Evidence.CONFLICT);
        if (enabled.size() == 1) {
            int value = enabled.getFirst();
            if (lime.size() == 1 && lime.getFirst() != value) return Result.none(Evidence.CONFLICT);
            return Result.found(value, Evidence.ENABLED_NAME);
        }
        if (lime.size() == 1) return Result.found(lime.getFirst(), Evidence.LIME_DYE);
        return Result.none(Evidence.MISSING);
    }

    private static OptionalInt presetNumber(String value) {
        Matcher matcher = PRESET_NUMBER.matcher(value);
        return matcher.find() ? OptionalInt.of(Integer.parseInt(matcher.group(1))) : OptionalInt.empty();
    }

    public enum Evidence {
        ENABLED_NAME,
        LIME_DYE,
        CONFLICT,
        MISSING
    }

    public record Control(String itemId, String displayName) {
        public Control {
            itemId = Objects.requireNonNullElse(itemId, "");
            displayName = Objects.requireNonNullElse(displayName, "");
        }
    }

    public record Result(OptionalInt presetNumber, Evidence evidence) {
        public static Result found(int presetNumber, Evidence evidence) {
            return new Result(OptionalInt.of(presetNumber), evidence);
        }

        public static Result none(Evidence evidence) {
            return new Result(OptionalInt.empty(), evidence);
        }
    }
}
