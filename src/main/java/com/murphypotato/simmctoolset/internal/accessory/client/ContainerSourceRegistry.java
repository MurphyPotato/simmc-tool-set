package com.murphypotato.simmctoolset.internal.accessory.client;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class ContainerSourceRegistry {
    private final Map<Object, PageSource> genericSources = new IdentityHashMap<>();
    private final Map<String, Integer> titleCounts = new HashMap<>();
    private int pageSequence;

    public PageSource resolve(Object identity, String rawTitle, List<PresetSourceDetector.Control> controls) {
        PresetSourceDetector.Result preset = PresetSourceDetector.detect(controls);
        if (preset.presetNumber().isPresent()) {
            String label = "服务器预设 " + preset.presetNumber().getAsInt();
            return new PageSource(label, label, true);
        }

        Object key = identity == null ? new Object() : identity;
        PageSource existing = genericSources.get(key);
        if (existing != null) return existing;

        int page = ++pageSequence;
        String safeTitle = looksLikePresetPage(controls) ? "" : SourceTextSanitizer.sanitize(rawTitle);
        PageSource created;
        if (safeTitle.isEmpty()) {
            created = new PageSource("容器界面", "本次容器页 " + page, false);
        } else {
            int number = titleCounts.merge(safeTitle, 1, Integer::sum);
            created = new PageSource(safeTitle, safeTitle + " " + number, true);
        }
        genericSources.put(key, created);
        return created;
    }

    private static boolean looksLikePresetPage(List<PresetSourceDetector.Control> controls) {
        if (controls == null || controls.isEmpty()) return false;
        long namedPresetControls = controls.stream()
            .map(PresetSourceDetector.Control::displayName)
            .map(SourceTextSanitizer::sanitize)
            .filter(name -> name.matches(".*预设\\s*[123].*"))
            .count();
        long dyeControls = controls.stream()
            .map(PresetSourceDetector.Control::itemId)
            .filter(itemId -> itemId.endsWith("_dye"))
            .count();
        return namedPresetControls >= 2 || dyeControls >= 3;
    }

    public void clear() {
        genericSources.clear();
        titleCounts.clear();
        pageSequence = 0;
    }

    public record PageSource(String persistedTitle, String displayTitle, boolean reliable) {
    }
}
