package com.murphypotato.simmctoolset.internal.accessory.client;

import com.murphypotato.simmctoolset.internal.accessory.domain.AccessoryRecord;
import com.murphypotato.simmctoolset.internal.accessory.domain.AccessoryScore;
import com.murphypotato.simmctoolset.internal.accessory.domain.AccessoryScorer;
import com.murphypotato.simmctoolset.internal.accessory.domain.AccessorySlot;
import com.murphypotato.simmctoolset.internal.accessory.domain.AffixRecord;
import com.murphypotato.simmctoolset.internal.accessory.domain.AffixStat;
import com.murphypotato.simmctoolset.internal.accessory.domain.DamageBreakdown;
import com.murphypotato.simmctoolset.internal.accessory.domain.LoadoutAnalysis;
import com.murphypotato.simmctoolset.internal.accessory.domain.LoadoutResult;
import com.murphypotato.simmctoolset.internal.accessory.domain.LoadoutScore;
import com.murphypotato.simmctoolset.internal.accessory.domain.PlanVariant;
import com.murphypotato.simmctoolset.internal.accessory.domain.RiskLevel;
import com.murphypotato.simmctoolset.internal.accessory.domain.StabilityProfile;
import com.murphypotato.simmctoolset.internal.accessory.domain.WeaponMode;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class AccessoryToolScreen extends Screen {
    private static final int MARGIN = 8;
    private static final int ROW_HEIGHT = 56;
    private static final int TOOLBAR_BUTTON_COUNT = 8;

    private final ClientAccessoryController controller;
    private final ToolSession session;
    private ToolSession.View view;
    private WeaponMode weapon;
    private int reviewPage;
    private int poolPage;
    private int planPage;
    private int tabY;
    private int contentTop;
    private int rowsTop;
    private int pageSize;
    private String lastSignature = "";
    private ItemStack hoveredStack = ItemStack.EMPTY;

    public AccessoryToolScreen(ClientAccessoryController controller, ToolSession session) {
        super(Text.literal("旅行猎手饰品工具 v6 Fabric"));
        this.controller = controller;
        this.session = session;
        session.initializeView(controller.storageCorrupted() || !controller.reviewQueue().isEmpty());
        this.view = session.view();
        this.weapon = session.weapon();
    }

    @Override
    protected void init() {
        clearChildren();
        normalizeVariant();
        int toolbarColumns = width >= 900 ? 8 : width >= 440 ? 4 : width >= 300 ? 3 : 2;
        int toolbarRows = (TOOLBAR_BUTTON_COUNT + toolbarColumns - 1) / toolbarColumns;
        addToolbar(toolbarColumns);
        tabY = 18 + toolbarRows * 23 + 2;
        addTabs();
        contentTop = tabY + 23;
        rowsTop = contentTop + (view == ToolSession.View.PLAN ? 94 : 43);
        int bottomReserve = view == ToolSession.View.PLAN ? 2 : 27;
        pageSize = Math.max(1, (height - rowsTop - bottomReserve) / ROW_HEIGHT);

        switch (view) {
            case REVIEW -> addReviewActions();
            case POOL -> addPoolActions();
            case PLAN -> addPlanActions();
        }
        lastSignature = signature();
    }

    private void addToolbar(int columns) {
        List<ButtonWidget> buttons = new ArrayList<>();
        buttons.add(ButtonWidget.builder(Text.literal(session.returnButtonLabel(controller)), button -> {
            if (client != null) session.returnToOrigin(client, controller);
        }).build());
        buttons.add(ButtonWidget.builder(Text.literal("扫描物品栏"), button -> {
            ClientAccessoryController.ScanSummary summary = controller.scanInventory();
            if (summary.needsReview() > 0) setView(ToolSession.View.REVIEW);
            clearAndInit();
        }).build());
        ButtonWidget captured = ButtonWidget.builder(Text.literal("扫描当前容器"), button -> {
            ClientAccessoryController.ScanSummary summary = controller.scanCapturedContainer();
            if (summary.needsReview() > 0) setView(ToolSession.View.REVIEW);
            clearAndInit();
        }).build();
        captured.active = controller.hasCapturedContainer();
        buttons.add(captured);
        ButtonWidget calculate = ButtonWidget.builder(
            Text.literal(controller.calculating() ? "计算中..." : "计算并替换"),
            button -> {
                controller.calculateAsync();
                setView(ToolSession.View.PLAN);
                clearAndInit();
            }
        ).build();
        calculate.active = !controller.calculating();
        buttons.add(calculate);
        buttons.add(ButtonWidget.builder(Text.literal("始终复核：" + (controller.alwaysReview() ? "开" : "关")), button -> {
            controller.setAlwaysReview(!controller.alwaysReview());
            clearAndInit();
        }).build());
        buttons.add(ButtonWidget.builder(Text.literal("使用说明"), button -> {
            if (client != null) client.setScreen(new AccessoryHelpScreen(controller, session));
        }).build());
        buttons.add(ButtonWidget.builder(Text.literal("关于/设置"), button -> {
            if (client != null) client.setScreen(new AccessoryAboutScreen(controller, session));
        }).build());
        ButtonWidget endGuidance = ButtonWidget.builder(Text.literal("结束配装指引"), button -> {
            session.endGuidance();
            clearAndInit();
        }).build();
        endGuidance.active = session.guidanceActive();
        buttons.add(endGuidance);

        int buttonWidth = Math.max(48, (width - MARGIN * 2 - (columns - 1) * 4) / columns);
        for (int index = 0; index < buttons.size(); index++) {
            int row = index / columns;
            int column = index % columns;
            ButtonWidget button = buttons.get(index);
            button.setDimensionsAndPosition(buttonWidth, 20, MARGIN + column * (buttonWidth + 4), 18 + row * 23);
            addDrawableChild(button);
        }
    }

    private void addTabs() {
        int available = width - MARGIN * 2;
        int tabWidth = Math.max(42, (available - 8) / 3);
        addTabButton(ToolSession.View.REVIEW, "需要复核 " + controller.reviewQueue().size(), MARGIN, tabWidth);
        addTabButton(ToolSession.View.POOL, "待选库 " + controller.accessories().size(), MARGIN + tabWidth + 4, tabWidth);
        addTabButton(ToolSession.View.PLAN, "方案预览", MARGIN + (tabWidth + 4) * 2, tabWidth);
    }

    private void addTabButton(ToolSession.View target, String label, int x, int buttonWidth) {
        ButtonWidget button = ButtonWidget.builder(Text.literal(label), ignored -> {
            setView(target);
            clearAndInit();
        }).dimensions(x, tabY, buttonWidth, 20).build();
        button.active = view != target;
        addDrawableChild(button);
    }

    private void addReviewActions() {
        if (controller.storageCorrupted()) {
            addDrawableChild(ButtonWidget.builder(Text.literal("备份损坏文件并重建空库"), button -> confirmStorageReset())
                .dimensions(width - MARGIN - 170, contentTop + 17, 170, 20).build());
        }

        List<ReviewEntry> items = controller.reviewQueue();
        int pages = pageCount(items.size());
        reviewPage = Math.min(reviewPage, pages - 1);
        int start = reviewPage * pageSize;
        int end = Math.min(items.size(), start + pageSize);
        for (int index = start; index < end; index++) {
            ReviewEntry entry = items.get(index);
            int y = rowsTop + (index - start) * ROW_HEIGHT;
            boolean sourceConflict = controller.sourceConflict(entry.id()).isPresent();
            addDrawableChild(ButtonWidget.builder(Text.literal(sourceConflict ? "来源" : "复核"), button -> {
                if (client == null) return;
                if (sourceConflict) client.setScreen(new AccessorySourceConflictScreen(controller, session, entry.id()));
                else client.setScreen(new AccessoryEditorScreen(controller, session, entry.id(), true));
            }).dimensions(width - MARGIN - 76, y + 18, 34, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("丢弃"), button -> {
                controller.discardReview(entry.id());
                clearAndInit();
            }).dimensions(width - MARGIN - 38, y + 18, 34, 20).build());
        }
        addPagination(reviewPage, pages, page -> {
            reviewPage = page;
            clearAndInit();
        });
    }

    private void addPoolActions() {
        ButtonWidget clear = ButtonWidget.builder(Text.literal("清空全部"), button -> confirmClearAll())
            .dimensions(width - MARGIN - 76, contentTop + 17, 76, 20).build();
        clear.active = !controller.accessories().isEmpty() || !controller.reviewQueue().isEmpty();
        addDrawableChild(clear);

        List<AccessoryRecord> items = controller.accessories();
        int pages = pageCount(items.size());
        poolPage = Math.min(poolPage, pages - 1);
        int start = poolPage * pageSize;
        int end = Math.min(items.size(), start + pageSize);
        for (int index = start; index < end; index++) {
            AccessoryRecord item = items.get(index);
            int y = rowsTop + (index - start) * ROW_HEIGHT;
            addDrawableChild(ButtonWidget.builder(Text.literal("编辑"), button -> openEditor(item))
                .dimensions(width - MARGIN - 154, y + 18, 34, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("详情"), button -> openDetail(item, null))
                .dimensions(width - MARGIN - 116, y + 18, 34, 20).build());
            ButtonWidget score = ButtonWidget.builder(Text.literal("评分"), button -> openScore(item, false))
                .dimensions(width - MARGIN - 78, y + 18, 40, 20).build();
            score.active = controller.scoresReady();
            addDrawableChild(score);
            addDrawableChild(ButtonWidget.builder(Text.literal("删除"), button -> confirmDelete(item))
                .dimensions(width - MARGIN - 34, y + 18, 30, 20).build());
        }
        addPagination(poolPage, pages, page -> {
            poolPage = page;
            clearAndInit();
        });
    }

    private void addPlanActions() {
        int y = contentTop + 17;
        boolean hasStable = hasStableVariant();
        if (hasStable) {
            boolean compact = width < 300;
            int variantWidth = compact ? 48 : 56;
            ButtonWidget expected = ButtonWidget.builder(Text.literal("最高期望"), button -> {
                session.setVariant(PlanVariant.EXPECTED);
                planPage = 0;
                clearAndInit();
            }).dimensions(MARGIN, y, variantWidth, 20).build();
            expected.active = session.variant() != PlanVariant.EXPECTED;
            addDrawableChild(expected);

            ButtonWidget stable = ButtonWidget.builder(Text.literal("稳定输出"), button -> {
                session.setVariant(PlanVariant.STABLE);
                planPage = 0;
                clearAndInit();
            }).dimensions(MARGIN + variantWidth + 4, y, variantWidth, 20).build();
            stable.active = session.variant() != PlanVariant.STABLE;
            addDrawableChild(stable);
        }

        ButtonWidget bow = ButtonWidget.builder(Text.literal("弓套"), button -> switchWeapon(WeaponMode.BOW))
            .dimensions(width - MARGIN - 92, y, 44, 20).build();
        bow.active = weapon != WeaponMode.BOW;
        addDrawableChild(bow);
        ButtonWidget sword = ButtonWidget.builder(Text.literal("剑套"), button -> switchWeapon(WeaponMode.SWORD))
            .dimensions(width - MARGIN - 44, y, 44, 20).build();
        sword.active = weapon != WeaponMode.SWORD;
        addDrawableChild(sword);
        int detailWidth = width < 300 ? 20 : 44;
        String detailLabel = width < 300 ? "?" : "明细 ?";
        addDrawableChild(ButtonWidget.builder(Text.literal(detailLabel), button -> {
            if (client != null) client.setScreen(new VolatilityHelpScreen(controller, session));
        }).dimensions(width - MARGIN - 96 - detailWidth, y, detailWidth, 20).build());

        Optional<LoadoutResult> result = controller.loadout(weapon, session.variant());
        if (result.isEmpty()) return;
        int pages = Math.max(1, (AccessorySlot.values().length + pageSize - 1) / pageSize);
        planPage = Math.min(planPage, pages - 1);
        addPlanPagination(pages, y);
        int start = planPage * pageSize;
        int end = Math.min(AccessorySlot.values().length, start + pageSize);
        for (int index = start; index < end; index++) {
            AccessoryRecord item = result.get().accessories().get(AccessorySlot.values()[index]);
            if (item.isBlank()) continue;
            int yRow = rowsTop + (index - start) * ROW_HEIGHT;
            addDrawableChild(ButtonWidget.builder(Text.literal("编辑"), button -> openEditor(item))
                .dimensions(width - MARGIN - 126, yRow + 18, 38, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("详情"), button -> openDetail(item, weapon))
                .dimensions(width - MARGIN - 84, yRow + 18, 38, 20).build());
            ButtonWidget score = ButtonWidget.builder(Text.literal("评分"), button -> openScore(item, true))
                .dimensions(width - MARGIN - 42, yRow + 18, 38, 20).build();
            score.active = controller.scoresReady();
            addDrawableChild(score);
        }
    }

    private void addPlanPagination(int pages, int y) {
        if (pages <= 1) return;
        ButtonWidget previous = ButtonWidget.builder(Text.literal("<"), button -> {
            planPage--;
            clearAndInit();
        }).dimensions(MARGIN + 120, y, 24, 20).build();
        previous.active = planPage > 0;
        addDrawableChild(previous);
        ButtonWidget next = ButtonWidget.builder(Text.literal(">"), button -> {
            planPage++;
            clearAndInit();
        }).dimensions(MARGIN + 148, y, 24, 20).build();
        next.active = planPage + 1 < pages;
        addDrawableChild(next);
    }

    private void addPagination(int page, int pages, PageChange change) {
        if (pages <= 1) return;
        int y = height - 24;
        ButtonWidget previous = ButtonWidget.builder(Text.literal("<"), button -> change.apply(page - 1))
            .dimensions(width / 2 - 48, y, 40, 20).build();
        previous.active = page > 0;
        addDrawableChild(previous);
        ButtonWidget next = ButtonWidget.builder(Text.literal(">"), button -> change.apply(page + 1))
            .dimensions(width / 2 + 8, y, 40, 20).build();
        next.active = page + 1 < pages;
        addDrawableChild(next);
    }

    @Override
    public void tick() {
        if (!lastSignature.equals(signature())) clearAndInit();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, UiColors.BACKGROUND);
        hoveredStack = ItemStack.EMPTY;
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 5, UiColors.PRIMARY);
        String status = controller.status();
        int statusColor = controller.storageCorrupted() ? UiColors.ERROR : UiColors.SECONDARY;
        context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(status, width - MARGIN * 2), MARGIN, contentTop + 3, statusColor);

        switch (view) {
            case REVIEW -> renderReviews(context, mouseX, mouseY);
            case POOL -> renderPool(context, mouseX, mouseY);
            case PLAN -> renderPlan(context, mouseX, mouseY);
        }
        super.render(context, mouseX, mouseY, delta);
        if (!hoveredStack.isEmpty()) context.drawItemTooltip(textRenderer, hoveredStack, mouseX, mouseY);
    }

    private void renderReviews(DrawContext context, int mouseX, int mouseY) {
        String heading = controller.reviewQueue().isEmpty()
            ? "没有待复核项目"
            : "红色原因必须由玩家确认，异常饰品不会自动入库";
        int headingWidth = controller.storageCorrupted() ? width - 190 : width - MARGIN * 2;
        context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(heading, Math.max(40, headingWidth)), MARGIN, contentTop + 23, controller.reviewQueue().isEmpty() ? UiColors.SUCCESS : UiColors.ERROR);

        List<ReviewEntry> items = controller.reviewQueue();
        int start = Math.min(items.size(), reviewPage * pageSize);
        int end = Math.min(items.size(), start + pageSize);
        for (int index = start; index < end; index++) {
            ReviewEntry entry = items.get(index);
            AccessoryRecord item = entry.result().accessory();
            int y = rowsTop + (index - start) * ROW_HEIGHT;
            drawRowBackground(context, y, index);
            drawItemAndTrackHover(context, item, MARGIN + 5, y + 19, mouseX, mouseY);
            int textX = MARGIN + 27;
            int textWidth = Math.max(40, width - textX - 92);
            context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(item.name() + iconStatus(item) + " · " + item.slot().label() + " · +" + item.level(), textWidth), textX, y + 4, UiColors.PRIMARY);
            context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(String.join("；", entry.result().reviewMessages()), textWidth), textX, y + 17, UiColors.ERROR);
            drawAffixSummary(context, item, null, textX, y + 30, textWidth);
            context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(sourceLabel(item), textWidth), textX, y + 43, UiColors.MUTED);
        }
    }

    private void renderPool(DrawContext context, int mouseX, int mouseY) {
        context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth("已确认饰品；计算后可查看剑/弓动态评分", width - 92), MARGIN, contentTop + 23, UiColors.SECONDARY);
        List<AccessoryRecord> items = controller.accessories();
        int start = Math.min(items.size(), poolPage * pageSize);
        int end = Math.min(items.size(), start + pageSize);
        for (int index = start; index < end; index++) {
            AccessoryRecord item = items.get(index);
            int y = rowsTop + (index - start) * ROW_HEIGHT;
            drawRowBackground(context, y, index);
            drawItemAndTrackHover(context, item, MARGIN + 5, y + 19, mouseX, mouseY);
            int textX = MARGIN + 27;
            int textWidth = Math.max(40, width - textX - 168);
            context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(item.name() + iconStatus(item) + " · " + item.slot().label() + " +" + item.level() + selectionMarker(item), textWidth), textX, y + 4, UiColors.PRIMARY);
            drawAffixSummary(context, item, null, textX, y + 17, textWidth);
            context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(scoreSummary(item), textWidth), textX, y + 30, controller.scoresReady() ? UiColors.SUCCESS : UiColors.WARNING);
            context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(sourceLabel(item), textWidth), textX, y + 43, UiColors.MUTED);
        }
        if (items.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, "扫描后自动入库，异常项目会进入“需要复核”", width / 2, rowsTop + 18, UiColors.MUTED);
        }
    }

    private void renderPlan(DrawContext context, int mouseX, int mouseY) {
        Optional<LoadoutAnalysis> analysisOptional = controller.analysis(weapon);
        if (analysisOptional.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, controller.calculating() ? "正在计算..." : "点击“计算并替换”生成剑套和弓套", width / 2, rowsTop, UiColors.SECONDARY);
            return;
        }

        LoadoutAnalysis analysis = analysisOptional.get();
        LoadoutResult result = analysis.result(session.variant());
        DamageBreakdown damage = result.breakdown();
        StabilityProfile stability = analysis.stability(session.variant());
        Optional<LoadoutScore> loadoutScore = controller.scoresReady()
            ? controller.loadoutScore(weapon, session.variant())
            : Optional.empty();
        String scoreText = loadoutScore.map(score -> format(score.totalScore()) + "分").orElse("待重算");
        String first = session.variant().label() + " · 期望 " + format(damage.expected())
            + "  非暴击 " + format(damage.nonCrit()) + "  暴击 " + format(damage.crit()) + "  套装 " + scoreText;
        String second = "暴击率 " + percent(damage.critChance()) + "  十次零暴击 " + percent(stability.noCritTenProbability()) + "  稳定下限 " + format(stability.stableFloor80());
        context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(first, width - MARGIN * 2), MARGIN, contentTop + 41, UiColors.SUCCESS);
        context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(second, width - MARGIN * 2), MARGIN, contentTop + 54, UiColors.ACCENT);

        String effective = "有效词条：" + loadoutScore.map(this::effectiveAffixSummary).orElse("评分待重算");
        context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(effective, width - MARGIN * 2), MARGIN, contentTop + 67, UiColors.PRIMARY);

        String riskText = controller.plansDirty()
            ? "饰品库已变化：请重新计算，当前方案和评分已过期"
            : "零暴击 " + percent(stability.noCritTenProbability())
                + " · 暴击提升 " + percent(stability.critLiftRatio())
                + " · 期望占比 " + percent(stability.critDependencyRatio())
                + " · " + riskText(analysis, stability);
        int riskColor = controller.plansDirty() || analysis.expectedStability().riskLevel() == RiskLevel.HIGH ? UiColors.ERROR
            : analysis.expectedStability().riskLevel() == RiskLevel.CAUTION || !analysis.expectedStability().warnings().isEmpty()
                ? UiColors.WARNING : UiColors.MUTED;
        context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(riskText, width - MARGIN * 2), MARGIN, contentTop + 80, riskColor);

        int start = planPage * pageSize;
        int end = Math.min(AccessorySlot.values().length, start + pageSize);
        for (int index = start; index < end; index++) {
            AccessorySlot slot = AccessorySlot.values()[index];
            AccessoryRecord item = result.accessories().get(slot);
            int y = rowsTop + (index - start) * ROW_HEIGHT;
            drawRowBackground(context, y, index);
            int textX = MARGIN + 27;
            int textWidth = Math.max(40, width - textX - (item.isBlank() ? 10 : 140));
            if (!item.isBlank()) drawItemAndTrackHover(context, item, MARGIN + 5, y + 19, mouseX, mouseY);
            context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(slot.label() + "：" + item.name() + iconStatus(item), textWidth), textX, y + 4, item.isBlank() ? UiColors.BLANK : UiColors.PRIMARY);
            drawAffixSummary(context, item, weapon, textX, y + 17, textWidth);
            context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(item.isBlank() ? "评分 0" : currentScoreSummary(item), textWidth), textX, y + 30, UiColors.SUCCESS);
            context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(sourceGroupLabel(item) + " · " + sourceLabel(item), textWidth), textX, y + 43, UiColors.MUTED);
        }

        List<String> changes = controller.changeLog(weapon);
        int logY = rowsTop + Math.max(0, end - start) * ROW_HEIGHT + 2;
        if (!changes.isEmpty() && logY <= height - 34) {
            context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth("方案变化：" + String.join("；", changes), width - MARGIN * 2), MARGIN, logY, UiColors.CHANGE);
        }
    }

    private String riskText(LoadoutAnalysis analysis, StabilityProfile current) {
        StabilityProfile expected = analysis.expectedStability();
        if (!expected.warnings().isEmpty()) {
            String warning = String.join("；", expected.warnings());
            if (expected.risky() && analysis.stable().isEmpty()) warning += "；当前仓库没有合适稳定替代";
            return warning;
        }
        if (expected.risky()) {
            return (expected.riskLevel() == RiskLevel.HIGH ? "高波动风险" : "波动提醒")
                + "；当前方案稳定下限 " + format(current.stableFloor80());
        }
        return "未触发低暴击高爆伤风险";
    }

    private String sourceGroupSummary(LoadoutResult result) {
        Map<String, Integer> groups = new LinkedHashMap<>();
        for (AccessoryRecord item : result.accessories().values()) {
            if (!item.isBlank()) groups.merge(sourceGroupLabel(item), 1, Integer::sum);
        }
        if (groups.isEmpty()) return "配装来源：空白方案";
        String joined = groups.entrySet().stream()
            .map(entry -> entry.getKey() + " " + entry.getValue() + "件")
            .reduce((left, right) -> left + "；" + right)
            .orElse("");
        return "配装来源：" + joined;
    }

    private void drawItemAndTrackHover(DrawContext context, AccessoryRecord item, int x, int y, int mouseX, int mouseY) {
        ItemStack stack = controller.displayStack(item);
        if (stack.isEmpty()) return;
        context.drawItem(stack, x, y);
        if (!controller.iconPending(item)
            && mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18) hoveredStack = stack;
    }

    private String iconStatus(AccessoryRecord item) {
        return controller.iconPending(item) ? " [图标待刷新]" : "";
    }

    private void drawRowBackground(DrawContext context, int y, int index) {
        int color = index % 2 == 0 ? 0x8A131820 : 0x8A1A202A;
        context.fill(MARGIN, y, width - MARGIN, y + ROW_HEIGHT - 2, color);
    }

    private String selectionMarker(AccessoryRecord item) {
        return controller.selectedBy(item, session.weapon(), session.variant()) ? " [当前预览]" : "";
    }

    private String scoreSummary(AccessoryRecord item) {
        if (!controller.scoresReady()) return "弓/剑评分待重算";
        return "弓 " + scoreValue(item, WeaponMode.BOW) + " · 剑 " + scoreValue(item, WeaponMode.SWORD);
    }

    private String currentScoreSummary(AccessoryRecord item) {
        if (!controller.scoresReady()) return "评分待重算";
        Optional<LoadoutScore> score = controller.loadoutScore(weapon, session.variant());
        return score.map(value -> weapon.label() + "套内贡献 " + format(value.accessoryScore(item.id())) + "分")
            .orElse("评分待重算");
    }

    private String scoreValue(AccessoryRecord item, WeaponMode mode) {
        Optional<AccessoryScore> score = controller.score(item, mode);
        if (score.isEmpty()) return "--";
        if (!score.get().available()) return "不可用";
        return format(score.get().totalScore()) + "分";
    }

    private String sourceLabel(AccessoryRecord item) {
        return controller.sourceLabel(item);
    }

    private String sourceGroupLabel(AccessoryRecord item) {
        return controller.sourceGroupLabel(item);
    }

    private static String affixSummary(AccessoryRecord item) {
        if (item.affixes().isEmpty()) return "无词条";
        return item.affixes().stream()
            .map(AffixRecord::displayText)
            .reduce((left, right) -> left + " · " + right)
            .orElse("无词条");
    }

    private void drawAffixSummary(
        DrawContext context,
        AccessoryRecord item,
        WeaponMode mode,
        int x,
        int y,
        int maxWidth
    ) {
        if (item.affixes().isEmpty()) {
            context.drawTextWithShadow(textRenderer, "无词条", x, y, UiColors.DIM);
            return;
        }
        int cursor = x;
        int right = x + maxWidth;
        for (int index = 0; index < item.affixes().size(); index++) {
            AffixRecord affix = item.affixes().get(index);
            boolean effective = mode == null ? affix.stat().combat() : AccessoryScorer.isEffective(affix.stat(), mode);
            boolean oppositeMode = mode != null && affix.stat().combat() && !effective;
            String segment = (index == 0 ? "" : " · ") + affix.displayText()
                + (oppositeMode ? " [本模式不计]" : "");
            int available = right - cursor;
            if (available <= 0) break;
            String visible = textRenderer.trimToWidth(segment, available);
            int color = effective ? UiColors.PRIMARY : oppositeMode ? UiColors.MUTED : UiColors.DIM;
            context.drawTextWithShadow(textRenderer, visible, cursor, y, color);
            cursor += textRenderer.getWidth(visible);
            if (!visible.equals(segment)) break;
        }
    }

    private String effectiveAffixSummary(LoadoutScore score) {
        if (!score.available()) return score.warning();
        if (score.effectiveAffixTotals().isEmpty()) return "无";
        return score.effectiveAffixTotals().entrySet().stream()
            .map(entry -> shortStatLabel(entry.getKey()) + " +" + format(entry.getValue()))
            .reduce((left, right) -> left + "、" + right)
            .orElse("无");
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

    private int pageCount(int itemCount) {
        return Math.max(1, (itemCount + pageSize - 1) / pageSize);
    }

    private void normalizeVariant() {
        if (session.variant() == PlanVariant.STABLE && !hasStableVariant()) session.setVariant(PlanVariant.EXPECTED);
    }

    private boolean hasStableVariant() {
        return controller.analysis(weapon).map(value -> value.stable().isPresent()).orElse(false);
    }

    private void switchWeapon(WeaponMode next) {
        weapon = next;
        session.setWeapon(next);
        if (!hasStableVariant()) session.setVariant(PlanVariant.EXPECTED);
        planPage = 0;
        clearAndInit();
    }

    private void setView(ToolSession.View target) {
        view = target;
        session.setView(target);
    }

    private void openEditor(AccessoryRecord item) {
        if (client != null) client.setScreen(new AccessoryEditorScreen(controller, session, item.id(), false));
    }

    private void openDetail(AccessoryRecord item, WeaponMode mode) {
        if (client != null) client.setScreen(new AccessoryDetailScreen(controller, session, item.id(), mode));
    }

    private void openScore(AccessoryRecord item, boolean loadoutContext) {
        if (client != null) client.setScreen(new AccessoryScoreScreen(controller, session, item.id(), loadoutContext));
    }

    private String signature() {
        return controller.reviewQueue().size() + "|"
            + controller.accessories().size() + "|"
            + controller.status() + "|"
            + controller.calculating() + "|"
            + controller.plansDirty() + "|"
            + controller.scoresReady() + "|"
            + controller.alwaysReview() + "|"
            + controller.storageCorrupted() + "|"
            + session.guidanceActive() + "|"
            + weapon + "|" + session.variant() + "|"
            + controller.loadout(WeaponMode.BOW).map(value -> value.breakdown().expected()).orElse(-1.0) + "|"
            + controller.loadout(WeaponMode.SWORD).map(value -> value.breakdown().expected()).orElse(-1.0);
    }

    private void confirmDelete(AccessoryRecord item) {
        if (client == null) return;
        client.setScreen(new AccessoryConfirmScreen("删除饰品？", List.of(
            item.name() + " · " + item.quality().label() + " · " + item.slot().label() + " · +" + item.level(),
            affixSummary(item)
        ), confirmed -> {
            if (confirmed) controller.deleteAccessory(item.id());
            client.setScreen(new AccessoryToolScreen(controller, session));
        }));
    }

    private void confirmClearAll() {
        if (client == null) return;
        client.setScreen(new AccessoryConfirmScreen("清空全部饰品？", List.of(
            "待选库、复核队列和当前方案都会清空。",
            "数据文件会立即写入空库，此操作无法撤销。"
        ), confirmed -> {
            if (confirmed) controller.clearAll();
            client.setScreen(new AccessoryToolScreen(controller, session));
        }));
    }

    private void confirmStorageReset() {
        if (client == null) return;
        client.setScreen(new AccessoryConfirmScreen("备份并重建饰品库？", List.of(
            "损坏原文件会先复制为 .bak。",
            "确认后建立空库；未确认前不会覆盖原文件。"
        ), confirmed -> {
            if (confirmed) controller.resetCorruptStorage();
            client.setScreen(new AccessoryToolScreen(controller, session));
        }));
    }

    private static String format(double value) {
        return BigDecimal.valueOf(value).setScale(4, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private static String percent(double value) {
        return format(value * 100.0) + "%";
    }

    @Override
    public void close() {
        if (client != null) session.returnToOrigin(client, controller);
    }

    @FunctionalInterface
    private interface PageChange {
        void apply(int page);
    }
}
