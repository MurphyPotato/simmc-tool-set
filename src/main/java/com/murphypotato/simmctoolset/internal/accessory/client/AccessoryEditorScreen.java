package com.murphypotato.simmctoolset.internal.accessory.client;

import com.murphypotato.simmctoolset.internal.accessory.domain.AccessoryQuality;
import com.murphypotato.simmctoolset.internal.accessory.domain.AccessoryRecord;
import com.murphypotato.simmctoolset.internal.accessory.domain.AccessorySlot;
import com.murphypotato.simmctoolset.internal.accessory.domain.AffixRecord;
import com.murphypotato.simmctoolset.internal.accessory.domain.AffixStat;
import com.murphypotato.simmctoolset.internal.accessory.domain.AffixUnit;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class AccessoryEditorScreen extends Screen {
    private static final int DROPDOWN_ROW_HEIGHT = 18;
    private static final int DROPDOWN_VISIBLE_ROWS = 7;
    private static final List<DropdownEntry> DROPDOWN_ENTRIES = dropdownEntries();

    private final ClientAccessoryController controller;
    private final ToolSession session;
    private final String targetId;
    private final boolean reviewMode;
    private final AccessoryRecord original;
    private final List<AffixDraft> affixes = new ArrayList<>();
    private final String reviewReasons;

    private AccessorySlot slot;
    private AccessoryQuality quality;
    private TextFieldWidget nameField;
    private TextFieldWidget levelField;
    private String draftName;
    private String draftLevel;
    private int affixPage;
    private int affixPageSize;
    private int affixRowsTop;
    private String errorMessage = "";
    private DropdownState dropdown;

    public AccessoryEditorScreen(ClientAccessoryController controller, ToolSession session, String targetId, boolean reviewMode) {
        super(Text.literal(reviewMode ? "复核饰品" : "编辑饰品"));
        this.controller = controller;
        this.session = session;
        this.targetId = targetId;
        this.reviewMode = reviewMode;
        ReviewEntry review = reviewMode ? controller.findReview(targetId).orElseThrow() : null;
        this.original = reviewMode ? review.result().accessory() : controller.findAccessory(targetId).orElseThrow();
        this.reviewReasons = reviewMode ? String.join("；", review.result().reviewMessages()) : "";
        this.slot = original.slot();
        this.quality = original.quality();
        this.draftName = original.name();
        this.draftLevel = Integer.toString(original.level());
        for (AffixRecord affix : original.affixes()) affixes.add(new AffixDraft(affix));
    }

    @Override
    protected void init() {
        clearChildren();
        dropdown = null;
        int margin = 10;
        int usableWidth = width - margin * 2;

        nameField = new TextFieldWidget(textRenderer, margin + 42, 24, Math.max(90, usableWidth - 170), 20, Text.literal("饰品名称"));
        nameField.setMaxLength(200);
        nameField.setText(draftName);
        nameField.setChangedListener(value -> draftName = value);
        addDrawableChild(nameField);

        levelField = new TextFieldWidget(textRenderer, width - margin - 55, 24, 55, 20, Text.literal("强化等级"));
        levelField.setMaxLength(3);
        levelField.setText(draftLevel);
        levelField.setTextPredicate(text -> text.isEmpty() || text.matches("[0-9]{1,3}"));
        levelField.setChangedListener(value -> draftLevel = value);
        addDrawableChild(levelField);

        int half = (usableWidth - 4) / 2;
        addDrawableChild(ButtonWidget.builder(Text.literal("品质：" + quality.label()), button -> {
            quality = next(quality);
            button.setMessage(Text.literal("品质：" + quality.label()));
        }).dimensions(margin, 49, half, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("部位：" + slot.label()), button -> {
            slot = next(slot);
            button.setMessage(Text.literal("部位：" + slot.label()));
        }).dimensions(margin + half + 4, 49, half, 20).build());

        affixRowsTop = 91;
        affixPageSize = Math.max(1, (height - affixRowsTop - 38) / 28);
        int pageCount = Math.max(1, (affixes.size() + affixPageSize - 1) / affixPageSize);
        affixPage = Math.min(affixPage, pageCount - 1);
        int start = affixPage * affixPageSize;
        int end = Math.min(affixes.size(), start + affixPageSize);
        for (int index = start; index < end; index++) addAffixRow(index, affixes.get(index));

        int bottomY = height - 25;
        addDrawableChild(ButtonWidget.builder(Text.literal("+ 词条"), button -> {
            affixes.add(AffixDraft.empty());
            affixPage = (affixes.size() - 1) / affixPageSize;
            clearAndInit();
        }).dimensions(margin, bottomY, 64, 20).build());

        int pageButtonWidth = 34;
        if (pageCount > 1) {
            ButtonWidget previous = ButtonWidget.builder(Text.literal("<"), button -> {
                affixPage--;
                clearAndInit();
            }).dimensions(margin + 69, bottomY, pageButtonWidth, 20).build();
            previous.active = affixPage > 0;
            addDrawableChild(previous);
            ButtonWidget next = ButtonWidget.builder(Text.literal(">"), button -> {
                affixPage++;
                clearAndInit();
            }).dimensions(margin + 107, bottomY, pageButtonWidth, 20).build();
            next.active = affixPage + 1 < pageCount;
            addDrawableChild(next);
        }

        addDrawableChild(ButtonWidget.builder(Text.literal("取消"), button -> close())
            .dimensions(width - margin - 154, bottomY, 72, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(reviewMode ? "确认入库" : "保存修改"), button -> save())
            .dimensions(width - margin - 78, bottomY, 78, 20).build());
    }

    private void addAffixRow(int index, AffixDraft draft) {
        int margin = 10;
        int y = affixRowsTop + (index - affixPage * affixPageSize) * 28;
        int usable = width - margin * 2;
        int statWidth = Math.min(142, Math.max(96, usable * 30 / 100));
        int valueWidth = Math.min(74, Math.max(54, usable * 16 / 100));
        int deleteWidth = 36;
        int labelWidth = usable - statWidth - valueWidth - deleteWidth - 12;

        TextFieldWidget label = new TextFieldWidget(textRenderer, margin + statWidth + 4, y, labelWidth, 20, Text.literal("词条名称"));
        label.setMaxLength(100);
        label.setText(draft.label);
        label.setChangedListener(value -> draft.label = value);
        label.setEditableColor(draft.stat.combat() ? UiColors.PRIMARY : UiColors.DIM);
        label.setUneditableColor(UiColors.DIM);
        addDrawableChild(label);

        TextFieldWidget value = new TextFieldWidget(textRenderer, margin + statWidth + labelWidth + 8, y, valueWidth, 20, Text.literal("词条值"));
        value.setMaxLength(20);
        value.setText(draft.valueText);
        value.setTextPredicate(text -> text.isEmpty() || text.matches("\\+?[0-9]*(?:\\.[0-9]*)?%?"));
        value.setChangedListener(text -> draft.valueText = text);
        value.setEditableColor(draft.stat.combat() ? UiColors.PRIMARY : UiColors.DIM);
        value.setUneditableColor(UiColors.DIM);
        addDrawableChild(value);

        addDrawableChild(ButtonWidget.builder(Text.literal(draft.stat.label() + " ▼"), button ->
            openDropdown(index, margin, y, statWidth, draft.stat)
        ).dimensions(margin, y, statWidth, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("删"), button -> {
            affixes.remove(index);
            clearAndInit();
        }).dimensions(width - margin - deleteWidth, y, deleteWidth, 20).build());
    }

    private void save() {
        errorMessage = "";
        String name = draftName.strip();
        if (name.isEmpty()) {
            errorMessage = "饰品名称不能为空";
            return;
        }

        int level;
        try {
            level = Integer.parseInt(draftLevel);
        } catch (NumberFormatException error) {
            errorMessage = "强化等级必须是整数";
            return;
        }
        if (level < 0 || level > quality.maxLevel()) {
            errorMessage = "该品质强化等级必须在 0-" + quality.maxLevel() + " 之间";
            return;
        }

        List<AffixRecord> parsedAffixes = new ArrayList<>();
        for (int index = 0; index < affixes.size(); index++) {
            AffixDraft draft = affixes.get(index);
            String token = draft.valueText.startsWith("+") ? draft.valueText.substring(1) : draft.valueText;
            AffixUnit unit = token.endsWith("%") ? AffixUnit.PERCENT : AffixUnit.NONE;
            if (unit == AffixUnit.PERCENT) token = token.substring(0, token.length() - 1);
            double value;
            try {
                value = Double.parseDouble(token);
            } catch (NumberFormatException error) {
                errorMessage = "第 " + (index + 1) + " 条词条数值无效";
                return;
            }
            String label = draft.label.strip();
            if (label.isEmpty()) label = draft.stat.label();
            parsedAffixes.add(new AffixRecord(draft.id, draft.stat, value, unit, label, draft.rawText, ""));
        }

        AccessoryRecord edited = original.withEditedFields(name, slot, quality, level, parsedAffixes);
        if (reviewMode) controller.confirmReview(targetId, edited);
        else controller.updateAccessory(edited);
        if (client != null) client.setScreen(new AccessoryToolScreen(controller, session));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Modded render chains may already have applied Minecraft's single-frame blur.
        context.fill(0, 0, width, height, UiColors.BACKGROUND);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 8, UiColors.PRIMARY);
        context.drawTextWithShadow(textRenderer, "名称", 10, 30, UiColors.SECONDARY);
        context.drawTextWithShadow(textRenderer, "+", width - 70, 30, UiColors.SECONDARY);
        context.drawTextWithShadow(textRenderer, "词条 " + affixes.size() + " / 当前规则 " + quality.totalAffixSlots(parseLevelOrZero()), 10, 78, UiColors.ACCENT);
        String source = controller.sourceLabel(original);
        context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(source, Math.max(80, width - 180)), 150, 78, UiColors.MUTED);
        String bottomMessage = !errorMessage.isEmpty() ? errorMessage : reviewReasons;
        if (!bottomMessage.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, textRenderer.trimToWidth(bottomMessage, width - 20), width / 2, height - 36, UiColors.ERROR);
        }
        super.render(context, mouseX, mouseY, delta);
        renderDropdown(context, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        if (dropdown != null) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && dropdown.contains(mouseX, mouseY)) {
                int row = (int) ((mouseY - dropdown.y - 1) / DROPDOWN_ROW_HEIGHT);
                int entryIndex = dropdown.scroll + row;
                if (row >= 0 && row < dropdown.visibleRows() && entryIndex < DROPDOWN_ENTRIES.size()) {
                    DropdownEntry entry = DROPDOWN_ENTRIES.get(entryIndex);
                    if (entry.stat != null) selectDropdown(entry.stat);
                }
                return true;
            }
            dropdown = null;
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (dropdown != null) {
            int direction = verticalAmount > 0 ? -1 : verticalAmount < 0 ? 1 : 0;
            dropdown.scroll = clamp(dropdown.scroll + direction, 0, dropdown.maxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int keyCode = input.key();
        if (dropdown != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                dropdown = null;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN) {
                moveDropdownFocus(keyCode == GLFW.GLFW_KEY_UP ? -1 : 1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_HOME || keyCode == GLFW.GLFW_KEY_END) {
                moveDropdownToEdge(keyCode == GLFW.GLFW_KEY_HOME);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER || keyCode == GLFW.GLFW_KEY_SPACE) {
                DropdownEntry entry = DROPDOWN_ENTRIES.get(dropdown.focusedEntry);
                if (entry.stat != null) selectDropdown(entry.stat);
                return true;
            }
        }
        return super.keyPressed(input);
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(new AccessoryToolScreen(controller, session));
    }

    private int parseLevelOrZero() {
        if (draftLevel == null || draftLevel.isEmpty()) return 0;
        try {
            return Integer.parseInt(draftLevel);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static AccessoryQuality next(AccessoryQuality current) {
        AccessoryQuality[] values = AccessoryQuality.values();
        return values[(current.ordinal() + 1) % values.length];
    }

    private static AccessorySlot next(AccessorySlot current) {
        AccessorySlot[] values = AccessorySlot.values();
        return values[(current.ordinal() + 1) % values.length];
    }

    private void openDropdown(int affixIndex, int x, int buttonY, int width, AffixStat current) {
        int focused = 0;
        for (int index = 0; index < DROPDOWN_ENTRIES.size(); index++) {
            if (DROPDOWN_ENTRIES.get(index).stat == current) {
                focused = index;
                break;
            }
        }
        int visibleRows = Math.min(DROPDOWN_VISIBLE_ROWS, DROPDOWN_ENTRIES.size());
        int height = visibleRows * DROPDOWN_ROW_HEIGHT + 2;
        int y = buttonY + 20;
        if (y + height > this.height - 30) y = Math.max(4, buttonY - height);
        int scroll = clamp(focused - 1, 0, Math.max(0, DROPDOWN_ENTRIES.size() - visibleRows));
        dropdown = new DropdownState(affixIndex, x, y, width, scroll, focused);
    }

    private void renderDropdown(DrawContext context, int mouseX, int mouseY) {
        if (dropdown == null) return;
        int panelHeight = dropdown.visibleRows() * DROPDOWN_ROW_HEIGHT + 2;
        context.fill(dropdown.x - 1, dropdown.y - 1, dropdown.x + dropdown.width + 1, dropdown.y + panelHeight, 0xFF05070A);
        context.fill(dropdown.x, dropdown.y, dropdown.x + dropdown.width, dropdown.y + panelHeight - 1, 0xFF202833);
        for (int row = 0; row < dropdown.visibleRows(); row++) {
            int entryIndex = dropdown.scroll + row;
            if (entryIndex >= DROPDOWN_ENTRIES.size()) break;
            DropdownEntry entry = DROPDOWN_ENTRIES.get(entryIndex);
            int y = dropdown.y + 1 + row * DROPDOWN_ROW_HEIGHT;
            boolean hovered = mouseX >= dropdown.x && mouseX < dropdown.x + dropdown.width
                && mouseY >= y && mouseY < y + DROPDOWN_ROW_HEIGHT;
            if (entry.stat == null) {
                context.fill(dropdown.x, y, dropdown.x + dropdown.width, y + DROPDOWN_ROW_HEIGHT, 0xFF151B23);
                context.drawTextWithShadow(textRenderer, entry.label, dropdown.x + 5, y + 5, UiColors.ACCENT);
            } else {
                if (hovered || entryIndex == dropdown.focusedEntry) {
                    context.fill(dropdown.x + 1, y, dropdown.x + dropdown.width - 1, y + DROPDOWN_ROW_HEIGHT, 0xFF394653);
                }
                int color = entry.stat.combat() ? UiColors.PRIMARY : UiColors.DIM;
                context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(entry.label, dropdown.width - 12), dropdown.x + 5, y + 5, color);
            }
        }
    }

    private void moveDropdownFocus(int direction) {
        int next = dropdown.focusedEntry;
        do {
            next += direction;
        } while (next >= 0 && next < DROPDOWN_ENTRIES.size() && DROPDOWN_ENTRIES.get(next).stat == null);
        if (next < 0 || next >= DROPDOWN_ENTRIES.size()) return;
        dropdown.focusedEntry = next;
        ensureDropdownFocusVisible();
    }

    private void moveDropdownToEdge(boolean first) {
        int index = first ? 0 : DROPDOWN_ENTRIES.size() - 1;
        int direction = first ? 1 : -1;
        while (DROPDOWN_ENTRIES.get(index).stat == null) index += direction;
        dropdown.focusedEntry = index;
        ensureDropdownFocusVisible();
    }

    private void ensureDropdownFocusVisible() {
        if (dropdown.focusedEntry < dropdown.scroll) dropdown.scroll = dropdown.focusedEntry;
        if (dropdown.focusedEntry >= dropdown.scroll + dropdown.visibleRows()) {
            dropdown.scroll = dropdown.focusedEntry - dropdown.visibleRows() + 1;
        }
        dropdown.scroll = clamp(dropdown.scroll, 0, dropdown.maxScroll());
    }

    private void selectDropdown(AffixStat selected) {
        AffixDraft draft = affixes.get(dropdown.affixIndex);
        draft.stat = selected;
        if (selected != AffixStat.OTHER) draft.label = selected.label();
        dropdown = null;
        clearAndInit();
    }

    private static List<DropdownEntry> dropdownEntries() {
        List<DropdownEntry> result = new ArrayList<>();
        result.add(new DropdownEntry("伤害相关", null));
        for (AffixStat stat : AffixStat.values()) {
            if (stat.combat()) result.add(new DropdownEntry(stat.label(), stat));
        }
        result.add(new DropdownEntry("非伤害", null));
        for (AffixStat stat : AffixStat.values()) {
            if (!stat.combat()) result.add(new DropdownEntry(stat.label(), stat));
        }
        return List.copyOf(result);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }

    private static String format(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private static final class AffixDraft {
        private final String id;
        private final String rawText;
        private AffixStat stat;
        private String label;
        private String valueText;

        private AffixDraft(AffixRecord source) {
            this.id = source.id();
            this.rawText = source.rawText();
            this.stat = source.stat();
            this.label = source.label();
            this.valueText = source.valueText();
        }

        private AffixDraft(String id, AffixStat stat, String label, String valueText, String rawText) {
            this.id = id;
            this.stat = stat;
            this.label = label;
            this.valueText = valueText;
            this.rawText = rawText;
        }

        private static AffixDraft empty() {
            return new AffixDraft(UUID.randomUUID().toString(), AffixStat.OTHER, "非伤害类词条", "+0", "");
        }
    }

    private record DropdownEntry(String label, AffixStat stat) {
    }

    private static final class DropdownState {
        private final int affixIndex;
        private final int x;
        private final int y;
        private final int width;
        private int scroll;
        private int focusedEntry;

        private DropdownState(int affixIndex, int x, int y, int width, int scroll, int focusedEntry) {
            this.affixIndex = affixIndex;
            this.x = x;
            this.y = y;
            this.width = width;
            this.scroll = scroll;
            this.focusedEntry = focusedEntry;
        }

        private int visibleRows() {
            return Math.min(DROPDOWN_VISIBLE_ROWS, DROPDOWN_ENTRIES.size());
        }

        private int maxScroll() {
            return Math.max(0, DROPDOWN_ENTRIES.size() - visibleRows());
        }

        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width
                && mouseY >= y && mouseY < y + visibleRows() * DROPDOWN_ROW_HEIGHT + 2;
        }
    }
}
