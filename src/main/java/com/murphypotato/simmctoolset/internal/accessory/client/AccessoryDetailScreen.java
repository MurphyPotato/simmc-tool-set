package com.murphypotato.simmctoolset.internal.accessory.client;

import com.murphypotato.simmctoolset.internal.accessory.domain.AccessoryRecord;
import com.murphypotato.simmctoolset.internal.accessory.domain.AccessoryScorer;
import com.murphypotato.simmctoolset.internal.accessory.domain.AffixRecord;
import com.murphypotato.simmctoolset.internal.accessory.domain.WeaponMode;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public final class AccessoryDetailScreen extends Screen {
    private static final int MARGIN = 10;
    private static final int CONTENT_TOP = 92;
    private static final int LINE_HEIGHT = 12;

    private final ClientAccessoryController controller;
    private final ToolSession session;
    private final AccessoryRecord accessory;
    private final WeaponMode mode;
    private int scroll;
    private int totalLines;
    private ItemStack hoveredStack = ItemStack.EMPTY;

    public AccessoryDetailScreen(
        ClientAccessoryController controller,
        ToolSession session,
        String accessoryId,
        WeaponMode mode
    ) {
        super(Text.literal("饰品完整详情"));
        this.controller = controller;
        this.session = session;
        this.accessory = controller.findAccessory(accessoryId).orElseThrow();
        this.mode = mode;
    }

    @Override
    protected void init() {
        clearChildren();
        addDrawableChild(ButtonWidget.builder(Text.literal("返回工具"), button -> close())
            .dimensions(MARGIN, 18, 72, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("编辑"), button -> {
            if (client != null) client.setScreen(new AccessoryEditorScreen(controller, session, accessory.id(), false));
        }).dimensions(width - MARGIN - 50, 18, 50, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, UiColors.BACKGROUND);
        hoveredStack = ItemStack.EMPTY;
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 5, UiColors.PRIMARY);
        ItemStack stack = controller.displayStack(accessory);
        if (!stack.isEmpty()) {
            context.drawItem(stack, MARGIN, 48);
            if (!controller.iconPending(accessory)
                && mouseX >= MARGIN && mouseX < MARGIN + 18 && mouseY >= 48 && mouseY < 66) hoveredStack = stack;
        }
        String identity = accessory.name() + (controller.iconPending(accessory) ? " [图标待刷新]" : "")
            + " · " + accessory.quality().label() + " · "
            + accessory.slot().label() + " · +" + accessory.level();
        context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(identity, width - 48), 34, 48, UiColors.PRIMARY);
        context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(controller.sourceLabel(accessory), width - 48), 34, 61, UiColors.MUTED);
        context.drawTextWithShadow(textRenderer, mode == null ? "全部识别词条" : mode.label() + "方案中的词条状态", MARGIN, 78, UiColors.SECONDARY);

        List<DetailLine> lines = buildLines();
        totalLines = lines.size();
        scroll = clamp(scroll, 0, maxScroll());
        int end = Math.min(totalLines, scroll + visibleRows());
        int y = CONTENT_TOP;
        for (int index = scroll; index < end; index++) {
            DetailLine line = lines.get(index);
            context.drawTextWithShadow(textRenderer, line.text(), MARGIN, y, line.color());
            y += LINE_HEIGHT;
        }
        if (totalLines > visibleRows()) {
            String position = (scroll + 1) + "-" + end + " / " + totalLines + " · 滚轮或方向键浏览";
            context.drawTextWithShadow(textRenderer, position, MARGIN, height - 18, UiColors.MUTED);
        }
        super.render(context, mouseX, mouseY, delta);
        if (!hoveredStack.isEmpty()) context.drawItemTooltip(textRenderer, hoveredStack, mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount == 0) return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        scroll = clamp(scroll + (verticalAmount > 0 ? -3 : 3), 0, maxScroll());
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN) {
            scroll = clamp(scroll + (keyCode == GLFW.GLFW_KEY_UP ? -1 : 1), 0, maxScroll());
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_UP || keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
            int direction = keyCode == GLFW.GLFW_KEY_PAGE_UP ? -1 : 1;
            scroll = clamp(scroll + direction * visibleRows(), 0, maxScroll());
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(new AccessoryToolScreen(controller, session));
    }

    private List<DetailLine> buildLines() {
        List<DetailLine> lines = new ArrayList<>();
        if (accessory.affixes().isEmpty()) {
            addWrapped(lines, "该饰品没有词条", UiColors.MUTED);
            return lines;
        }
        for (AffixRecord affix : accessory.affixes()) {
            boolean effective = mode == null ? affix.stat().combat() : AccessoryScorer.isEffective(affix.stat(), mode);
            boolean opposite = mode != null && affix.stat().combat() && !effective;
            int color = effective ? UiColors.PRIMARY : opposite ? UiColors.MUTED : UiColors.DIM;
            String state = effective ? "计入伤害" : opposite ? "本模式不计" : "非伤害词条";
            addWrapped(lines, affix.displayText(), color);
            addWrapped(lines, "状态：" + state, color);
        }
        return lines;
    }

    private void addWrapped(List<DetailLine> lines, String value, int color) {
        for (OrderedText line : textRenderer.wrapLines(Text.literal(value), Math.max(80, width - MARGIN * 2))) {
            lines.add(new DetailLine(line, color));
        }
    }

    private int visibleRows() {
        return Math.max(1, (height - CONTENT_TOP - 30) / LINE_HEIGHT);
    }

    private int maxScroll() {
        return Math.max(0, totalLines - visibleRows());
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }

    private static String format(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private record DetailLine(OrderedText text, int color) {
    }
}
