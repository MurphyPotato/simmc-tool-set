package com.murphypotato.simmctoolset.internal.simes;

import com.murphypotato.simmctoolset.client.ToolSetSettings;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Authorized Simes feature switches; layout editing lives on a dedicated child screen. */
public final class SimesArcaneHudSettingsScreen extends Screen {
    private final Screen parent;

    public SimesArcaneHudSettingsScreen(Screen parent) {
        super(Text.literal("Simes 设置"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int center = width / 2;
        int left = center - 204;
        int right = center + 4;
        addDrawableChild(toggle(left, 48, "奥术冷却监听", config().arcaneEnabled, () -> {
            config().arcaneEnabled = !config().arcaneEnabled;
            saveAndRefresh();
        }));
        addDrawableChild(ButtonWidget.builder(modeText(), button -> {
            config().simesMode = !config().simesMode;
            SimesArcaneStatusHud.reset();
            saveAndRefresh();
        }).dimensions(right, 48, 200, 20).build());
        addDrawableChild(toggle(left, 76, "吟唱与持续状态", config().arcaneStatusEnabled, () -> {
            config().arcaneStatusEnabled = !config().arcaneStatusEnabled;
            SimesArcaneStatusHud.reset();
            saveAndRefresh();
        }));
        addDrawableChild(toggle(right, 76, "法杖魔力 HUD", config().manaHudEnabled, () -> {
            config().manaHudEnabled = !config().manaHudEnabled;
            saveAndRefresh();
        }));
        addDrawableChild(toggle(left, 104, "发酵桶助手", ToolSetSettings.fermentationEnabled(), () -> {
            ToolSetSettings.setFermentationEnabled(!ToolSetSettings.fermentationEnabled());
            clearAndInit();
        }));
        addDrawableChild(toggle(right, 104, "蒸煮煎锅助手", ToolSetSettings.cookwareEnabled(), () -> {
            ToolSetSettings.setCookwareEnabled(!ToolSetSettings.cookwareEnabled());
            clearAndInit();
        }));
        addDrawableChild(ButtonWidget.builder(Text.literal("统一 HUD 布局与缩放"), button -> {
            if (client != null) client.setScreen(new SimesHudLayoutScreen(this));
        }).dimensions(left, 136, 408, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("返回"), button -> close())
                .dimensions(center - 100, Math.min(height - 28, 172), 200, 20).build());
    }

    private ButtonWidget toggle(int x, int y, String label, boolean enabled, Runnable action) {
        return ButtonWidget.builder(toggleText(label, enabled), button -> action.run())
                .dimensions(x, y, 200, 20).build();
    }

    private static Text toggleText(String label, boolean enabled) {
        return Text.literal(label + "：" + (enabled ? "开启" : "关闭"));
    }

    private Text modeText() {
        return Text.literal("奥术显示：" + (config().simesMode ? "Simes HUD" : "原版 Action Bar"));
    }

    private ArcaneHudConfig config() {
        return SimesArcaneHud.config();
    }

    private void saveAndRefresh() {
        config().save();
        clearAndInit();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xE0121720);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 14, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("按模块开关功能；布局位置与缩放在独立页面调整"),
                width / 2, 28, 0xFFB8C5D6);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }
}
