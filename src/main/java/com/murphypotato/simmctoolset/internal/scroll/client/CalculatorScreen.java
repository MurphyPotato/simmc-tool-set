package com.murphypotato.simmctoolset.internal.scroll.client;

import com.murphypotato.simmctoolset.internal.scroll.config.ArcaneSettings;
import com.murphypotato.simmctoolset.internal.scroll.domain.ArcaneSolver;
import com.murphypotato.simmctoolset.internal.scroll.domain.CraftPlan;
import com.murphypotato.simmctoolset.internal.scroll.domain.Element;
import com.murphypotato.simmctoolset.internal.scroll.domain.ElementAmounts;
import com.murphypotato.simmctoolset.internal.scroll.domain.RotationBatch;
import com.murphypotato.simmctoolset.internal.scroll.domain.PlanEditor;
import com.murphypotato.simmctoolset.internal.scroll.domain.PresetPlan;
import com.murphypotato.simmctoolset.internal.scroll.domain.ScrollRecipe;
import com.murphypotato.simmctoolset.internal.scroll.domain.SearchBudget;
import com.murphypotato.simmctoolset.internal.scroll.domain.EvaluatedPlan;
import com.murphypotato.simmctoolset.internal.scroll.domain.UsageCommitRequest;
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
import java.util.UUID;

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
    private TextFieldWidget presetNameField;
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
    private boolean confirmArmed;
    private UUID transactionId;
    private boolean manualMode;
    private List<RotationBatch> editedBatches = List.of();
    private List<RotationBatch> defaultBatches = List.of();
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
        toolbar.add(ButtonWidget.builder(Text.literal("速度：" + budgetLabel(settings.searchBudget())), button -> {
            SearchBudget[] values = SearchBudget.values();
            SearchBudget next = values[(settings.searchBudget().ordinal() + 1) % values.length];
            controller.updateSettings(controller.settings().withSearchBudget(next));
            invalidateResult();
            clearAndInit();
        }).build());
        toolbar.add(ButtonWidget.builder(Text.literal(manualMode ? "模式：手动" : "模式：自动"), button -> {
            manualMode = !manualMode;
            if (!manualMode) editedBatches = defaultBatches;
            rebuildDisplayLines();
            clearAndInit();
        }).build());
        ButtonWidget resetPlanButton = ButtonWidget.builder(Text.literal("重置方案"), button -> {
            if (manualMode && !defaultBatches.isEmpty()) {
                editedBatches = defaultBatches;
                rebuildDisplayLines();
                clearAndInit();
            }
        }).build();
        resetPlanButton.active = manualMode && !defaultBatches.isEmpty();
        toolbar.add(resetPlanButton);
        toolbar.add(ButtonWidget.builder(Text.literal("使用记录"), button -> {
            persistInputs();
            if (client != null) client.setScreen(new ScrollUsageScreen(controller, this));
        }).build());
        toolbar.add(ButtonWidget.builder(Text.literal("确认使用"), button -> {
            if (result == null || result.planning() == null || !result.planning().plan().feasible()) return;
            if (!confirmArmed) {
                confirmArmed = true;
                controller.invalidate();
                rebuildDisplayLines();
                return;
            }
            controller.playerId(client).ifPresentOrElse(player -> {
                try {
                    if (transactionId == null) transactionId = UUID.randomUUID();
                    EvaluatedPlan evaluated = currentEvaluatedPlan(player);
                    if (evaluated == null || !evaluated.feasible()
                        || evaluated.plannedCrafts() != evaluated.desiredCrafts()) {
                        confirmArmed = false;
                        rebuildDisplayLines();
                        return;
                    }
                    UsageCommitRequest request = controller.commitRequest(player, result, evaluated,
                        !manualMode, manualMode, transactionId);
                    controller.commitUsage(request);
                    transactionId = null;
                    confirmArmed = false;
                    result = null;
                    rebuildDisplayLines();
                } catch (Exception error) {
                    controller.invalidate();
                }
            }, () -> controller.invalidate());
        }).build());
        toolbar.add(ButtonWidget.builder(Text.literal("使用说明"), button -> {
            if (client != null) client.setScreen(new HelpScreen(this));
        }).build());
        toolbar.add(ButtonWidget.builder(Text.literal("关于 / 隐私"), button -> {
            if (client != null) client.setScreen(new AboutScreen(this));
        }).build());
        int toolbarRows = (toolbar.size() + columns - 1) / columns;
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

        presetNameField = new TextFieldWidget(textRenderer, MARGIN, fieldsY + 23, 132, 20, Text.literal("预设名称"));
        presetNameField.setMaxLength(20);
        presetNameField.setPlaceholder(Text.literal("预设名称（≤20字）"));
        addDrawableChild(presetNameField);
        addDrawableChild(ButtonWidget.builder(Text.literal("保存当前预设"), button -> savePreset())
            .dimensions(MARGIN + 136, fieldsY + 23, 100, 20).build());

        int contentTop = fieldsY + 49;
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
        if (manualMode && !editedBatches.isEmpty()) {
            int y = resultY + 4;
            for (int i = 0; i < editedBatches.size() && y < resultY + resultHeight - 20; i++, y += 22) {
                final int index = i;
                ButtonWidget remove = ButtonWidget.builder(Text.literal("删"), b -> removeBatch(index))
                    .dimensions(resultX + Math.max(0, resultWidth - 66), y - 3, 20, 18).build();
                ButtonWidget decrement = ButtonWidget.builder(Text.literal("−"), b -> editBatch(index, -1))
                    .dimensions(resultX + Math.max(0, resultWidth - 44), y - 3, 20, 18).build();
                ButtonWidget increment = ButtonWidget.builder(Text.literal("+"), b -> editBatch(index, 1))
                    .dimensions(resultX + Math.max(0, resultWidth - 22), y - 3, 20, 18).build();
                remove.active = editedBatches.size() > 1;
                decrement.active = editedBatches.get(index).crafts() > 1;
                addDrawableChild(remove);
                addDrawableChild(decrement);
                addDrawableChild(increment);
            }
        }
        rebuildDisplayLines();
        lastControllerState = controller.calculating() + "|" + controller.status();
        rebuilding = false;
    }

    private static String budgetLabel(SearchBudget budget) {
        return switch (budget) {
            case FAST -> "快速";
            case BALANCED -> "均衡";
            case EXTREME -> "极致";
        };
    }

    private void calculate() {
        ArcaneSettings next = readInputSettings();
        controller.updateSettings(next);
        result = null;
        defaultBatches = List.of();
        editedBatches = List.of();
        resultScroll = 0;
        rebuildDisplayLines();
        if (client != null) controller.calculate(client, completed -> {
            result = completed;
            controller.playerId(client).ifPresent(player -> {
                Map<String,Integer> preview = new LinkedHashMap<>();
                completed.planning().plan().batches().forEach(batch ->
                    batch.plan().materials().forEach((name, amount) ->
                        preview.merge(name, amount * batch.crafts(), Integer::sum)));
                controller.setTemporaryUsage(player, preview);
            });
            resultScroll = 0;
            rebuildDisplayLines();
            clearAndInit();
        });
        clearAndInit();
    }

    private void savePreset() {
        if (result == null || result.planning() == null || presetNameField == null) return;
        String name = presetNameField.getText().strip();
        if (name.isEmpty()) return;
        try {
            List<RotationBatch> batches = editedBatches.isEmpty()
                ? result.planning().plan().batches().stream()
                    .map(batch -> new RotationBatch(batch.plan(), batch.crafts())).toList()
                : editedBatches;
            controller.savePreset(new PresetPlan(name, result.recipe().name(), batches));
            rebuildDisplayLines();
        } catch (RuntimeException error) {
            controller.invalidate();
        }
    }

    private void editBatch(int index, int delta) {
        if (!manualMode || editedBatches.isEmpty()) return;
        try {
            RotationBatch batch = editedBatches.get(index);
            int next = Math.max(1, Math.addExact(batch.crafts(), delta));
            editedBatches = PlanEditor.withCrafts(editedBatches, index, next);
            refreshTemporaryUsage();
            confirmArmed = false;
            rebuildDisplayLines();
            clearAndInit();
        } catch (RuntimeException ignored) {
            controller.invalidate();
        }
    }

    private void removeBatch(int index) {
        if (!manualMode || editedBatches.size() <= 1 || index < 0 || index >= editedBatches.size()) return;
        int removed = editedBatches.get(index).crafts();
        List<RotationBatch> kept = new ArrayList<>(editedBatches);
        kept.remove(index);
        int each = removed / kept.size();
        int remainder = removed % kept.size();
        for (int i = 0; i < kept.size(); i++) {
            RotationBatch b = kept.get(i);
            kept.set(i, new RotationBatch(b.plan(), b.crafts() + each + (i < remainder ? 1 : 0)));
        }
        editedBatches = List.copyOf(kept);
        refreshTemporaryUsage();
        confirmArmed = false;
        rebuildDisplayLines();
        clearAndInit();
    }

    private EvaluatedPlan currentEvaluatedPlan(UUID player) {
        if (result == null || result.planning() == null) return null;
        List<RotationBatch> batches = manualMode && !editedBatches.isEmpty()
            ? editedBatches : result.planning().plan().batches().stream()
                .map(batch -> new RotationBatch(batch.plan(), batch.crafts())).toList();
        return controller.evaluateBatches(player, result.recipe().name(), batches);
    }

    private void refreshTemporaryUsage() {
        if (client == null || result == null) return;
        controller.playerId(client).ifPresent(player -> {
            Map<String,Integer> preview = new LinkedHashMap<>();
            List<RotationBatch> batches = manualMode && !editedBatches.isEmpty()
                ? editedBatches : defaultBatches;
            batches.forEach(batch -> batch.plan().materials().forEach((name, amount) ->
                preview.merge(name, amount * batch.crafts(), Integer::sum)));
            controller.setTemporaryUsage(player, preview);
        });
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
        if (result.planning() != null && !result.planning().plan().batches().isEmpty()
            && defaultBatches.isEmpty()) {
            defaultBatches = result.planning().plan().batches().stream()
                .map(batch -> new RotationBatch(batch.plan(), batch.crafts())).toList();
            editedBatches = defaultBatches;
        }
        if (result.planning() == null || result.planning().plan().batches().isEmpty()) {
            addWrapped(lines, "当前启用材料内没有满足目标且总杂质小于 8 的方案。", UiColors.ERROR);
            displayLines = List.copyOf(lines);
            return;
        }
        addWrapped(lines, "求解耗时：" + String.format(java.util.Locale.ROOT, "%.2f ms", result.elapsedNanos() / 1_000_000.0), UiColors.MUTED);
        if (result.timedOut()) {
            addWrapped(lines, "已达到“" + budgetLabel(settings.searchBudget()) + "”搜索预算，以下为预算内当前最佳方案（不保证全局最优）。", UiColors.WARNING);
        }
        addWrapped(lines, "当前模式：" + (manualMode ? "手动（修改需重新评估）" : "自动（默认方案已冻结）"), UiColors.ACCENT);
        if (manualMode) addWrapped(lines, "手动修改可能增加衰减或使方案不可行；确认使用前请检查总制作数。", UiColors.WARNING);
        EvaluatedPlan evaluated = controller.playerId(client)
            .map(this::currentEvaluatedPlan).orElse(result.planning().plan());
        if (evaluated == null) evaluated = result.planning().plan();
        addWrapped(lines, "计划：" + evaluated.plannedCrafts() + "/" + evaluated.desiredCrafts()
            + " · M " + maxUsage(evaluated.beforeUsage()) + " → " + maxUsage(evaluated.afterUsage())
            + " · 杂质 " + evaluated.impurity() + " · 溢出 " + evaluated.excess(), evaluated.feasible() ? UiColors.ACCENT : UiColors.WARNING);
        addWrapped(lines, "理论元素：" + formatAmounts(evaluated.theoreticalElements())
            + " · 衰减后元素：" + formatEffectiveAmounts(evaluated.effectiveElements()), UiColors.SECONDARY);
        if (evaluated.hasExtraMaterials()) {
            addWrapped(lines, "预计额外材料：" + formatMaterials(evaluated.extraMaterials()), UiColors.WARNING);
        }
        if (evaluated.plannedCrafts() != evaluated.desiredCrafts()) {
            addWrapped(lines, "总制作数不匹配：当前 " + evaluated.plannedCrafts()
                + "，目标 " + evaluated.desiredCrafts() + "；不能确认使用。", UiColors.ERROR);
        }
        for (int index = 0; index < evaluated.batches().size(); index++) {
            var batch = evaluated.batches().get(index);
            addWrapped(lines, "第 " + (index + 1) + " 批：" + batch.crafts() + " 次 · "
                + formatMaterials(batch.plan().materials()) + " · " + (batch.feasible() ? "可行" : "不可行"), UiColors.PRIMARY);
        }
        if (!result.includeMainMaterial()) addWrapped(lines, "主材料仅影响显示；实际 320 输入上限仍包含主材料。", UiColors.WARNING);
        if (confirmArmed) addWrapped(lines, "再次点击“确认使用”以记录实际消耗（事务可安全重试）。", UiColors.ERROR);
        addWrapped(lines, "验证使用未取整的衰减值；界面显示“约”值可能仍低于目标。", UiColors.MUTED);
        displayLines = List.copyOf(lines);
    }

    private static int maxUsage(Map<String, Integer> usage) {
        return usage.values().stream().mapToInt(Integer::intValue).max().orElse(0);
    }

    private static String formatEffectiveAmounts(Map<Element, java.math.BigDecimal> values) {
        List<String> parts = new ArrayList<>();
        for (Element element : Element.values()) {
            java.math.BigDecimal value = values.getOrDefault(element, java.math.BigDecimal.ZERO);
            if (value.signum() != 0) {
                parts.add(element.name() + "≈" + value.setScale(0, java.math.RoundingMode.HALF_UP).toPlainString());
            }
        }
        return parts.isEmpty() ? "无" : String.join("、", parts);
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
