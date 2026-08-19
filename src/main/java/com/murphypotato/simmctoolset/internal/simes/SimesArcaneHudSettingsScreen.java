package com.murphypotato.simmctoolset.internal.simes;

import com.murphypotato.simmctoolset.client.ToolSetSettings;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Native layout screen for the three authorized Arcane HUD surfaces. */
public final class SimesArcaneHudSettingsScreen extends Screen {
    private enum Target {
        COOLDOWN,
        STATUS,
        GLOBAL_COOLDOWN
    }

    private final Screen parent;
    private Target selected = Target.COOLDOWN;
    private boolean dragging;
    private double dragOffsetX;
    private double dragOffsetY;
    private String status = "";

    public SimesArcaneHudSettingsScreen(Screen parent) {
        super(Text.literal("Simes 设置"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int left = width / 2 - 150;
        addDrawableChild(ButtonWidget.builder(Text.literal("HUD：" + (config().arcaneEnabled ? "开" : "关")), button -> {
            config().arcaneEnabled = !config().arcaneEnabled;
            config().save();
            clearAndInit();
        }).dimensions(left, 52, 145, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Simes 模式：" + (config().simesMode ? "开" : "关")), button -> {
            config().simesMode = !config().simesMode;
            config().save();
            clearAndInit();
        }).dimensions(left + 155, 52, 145, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("发酵桶助手：" + (ToolSetSettings.fermentationEnabled() ? "开" : "关")), button -> {
            ToolSetSettings.setFermentationEnabled(!ToolSetSettings.fermentationEnabled());
            clearAndInit();
        }).dimensions(left, 80, 145, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("蒸煮煎锅助手：" + (ToolSetSettings.cookwareEnabled() ? "开" : "关")), button -> {
            ToolSetSettings.setCookwareEnabled(!ToolSetSettings.cookwareEnabled());
            clearAndInit();
        }).dimensions(left + 155, 80, 145, 20).build());

        addDrawableChild(targetButton("冷却", Target.COOLDOWN, left, 108));
        addDrawableChild(targetButton("吟唱/持续", Target.STATUS, left + 102, 108));
        addDrawableChild(targetButton("公共冷却", Target.GLOBAL_COOLDOWN, left + 204, 108));

        addDrawableChild(ButtonWidget.builder(Text.literal("缩放 -"), button -> changeScale(-10))
                .dimensions(left, 134, 58, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(scaleText()), button -> setScale(100))
                .dimensions(left + 63, 134, 112, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("缩放 +"), button -> changeScale(10))
                .dimensions(left + 180, 134, 58, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("重置当前"), button -> resetSelected())
                .dimensions(left + 243, 134, 57, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("重置全部"), button -> resetAll())
                .dimensions(left, height - 28, 96, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("返回"), button -> close())
                .dimensions(left + 204, height - 28, 96, 20).build());
    }

    private ButtonWidget targetButton(String label, Target target, int x, int y) {
        return ButtonWidget.builder(Text.literal((selected == target ? "[" : "") + label
                + (selected == target ? "]" : "")), button -> selectTarget(target))
                .dimensions(x, y, 96, 20).build();
    }

    private void selectTarget(Target target) {
        selected = target;
        dragging = false;
        clearAndInit();
    }

    private ArcaneHudConfig config() {
        return SimesArcaneHud.config();
    }

    private void changeScale(int amount) {
        setScale(selectedScale() + amount);
    }

    private void setScale(int value) {
        ArcaneHudConfig config = config();
        int normalized = ArcaneHudConfig.scale(value);
        switch (selected) {
            case COOLDOWN -> config.cooldownScalePercent = normalized;
            case STATUS -> config.arcaneStatusScalePercent = normalized;
            case GLOBAL_COOLDOWN -> config.globalCooldownScalePercent = normalized;
        }
        config.save();
        clearAndInit();
    }

    private int selectedScale() {
        ArcaneHudConfig config = config();
        return switch (selected) {
            case COOLDOWN -> config.cooldownScalePercent;
            case STATUS -> config.arcaneStatusScalePercent;
            case GLOBAL_COOLDOWN -> config.globalCooldownScalePercent;
        };
    }

    private String scaleText() {
        return "缩放 " + selectedScale() + "%";
    }

    private void resetSelected() {
        ArcaneHudConfig config = config();
        switch (selected) {
            case COOLDOWN -> {
                config.resetCooldownPosition();
                config.cooldownScalePercent = 100;
            }
            case STATUS -> {
                config.resetArcaneStatusPosition();
                config.arcaneStatusScalePercent = 100;
            }
            case GLOBAL_COOLDOWN -> {
                config.resetGlobalCooldownPosition();
                config.globalCooldownScalePercent = 100;
            }
        }
        config.save();
        status = "已重置当前布局";
        clearAndInit();
    }

    private void resetAll() {
        ArcaneHudConfig config = config();
        config.resetCooldownPosition();
        config.resetArcaneStatusPosition();
        config.resetGlobalCooldownPosition();
        config.cooldownScalePercent = 100;
        config.arcaneStatusScalePercent = 100;
        config.globalCooldownScalePercent = 100;
        config.save();
        status = "已重置全部布局";
        clearAndInit();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Widgets get first refusal so a preview cannot steal reset or return clicks.
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0) return false;
        PreviewBounds bounds = previewBounds();
        if (!bounds.contains(mouseX, mouseY)) return false;
        dragging = true;
        dragOffsetX = mouseX - bounds.x;
        dragOffsetY = mouseY - bounds.y;
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (dragging && button == 0) {
            setPosition(mouseX - dragOffsetX, mouseY - dragOffsetY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && dragging) {
            dragging = false;
            config().save();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void setPosition(double x, double y) {
        ArcaneHudConfig config = config();
        PreviewBounds bounds = previewBounds();
        double maxX = Math.max(4.0, width - bounds.width - 4.0);
        double minY = previewMinimumY(bounds.height);
        double maxY = Math.max(minY, height - 54.0);
        double clampedX = Math.max(4.0, Math.min(maxX, x));
        double clampedY = Math.max(minY, Math.min(maxY, y));
        switch (selected) {
            case COOLDOWN -> {
                config.cooldownX = clampedX / Math.max(1, width);
                config.cooldownY = clampedY / Math.max(1, height);
            }
            case STATUS -> {
                config.arcaneStatusX = clampedX / Math.max(1, width);
                config.arcaneStatusY = clampedY / Math.max(1, height);
            }
            case GLOBAL_COOLDOWN -> {
                config.globalCooldownX = clampedX / Math.max(1, width);
                config.globalCooldownY = clampedY / Math.max(1, height);
            }
        }
    }

    private PreviewBounds previewBounds() {
        ArcaneHudConfig config = config();
        float scale = selectedScale() / 100.0f;
        int panelWidth = switch (selected) {
            case COOLDOWN -> Math.round(SimesArcaneHud.totalWidth() * scale);
            case STATUS -> Math.round(SimesArcaneStatusHud.totalWidth() * scale);
            case GLOBAL_COOLDOWN -> Math.round(SimesArcaneStatusHud.globalTotalWidth() * scale);
        };
        int panelHeight = switch (selected) {
            case COOLDOWN -> Math.round(3 * 19 * scale);
            case STATUS -> Math.round(SimesArcaneStatusHud.previewHeight() * scale);
            case GLOBAL_COOLDOWN -> Math.round(SimesArcaneStatusHud.globalPreviewHeight() * scale);
        };
        int configuredX = switch (selected) {
            case COOLDOWN -> SimesArcaneHud.configuredX(width);
            case STATUS -> SimesArcaneStatusHud.configuredX(width);
            case GLOBAL_COOLDOWN -> SimesArcaneStatusHud.configuredGlobalX(width);
        };
        int configuredY = switch (selected) {
            case COOLDOWN -> SimesArcaneHud.configuredY(height);
            case STATUS -> SimesArcaneStatusHud.configuredY(height);
            case GLOBAL_COOLDOWN -> SimesArcaneStatusHud.configuredGlobalY(height);
        };
        int x = Math.max(4, Math.min(Math.max(4, width - panelWidth - 4), configuredX));
        double minimumY = previewMinimumY(panelHeight);
        int y = (int) Math.round(Math.max(minimumY, Math.min(Math.max(minimumY, height - 54.0), configuredY)));
        return new PreviewBounds(x, y, panelWidth, panelHeight);
    }

    private double previewMinimumY(int panelHeight) {
        return 172.0 + panelHeight + 4.0;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xE0121720);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 14, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("选择预览后拖动，按钮调整独立布局"),
                width / 2, 30, 0xFFB8C5D6);
        PreviewBounds bounds = previewBounds();
        int frameColor = dragging ? 0xFFFFFF55 : 0xFF777777;
        context.fill(bounds.x - 4, bounds.top() - 4, bounds.x + bounds.width + 4,
                bounds.y + 4, 0xA52A3544);
        context.fill(bounds.x - 4, bounds.top() - 4, bounds.x + bounds.width + 4,
                bounds.top(), frameColor);
        context.fill(bounds.x - 4, bounds.y + 4, bounds.x + bounds.width + 4,
                bounds.y + 5, frameColor);
        switch (selected) {
            case COOLDOWN -> SimesArcaneHud.renderPreview(context, bounds.x, bounds.y, config().cooldownScalePercent / 100.0f);
            case STATUS -> SimesArcaneStatusHud.renderPreview(context, bounds.x, bounds.y,
                    config().arcaneStatusScalePercent / 100.0f);
            case GLOBAL_COOLDOWN -> SimesArcaneStatusHud.renderGlobalPreview(context, bounds.x, bounds.y,
                    config().globalCooldownScalePercent / 100.0f);
        }
        context.drawTextWithShadow(textRenderer, Text.literal(targetLabel()), bounds.x, bounds.top() - 14, 0xFFFFFFFF);
        if (!status.isEmpty()) context.drawCenteredTextWithShadow(textRenderer, Text.literal(status),
                width / 2, height - 46, 0xFFFFD36B);
        super.render(context, mouseX, mouseY, delta);
    }

    private String targetLabel() {
        return switch (selected) {
            case COOLDOWN -> "冷却";
            case STATUS -> "吟唱/持续";
            case GLOBAL_COOLDOWN -> "公共冷却";
        };
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }

    private record PreviewBounds(int x, int y, int width, int height) {
        private int top() {
            return y - height;
        }

        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= x - 4 && mouseX <= x + width + 4
                    && mouseY >= top() - 4 && mouseY <= y + 4;
        }
    }
}
