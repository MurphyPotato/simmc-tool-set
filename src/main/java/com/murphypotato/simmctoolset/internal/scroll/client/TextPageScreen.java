package com.murphypotato.simmctoolset.internal.scroll.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public abstract class TextPageScreen extends Screen {
    private final Screen previous;
    private final List<String> paragraphs;
    private List<OrderedText> lines = List.of();
    private int scroll;
    private ButtonWidget backButton;

    protected TextPageScreen(Text title, Screen previous, List<String> paragraphs) {
        super(title);
        this.previous = previous;
        this.paragraphs = List.copyOf(paragraphs);
    }

    @Override
    protected void init() {
        clearChildren();
        backButton = ButtonWidget.builder(Text.literal("返回"), button -> close())
            .dimensions(width / 2 - 50, height - 26, 100, 20).build();
        addDrawableChild(backButton);
        lines = buildLines();
    }

    protected Screen previous() {
        return previous;
    }

    protected int bottomButtonY() {
        return height - 26;
    }

    protected ButtonWidget backButton() {
        return backButton;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, UiColors.BACKGROUND);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 10, UiColors.PRIMARY);
        int visible = Math.max(1, (height - 58) / 12);
        scroll = clamp(scroll, 0, Math.max(0, lines.size() - visible));
        int end = Math.min(lines.size(), scroll + visible);
        int y = 30;
        for (int index = scroll; index < end; index++) {
            context.drawTextWithShadow(textRenderer, lines.get(index), 16, y, UiColors.SECONDARY);
            y += 12;
        }
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount == 0) return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        scroll = clamp(scroll + (verticalAmount > 0 ? -3 : 3), 0, Math.max(0, lines.size() - 1));
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int keyCode = input.key();
        if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN || keyCode == GLFW.GLFW_KEY_PAGE_UP || keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
            int amount = (keyCode == GLFW.GLFW_KEY_PAGE_UP || keyCode == GLFW.GLFW_KEY_PAGE_DOWN) ? Math.max(1, (height - 58) / 12) : 1;
            int direction = (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_PAGE_UP) ? -1 : 1;
            scroll = clamp(scroll + direction * amount, 0, Math.max(0, lines.size() - 1));
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(previous);
    }

    private List<OrderedText> buildLines() {
        List<OrderedText> result = new ArrayList<>();
        int maxWidth = Math.max(100, width - 32);
        for (String paragraph : paragraphs) {
            result.addAll(textRenderer.wrapLines(Text.literal(paragraph), maxWidth));
            result.add(Text.literal(" ").asOrderedText());
        }
        return List.copyOf(result);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }
}
