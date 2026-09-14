package com.murphypotato.simmctoolset.internal.scroll;

import com.murphypotato.simmctoolset.internal.scroll.domain.Element;
import com.murphypotato.simmctoolset.internal.scroll.domain.GameData;
import com.murphypotato.simmctoolset.internal.scroll.domain.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Independent spot checks against the owner's first worksheet (A2:I127). */
class MaterialSourceTest {
    @Test
    void workbookRowsReplaceLegacyMaterialTableWithoutChangingRecipeIdentity() {
        GameData data = GameData.load();
        assertEquals(126, data.materials().size());
        assertFalse(data.recipes().isEmpty());
        assertEquals(126, data.materials().stream().map(Material::name).distinct().count());
        assertTrue(data.materials().stream().noneMatch(m -> m.name().equals("灵魂土")),
                "The selected workbook does not supply this legacy material; do not invent its values");
    }

    @Test
    void firstMiddleAndLastSourceVectorsUseNamedElementColumns() {
        GameData data = GameData.load();
        Material copper = find(data, "铜");
        assertEquals(1, copper.elements().get(Element.METAL));
        assertEquals(3, copper.elements().get(Element.LIGHTNING));
        assertEquals(0, copper.elements().get(Element.WATER));
        Material dripstone = find(data, "滴水石锥");
        assertEquals(3, dripstone.elements().get(Element.EARTH));
        assertEquals(1, dripstone.elements().get(Element.WATER));
        Material magma = find(data, "岩浆膏");
        assertEquals(1, magma.elements().get(Element.WATER));
        assertEquals(2, magma.elements().get(Element.FIRE));
    }

    private static Material find(GameData data, String name) {
        return data.materials().stream().filter(m -> m.name().equals(name)).findFirst().orElseThrow();
    }
}
