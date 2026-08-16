package com.murphypotato.simmctoolset.internal.simes;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Native settings surface for the restored Simes Arcane HUD. */
public final class SimesArcaneHudSettingsScreen extends Screen {
    private final Screen parent;
    private String status = "";
    private boolean dragging;

    public SimesArcaneHudSettingsScreen(Screen parent) {
        super(Text.literal("奥术 HUD 设置"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ArcaneHudConfig config = SimesArcaneHud.config();
        int left = width / 2 - 150;
        int y = 52;
        addDrawableChild(ButtonWidget.builder(Text.literal("HUD：" + (config.arcaneEnabled ? "开" : "关")), button -> {
            config.arcaneEnabled = !config.arcaneEnabled; config.save(); clearAndInit();
        }).dimensions(left, y, 145, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Simes 模式：" + (config.simesMode ? "开" : "关")), button -> {
            config.simesMode = !config.simesMode; config.save(); clearAndInit();
        }).dimensions(left + 155, y, 145, 20).build());
        y += 26;
        addDrawableChild(ButtonWidget.builder(Text.literal("缩放 -"), button -> {
            config.cooldownScalePercent = ArcaneHudConfig.scale(config.cooldownScalePercent - 10); config.save(); clearAndInit();
        }).dimensions(left, y, 95, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("缩放 " + config.cooldownScalePercent + "%"), button -> {
            config.cooldownScalePercent = 100; config.save(); status = "已恢复 100% 缩放"; clearAndInit();
        }).dimensions(left + 100, y, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("缩放 +"), button -> {
            config.cooldownScalePercent = ArcaneHudConfig.scale(config.cooldownScalePercent + 10); config.save(); clearAndInit();
        }).dimensions(left + 205, y, 95, 20).build());
        y += 26;
        addDrawableChild(ButtonWidget.builder(Text.literal("重置 HUD 位置"), button -> {
            config.resetCooldownPosition(); config.save(); status = "已重置位置"; clearAndInit();
        }).dimensions(left, y, 145, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("返回"), button -> close())
                .dimensions(left + 155, y, 145, 20).build());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseY > 100 && mouseY < height - 48) { dragging = true; setPosition(mouseX, mouseY); return true; }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (dragging && button == 0) { setPosition(mouseX, mouseY); return true; }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void setPosition(double mouseX, double mouseY) {
        ArcaneHudConfig config = SimesArcaneHud.config();
        config.cooldownX = Math.max(0.0, Math.min(1.0, mouseX / Math.max(1, width)));
        config.cooldownY = Math.max(0.0, Math.min(1.0, mouseY / Math.max(1, height)));
        config.save();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xE0121720);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 14, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("拖动预览区域可调整 HUD 位置，按钮可调整缩放和模式"), width / 2, 30, 0xB8C5D6);
        ArcaneHudConfig config = SimesArcaneHud.config();
        context.fill(width / 2 - 150, 116, width / 2 + 150, 200, 0xA52A3544);
        SimesArcaneHud.renderPreview(context, width / 2 - 130, 184, config.cooldownScalePercent / 100.0f);
        if (!status.isEmpty()) context.drawCenteredTextWithShadow(textRenderer, Text.literal(status), width / 2, height - 30, 0xFFD36B);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() { if (client != null) client.setScreen(parent); }
}
