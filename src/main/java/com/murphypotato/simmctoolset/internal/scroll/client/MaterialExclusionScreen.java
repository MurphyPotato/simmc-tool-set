package com.murphypotato.simmctoolset.internal.scroll.client;

import com.murphypotato.simmctoolset.internal.scroll.config.ArcaneSettings;
import com.murphypotato.simmctoolset.internal.scroll.domain.Element;
import com.murphypotato.simmctoolset.internal.scroll.domain.Material;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class MaterialExclusionScreen extends Screen {
    private static final int MARGIN = 10;
    private static final int ROW_HEIGHT = 22;

    private final ArcaneController controller;
    private final Screen previous;
    private String query = "";
    private TextFieldWidget search;
    private int listTop;
    private int listBottom;
    private int scroll;
    private int focusedRow;

    public MaterialExclusionScreen(ArcaneController controller, Screen previous) {
        super(Text.literal("材料排除"));
        this.controller = controller;
        this.previous = previous;
    }

    @Override
    protected void init() {
        clearChildren();
        int buttonY = height - 26;
        int available = width - MARGIN * 2;
        int buttonWidth = Math.max(52, (available - 8) / 3);
        addDrawableChild(ButtonWidget.builder(Text.literal("全部启用"), button -> updateExcluded(Set.of()))
            .dimensions(MARGIN, buttonY, buttonWidth, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("排除当前列表"), button -> {
            Set<String> next = new LinkedHashSet<>(controller.settings().excludedMaterials());
            filteredMaterials().forEach(material -> next.add(material.name()));
            updateExcluded(next);
        }).dimensions(MARGIN + buttonWidth + 4, buttonY, buttonWidth, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("返回"), button -> close())
            .dimensions(MARGIN + (buttonWidth + 4) * 2, buttonY, buttonWidth, 20).build());

        search = new TextFieldWidget(textRenderer, MARGIN, 28, available, 20, Text.literal("材料搜索"));
        search.setMaxLength(80);
        search.setPlaceholder(Text.literal("输入材料名称"));
        search.setText(query);
        search.setChangedListener(value -> {
            query = value;
            scroll = 0;
            focusedRow = 0;
        });
        addDrawableChild(search);
        listTop = 52;
        listBottom = buttonY - 4;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, UiColors.BACKGROUND);
        int excluded = controller.settings().excludedMaterials().size();
        context.drawTextWithShadow(textRenderer, title, MARGIN, 8, UiColors.PRIMARY);
        String summary = "已排除 " + excluded + " / " + controller.data().materials().size();
        context.drawTextWithShadow(textRenderer, summary, width - MARGIN - textRenderer.getWidth(summary), 8, UiColors.MUTED);
        context.fill(MARGIN, listTop, width - MARGIN, listBottom, UiColors.PANEL);
        List<Material> filtered = filteredMaterials();
        int visibleRows = Math.max(1, (listBottom - listTop) / ROW_HEIGHT);
        scroll = clamp(scroll, 0, Math.max(0, filtered.size() - visibleRows));
        focusedRow = clamp(focusedRow, 0, Math.max(0, filtered.size() - 1));
        int end = Math.min(filtered.size(), scroll + visibleRows);
        for (int index = scroll; index < end; index++) {
            Material material = filtered.get(index);
            int y = listTop + (index - scroll) * ROW_HEIGHT;
            boolean excludedMaterial = controller.settings().excludedMaterials().contains(material.name());
            if (index == focusedRow) context.fill(MARGIN + 1, y + 1, width - MARGIN - 1, y + ROW_HEIGHT - 1, UiColors.PANEL_ALT);
            String marker = excludedMaterial ? "[ ] " : "[x] ";
            context.drawTextWithShadow(textRenderer, marker + material.name(), MARGIN + 5, y + 6,
                excludedMaterial ? UiColors.MUTED : UiColors.PRIMARY);
            String elements = formatElements(material);
            context.drawTextWithShadow(textRenderer, elements, width - MARGIN - 5 - textRenderer.getWidth(elements), y + 6, UiColors.MUTED);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && mouseX >= MARGIN && mouseX < width - MARGIN && mouseY >= listTop && mouseY < listBottom) {
            int index = scroll + (int) ((mouseY - listTop) / ROW_HEIGHT);
            List<Material> filtered = filteredMaterials();
            if (index < filtered.size()) {
                focusedRow = index;
                toggle(filtered.get(index).name());
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int direction = verticalAmount > 0 ? -3 : verticalAmount < 0 ? 3 : 0;
        if (direction == 0) return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        scroll = clamp(scroll + direction, 0, Math.max(0, filteredMaterials().size() - 1));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        List<Material> filtered = filteredMaterials();
        if ((keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN) && !filtered.isEmpty()) {
            focusedRow = clamp(focusedRow + (keyCode == GLFW.GLFW_KEY_UP ? -1 : 1), 0, filtered.size() - 1);
            int visible = Math.max(1, (listBottom - listTop) / ROW_HEIGHT);
            if (focusedRow < scroll) scroll = focusedRow;
            if (focusedRow >= scroll + visible) scroll = focusedRow - visible + 1;
            return true;
        }
        if ((keyCode == GLFW.GLFW_KEY_SPACE || keyCode == GLFW.GLFW_KEY_ENTER) && !filtered.isEmpty() && getFocused() != search) {
            toggle(filtered.get(focusedRow).name());
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void toggle(String name) {
        Set<String> next = new LinkedHashSet<>(controller.settings().excludedMaterials());
        if (!next.remove(name)) next.add(name);
        updateExcluded(next);
    }

    private void updateExcluded(Set<String> names) {
        ArcaneSettings next = controller.settings().withExcludedMaterials(names);
        controller.invalidate();
        controller.updateSettings(next);
    }

    private List<Material> filteredMaterials() {
        String normalized = query.strip().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) return controller.data().materials();
        return controller.data().materials().stream()
            .filter(material -> material.name().toLowerCase(Locale.ROOT).contains(normalized))
            .toList();
    }

    private static String formatElements(Material material) {
        StringBuilder value = new StringBuilder();
        for (Element element : Element.values()) {
            int count = material.elements().get(element);
            if (count <= 0) continue;
            if (!value.isEmpty()) value.append('/');
            value.append(element.label()).append(count);
        }
        return value.toString();
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(previous);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }
}
