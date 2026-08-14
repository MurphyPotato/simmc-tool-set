package com.murphypotato.simmctoolset.internal.scroll.config;

import com.murphypotato.simmctoolset.internal.scroll.domain.ArcaneSolver;
import com.murphypotato.simmctoolset.internal.scroll.domain.GameData;

import java.util.LinkedHashSet;
import java.util.Set;

public record ArcaneSettings(
    Set<String> excludedMaterials,
    String selectedRecipe,
    int quantity,
    boolean includeMainMaterial,
    int repeatThreshold
) {
    public ArcaneSettings {
        excludedMaterials = Set.copyOf(new LinkedHashSet<>(excludedMaterials));
        selectedRecipe = selectedRecipe == null ? "" : selectedRecipe;
        quantity = Math.max(1, Math.min(9999, quantity));
        repeatThreshold = Math.max(1, Math.min(999, repeatThreshold));
    }

    public static ArcaneSettings defaults(GameData data) {
        return new ArcaneSettings(
            Set.of(), data.recipes().getFirst().name(), 1, true, ArcaneSolver.DEFAULT_REPEAT_THRESHOLD
        );
    }

    public ArcaneSettings withExcludedMaterials(Set<String> names) {
        return new ArcaneSettings(names, selectedRecipe, quantity, includeMainMaterial, repeatThreshold);
    }

    public ArcaneSettings withSelectedRecipe(String name) {
        return new ArcaneSettings(excludedMaterials, name, quantity, includeMainMaterial, repeatThreshold);
    }

    public ArcaneSettings withInputs(int nextQuantity, boolean nextIncludeMainMaterial, int nextRepeatThreshold) {
        return new ArcaneSettings(excludedMaterials, selectedRecipe, nextQuantity, nextIncludeMainMaterial, nextRepeatThreshold);
    }
}
