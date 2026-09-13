package com.murphypotato.simmctoolset.client;

import com.murphypotato.simmctoolset.internal.simes.SimesFeatureController;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Compact native control screen shared by all internal modules. */
public final class ToolSetScreen extends Screen {
    private static final int NAV_WIDTH = 156;
    private static final int MARGIN = 12;

    public enum Panel {
        OVERVIEW("总览"),
        ARCANE_HUD("奥术 HUD"),
        SCROLL("卷轴计算"),
        ACCESSORY("饰品配装"),
        BREWING("发酵与厨具"),
        FLEX("预留板块"),
        HOTKEYS("工具组按键"),
        DIAGNOSTICS("诊断与日志");

        private final String title;

        Panel(String title) {
            this.title = title;
        }
    }

    private final Screen parent;
    private Panel panel;
    private int diagnosticScroll;
    private int diagnosticKnownLineCount = -1;
    private int diagnosticKnownVisibleLines = -1;

    public ToolSetScreen(Screen parent, Panel panel) {
        super(Text.literal("simMC 工具组"));
        this.parent = parent;
        this.panel = panel;
    }

    public void select(Panel next) {
        diagnosticScroll = diagnosticScrollOnPanelSelect(panel, next, diagnosticScroll,
                diagnosticMaxScroll(diagnosticKnownLineCount, diagnosticKnownVisibleLines));
        panel = next;
        clearAndInit();
    }

    @Override
    protected void init() {
        int x = MARGIN;
        int y = 28;
        for (Panel item : List.of(Panel.OVERVIEW, Panel.ARCANE_HUD, Panel.SCROLL,
                Panel.ACCESSORY, Panel.BREWING)) {
            addDrawableChild(navButton(item, x, y));
            y += 24;
        }
        addDrawableChild(navButton(Panel.FLEX, x, y));
        y += 24;
        addDrawableChild(navButton(Panel.HOTKEYS, x, y));
        addDrawableChild(navButton(Panel.DIAGNOSTICS, x, height - 32));
        addPanelControls();
    }

    private ButtonWidget navButton(Panel item, int x, int y) {
        String prefix = panel == item ? "> " : "  ";
        return ButtonWidget.builder(Text.literal(prefix + item.title), button -> select(item))
                .dimensions(x, y, NAV_WIDTH - MARGIN * 2, 20).build();
    }

    private void addPanelControls() {
        int x = NAV_WIDTH + MARGIN;
        int width = Math.max(140, this.width - x - MARGIN);
        int y = 46;
        for (String line : panelLines()) {
            y += textRenderer.wrapLines(Text.literal(line), Math.max(80, width)).size() * 12 + 4;
        }
        switch (panel) {
            case SCROLL -> addDrawableChild(ButtonWidget.builder(Text.literal("打开卷轴计算器"),
                    button -> ToolSetClient.openScroll(client, this))
                    .dimensions(x, y, Math.min(220, width), 20).build());
            case ACCESSORY -> addDrawableChild(ButtonWidget.builder(Text.literal("打开饰品配装工具"),
                    button -> ToolSetClient.openAccessory(client, this))
                    .dimensions(x, y, Math.min(220, width), 20).build());
            case ARCANE_HUD -> {
                addDrawableChild(toggle(x, y, width, "奥术 HUD", ToolSetSettings.arcaneHudEnabled(),
                        SimesFeatureController::setArcaneEnabled));
                addSimesSettingsButton(x, y + 24, width);
            }
            case BREWING -> {
                if (FabricLoader.getInstance().isModLoaded("simes")) {
                    addSimesSettingsButton(x, y, width);
                } else {
                    addDrawableChild(toggle(x, y, width, "发酵提示", ToolSetSettings.fermentationEnabled(),
                            ToolSetSettings::setFermentationEnabled));
                    addDrawableChild(toggle(x, y + 24, width, "厨具提示", ToolSetSettings.cookwareEnabled(),
                            ToolSetSettings::setCookwareEnabled));
                    addSimesSettingsButton(x, y + 48, width);
                }
            }
            case HOTKEYS -> addHotkeyControls(x, y, width);
            case DIAGNOSTICS -> addDiagnosticsControls(x, diagnosticControlsTop(height), width);
            default -> { }
        }
    }

