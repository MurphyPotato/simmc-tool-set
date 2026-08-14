package com.murphypotato.simmctoolset.internal.accessory.client;

import com.murphypotato.simmctoolset.internal.accessory.domain.AccessoryRecord;
import com.murphypotato.simmctoolset.internal.accessory.domain.AccessoryScore;
import com.murphypotato.simmctoolset.internal.accessory.domain.AccessoryScorer;
import com.murphypotato.simmctoolset.internal.accessory.domain.AffixContribution;
import com.murphypotato.simmctoolset.internal.accessory.domain.AffixRecord;
import com.murphypotato.simmctoolset.internal.accessory.domain.LoadoutScore;
import com.murphypotato.simmctoolset.internal.accessory.domain.PlanVariant;
import com.murphypotato.simmctoolset.internal.accessory.domain.WeaponMode;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class AccessoryScoreScreen extends Screen {
    private static final int MARGIN = 10;
    private static final int ROW_HEIGHT = 30;

    private final ClientAccessoryController controller;
    private final ToolSession session;
    private final String accessoryId;
    private final AccessoryRecord accessory;
    private final boolean loadoutContext;
    private int page;
    private int pageSize;
    private int rowsTop;
    private ItemStack hoveredStack = ItemStack.EMPTY;

    public AccessoryScoreScreen(
        ClientAccessoryController controller,
        ToolSession session,
        String accessoryId,
        boolean loadoutContext
    ) {
        super(Text.literal(loadoutContext ? "方案内评分详情" : "饰品替换评分详情"));
        this.controller = controller;
        this.session = session;
        this.accessoryId = accessoryId;
        this.accessory = controller.findAccessory(accessoryId).orElseThrow();
        this.loadoutContext = loadoutContext;
    }

    @Override
    protected void init() {
        clearChildren();
        addDrawableChild(ButtonWidget.builder(Text.literal("返回工具"), button -> close())
            .dimensions(MARGIN, 18, 72, 20).build());
        ButtonWidget bow = ButtonWidget.builder(Text.literal("弓评分"), button -> switchWeapon(WeaponMode.BOW))
            .dimensions(width - MARGIN - 104, 18, 50, 20).build();
        bow.active = session.weapon() != WeaponMode.BOW;
        addDrawableChild(bow);
        ButtonWidget sword = ButtonWidget.builder(Text.literal("剑评分"), button -> switchWeapon(WeaponMode.SWORD))
            .dimensions(width - MARGIN - 50, 18, 50, 20).build();
        sword.active = session.weapon() != WeaponMode.SWORD;
        addDrawableChild(sword);

        rowsTop = 104;
        pageSize = Math.max(1, (height - rowsTop - 30) / ROW_HEIGHT);
        int pages = Math.max(1, (accessory.affixes().size() + pageSize - 1) / pageSize);
        page = Math.min(page, pages - 1);
        if (pages > 1) {
            ButtonWidget previous = ButtonWidget.builder(Text.literal("<"), button -> {
                page--;
                clearAndInit();
            }).dimensions(width / 2 - 48, height - 24, 40, 20).build();
            previous.active = page > 0;
            addDrawableChild(previous);
            ButtonWidget next = ButtonWidget.builder(Text.literal(">"), button -> {
                page++;
                clearAndInit();
            }).dimensions(width / 2 + 8, height - 24, 40, 20).build();
            next.active = page + 1 < pages;
            addDrawableChild(next);
        }
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
            + " · " + accessory.slot().label() + " · +" + accessory.level();
        context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(identity, width - 48), 34, 48, UiColors.PRIMARY);
        context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(controller.sourceLabel(accessory), width - 48), 34, 61, UiColors.MUTED);

        Optional<AccessoryScore> scoreOptional = loadoutContext
            ? Optional.empty()
            : controller.score(accessory, session.weapon());
        Optional<LoadoutScore> loadoutScore = loadoutContext && !controller.plansDirty()
            ? controller.loadoutScore(session.weapon(), session.variant())
            : Optional.empty();
        if ((loadoutContext ? loadoutScore.isEmpty() : scoreOptional.isEmpty()) || controller.plansDirty()) {
            context.drawTextWithShadow(textRenderer, "评分待重算：返回工具后点击“计算并替换”", MARGIN, 78, UiColors.WARNING);
        } else if (loadoutContext) {
            LoadoutScore score = loadoutScore.orElseThrow();
            double expected = controller.loadout(session.weapon(), session.variant())
                .map(value -> value.breakdown().expected()).orElse(10.0);
            String summary = session.weapon().label() + " · " + session.variant().label()
                + " · 套内贡献 " + format(score.accessoryScore(accessory.id())) + "分"
                + " · 套装期望 " + format(expected);
            context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(summary, width - MARGIN * 2), MARGIN, 78,
                score.available() ? UiColors.SUCCESS : UiColors.ERROR);
            if (!score.warning().isEmpty()) {
                context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(score.warning(), width - MARGIN * 2), MARGIN, 90, UiColors.ERROR);
            }
        } else {
            AccessoryScore score = scoreOptional.get();
            String summary = session.weapon().label() + "总评分 " + format(score.totalScore()) + "分"
                + " · 替换后期望 " + format(score.expectedWhenEquipped())
                + " · 距最优 " + signed(score.gapToBest());
            context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(summary, width - MARGIN * 2), MARGIN, 78, score.available() ? UiColors.SUCCESS : UiColors.ERROR);
            if (!score.warning().isEmpty()) {
                context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(score.warning(), width - MARGIN * 2), MARGIN, 88, UiColors.ERROR);
            }
        }

        Map<String, AffixContribution> contributions = loadoutContext
            ? contributionMap(loadoutScore.map(LoadoutScore::affixes).orElse(List.of()))
            : contributionMap(scoreOptional.map(AccessoryScore::affixes).orElse(List.of()));
        int start = Math.min(accessory.affixes().size(), page * pageSize);
        int end = Math.min(accessory.affixes().size(), start + pageSize);
        for (int index = start; index < end; index++) {
            AffixRecord affix = accessory.affixes().get(index);
            AffixContribution contribution = contributions.get(affix.id());
            int y = rowsTop + (index - start) * ROW_HEIGHT;
            context.fill(MARGIN, y, width - MARGIN, y + ROW_HEIGHT - 2, index % 2 == 0 ? 0x8A131820 : 0x8A1A202A);
            context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(affix.displayText(), width - MARGIN * 2), MARGIN + 5, y + 4,
                AccessoryScorer.isEffective(affix.stat(), session.weapon()) ? UiColors.PRIMARY : UiColors.DIM);
            String detail;
            int color;
            if (contribution == null) {
                detail = "评分待重算";
                color = UiColors.WARNING;
            } else if (!contribution.effective()) {
                detail = "本模式不计伤害评分";
                color = UiColors.MUTED;
            } else {
                detail = format(contribution.scorePoints()) + "分 · 期望伤害贡献 " + signed(contribution.expectedDamageContribution());
                color = UiColors.SUCCESS;
            }
            context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(detail, width - MARGIN * 2 - 10), MARGIN + 5, y + 17, color);
        }
        if (accessory.affixes().isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, "该饰品没有词条", width / 2, rowsTop + 8, UiColors.MUTED);
        }
        super.render(context, mouseX, mouseY, delta);
        if (!hoveredStack.isEmpty()) context.drawItemTooltip(textRenderer, hoveredStack, mouseX, mouseY);
    }

    private void switchWeapon(WeaponMode weapon) {
        session.setWeapon(weapon);
        boolean hasStable = controller.analysis(weapon)
            .map(analysis -> analysis.stable().isPresent())
            .orElse(false);
        if (session.variant() == PlanVariant.STABLE && !hasStable) {
            session.setVariant(PlanVariant.EXPECTED);
        }
        clearAndInit();
    }

    private static Map<String, AffixContribution> contributionMap(List<AffixContribution> contributions) {
        Map<String, AffixContribution> result = new HashMap<>();
        for (AffixContribution contribution : contributions) result.put(contribution.affixId(), contribution);
        return result;
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(new AccessoryToolScreen(controller, session));
    }

    private static String signed(double value) {
        return (value > 0 ? "+" : "") + format(value);
    }

    private static String format(double value) {
        return BigDecimal.valueOf(value).setScale(4, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
}
