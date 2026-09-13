package com.murphypotato.simmctoolset.internal.accessory.client;

import com.murphypotato.simmctoolset.internal.accessory.domain.AccessoryRecord;
import com.murphypotato.simmctoolset.internal.accessory.domain.AccessorySlot;
import com.murphypotato.simmctoolset.internal.accessory.domain.AffixStat;
import com.murphypotato.simmctoolset.internal.accessory.domain.DamageBreakdown;
import com.murphypotato.simmctoolset.internal.accessory.domain.LoadoutAnalysis;
import com.murphypotato.simmctoolset.internal.accessory.domain.LoadoutResult;
import com.murphypotato.simmctoolset.internal.accessory.domain.LoadoutScore;
import com.murphypotato.simmctoolset.internal.accessory.domain.StabilityProfile;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public final class VolatilityHelpScreen extends Screen {
    private static final int LINE_HEIGHT = 12;
    private static final int CONTENT_TOP = 36;

    private final ClientAccessoryController controller;
    private final ToolSession session;
    private int scroll;
    private int totalLines;

    public VolatilityHelpScreen(ClientAccessoryController controller, ToolSession session) {
        super(Text.literal("方案明细与波动说明"));
        this.controller = controller;
        this.session = session;
    }

    @Override
    protected void init() {
        clearChildren();
        addDrawableChild(ButtonWidget.builder(Text.literal("返回方案"), button -> close())
            .dimensions(Math.max(8, width / 2 - 50), height - 28, 100, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, UiColors.BACKGROUND);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 10, UiColors.PRIMARY);
        List<DetailLine> lines = buildLines();
        totalLines = lines.size();
        int visibleRows = visibleRows();
        scroll = clamp(scroll, 0, maxScroll());
        int end = Math.min(lines.size(), scroll + visibleRows);
        int y = CONTENT_TOP;
        for (int index = scroll; index < end; index++) {
            DetailLine line = lines.get(index);
            context.drawTextWithShadow(textRenderer, line.text(), 12, y, line.color());
            y += LINE_HEIGHT;
        }
        if (totalLines > visibleRows) {
            String position = (scroll + 1) + "-" + end + " / " + totalLines + " · 滚轮或方向键浏览";
            context.drawTextWithShadow(textRenderer, position, 12, height - 18, UiColors.MUTED);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount == 0) return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        scroll = clamp(scroll + (verticalAmount > 0 ? -3 : 3), 0, maxScroll());
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int keyCode = input.key();
        if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN) {
            scroll = clamp(scroll + (keyCode == GLFW.GLFW_KEY_UP ? -1 : 1), 0, maxScroll());
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_UP || keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
            int direction = keyCode == GLFW.GLFW_KEY_PAGE_UP ? -1 : 1;
            scroll = clamp(scroll + direction * visibleRows(), 0, maxScroll());
            return true;
        }
        return super.keyPressed(input);
    }

    private List<DetailLine> buildLines() {
        List<DetailLine> lines = new ArrayList<>();
        LoadoutAnalysis analysis = controller.analysis(session.weapon()).orElse(null);
        if (analysis == null) {
            addWrapped(lines, "尚未计算方案。返回工具后点击“计算并替换”。", UiColors.WARNING);
            return lines;
        }

        LoadoutResult loadout = analysis.result(session.variant());
        DamageBreakdown damage = loadout.breakdown();
        LoadoutScore score = controller.loadoutScore(session.weapon(), session.variant()).orElse(null);
        StabilityProfile current = analysis.stability(session.variant());
        StabilityProfile expected = analysis.expectedStability();

        addWrapped(lines, session.weapon().label() + " · " + session.variant().label(), UiColors.ACCENT);
        addWrapped(lines, "期望 " + format(damage.expected()) + " · 非暴击 " + format(damage.nonCrit())
            + " · 暴击 " + format(damage.crit()) + " · 暴击率 " + percent(damage.critChance()), UiColors.PRIMARY);
        addWrapped(lines, "套装评分 " + (score == null ? "待重算" : format(score.totalScore()) + "分")
            + " · 80%稳定下限 " + format(current.stableFloor80()), UiColors.SUCCESS);

        addWrapped(lines, "有效伤害词条总览", UiColors.ACCENT);
        if (score == null || score.effectiveAffixTotals().isEmpty()) {
            addWrapped(lines, score == null ? "评分待重算" : "无", UiColors.MUTED);
        } else {
            for (var entry : score.effectiveAffixTotals().entrySet()) {
                addWrapped(lines, shortStatLabel(entry.getKey()) + " +" + format(entry.getValue()), UiColors.PRIMARY);
            }
        }

        addWrapped(lines, "四件饰品贡献", UiColors.ACCENT);
        for (AccessorySlot slot : AccessorySlot.values()) {
            AccessoryRecord accessory = loadout.accessories().get(slot);
            String points = accessory.isBlank() || score == null ? "0" : format(score.accessoryScore(accessory.id()));
            addWrapped(lines, slot.label() + "：" + accessory.name() + " · " + points + "分",
                accessory.isBlank() ? UiColors.DIM : UiColors.PRIMARY);
        }

        addWrapped(lines, "波动风险（以下前三项必须同时达到）", UiColors.ACCENT);
        addMetric(lines, "十次零暴击", expected.noCritTenProbability(), 0.30);
        addMetric(lines, "暴击相对提升", expected.critLiftRatio(), 0.50);
        addMetric(lines, "暴击期望占比", expected.critDependencyRatio(), 0.05);
        addWrapped(lines, "高风险额外条件：零暴击 >= 50%，或期望与稳定下限差距 >= 15%。", UiColors.SECONDARY);
        addMetric(lines, "当前稳定差距", expected.stabilityGapRatio(), 0.15);
        addWrapped(lines, "稳定方案要求：期望至少保留 95%，且稳定下限至少提高 1%。", UiColors.SECONDARY);
        if (!expected.warnings().isEmpty()) {
            for (String warning : expected.warnings()) addWrapped(lines, warning, UiColors.WARNING);
        }
        if (controller.plansDirty()) {
            addWrapped(lines, "饰品库已变化，以上方案和评分需要重新计算。", UiColors.ERROR);
        }
        return lines;
    }

    private void addMetric(List<DetailLine> lines, String label, double value, double threshold) {
        boolean reached = value + 1e-12 >= threshold;
        addWrapped(lines, label + " " + percent(value) + " / 阈值 " + percent(threshold)
            + (reached ? "（达到）" : "（未达到）"), reached ? UiColors.WARNING : UiColors.SUCCESS);
    }

    private void addWrapped(List<DetailLine> lines, String value, int color) {
        for (OrderedText line : textRenderer.wrapLines(Text.literal(value), Math.max(120, width - 24))) {
            lines.add(new DetailLine(line, color));
        }
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(new AccessoryToolScreen(controller, session));
    }

    private int visibleRows() {
        return Math.max(1, (height - CONTENT_TOP - 34) / LINE_HEIGHT);
    }

    private int maxScroll() {
        return Math.max(0, totalLines - visibleRows());
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }

    private static String shortStatLabel(AffixStat stat) {
        return switch (stat) {
            case HUNTER_DAMAGE_MULTIPLIER -> "旅猎倍率";
            case HUNTER_CRIT_CHANCE -> "暴击率";
            case HUNTER_CRIT_DAMAGE -> "爆伤";
            case BOW_MASTERY_MULTIPLIER -> "弓专精倍率";
            case SWORD_MASTERY_MULTIPLIER -> "剑专精倍率";
            case BOW_FINAL_DAMAGE -> "弓最终伤害";
            case SWORD_FINAL_DAMAGE -> "剑最终伤害";
            case BOW_EXTRA_DAMAGE -> "弓额外伤害";
            case SWORD_EXTRA_DAMAGE -> "剑额外伤害";
            case OTHER -> stat.label();
        };
    }

    private static String percent(double value) {
        return format(value * 100) + "%";
    }

    private static String format(double value) {
        return BigDecimal.valueOf(value).setScale(4, java.math.RoundingMode.HALF_UP)
            .stripTrailingZeros().toPlainString();
    }

    private record DetailLine(OrderedText text, int color) {
    }
}