    private void addSimesSettingsButton(int x, int y, int width) {
        ButtonWidget button = ButtonWidget.builder(
                        Text.literal(FabricLoader.getInstance().isModLoaded("simes")
                                ? "外置 Simes 已接管（请按 O）" : "打开 Simes 设置"),
                        ignored -> {
                            if (!FabricLoader.getInstance().isModLoaded("simes")) {
                                com.murphypotato.simmctoolset.internal.simes.SimesArcaneHud.openSettings(this);
                            }
                        })
                .dimensions(x, y, Math.min(260, width), 20).build();
        button.active = !FabricLoader.getInstance().isModLoaded("simes");
        addDrawableChild(button);
    }

    private void addHotkeyControls(int x, int y, int width) {
        addDrawableChild(ButtonWidget.builder(Text.literal("打开工具组按键设置"), button -> ToolSetClient.openHotkeys(this))
                .dimensions(x, y, Math.min(260, width), 20).build());
    }

    private ButtonWidget toggle(int x, int y, int width, String label, boolean current, Consumer<Boolean> save) {
        return ButtonWidget.builder(Text.literal(label + "：" + (current ? "开" : "关")), button -> {
            save.accept(!current);
            clearAndInit();
        }).dimensions(x, y, Math.min(260, width), 20).build();
    }

    private void addDiagnosticsControls(int x, int y, int width) {
        addDrawableChild(ButtonWidget.builder(Text.literal("导出本地诊断日志"), button -> {
            try {
                Path exported = DiagnosticLog.export();
                DiagnosticLog.info("诊断日志已导出：" + exported.toAbsolutePath());
                clearAndInit();
            } catch (IOException error) {
                DiagnosticLog.error("诊断日志导出失败", error);
            }
        }).dimensions(x, diagnosticButtonTop(y, height), Math.min(220, width), 20).build());
        if (diagnosticHasSecondaryButton(height)) {
            addDrawableChild(ButtonWidget.builder(Text.literal("复制最近文件路径"), button -> {
                DiagnosticLog.lastExportPath().ifPresent(path -> {
                    if (client != null) client.keyboard.setClipboard(path.toString());
                    DiagnosticLog.info("已复制诊断日志路径：" + path);
                    clearAndInit();
                });
            }).dimensions(x, diagnosticButtonTop(y + 24, height), Math.min(220, width), 20).build());
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xE0121720);
        context.fill(0, 0, NAV_WIDTH, height, 0xE01F2937);
        context.drawTextWithShadow(textRenderer, title, MARGIN, 8, 0xFFFFFFFF);
        int x = NAV_WIDTH + MARGIN;
        context.drawTextWithShadow(textRenderer, Text.literal(panel.title), x, 18, 0xFFFFFFFF);
        renderPanelText(context, x, 38, width - x - MARGIN);
        super.render(context, mouseX, mouseY, delta);
    }

    private void renderPanelText(DrawContext context, int x, int y, int usableWidth) {
        if (panel == Panel.DIAGNOSTICS) {
            renderDiagnosticText(context, x, y, usableWidth);
            return;
        }
        int lineY = y;
        for (String line : panelLines()) {
            for (var wrapped : textRenderer.wrapLines(Text.literal(line), Math.max(80, usableWidth))) {
                context.drawTextWithShadow(textRenderer, wrapped, x, lineY, 0xFFD7DEE8);
                lineY += 12;
            }
            lineY += 4;
            if (lineY > height - 12) return;
        }
    }

    private List<String> panelLines() {
        return switch (panel) {
            case OVERVIEW -> List.of(
                    ToolSetKeyRouter.currentShortcutSummary(),
                    "当前状态：" + ToolSetClient.runtimeSummary()
            );
            case ARCANE_HUD -> List.of(
                    "当前状态：" + SimesFeatureController.arcaneStatus(),
                    "使用奥术后，服务器的吟唱/持续时间/公共冷却消息会默认显示在屏幕中间偏下。",
                    "此版块自Simes mod中移植，原作者7imes"
            );
            case SCROLL -> List.of(
                    ToolSetClient.scrollStatus(),
                    scrollPanelDescription(),
                    "外置桥接版本要求 2.1.0-fabric 或更新版本。"
            );
            case ACCESSORY -> List.of(
                    ToolSetClient.accessoryStatus(),
                    "可从物品栏或当前容器扫描饰品，再计算并替换配装。",
                    "外置桥接版本要求 6.1.0-fabric 或更新版本。"
            );
            case BREWING -> List.of(
                    "当前状态：" + SimesFeatureController.brewingStatus(),
                    "发酵提示：" + (ToolSetSettings.fermentationEnabled() ? "开" : "关") + "；厨具提示：" + (ToolSetSettings.cookwareEnabled() ? "开" : "关") + "。",
                    "原生助手会在目标方块附近显示材料、校准状态和服务器确认结果；没有数据时不会伪造计时。",
                    "此版块自Simes mod中移植，原作者7imes"
            );
            case FLEX -> List.of("此板块为未来模块预留。");
            case HOTKEYS -> List.of(
                    ToolSetKeyRouter.currentShortcutSummary(),
                    "按住\\再按功能键触发；单独松开\\打开工具组总控。",
                    "工具组组合键前缀键 \\、饰品工具直达键 0、打开 Simes 设置键 O，需在 Minecraft-按键控制-按键绑定 中修改。"
            );
            case DIAGNOSTICS -> List.of();
        };
    }

