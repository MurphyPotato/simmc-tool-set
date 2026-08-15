package com.murphypotato.simmctoolset.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** Tool Set-only key page; it does not navigate to Minecraft's global controls page. */
public final class ToolSetHotkeyScreen extends Screen {
    private final Screen parent;
    private List<ToolSetKeyRouter.BindingEntry> bindings = List.of();
    private int listening = -1;
    private String status = "";

    public ToolSetHotkeyScreen(Screen parent) {
        super(Text.literal("工具组按键"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        bindings = ToolSetKeyRouter.bindings();
        int left = Math.max(18, width / 2 - 210);
        int top = 42;
        for (int i = 0; i < bindings.size(); i++) {
            int rowY = top + i * 29;
            int index = i;
            addDrawableChild(ButtonWidget.builder(Text.literal(listening == i ? "按键中..." : "修改"), button -> {
                listening = index;
                status = "请按下要绑定的键；按 Esc 取消绑定";
                clearAndInit();
            }).dimensions(left + 300, rowY - 2, 70, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("恢复"), button -> {
                ToolSetKeyRouter.resetBinding(bindings.get(index));
                listening = -1;
                status = "已恢复默认键";
                clearAndInit();
            }).dimensions(left + 374, rowY - 2, 70, 20).build());
        }
        addDrawableChild(ButtonWidget.builder(Text.literal("返回"), button -> close())
                .dimensions(width / 2 - 70, height - 28, 140, 20).build());
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (listening >= 0 && listening < bindings.size()) {
            ToolSetKeyRouter.BindingEntry entry = bindings.get(listening);
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                ToolSetKeyRouter.unbind(entry);
                status = "已取消绑定";
            } else {
                boolean conflict = ToolSetKeyRouter.conflicts(entry, keyCode, scanCode);
                ToolSetKeyRouter.setBinding(entry, keyCode, scanCode);
                status = conflict ? "已保存；该键与其它工具组按键重复，实际触发时按顺序匹配" : "已保存";
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
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 10, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("仅显示 simMC 工具组按键；不会改变玩家使用鼠标点击子板块的方式"), width / 2, 24, 0xB8C5D6);
        int left = Math.max(18, width / 2 - 210);
        for (int i = 0; i < bindings.size(); i++) {
            int rowY = 42 + i * 29;
            context.fill(left - 8, rowY - 5, left + 444, rowY + 21, i % 2 == 0 ? 0xA52A3544 : 0xA5232D3A);
            ToolSetKeyRouter.BindingEntry entry = bindings.get(i);
            context.drawTextWithShadow(textRenderer, Text.literal(entry.label()), left, rowY, 0xFFFFFF);
            context.drawTextWithShadow(textRenderer, Text.literal(entry.description()), left, rowY + 11, 0x9EADBF);
            String key = entry.binding().getBoundKeyLocalizedText().getString();
            context.drawTextWithShadow(textRenderer, Text.literal(key), left + 210, rowY + 5, 0x8FE8FF);
        }
        if (!status.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal(textRenderer.trimToWidth(status, width - 28)), width / 2, height - 45, 0xFFD36B);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }
}
