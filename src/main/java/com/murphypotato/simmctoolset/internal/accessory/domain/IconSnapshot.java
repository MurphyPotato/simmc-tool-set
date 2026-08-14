package com.murphypotato.simmctoolset.internal.accessory.domain;

import com.google.gson.Gson;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

public record IconSnapshot(
    String itemId,
    String itemModel,
    List<Float> customModelFloats,
    List<Boolean> customModelFlags,
    List<String> customModelStrings,
    List<Integer> customModelColors,
    Integer dyedColor,
    Boolean enchantmentGlint
) {
    public static final int MAX_ENCODED_SIZE = 16 * 1024;
    private static final int MAX_COMPONENT_VALUES = 256;
    private static final Gson GSON = new Gson();

    public IconSnapshot {
        itemId = clean(itemId);
        itemModel = clean(itemModel);
        customModelFloats = copy(customModelFloats);
        customModelFlags = copy(customModelFlags);
        customModelStrings = copy(customModelStrings);
        customModelColors = copy(customModelColors);
        if (!validColor(dyedColor)) dyedColor = null;
    }

    public static IconSnapshot empty(String itemId) {
        return new IconSnapshot(itemId, "", List.of(), List.of(), List.of(), List.of(), null, null);
    }

    public boolean hasVisualComponents() {
        return !itemModel.isBlank() || !customModelFloats.isEmpty() || !customModelFlags.isEmpty()
            || !customModelStrings.isEmpty() || !customModelColors.isEmpty()
            || dyedColor != null || enchantmentGlint != null;
    }

    public boolean valid() {
        if (itemId.isBlank() || itemId.length() > 300 || itemModel.length() > 300) return false;
        if (customModelFloats.size() > MAX_COMPONENT_VALUES
            || customModelFlags.size() > MAX_COMPONENT_VALUES
            || customModelStrings.size() > MAX_COMPONENT_VALUES
            || customModelColors.size() > MAX_COMPONENT_VALUES) return false;
        for (Float value : customModelFloats) {
            if (value == null || !Float.isFinite(value)) return false;
        }
        for (Boolean value : customModelFlags) {
            if (value == null) return false;
        }
        for (String value : customModelStrings) {
            if (value == null || value.length() > 1024) return false;
        }
        for (Integer value : customModelColors) {
            if (value == null) return false;
        }
        return GSON.toJson(this).getBytes(StandardCharsets.UTF_8).length <= MAX_ENCODED_SIZE;
    }

    private static boolean validColor(Integer value) {
        return value == null || value >= 0 && value <= 0xFFFFFF;
    }

    private static String clean(String value) {
        return Objects.requireNonNullElse(value, "").strip();
    }

    private static <T> List<T> copy(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
