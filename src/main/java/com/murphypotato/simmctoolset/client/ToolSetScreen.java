package com.murphypotato.simmctoolset.client;

import com.murphypotato.simmctoolset.map.MapCompatibility;
import com.murphypotato.simmctoolset.internal.simes.SimesFeatureController;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
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
        MAP("SIMMC 网页地图"),
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

    public ToolSetScreen(Screen parent, Panel panel) {
        super(Text.literal("simMC 工具组"));
        this.parent = parent;
        this.panel = panel;
    }

    public void select(Panel next) {
        panel = next;
        clearAndInit();
    }

    @Override
    protected void init() {
        int x = MARGIN;
        int y = 28;
        for (Panel item : List.of(Panel.OVERVIEW, Panel.ARCANE_HUD, Panel.SCROLL,
                Panel.ACCESSORY, Panel.BREWING, Panel.MAP)) {
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
        int y = Math.min(156, Math.max(72, height - 210));
        int width = Math.max(140, this.width - x - MARGIN);
        switch (panel) {
            case SCROLL -> addDrawableChild(ButtonWidget.builder(Text.literal("打开卷轴计算器"),
                    button -> ToolSetClient.openScroll(client, this))
                    .dimensions(x, y, Math.min(220, width), 20).build());
            case ACCESSORY -> addDrawableChild(ButtonWidget.builder(Text.literal("打开饰品配装工具"),
                    button -> ToolSetClient.openAccessory(client, this))
                    .dimensions(x, y, Math.min(220, width), 20).build());
            case ARCANE_HUD -> {
                addDrawableChild(toggle(x, y, width, "奥术 HUD", ToolSetSettings.arcaneHudEnabled(),
                        ToolSetSettings::setArcaneHudEnabled));
                addDrawableChild(ButtonWidget.builder(Text.literal("打开 Simes 设置"),
                        button -> com.murphypotato.simmctoolset.internal.simes.SimesArcaneHud.openSettings(this))
                        .dimensions(x, y + 24, Math.min(240, width), 20).build());
            }
            case BREWING -> {
                addDrawableChild(toggle(x, y, width, "发酵提示", ToolSetSettings.fermentationEnabled(),
                        ToolSetSettings::setFermentationEnabled));
                addDrawableChild(toggle(x, y + 24, width, "厨具提示", ToolSetSettings.cookwareEnabled(),
                        ToolSetSettings::setCookwareEnabled));
                addDrawableChild(ButtonWidget.builder(Text.literal("打开 Simes 设置"),
                        button -> com.murphypotato.simmctoolset.internal.simes.SimesArcaneHud.openSettings(this))
                        .dimensions(x, y + 48, Math.min(240, width), 20).build());
            }
            case MAP -> addMapControls(x, y, width);
            case HOTKEYS -> addHotkeyControls(x, y, width);
            case DIAGNOSTICS -> addDiagnosticsControls(x, y, width);
            default -> { }
        }
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

    private void addMapControls(int x, int y, int width) {
        MapCompatibility.Status status = MapCompatibility.status();
        if (ToolSetClient.isMapInternal()) {
            addDrawableChild(toggle(x, y, width, "SIMMC 覆盖层（世界地图与小地图）", ToolSetClient.mapWorldOverlayEnabled(),
                    ignored -> ToolSetClient.toggleMapWorldOverlay()));
            addDrawableChild(toggle(x, y + 24, width, "世界地图背景", ToolSetClient.mapWorldBackgroundEnabled(),
                    ignored -> ToolSetClient.toggleMapWorldBackground()));
            addDrawableChild(toggle(x, y + 48, width, "小地图背景", ToolSetClient.mapMinimapBackgroundEnabled(),
                    ignored -> ToolSetClient.toggleMapMinimapBackground()));
            addDrawableChild(ButtonWidget.builder(Text.literal("立即刷新地图数据"), button -> {
                ToolSetClient.refreshMap();
                DiagnosticLog.info("已从工具组地图页面请求刷新");
            }).dimensions(x, y + 72, Math.min(220, width), 20).build());
        }
        if (status.canEnableExperimental()) {
            addDrawableChild(ButtonWidget.builder(Text.literal("尝试兼容模式（需重启）"), button -> {
                ToolSetSettings.setMapExperimentalEnabled(true);
                DiagnosticLog.info("已开启 Xaero 实验兼容模式，重启后尝试加载");
                clearAndInit();
            }).dimensions(x, y + (ToolSetClient.isMapInternal() ? 96 : 0), Math.min(300, width), 20).build());
        }
        if (status.experimentalEnabled()) {
            addDrawableChild(ButtonWidget.builder(Text.literal("关闭兼容模式"), button -> {
                ToolSetSettings.setMapExperimentalEnabled(false);
                clearAndInit();
            }).dimensions(x, y + (ToolSetClient.isMapInternal() ? 120 : 24), Math.min(220, width), 20).build());
        }
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
        }).dimensions(x, y, Math.min(220, width), 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("复制最近文件路径"), button -> {
            DiagnosticLog.lastExportPath().ifPresent(path -> {
                if (client != null) client.keyboard.setClipboard(path.toString());
                DiagnosticLog.info("已复制诊断日志路径：" + path);
                clearAndInit();
            });
        }).dimensions(x, y + 24, Math.min(220, width), 20).build());
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
        List<String> lines = switch (panel) {
            case OVERVIEW -> List.of(
                    "鼠标点击始终是完整入口，快捷键不会锁定或替换任何子板块按钮。",
                    "组合键：\\+1 奥术 HUD，\\+2 卷轴计算，\\+3 饰品配装，\\+4 发酵与厨具，\\+5 网页地图，\\+` 诊断日志。",
                    "外置维护版会优先接管对应模块；其它模块继续独立运行。",
                    "当前状态：" + ToolSetClient.runtimeSummary()
            );
            case ARCANE_HUD -> List.of(
                    "授权版奥术状态 HUD 仅在 play.simmc.cn 服务器内激活。",
                    "当前状态：" + SimesFeatureController.arcaneStatus(),
                    "使用奥术后，服务器的吟唱/持续时间/公共冷却消息会显示在屏幕左上角。"
            );
            case SCROLL -> List.of(
                    ToolSetClient.scrollStatus(),
                    "保留原有设置、材料排除和计算取消行为。",
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
                    "原生助手会在目标方块附近显示材料、校准状态和服务器确认结果；没有数据时不会伪造计时。"
            );
            case MAP -> mapLines();
            case FLEX -> List.of("此板块为未来已授权模块预留。", "当前不会创建线程、事件或配置。");
            case HOTKEYS -> List.of(
                    "默认：\\+1 奥术 HUD，\\+2 卷轴计算，\\+3 饰品配装，\\+4 发酵与厨具，\\+5 网页地图，\\+` 诊断日志。",
                    "按住\\再按功能键触发；单独松开\\打开工具组总控。\\、0、O 在 Minecraft 控制设置中修改。",
                    "F1-F12、导航区、SysRq、小键盘和方向键不作为默认键，但可以在专属页面重新绑定。"
            );
            case DIAGNOSTICS -> DiagnosticLog.snapshot().isEmpty()
                    ? List.of("尚无本地诊断记录。", "日志只在点击导出后写入 config/simmc-tool-set/diagnostics/。",
                    "最近导出：" + DiagnosticLog.lastExportPath().map(Path::toString).orElse("尚未导出"))
                    : new ArrayList<>(DiagnosticLog.snapshot());
        };
        int lineY = y;
        for (String line : lines) {
            for (var wrapped : textRenderer.wrapLines(Text.literal(line), Math.max(80, usableWidth))) {
                context.drawTextWithShadow(textRenderer, wrapped, x, lineY, 0xFFD7DEE8);
                lineY += 12;
            }
            lineY += 4;
            if (lineY > height - 12) return;
        }
    }

    private List<String> mapLines() {
        MapCompatibility.Status status = MapCompatibility.status();
        List<String> lines = new ArrayList<>(List.of(
                "地图状态：" + status.displayName(),
                status.detail(),
                "运行状态：" + ToolSetClient.mapRuntimeStatus(),
                "SIMMC 覆盖层会同时绘制到 Xaero 世界地图和小地图；缩放、拖动、点击和鼠标操作仍由 Xaero 处理。",
                "进入 play.simmc.cn 后点击“立即刷新地图数据”，打开 Xaero 世界地图即可看到标记；小地图覆盖会在同一服务器的主世界自动显示。",
                "地图来源：YeShengQius/SIMMC-Xaero-Map（Apache-2.0）；Xaero 两个外部依赖不会打包进本 JAR。"
            ));
        if (status.canEnableExperimental() || status.experimentalEnabled()) {
            lines.add("兼容模式只会在重启后尝试，其他子模块仍可正常使用。");
        }
        return List.copyOf(lines);
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }
}
