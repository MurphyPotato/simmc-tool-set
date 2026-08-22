package com.murphypotato.simmctoolset.internal.simes;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Simes-style simultaneous editor for the four authorized HUD surfaces. */
public final class SimesHudLayoutScreen extends Screen {
    private enum Target {
        COOLDOWN,
        STATUS,
        GLOBAL_COOLDOWN,
        MANA
    }

    private final Screen parent;
    private Target selected = Target.COOLDOWN;
    private boolean dragging;
    private double dragOffsetX;
    private double dragOffsetY;
    private String status = "";

    public SimesHudLayoutScreen(Screen parent) {
        super(Text.literal("Simes HUD 布局与缩放"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int left = width / 2 - 150;
        addDrawableChild(targetButton("冷却", Target.COOLDOWN, left, 38));
        addDrawableChild(targetButton("吟唱/持续", Target.STATUS, left + 76, 38));
        addDrawableChild(targetButton("公共冷却", Target.GLOBAL_COOLDOWN, left + 152, 38));
        addDrawableChild(targetButton("Mana", Target.MANA, left + 228, 38));
        addDrawableChild(ButtonWidget.builder(Text.literal("缩放 -"), button -> changeScale(-10))
                .dimensions(left, 64, 58, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(scaleText()), button -> setScale(100))
                .dimensions(left + 63, 64, 112, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("缩放 +"), button -> changeScale(10))
                .dimensions(left + 180, 64, 58, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("重置当前"), button -> resetSelected())
                .dimensions(left + 243, 64, 57, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("重置全部"), button -> resetAll())
                .dimensions(left, height - 28, 96, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("返回"), button -> close())
                .dimensions(left + 204, height - 28, 96, 20).build());
    }

    private ButtonWidget targetButton(String label, Target target, int x, int y) {
        return ButtonWidget.builder(Text.literal((selected == target ? "[" : "") + label
                + (selected == target ? "]" : "")), button -> selectTarget(target))
                .dimensions(x, y, 72, 20).build();
    }

    private void selectTarget(Target target) {
        selected = target;
        dragging = false;
        clearAndInit();
    }

    private ArcaneHudConfig config() {
        return SimesArcaneHud.config();
    }

    private void changeScale(int delta) {
        setScale(scale(selected) + delta);
    }

    private void setScale(int value) {
        int normalized = ArcaneHudConfig.scale(value);
        switch (selected) {
            case COOLDOWN -> config().cooldownScalePercent = normalized;
            case STATUS -> config().arcaneStatusScalePercent = normalized;
            case GLOBAL_COOLDOWN -> config().globalCooldownScalePercent = normalized;
            case MANA -> config().manaHudScalePercent = normalized;
        }
        config().save();
        clearAndInit();
    }

    private int scale(Target target) {
        return switch (target) {
            case COOLDOWN -> config().cooldownScalePercent;
            case STATUS -> config().arcaneStatusScalePercent;
            case GLOBAL_COOLDOWN -> config().globalCooldownScalePercent;
            case MANA -> config().manaHudScalePercent;
        };
    }

    private String scaleText() {
        return "缩放 " + scale(selected) + "%";
    }

    private void resetSelected() {
        switch (selected) {
            case COOLDOWN -> {
                config().resetCooldownPosition();
                config().cooldownScalePercent = 100;
            }
            case STATUS -> {
                config().resetArcaneStatusPosition();
                config().arcaneStatusScalePercent = 100;
            }
            case GLOBAL_COOLDOWN -> {
                config().resetGlobalCooldownPosition();
                config().globalCooldownScalePercent = 100;
            }
            case MANA -> {
                config().resetManaHudPosition();
                config().manaHudScalePercent = 100;
            }
        }
        config().save();
        status = "已重置当前布局";
        clearAndInit();
    }

    private void resetAll() {
        config().resetCooldownPosition();
        config().resetArcaneStatusPosition();
        config().resetGlobalCooldownPosition();
        config().resetManaHudPosition();
        config().cooldownScalePercent = 100;
        config().arcaneStatusScalePercent = 100;
        config().globalCooldownScalePercent = 100;
        config().manaHudScalePercent = 100;
        config().save();
        status = "已重置全部布局";
        clearAndInit();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0) return false;
        for (Target target : Target.values()) {
            PreviewBounds bounds = previewBounds(target);
            if (bounds.contains(mouseX, mouseY)) {
                selected = target;
                dragging = true;
                dragOffsetX = mouseX - bounds.x;
                dragOffsetY = mouseY - bounds.y;
                clearAndInit();
                return true;
            }
        }
        return false;
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
        PreviewBounds bounds = previewBounds(selected);
        double maxX = Math.max(4.0, width - bounds.width - 4.0);
        double minY = previewMinimumY(bounds.height);
        double maxY = Math.max(minY, height - 34.0);
        double clampedX = Math.max(4.0, Math.min(maxX, x));
        double clampedY = Math.max(minY, Math.min(maxY, y));
        switch (selected) {
            case COOLDOWN -> {
                config().cooldownX = clampedX / Math.max(1, width);
                config().cooldownY = clampedY / Math.max(1, height);
            }
            case STATUS -> {
                config().arcaneStatusX = clampedX / Math.max(1, width);
                config().arcaneStatusY = clampedY / Math.max(1, height);
            }
            case GLOBAL_COOLDOWN -> {
                config().globalCooldownX = clampedX / Math.max(1, width);
                config().globalCooldownY = clampedY / Math.max(1, height);
            }
            case MANA -> {
                config().manaHudX = clampedX / Math.max(1, width);
                config().manaHudY = (clampedY - bounds.height) / Math.max(1, height);
            }
        }
    }

    private PreviewBounds previewBounds(Target target) {
        float scale = scale(target) / 100.0f;
        int panelWidth = switch (target) {
            case COOLDOWN -> Math.round(SimesArcaneHud.totalWidth() * scale);
            case STATUS -> Math.round(SimesArcaneStatusHud.totalWidth() * scale);
            case GLOBAL_COOLDOWN -> Math.round(SimesArcaneStatusHud.globalTotalWidth() * scale);
            case MANA -> Math.round(ManaHud.totalWidth() * scale);
        };
        int panelHeight = switch (target) {
            case COOLDOWN -> Math.round(3 * 19 * scale);
            case STATUS -> Math.round(SimesArcaneStatusHud.previewHeight() * scale);
            case GLOBAL_COOLDOWN -> Math.round(SimesArcaneStatusHud.globalPreviewHeight() * scale);
            case MANA -> Math.round(ManaHud.totalHeight() * scale);
        };
        int configuredX = switch (target) {
            case COOLDOWN -> SimesArcaneHud.configuredX(width);
            case STATUS -> SimesArcaneStatusHud.configuredX(width);
            case GLOBAL_COOLDOWN -> SimesArcaneStatusHud.configuredGlobalX(width);
            case MANA -> ManaHud.configuredX(width);
        };
        int configuredY = switch (target) {
            case COOLDOWN -> SimesArcaneHud.configuredY(height);
            case STATUS -> SimesArcaneStatusHud.configuredY(height);
            case GLOBAL_COOLDOWN -> SimesArcaneStatusHud.configuredGlobalY(height);
            case MANA -> ManaHud.configuredY(height) + panelHeight;
        };
        int x = Math.max(4, Math.min(Math.max(4, width - panelWidth - 4), configuredX));
        double minimumY = previewMinimumY(panelHeight);
        int y = (int) Math.round(Math.max(minimumY,
                Math.min(Math.max(minimumY, height - 34.0), configuredY)));
        return new PreviewBounds(x, y, panelWidth, panelHeight);
    }

    private double previewMinimumY(int panelHeight) {
        return 108.0 + panelHeight + 4.0;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xE0121720);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("点击 HUD 后拖动；缩放范围 50%-200%，每次 10%"),
                width / 2, 24, 0xFFB8C5D6);
        for (Target target : Target.values()) {
            PreviewBounds bounds = previewBounds(target);
            int frameColor = target == selected ? 0xFFFFFF55 : 0xFF777777;
            context.fill(bounds.x - 4, bounds.top() - 4, bounds.x + bounds.width + 4,
                    bounds.y + 4, target == selected ? 0xA52A3544 : 0x70212A36);
            context.fill(bounds.x - 4, bounds.top() - 4, bounds.x + bounds.width + 4,
                    bounds.top(), frameColor);
            context.fill(bounds.x - 4, bounds.y + 4, bounds.x + bounds.width + 4,
                    bounds.y + 5, frameColor);
            renderPreview(context, target, bounds);
            context.drawTextWithShadow(textRenderer, Text.literal(targetLabel(target)),
                    bounds.x, Math.max(90, bounds.top() - 14), 0xFFFFFFFF);
        }
        if (!status.isEmpty()) context.drawCenteredTextWithShadow(textRenderer, Text.literal(status),
                width / 2, height - 46, 0xFFFFD36B);
        super.render(context, mouseX, mouseY, delta);
    }

    private void renderPreview(DrawContext context, Target target, PreviewBounds bounds) {
        switch (target) {
            case COOLDOWN -> SimesArcaneHud.renderPreview(context, bounds.x, bounds.y,
                    config().cooldownScalePercent / 100.0f);
            case STATUS -> SimesArcaneStatusHud.renderPreview(context, bounds.x, bounds.y,
                    config().arcaneStatusScalePercent / 100.0f);
            case GLOBAL_COOLDOWN -> SimesArcaneStatusHud.renderGlobalPreview(context, bounds.x, bounds.y,
                    config().globalCooldownScalePercent / 100.0f);
            case MANA -> ManaHud.renderPreview(context, bounds.x, bounds.top(),
                    config().manaHudScalePercent / 100.0f);
        }
    }

    private String targetLabel(Target target) {
        return switch (target) {
            case COOLDOWN -> "奥术 CD";
            case STATUS -> "吟唱/持续状态";
            case GLOBAL_COOLDOWN -> "公共冷却";
            case MANA -> "Mana HUD";
        };
    }

    @Override
    public void close() {
        config().save();
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
