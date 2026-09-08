package com.murphypotato.simmctoolset.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** Tool Set-only page for the retained prefix subkeys. */
public final class ToolSetHotkeyScreen extends Screen {
    private final Screen parent;
    private List<ToolSetKeyRouter.ShortcutBinding> bindings = List.of();
    private int listening = -1;
    private String status = "";

    public ToolSetHotkeyScreen(Screen parent) {
        super(Text.literal("工具组按键"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        bindings = ToolSetKeyRouter.bindings();
        int left = contentLeft();
        int top = 42;
        int rowHeight = rowHeight();
        int resetX = left + contentWidth() - 58;
        for (int i = 0; i < bindings.size(); i++) {
            int index = i;
            int rowY = top + i * rowHeight;
            addDrawableChild(ButtonWidget.builder(Text.literal(listening == i ? "按键中..." : "修改"), button -> {
                listening = index;
                status = "请按下要绑定的键；按 Esc 取消绑定";
                clearAndInit();
            }).dimensions(resetX - 62, rowY - 2, 58, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("恢复"), button -> {
                ToolSetKeyRouter.resetBinding(bindings.get(index));
                listening = -1;
                status = "已恢复默认键";
                clearAndInit();
            }).dimensions(resetX, rowY - 2, 58, 20).build());
        }
        addDrawableChild(ButtonWidget.builder(Text.literal("返回"), button -> close())
                .dimensions(width / 2 - 70, height - 28, 140, 20).build());
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (listening >= 0 && listening < bindings.size()) {
            ToolSetKeyRouter.ShortcutBinding binding = bindings.get(listening);
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                ToolSetKeyRouter.unbind(binding);
                status = "已取消绑定";
            } else {
                boolean conflict = ToolSetKeyRouter.conflicts(binding, keyCode, scanCode);
                ToolSetKeyRouter.setBinding(binding, keyCode, scanCode);
                status = conflict ? "已保存；该键与其它工具组按键重复" : "已保存";
            }
            listening = -1;
            clearAndInit();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xE0121720);
        context.fill(14, 32, width - 14, height - 38, 0xE01F2937);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 10, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("组合子键；前缀、0、O 请在原版按键绑定中修改"),
                width / 2, 24, 0xFFB8C5D6);
        int left = contentLeft();
        int rowHeight = rowHeight();
        int keyX = left + contentWidth() - 194;
        for (int i = 0; i < bindings.size(); i++) {
            int rowY = 42 + i * rowHeight;
            context.fill(left - 4, rowY - 4, left + contentWidth() + 4, rowY + rowHeight - 5,
                    i % 2 == 0 ? 0xA52A3544 : 0xA5232D3A);
            ToolSetKeyRouter.ShortcutBinding binding = bindings.get(i);
            context.drawTextWithShadow(textRenderer, Text.literal(binding.label()), left, rowY, 0xFFFFFFFF);
            context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(binding.description(), keyX - left - 6), left, rowY + 12, 0xFF9EADBF);
            context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(binding.displayName(), 68), keyX, rowY + 5, 0xFF8FE8FF);
        }
        if (!status.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal(textRenderer.trimToWidth(status, width - 28)), width / 2, height - 45, 0xFFFFD36B);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }

    private int contentWidth() {
        return Math.min(500, width - 36);
    }

    private int contentLeft() {
        return (width - contentWidth()) / 2;
    }

    private int rowHeight() {
        return Math.min(34, Math.max(24, (height - 100) / Math.max(1, bindings.size())));
    }
}
