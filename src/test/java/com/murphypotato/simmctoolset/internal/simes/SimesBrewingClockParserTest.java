package com.murphypotato.simmctoolset.internal.simes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class SimesBrewingClockParserTest {
    @Test
    void parsesMaterialHeaderAndIngredientRows() {
        assertInstanceOf(SimesBrewingClockParser.MaterialsHeader.class,
                SimesBrewingClockParser.parse("当前桶内的材料："));

        SimesBrewingClockParser.Ingredient ingredient = assertInstanceOf(
                SimesBrewingClockParser.Ingredient.class,
                SimesBrewingClockParser.parse("- [香料] x 3"));
        assertEquals("香料", ingredient.name());
        assertEquals(3, ingredient.count());
    }

    @Test
    void parsesInvalidationAndAuthoritativeTimeMessages() {
        SimesBrewingClockParser.Invalidate interrupted = assertInstanceOf(
                SimesBrewingClockParser.Invalidate.class,
                SimesBrewingClockParser.parse("腌制已中断"));
        assertEquals("已中断", interrupted.status());

        SimesBrewingClockParser.Remaining remaining = assertInstanceOf(
                SimesBrewingClockParser.Remaining.class,
                SimesBrewingClockParser.parse("剩余时间：1分 2.5秒。正在腌制：[酸菜]"));
        assertEquals("1分 2.5秒", remaining.remaining());
        assertEquals("酸菜", remaining.product());

        assertInstanceOf(SimesBrewingClockParser.Completed.class,
                SimesBrewingClockParser.parse("腌制已完成！"));
    }
}
