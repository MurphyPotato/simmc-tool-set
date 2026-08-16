package com.murphypotato.simmctoolset.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

final class ToolSetModMenuIntegrationTest {
    @Test
    void opensToolSetOverviewAndPreservesParent() throws Exception {
        Screen parent = new Screen(Text.literal("parent")) { };
        ConfigScreenFactory<?> factory = new ToolSetModMenuIntegration().getModConfigScreenFactory();

        ToolSetScreen screen = assertInstanceOf(ToolSetScreen.class, factory.create(parent));
        Field parentField = ToolSetScreen.class.getDeclaredField("parent");
        parentField.setAccessible(true);
        assertSame(parent, parentField.get(screen));
        Field panelField = ToolSetScreen.class.getDeclaredField("panel");
        panelField.setAccessible(true);
        assertSame(ToolSetScreen.Panel.OVERVIEW, panelField.get(screen));
    }
}