    private void renderDiagnosticText(DrawContext context, int x, int y, int usableWidth) {
        List<String> entries = DiagnosticLog.snapshot().isEmpty()
                ? List.of("尚无本地诊断记录。", "日志只在点击导出后写入 config/simmc-tool-set/diagnostics/。",
                "最近导出：" + DiagnosticLog.lastExportPath().map(Path::toString).orElse("尚未导出"))
                : DiagnosticLog.snapshot();
        List<OrderedText> lines = new ArrayList<>();
        for (String entry : entries) {
            lines.addAll(textRenderer.wrapLines(Text.literal(entry), Math.max(80, usableWidth)));
        }

        int visibleLines = diagnosticVisibleLines(y, height);
        int maxScroll = diagnosticMaxScroll(lines.size(), visibleLines);
        int previousMax = diagnosticMaxScroll(diagnosticKnownLineCount, diagnosticKnownVisibleLines);
        if (diagnosticKnownLineCount < 0 || diagnosticScroll >= previousMax) {
            diagnosticScroll = maxScroll;
        } else {
            diagnosticScroll = Math.min(diagnosticScroll, maxScroll);
        }
        diagnosticKnownLineCount = lines.size();
        diagnosticKnownVisibleLines = visibleLines;

        int contentBottom = diagnosticScissorBottom(y, height);
        context.enableScissor(x, y, Math.max(x, width - MARGIN), contentBottom);
        int lineY = y;
        for (int index = diagnosticScroll; index < lines.size() && lineY + 10 <= contentBottom; index++) {
            context.drawTextWithShadow(textRenderer, lines.get(index), x, lineY, 0xFFD7DEE8);
            lineY += 12;
        }
        context.disableScissor();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (panel != Panel.DIAGNOSTICS || verticalAmount == 0.0) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
        int maxScroll = diagnosticMaxScroll(diagnosticKnownLineCount, diagnosticKnownVisibleLines);
        diagnosticScroll = diagnosticScrollFor(panel, diagnosticScroll, verticalAmount, maxScroll);
        return true;
    }

    static String scrollPanelDescription() {
        return "用于计算奥术卷轴材料配比，支持材料排除与轮换方案。";
    }

    static int diagnosticControlsTop(int screenHeight) {
        return Math.max(0, screenHeight - 52);
    }

    static int diagnosticContentBottom(int screenHeight) {
        return Math.max(0, diagnosticControlsTop(screenHeight) - 8);
    }

    static int diagnosticScissorBottom(int contentTop, int screenHeight) {
        return Math.max(contentTop, diagnosticContentBottom(screenHeight));
    }

    static int diagnosticButtonTop(int requestedTop, int screenHeight) {
        return Math.max(0, Math.min(requestedTop, screenHeight - 20));
    }

    static boolean diagnosticHasSecondaryButton(int screenHeight) {
        return screenHeight >= 40;
    }

    static int diagnosticVisibleLines(int contentTop, int screenHeight) {
        return Math.max(1, (diagnosticContentBottom(screenHeight) - contentTop) / 12);
    }

    static int diagnosticMaxScroll(int lineCount, int visibleLines) {
        return Math.max(0, lineCount - Math.max(1, visibleLines));
    }

    static int diagnosticInitialScroll(int lineCount, int visibleLines) {
        return diagnosticMaxScroll(lineCount, visibleLines);
    }

    static int diagnosticScrollFor(Panel panel, int current, double wheelAmount, int maxScroll) {
        if (panel != Panel.DIAGNOSTICS || wheelAmount == 0.0) return current;
        int next = current + (wheelAmount > 0.0 ? -3 : 3);
        return Math.max(0, Math.min(maxScroll, next));
    }

    static int diagnosticScrollOnPanelSelect(Panel previous, Panel next, int current, int maxScroll) {
        return previous != Panel.DIAGNOSTICS && next == Panel.DIAGNOSTICS ? maxScroll : current;
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }
}
