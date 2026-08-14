package com.murphypotato.simmctoolset.internal.accessory.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.List;

public final class AccessoryHelpScreen extends Screen {
    private static final List<String> STEPS = List.of(
        "打开背包或饰品容器，按主键盘数字 0；容器与玩家物品栏会一起扫描。",
        "在“需要复核”中检查异常饰品，确认词条后入库。",
        "点击“计算并替换”，选择剑套或弓套；出现波动风险时比较最高期望和稳定输出。",
        "点击“返回并开始配装指引”，根据高亮取出饰品；切换或重新打开其他来源页面会自动继续框选。"
    );

    private final ClientAccessoryController controller;
    private final ToolSession session;

    public AccessoryHelpScreen(ClientAccessoryController controller, ToolSession session) {
        super(Text.literal("旅行猎手饰品工具 · 使用说明"));
        this.controller = controller;
        this.session = session;
    }

    @Override
    protected void init() {
        clearChildren();
        int y = height - 28;
        addDrawableChild(ButtonWidget.builder(Text.literal("返回工具"), button -> close())
            .dimensions(width / 2 - 104, y, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("关于与反馈"), button -> {
            if (client != null) client.setScreen(new AccessoryAboutScreen(controller, session));
        }).dimensions(width / 2 + 4, y, 100, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, UiColors.BACKGROUND);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, UiColors.PRIMARY);
        context.drawCenteredTextWithShadow(textRenderer, "说明不会自动弹出，需要时可随时打开", width / 2, 28, UiColors.MUTED);

        int maxWidth = Math.max(120, width - 54);
        int y = 52;
        for (int index = 0; index < STEPS.size(); index++) {
            List<OrderedText> lines = textRenderer.wrapLines(Text.literal(STEPS.get(index)), maxWidth);
            int blockHeight = Math.max(30, lines.size() * 12 + 10);
            context.fill(12, y - 5, width - 12, y + blockHeight - 5, index % 2 == 0 ? 0x7A151B23 : 0x7A1B222C);
            context.drawCenteredTextWithShadow(textRenderer, Integer.toString(index + 1), 27, y + 4, UiColors.ACCENT);
            for (int line = 0; line < lines.size(); line++) {
                context.drawTextWithShadow(textRenderer, lines.get(line), 44, y + line * 12, UiColors.PRIMARY);
            }
            y += blockHeight + 7;
        }
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(new AccessoryToolScreen(controller, session));
    }
}
