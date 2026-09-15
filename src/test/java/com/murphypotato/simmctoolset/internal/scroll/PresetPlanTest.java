package com.murphypotato.simmctoolset.internal.scroll;

import com.murphypotato.simmctoolset.internal.scroll.config.PresetStore;
import com.murphypotato.simmctoolset.internal.scroll.domain.CraftPlan;
import com.murphypotato.simmctoolset.internal.scroll.domain.Element;
import com.murphypotato.simmctoolset.internal.scroll.domain.ElementAmounts;
import com.murphypotato.simmctoolset.internal.scroll.domain.PresetPlan;
import com.murphypotato.simmctoolset.internal.scroll.domain.RotationBatch;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PresetPlanTest {
    @Test
    void combinationPreservesOrderAndExposesTotalCrafts() {
        RotationBatch first = batch("第一材料", 2, 1);
        RotationBatch second = batch("第二材料", 3, 2);
        PresetPlan preset = new PresetPlan("组合", "目标卷轴", List.of(first, second));

        assertEquals(3, preset.totalCrafts());
        assertEquals(List.of("第一材料", "第二材料"),
            preset.batches().stream().map(b -> b.plan().id()).toList());
        assertEquals(List.of(1, 2), preset.batches().stream().map(RotationBatch::crafts).toList());
    }

    @Test
    void batchQuantityAndOrderAreEditedImmutably() {
        RotationBatch first = batch("第一材料", 2, 1);
        RotationBatch second = batch("第二材料", 3, 2);
        PresetPlan preset = new PresetPlan("组合", "目标卷轴", List.of(first, second));

        PresetPlan edited = preset.withCrafts(0, 4).moveBatch(0, 1);

        assertEquals(List.of("第二材料", "第一材料"),
            edited.batches().stream().map(b -> b.plan().id()).toList());
        assertEquals(List.of(2, 4), edited.batches().stream().map(RotationBatch::crafts).toList());
        assertEquals(6, edited.totalCrafts());
        assertEquals(List.of("第一材料", "第二材料"),
            preset.batches().stream().map(b -> b.plan().id()).toList());
        assertThrows(IllegalArgumentException.class, () -> preset.withCrafts(0, 0));
        assertThrows(IllegalArgumentException.class, () -> preset.moveBatch(0, 2));
    }

    @Test
    void presetStoreRoundTripRetainsCombinationOrderAndQuantities() throws Exception {
        var dir = Files.createTempDirectory("preset-store");
        var file = dir.resolve("scroll-presets.json");
        PresetStore store = new PresetStore(file);
        PresetPlan original = new PresetPlan("组合", "目标卷轴", List.of(
            batch("第一材料", 7, 1),
            batch("第二材料", 4, 3)
        ));

        store.save(original);
        PresetStore reloaded = new PresetStore(file);
        PresetPlan restored = reloaded.list().getFirst();

        assertEquals(4, restored.totalCrafts());
        assertEquals(List.of(List.of("第一材料"), List.of("第二材料")),
            restored.batches().stream().map(b -> b.plan().materials().keySet().stream().toList()).toList());
        assertEquals(List.of(1, 3), restored.batches().stream().map(RotationBatch::crafts).toList());
        assertEquals(List.of(7, 4), restored.batches().stream()
            .map(b -> b.plan().materials().values().stream().findFirst().orElseThrow()).toList());
    }

    @Test
    void zeroCraftPresetBatchIsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> new PresetPlan("空批次", "目标卷轴", List.of(batch("材料", 1, 0))));
    }

    private static RotationBatch batch(String name, int materialCount, int crafts) {
        CraftPlan plan = new CraftPlan(name, Map.of(name, materialCount),
            amounts(materialCount), 0, 0, materialCount, 1, 1, 0);
        return new RotationBatch(plan, crafts);
    }

    private static ElementAmounts amounts(int metal) {
        int[] values = new int[Element.values().length];
        values[Element.METAL.ordinal()] = metal;
        return new ElementAmounts(values);
    }
}
