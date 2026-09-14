package com.murphypotato.simmctoolset.internal.scroll.client;

import com.murphypotato.simmctoolset.internal.scroll.domain.UsageRecord;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import java.util.ArrayList;
import java.util.List;

/** Usage history and explicitly acknowledged daily-M audit editor. */
public final class ScrollUsageScreen extends Screen {
    private final ArcaneController controller;
    private final Screen parent;
    private List<String> lines = List.of();
    private TextFieldWidget mField;
    private ButtonWidget acknowledge;
    private TextFieldWidget renameField;
    private String selectedPreset;
    private boolean acknowledged;
    private int scroll;

    public ScrollUsageScreen(ArcaneController controller, Screen parent) {
        super(Text.literal("卷轴使用记录"));
        this.controller = controller;
        this.parent = parent;
    }

    @Override protected void init() {
        clearChildren();
        var player = client == null ? java.util.Optional.<java.util.UUID>empty() : controller.playerId(client);
        if (player.isPresent()) {
            var snapshot = controller.usageStore().snapshot(player.get());
            mField = new TextFieldWidget(textRenderer, 16, 34, 90, 20, Text.literal("当前 M"));
            mField.setText(Integer.toString(snapshot.currentM()));
            mField.setTextPredicate(v -> v.isEmpty() || v.matches("[0-9]{1,9}"));
            addDrawableChild(mField);
            acknowledge = ButtonWidget.builder(Text.literal("我已确认风险"), b -> {
                acknowledged = !acknowledged;
                b.setMessage(Text.literal(acknowledged ? "已确认（再次点击取消）" : "我已确认风险"));
            }).dimensions(112, 34, 145, 20).build();
            addDrawableChild(acknowledge);
            addDrawableChild(ButtonWidget.builder(Text.literal("保存 M 编辑"), b -> editM(player.get())).dimensions(262, 34, 110, 20).build());
        }
        renameField = new TextFieldWidget(textRenderer, 16, 58, 150, 20, Text.literal("预设名称"));
        renameField.setMaxLength(20);
        renameField.setPlaceholder(Text.literal("预设名称（≤20字）"));
        addDrawableChild(renameField);
        addDrawableChild(ButtonWidget.builder(Text.literal("改名预设"), b -> {
            if (selectedPreset != null && renameField != null && !renameField.getText().isBlank()) {
                controller.renamePreset(selectedPreset, renameField.getText().strip());
                selectedPreset = renameField.getText().strip();
                rebuildLines();
            }
        }).dimensions(170, 58, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("删除预设"), b -> {
            if (selectedPreset != null) {
                controller.deletePreset(selectedPreset);
                selectedPreset = null;
                rebuildLines();
            }
        }).dimensions(274, 58, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("返回"), b -> close()).dimensions(width / 2 - 50, height - 26, 100, 20).build());
        rebuildLines();
    }

    private void editM(java.util.UUID player) {
        if (!acknowledged || mField == null) return;
        try {
            int nextM = Integer.parseInt(mField.getText());
            var snapshot = controller.usageStore().snapshot(player);
            controller.usageStore().edit(player, snapshot.beijingDate(), snapshot.revision(),
                snapshot.totals(), nextM, true, "用户确认的 M 编辑");
            acknowledged = false;
            if (acknowledge != null) acknowledge.setMessage(Text.literal("我已确认风险"));
            rebuildLines();
        } catch (Exception error) {
            controller.invalidate();
        }
    }

    private void rebuildLines() {
        List<String> result = new ArrayList<>();
        if (client == null || client.player == null) {
            result.add("连接玩家后才能读取 UUID 隔离的使用记录。");
        } else {
            var snapshot = controller.usageStore().snapshot(client.player.getUuid());
            result.add("当天累计材料 M（北京时间）：" + snapshot.currentM());
            result.add("记录版本：" + snapshot.revision() + " · 本地客户端模型，仅供参考");
            result.add("");
            List<UsageRecord> history = controller.usageStore().history(client.player.getUuid());
            if (history.isEmpty()) result.add("暂无已确认使用记录");
            else for (UsageRecord record : history) {
                result.add(record.beijingDate() + " · " + record.recipe() + " · "
                    + record.requestedCrafts() + " 次 · M " + record.beforeM() + " → " + record.afterM()
                    + (record.autoMode() ? " · 自动" : " · 手动"));
            }
            controller.usageStore().warning().ifPresent(w -> result.add("警告：" + w));
            result.add("");
            result.add("预设（点击名称后可改名或删除）：");
            var presets = controller.presetStore().list();
            if (presets.isEmpty()) result.add("暂无预设；预设保存入口将在计算结果页提供。");
            else for (var preset : presets) result.add("· " + preset.name() + " · " + preset.recipe()
                + " · " + preset.batches().size() + " 批");
        }
        result.add("");
        result.add("修改 M 会影响后续计划；必须勾选确认并会留下审计记录。");
        lines = List.copyOf(result);
    }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, UiColors.BACKGROUND);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 10, UiColors.PRIMARY);
        int visible = Math.max(1, (height - 72) / 13);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, lines.size() - visible)));
        for (int i = scroll, y = 62; i < Math.min(lines.size(), scroll + visible); i++, y += 13)
            context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(lines.get(i), width - 32), 16, y, UiColors.SECONDARY);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount == 0) return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        scroll = Math.max(0, Math.min(scroll + (verticalAmount > 0 ? -3 : 3), Math.max(0, lines.size() - 1)));
        return true;
    }

    @Override public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && click.y() >= 105) {
            int row = (int) ((click.y() - 105) / 13);
            var presets = controller.presetStore().list();
            if (row >= 0 && row < presets.size()) {
                selectedPreset = presets.get(row).name();
                if (renameField != null) renameField.setText(selectedPreset);
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) { close(); return true; }
        return super.keyPressed(input);
    }

    @Override public void close() {
        if (client != null) client.setScreen(parent);
    }
}
