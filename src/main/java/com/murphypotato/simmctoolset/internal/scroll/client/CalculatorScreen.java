package com.murphypotato.simmctoolset.internal.scroll.client;

import com.murphypotato.simmctoolset.internal.scroll.config.ArcaneSettings;
import com.murphypotato.simmctoolset.internal.scroll.domain.ArcaneSolver;
import com.murphypotato.simmctoolset.internal.scroll.domain.CraftPlan;
import com.murphypotato.simmctoolset.internal.scroll.domain.Element;
import com.murphypotato.simmctoolset.internal.scroll.domain.ElementAmounts;
import com.murphypotato.simmctoolset.internal.scroll.domain.RotationBatch;
import com.murphypotato.simmctoolset.internal.scroll.domain.ScrollRecipe;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CalculatorScreen extends Screen {
    private static final int MARGIN = 8;
    private static final int ROW_HEIGHT = 24;
    private static final int LINE_HEIGHT = 12;

    private final ArcaneController controller;
    private final Screen parent;
    private String query = "";
    private String quantityDraft;
    private String thresholdDraft;
    private CalculationResult result;
    private TextFieldWidget searchField;
    private TextFieldWidget quantityField;
    private TextFieldWidget thresholdField;
    private int recipeX;
    private int recipeY;
    private int recipeWidth;
    private int recipeHeight;
    private int resultX;
    private int resultY;
    private int resultWidth;
    private int resultHeight;
    private int recipeScroll;
    private int resultScroll;
    private int selectedVisibleIndex;
    private List<DisplayLine> displayLines = List.of();
    private boolean rebuilding;
    private String lastControllerState = "";

    public CalculatorScreen(ArcaneController controller) {
        this(controller, null);
    }

    public CalculatorScreen(ArcaneController controller, Screen parent) {
        super(Text.literal("simMC 奥术卷轴计算器"));
        this.controller = controller;
        this.parent = parent;
        ArcaneSettings settings = controller.settings();
        quantityDraft = Integer.toString(settings.quantity());
        thresholdDraft = Integer.toString(settings.repeatThreshold());
    }

    @Override
    protected void init() {
        clearChildren();
        rebuilding = true;
        ArcaneSettings settings = controller.settings();
        quantityDraft = Integer.toString(settings.quantity());
        thresholdDraft = Integer.toString(settings.repeatThreshold());

        int columns = width >= 700 ? 5 : width >= 430 ? 4 : 3;
        int toolbarRows = (5 + columns - 1) / columns;
        int toolbarY = 22;
        int buttonWidth = Math.max(46, (width - MARGIN * 2 - (columns - 1) * 4) / columns);
        List<ButtonWidget> toolbar = new ArrayList<>();
        toolbar.add(ButtonWidget.builder(Text.literal(controller.calculating() ? "取消" : "计算"), button -> {
            if (controller.calculating()) {
                controller.cancel();
                result = null;
                rebuildDisplayLines();
                clearAndInit();
            } else {
                calculate();
            }
        }).build());
        toolbar.add(ButtonWidget.builder(Text.literal("材料排除"), button -> {
            persistInputs();
            if (client != null) client.setScreen(new MaterialExclusionScreen(controller, this));
        }).build());
        toolbar.add(ButtonWidget.builder(Text.literal("主材料：" + (settings.includeMainMaterial() ? "计入" : "不计")), button -> {
            ArcaneSettings current = readInputSettings().withInputs(
                parseClamped(quantityDraft, 1, 9999), !controller.settings().includeMainMaterial(),
                parseClamped(thresholdDraft, 1, 999)
            );
            controller.updateSettings(current);
            invalidateResult();
            clearAndInit();
        }).build());
        toolbar.add(ButtonWidget.builder(Text.literal("使用说明"), button -> {
            if (client != null) client.setScreen(new HelpScreen(this));
        }).build());
        toolbar.add(ButtonWidget.builder(Text.literal("关于 / 隐私"), button -> {
            if (client != null) client.setScreen(new AboutScreen(this));
        }).build());
        for (int index = 0; index < toolbar.size(); index++) {
            int row = index / columns;
            int column = index % columns;
            ButtonWidget button = toolbar.get(index);
            button.setDimensionsAndPosition(buttonWidth, 20, MARGIN + column * (buttonWidth + 4), toolbarY + row * 23);
            addDrawableChild(button);
        }

        int fieldsY = toolbarY + toolbarRows * 23 + 11;
        int quantityWidth = 62;
        int thresholdWidth = 62;
        int searchWidth = Math.max(92, width - MARGIN * 2 - quantityWidth - thresholdWidth - 8);
        searchField = new TextFieldWidget(textRenderer, MARGIN, fieldsY, searchWidth, 20, Text.literal("卷轴搜索"));
        searchField.setMaxLength(80);
        searchField.setPlaceholder(Text.literal("卷轴搜索"));
        searchField.setText(query);
        searchField.setChangedListener(value -> {
            query = value;
            recipeScroll = 0;
            selectedVisibleIndex = 0;
        });
        addDrawableChild(searchField);

        quantityField = new TextFieldWidget(
            textRenderer, MARGIN + searchWidth + 4, fieldsY, quantityWidth, 20, Text.literal("数量")
        );
        quantityField.setMaxLength(4);
        quantityField.setTextPredicate(value -> value.isEmpty() || value.matches("[0-9]{1,4}"));
        quantityField.setText(quantityDraft);
        quantityField.setChangedListener(value -> {
            quantityDraft = value;
            if (!rebuilding) invalidateResult();
        });
        addDrawableChild(quantityField);

        thresholdField = new TextFieldWidget(
            textRenderer, MARGIN + searchWidth + quantityWidth + 8, fieldsY, thresholdWidth, 20, Text.literal("轮换阈值")
        );
        thresholdField.setMaxLength(3);
        thresholdField.setTextPredicate(value -> value.isEmpty() || value.matches("[0-9]{1,3}"));
        thresholdField.setText(thresholdDraft);
        thresholdField.setChangedListener(value -> {
            thresholdDraft = value;
            if (!rebuilding) invalidateResult();
        });
        addDrawableChild(thresholdField);

        int contentTop = fieldsY + 26;
        boolean narrow = width < 520;
        if (narrow) {
            recipeX = MARGIN;
            recipeY = contentTop;
            recipeWidth = width - MARGIN * 2;
            recipeHeight = Math.max(52, Math.min(100, (height - contentTop) / 3));
            resultX = MARGIN;
            resultY = recipeY + recipeHeight + 5;
            resultWidth = width - MARGIN * 2;
            resultHeight = Math.max(24, height - resultY - MARGIN);
        } else {
            recipeX = MARGIN;
            recipeY = contentTop;
            recipeWidth = Math.max(150, Math.min(210, width / 3));
            recipeHeight = Math.max(24, height - recipeY - MARGIN);
            resultX = recipeX + recipeWidth + 5;
            resultY = contentTop;
            resultWidth = Math.max(100, width - resultX - MARGIN);
            resultHeight = Math.max(24, height - resultY - MARGIN);
        }
        rebuildDisplayLines();
        lastControllerState = controller.calculating() + "|" + controller.status();
        rebuilding = false;
    }

    private void calculate() {
        ArcaneSettings next = readInputSettings();
        controller.updateSettings(next);
        result = null;
        resultScroll = 0;
        rebuildDisplayLines();
        if (client != null) controller.calculate(client, completed -> {
            result = completed;
            resultScroll = 0;
            rebuildDisplayLines();
            clearAndInit();
        });
        clearAndInit();
    }

    private ArcaneSettings readInputSettings() {
        ArcaneSettings current = controller.settings();
        return current.withInputs(
            parseClamped(quantityDraft, 1, 9999),
            current.includeMainMaterial(),
            parseClamped(thresholdDraft, 1, 999)
        );
    }

    private void persistInputs() {
        controller.updateSettings(readInputSettings());
    }

    private void invalidateResult() {
        if (rebuilding) return;
        controller.invalidate();
        lastControllerState = controller.calculating() + "|" + controller.status();
        result = null;
        resultScroll = 0;
        rebuildDisplayLines();
    }

    @Override
    public void tick() {
        String state = controller.calculating() + "|" + controller.status();
        if (!state.equals(lastControllerState)) clearAndInit();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, UiColors.BACKGROUND);
        context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(title.getString(), Math.max(80, width - 16)), MARGIN, 8, UiColors.PRIMARY);
        context.drawTextWithShadow(textRenderer, "卷轴", searchField.getX() + 2, searchField.getY() - 9, UiColors.MUTED);
        context.drawTextWithShadow(textRenderer, "数量", quantityField.getX() + 2, quantityField.getY() - 9, UiColors.MUTED);
        context.drawTextWithShadow(textRenderer, "阈值", thresholdField.getX() + 2, thresholdField.getY() - 9, UiColors.MUTED);
        renderRecipeList(context);
        renderResults(context);
        super.render(context, mouseX, mouseY, delta);
    }

    private void renderRecipeList(DrawContext context) {
        context.fill(recipeX, recipeY, recipeX + recipeWidth, recipeY + recipeHeight, UiColors.PANEL);
        List<ScrollRecipe> filtered = filteredRecipes();
        int visibleRows = Math.max(1, recipeHeight / ROW_HEIGHT);
        recipeScroll = clamp(recipeScroll, 0, Math.max(0, filtered.size() - visibleRows));
        int end = Math.min(filtered.size(), recipeScroll + visibleRows);
        for (int index = recipeScroll; index < end; index++) {
            ScrollRecipe recipe = filtered.get(index);
            int y = recipeY + (index - recipeScroll) * ROW_HEIGHT;
            boolean selected = recipe.name().equals(controller.settings().selectedRecipe());
            if (selected) context.fill(recipeX + 1, y + 1, recipeX + recipeWidth - 1, y + ROW_HEIGHT - 1, UiColors.PANEL_ALT);
            context.drawTextWithShadow(
                textRenderer, textRenderer.trimToWidth(recipe.name(), recipeWidth - 8), recipeX + 4, y + 3,
                selected ? UiColors.ACCENT : UiColors.PRIMARY
            );
            context.drawTextWithShadow(
                textRenderer, textRenderer.trimToWidth(formatAmounts(recipe.required()), recipeWidth - 8),
                recipeX + 4, y + 14, UiColors.MUTED
            );
        }
        if (filtered.isEmpty()) context.drawCenteredTextWithShadow(textRenderer, "没有匹配卷轴", recipeX + recipeWidth / 2, recipeY + 8, UiColors.MUTED);
    }

    private void renderResults(DrawContext context) {
        context.fill(resultX, resultY, resultX + resultWidth, resultY + resultHeight, UiColors.PANEL);
        int naturalVisible = Math.max(1, (resultHeight - 6) / LINE_HEIGHT);
        boolean showPosition = displayLines.size() > naturalVisible && resultHeight >= LINE_HEIGHT * 2 + 10;
        int visible = Math.max(1, (resultHeight - 6 - (showPosition ? LINE_HEIGHT + 2 : 0)) / LINE_HEIGHT);
        resultScroll = clamp(resultScroll, 0, Math.max(0, displayLines.size() - visible));
        int end = Math.min(displayLines.size(), resultScroll + visible);
        int y = resultY + 4;
        for (int index = resultScroll; index < end; index++) {
            DisplayLine line = displayLines.get(index);
            context.drawTextWithShadow(textRenderer, line.text(), resultX + 5, y, line.color());
            y += LINE_HEIGHT;
        }
        if (showPosition) {
            String position = (resultScroll + 1) + "-" + end + " / " + displayLines.size();
            int footerY = resultY + resultHeight - LINE_HEIGHT - 3;
            context.fill(resultX + 1, footerY - 2, resultX + resultWidth - 1, resultY + resultHeight - 1, UiColors.PANEL);
            context.drawTextWithShadow(textRenderer, position,
                resultX + resultWidth - textRenderer.getWidth(position) - 4, footerY, UiColors.MUTED);
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && inside(mouseX, mouseY, recipeX, recipeY, recipeWidth, recipeHeight)) {
            List<ScrollRecipe> filtered = filteredRecipes();
            int row = (int) ((mouseY - recipeY) / ROW_HEIGHT);
            int index = recipeScroll + row;
            if (index >= 0 && index < filtered.size()) selectRecipe(filtered.get(index));
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int direction = verticalAmount > 0 ? -2 : verticalAmount < 0 ? 2 : 0;
        if (direction == 0) return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        if (inside(mouseX, mouseY, recipeX, recipeY, recipeWidth, recipeHeight)) {
            recipeScroll = clamp(recipeScroll + direction, 0, Math.max(0, filteredRecipes().size() - 1));
        } else {
            resultScroll = clamp(resultScroll + direction * 2, 0, Math.max(0, displayLines.size() - 1));
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int keyCode = input.key();
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (!controller.calculating()) calculate();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_UP || keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
            int direction = keyCode == GLFW.GLFW_KEY_PAGE_UP ? -1 : 1;
            resultScroll = clamp(resultScroll + direction * Math.max(1, resultHeight / LINE_HEIGHT), 0, Math.max(0, displayLines.size() - 1));
            return true;
        }
        if ((keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN) && getFocused() == searchField) {
            List<ScrollRecipe> filtered = filteredRecipes();
            if (!filtered.isEmpty()) {
                selectedVisibleIndex = clamp(selectedVisibleIndex + (keyCode == GLFW.GLFW_KEY_UP ? -1 : 1), 0, filtered.size() - 1);
                selectRecipe(filtered.get(selectedVisibleIndex));
            }
            return true;
        }
        return super.keyPressed(input);
    }

    private void selectRecipe(ScrollRecipe recipe) {
        ArcaneSettings next = controller.settings().withSelectedRecipe(recipe.name());
        controller.updateSettings(next);
        invalidateResult();
        rebuildDisplayLines();
    }

    private List<ScrollRecipe> filteredRecipes() {
        String normalized = query.strip().toLowerCase(java.util.Locale.ROOT);
        if (normalized.isEmpty()) return controller.data().recipes();
        return controller.data().recipes().stream()
            .filter(recipe -> recipe.name().toLowerCase(java.util.Locale.ROOT).contains(normalized))
            .toList();
    }

    private void rebuildDisplayLines() {
        if (textRenderer == null || resultWidth <= 0) return;
        List<DisplayLine> lines = new ArrayList<>();
        ArcaneSettings settings = controller.settings();
        ScrollRecipe selected = controller.data().recipe(settings.selectedRecipe());
        addWrapped(lines, selected.name() + " · 主材料：" + selected.mainMaterial() + " x1", UiColors.ACCENT);
        addWrapped(lines, "目标元素：" + formatAmounts(selected.required()) + " · 总杂质 < 8", UiColors.SECONDARY);
        addWrapped(lines, "状态：" + controller.status(), controller.calculating() ? UiColors.WARNING : UiColors.MUTED);
        if (result == null) {
            addWrapped(lines, "设置数量与材料后点击“计算”。输入变化会取消旧结果。", UiColors.MUTED);
            displayLines = List.copyOf(lines);
            return;
        }
        if (result.plans().isEmpty()) {
            addWrapped(lines, "当前启用材料内没有满足目标且总杂质小于 8 的方案。", UiColors.ERROR);
            displayLines = List.copyOf(lines);
            return;
        }
        addWrapped(lines, "求解耗时：" + String.format(java.util.Locale.ROOT, "%.2f ms", result.elapsedNanos() / 1_000_000.0), UiColors.MUTED);
        for (int index = 0; index < result.plans().size(); index++) {
            CraftPlan plan = result.plans().get(index);
            addWrapped(lines, "方案 " + (index + 1) + " · " + plan.materialTotal() + " 件 · 杂质 " + plan.impurityTotal()
                + " · 溢出 " + plan.targetExcessTotal() + " · 单材最高 " + plan.maxRepeat(), UiColors.ACCENT);
            addWrapped(lines, "单个辅料：" + formatMaterials(plan.materials()), UiColors.PRIMARY);
            addWrapped(lines, "实际供给：" + formatAmounts(plan.supplied()), UiColors.SECONDARY);
        }
        List<RotationBatch> rotation = ArcaneSolver.makeRotationSchedule(
            result.plans(), result.quantity(), result.repeatThreshold()
        );
        addWrapped(lines, "轮换建议（每批最多 " + result.repeatThreshold() + " 次，本地保守输入上限 "
            + ArcaneSolver.MAX_BATCH_INPUTS + " 个）", UiColors.ACCENT);
        addWrapped(lines, "按轮换批次合计：" + formatMaterials(ArcaneSolver.aggregateRotationMaterials(
            rotation, result.includeMainMaterial(), result.recipe().mainMaterial()
        )), UiColors.PRIMARY);
        if (!result.includeMainMaterial()) {
            addWrapped(lines, "主材料另需：" + result.recipe().mainMaterial() + " x" + result.quantity(), UiColors.WARNING);
        }
        for (int index = 0; index < rotation.size(); index++) {
            RotationBatch batch = rotation.get(index);
            int planIndex = result.plans().indexOf(batch.plan()) + 1;
            addWrapped(lines, "第 " + (index + 1) + " 批：方案 " + planIndex + " · " + batch.crafts()
                + " 次 · 输入 " + ArcaneSolver.batchInputCount(batch) + "/" + ArcaneSolver.MAX_BATCH_INPUTS
                + " · 材料：" + formatMaterials(ArcaneSolver.scaleBatchMaterials(
                    batch, result.includeMainMaterial(), result.recipe().mainMaterial()
                )), UiColors.SECONDARY);
            if (!ArcaneSolver.batchFitsInputLimit(batch)) {
                addWrapped(lines, "该方案单次制作本身超过本地保守输入上限，无法安全执行。", UiColors.ERROR);
            }
        }
        addWrapped(lines, "重置机制未知；轮换仅用于降低连续使用同一材料约 64 次后衰减的风险。", UiColors.WARNING);
        displayLines = List.copyOf(lines);
    }

    private void addWrapped(List<DisplayLine> lines, String value, int color) {
        int maxWidth = Math.max(80, resultWidth - 12);
        for (OrderedText text : textRenderer.wrapLines(Text.literal(value), maxWidth)) lines.add(new DisplayLine(text, color));
        lines.add(new DisplayLine(Text.literal(" ").asOrderedText(), color));
    }

    private String formatMaterials(Map<String, Integer> materials) {
        return materials.entrySet().stream()
            .filter(entry -> entry.getValue() > 0)
            .sorted(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue).reversed()
                .thenComparing(Map.Entry::getKey, controller.data()::compareNames))
            .map(entry -> entry.getKey() + " x" + entry.getValue())
            .reduce((left, right) -> left + "、" + right)
            .orElse("无");
    }

    private static String formatAmounts(ElementAmounts amounts) {
        StringBuilder value = new StringBuilder();
        for (Element element : Element.values()) {
            int count = amounts.get(element);
            if (count <= 0) continue;
            if (!value.isEmpty()) value.append(" / ");
            value.append(element.label()).append(count);
        }
        return value.isEmpty() ? "无" : value.toString();
    }

    private static int parseClamped(String value, int minimum, int maximum) {
        try {
            return clamp(Integer.parseInt(value), minimum, maximum);
        } catch (NumberFormatException ignored) {
            return minimum;
        }
    }

    private static boolean inside(double x, double y, int left, int top, int areaWidth, int areaHeight) {
        return x >= left && x < left + areaWidth && y >= top && y < top + areaHeight;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }

    @Override
    public void close() {
        persistInputs();
        controller.invalidate();
        if (client != null && parent != null) {
            client.setScreen(parent);
        } else {
            super.close();
        }
    }

    private record DisplayLine(OrderedText text, int color) {
    }
}
