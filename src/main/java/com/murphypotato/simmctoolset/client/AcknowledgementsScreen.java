package com.murphypotato.simmctoolset.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;

/** Player-facing acknowledgements page for the combined Tool Set. */
public final class AcknowledgementsScreen extends Screen {
    private static final String ISSUES_URL = "https://github.com/MurphyPotato/simmc-tool-set/issues";
    private final Screen parent;
    private final List<String> paragraphs = List.of(
        "感谢玩家 7imes 对于“奥术 HUD”和“发酵与厨具”模块的移植和二次开发授权。",
        "感谢玩家 PeterPG_ 和玩家 JeanBH 对于“卷轴计算”模块所提供的核心计算过程和基础数据。",
        "感谢以下玩家在本模组测试过程中提出的宝贵意见和建议：Fei_Ge56、Gulanan、INTIMES、kwpog、MingXue_、SnMeow、WolfSoul2024、Yanweny。（以上排名不分先后。）"
    );
    private List<OrderedText> lines = List.of();
    private int scroll;

    public AcknowledgementsScreen(Screen parent) {
        super(Text.literal("致谢"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        clearChildren();
        lines = buildLines();
        addDrawableChild(ButtonWidget.builder(Text.literal("返回"), ignored -> close())
            .dimensions(width / 2 - 104, height - 26, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("问题反馈"), ignored ->
                Util.getOperatingSystem().open(ISSUES_URL))
            .dimensions(width / 2 + 4, height - 26, 100, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xE0121720);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFFFFFF);
        int visible = Math.max(1, (height - 58) / 12);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, lines.size() - visible)));
        int y = 36;
        for (int i = scroll; i < Math.min(lines.size(), scroll + visible); i++, y += 12) {
            context.drawTextWithShadow(textRenderer, lines.get(i), 20, y, 0xFFD7DEE8);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount == 0) return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        scroll = Math.max(0, Math.min(scroll + (verticalAmount > 0 ? -3 : 3),
            Math.max(0, lines.size() - 1)));
        return true;
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }

    private List<OrderedText> buildLines() {
        List<OrderedText> result = new ArrayList<>();
        for (String paragraph : paragraphs) {
            result.addAll(textRenderer.wrapLines(Text.literal(paragraph), Math.max(120, width - 40)));
            result.add(Text.literal(" ").asOrderedText());
        }
        return List.copyOf(result);
    }
}
