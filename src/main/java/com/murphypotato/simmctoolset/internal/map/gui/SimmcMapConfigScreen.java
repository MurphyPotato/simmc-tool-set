package com.murphypotato.simmctoolset.internal.map.gui;

import com.murphypotato.simmctoolset.internal.map.SimmcMapClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Compact Chinese settings surface available both from Mod Menu and /simmcmap settings. */
public final class SimmcMapConfigScreen extends Screen {
    private final Screen parent;

    public SimmcMapConfigScreen(Screen parent) {
        super(Text.translatable("simmcmap.title"));
        this.parent = parent;
    }

    @Override protected void init() {
        int x = width / 2 - 110;
        int y = 58;
        addDrawableChild(toggleButton(x, y, "simmcmap.settings.world_map",
                SimmcMapClient.worldMapEnabled(), SimmcMapClient::toggleWorldMap));
        addDrawableChild(toggleButton(x, y + 26, "simmcmap.settings.world_background",
                SimmcMapClient.worldBackgroundEnabled(), SimmcMapClient::toggleWorldBackground));
        addDrawableChild(toggleButton(x, y + 52, "simmcmap.settings.minimap_background",
                SimmcMapClient.minimapBackgroundEnabled(), SimmcMapClient::toggleMinimapBackground));
        addDrawableChild(ButtonWidget.builder(Text.translatable("simmcmap.settings.refresh"), button -> {
            SimmcMapClient.requestRefresh();
            button.setMessage(Text.translatable("simmcmap.status.refreshed"));
        }).dimensions(x, y + 88, 220, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), button -> close())
                .dimensions(x, height - 34, 220, 20).build());
    }

    private ButtonWidget toggleButton(int x, int y, String key, boolean enabled, Runnable action) {
        return ButtonWidget.builder(label(key, enabled), button -> {
            action.run();
            clearAndInit();
        }).dimensions(x, y, 220, 20).build();
    }

    private static Text label(String key, boolean enabled) {
        return Text.translatable(key).append(": ").append(Text.translatable(enabled ? "options.on" : "options.off"));
    }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 20, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, Text.translatable("simmcmap.settings.groups"),
                width / 2, 38, 0xA0A0A0);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override public void close() {
        SimmcMapClient.saveUserSettings();
        if (client != null) client.setScreen(parent);
    }
}
