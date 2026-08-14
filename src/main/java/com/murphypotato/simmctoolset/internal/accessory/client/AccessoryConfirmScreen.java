package com.murphypotato.simmctoolset.internal.accessory.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class AccessoryConfirmScreen extends Screen {
    private final List<String> details;
    private final Consumer<Boolean> callback;
    private boolean completed;

    public AccessoryConfirmScreen(String title, List<String> details, Consumer<Boolean> callback) {
        super(Text.literal(title));
        this.details = List.copyOf(details);
        this.callback = callback;
    }

    @Override
    protected void init() {
        clearChildren();
        int buttonY = height - 36;
        addDrawableChild(ButtonWidget.builder(Text.literal("确认"), button -> finish(true))
            .dimensions(width / 2 - 84, buttonY, 80, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("取消"), button -> finish(false))
            .dimensions(width / 2 + 4, buttonY, 80, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, UiColors.BACKGROUND);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 24, UiColors.PRIMARY);

        int maxWidth = Math.max(120, width - 40);
        List<OrderedText> lines = new ArrayList<>();
        for (String detail : details) {
            lines.addAll(textRenderer.wrapLines(Text.literal(detail), maxWidth));
        }
        int y = Math.max(52, (height - lines.size() * 13) / 2 - 8);
        for (OrderedText line : lines) {
            context.drawCenteredTextWithShadow(textRenderer, line, width / 2, y, UiColors.SECONDARY);
            y += 13;
        }
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        finish(false);
    }

    private void finish(boolean confirmed) {
        if (completed) return;
        completed = true;
        callback.accept(confirmed);
    }
}
