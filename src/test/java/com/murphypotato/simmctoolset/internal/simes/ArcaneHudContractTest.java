package com.murphypotato.simmctoolset.internal.simes;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ArcaneHudContractTest {
    @Test
    void parsesFullWidthColonSecondsAndResidualText() {
        ArcaneCooldownParser.Result result = ArcaneCooldownParser.parse(
                "火球术 冷却剩余：2.5s | 治愈术 冷却剩余: 1 秒 | ready");
        List<ArcaneCooldownParser.Value> values = result.values();
        assertEquals(2, values.size());
        assertEquals("火球术", values.get(0).name());
        assertEquals(2.5, values.get(0).remaining());
        assertEquals("治愈术", values.get(1).name());
        assertEquals("ready", result.residual());
    }

    @Test
    void canonicalizesTheTwoKnownZhuhuaSpellNames() {
        assertEquals("蜘化术", ArcaneColors.canonicalName("蛛化术"));
        assertEquals(ArcaneColors.forName("蜘化术"), ArcaneColors.forName("蛛化术"));
        assertEquals(ArcaneColors.iconFile("蜘化术"), ArcaneColors.iconFile("蛛化术"));
        assertEquals(20, ArcaneColors.spellNames().size());
        assertNotEquals("21_red_barrier.png", ArcaneColors.iconFile("火球术"));
    }

    @Test
    void normalizesCoordinatesAndScaleWithoutForbiddenFields() {
        ArcaneHudConfig config = new ArcaneHudConfig();
        config.cooldownX = 4.0;
        config.cooldownY = Double.NaN;
        config.cooldownScalePercent = 1;
        config.arcaneStatusX = -4.0;
        config.arcaneStatusScalePercent = 999;
        config.normalize();
        assertEquals(-1.0, config.cooldownX);
        assertEquals(-1.0, config.cooldownY);
        assertEquals(50, config.cooldownScalePercent);
        assertEquals(-1.0, config.arcaneStatusX);
        assertEquals(200, config.arcaneStatusScalePercent);
        assertTrue(Arrays.stream(ArcaneHudConfig.class.getDeclaredFields()).noneMatch(field -> field.getName().contains("mana")
                || field.getName().contains("market")
                || field.getName().contains("value")
                || field.getName().contains("autoMessage")));
    }

    @Test
    void tracksSuppressedBossBarIdsUntilReleaseOrClear() {
        SuppressedBossBarIds ids = new SuppressedBossBarIds();
        UUID id = UUID.randomUUID();
        ids.suppress(id);
        assertTrue(ids.contains(id));
        assertTrue(ids.release(id));
        assertFalse(ids.contains(id));
        ids.suppress(id);
        ids.clear();
        assertFalse(ids.contains(id));
    }
}
