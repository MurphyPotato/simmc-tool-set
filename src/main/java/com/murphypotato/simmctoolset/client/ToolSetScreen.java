package com.murphypotato.simmctoolset.client;

import com.murphypotato.simmctoolset.map.MapCompatibility;
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
        OVERVIEW("Overview"),
        ARCANE_HUD("Arcane HUD"),
        SCROLL("Scroll calculator"),
        ACCESSORY("Accessory fitting"),
        BREWING("Brewing and cookware"),
        MAP("SIMMC web map"),
        FLEX("Reserved module"),
        HOTKEYS("Hotkeys"),
        DIAGNOSTICS("Diagnostics and logs");

        private final String title;

        Panel(String title) {
            this.title = title;
        }
    }

    private final Screen parent;
    private Panel panel;

    public ToolSetScreen(Screen parent, Panel panel) {
        super(Text.literal("simMC Tool Set"));
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
        int y = 58;
        int width = Math.max(140, this.width - x - MARGIN);
        switch (panel) {
            case SCROLL -> addDrawableChild(ButtonWidget.builder(Text.literal("Open scroll calculator"),
                    button -> ToolSetClient.openScroll(client, this))
                    .dimensions(x, y, Math.min(220, width), 20).build());
            case ACCESSORY -> addDrawableChild(ButtonWidget.builder(Text.literal("Open accessory fitting"),
                    button -> ToolSetClient.openAccessory(client, this))
                    .dimensions(x, y, Math.min(220, width), 20).build());
            case ARCANE_HUD -> addDrawableChild(toggle(x, y, width, "Arcane HUD", ToolSetSettings.arcaneHudEnabled(),
                    ToolSetSettings::setArcaneHudEnabled));
            case BREWING -> addDrawableChild(toggle(x, y, width, "Brewing and cookware", ToolSetSettings.brewingEnabled(),
                    ToolSetSettings::setBrewingEnabled));
            case MAP -> addMapControls(x, y, width);
            case HOTKEYS -> addHotkeyControls(x, y, width);
            case DIAGNOSTICS -> addDiagnosticsControls(x, y, width);
            default -> { }
        }
    }

    private void addHotkeyControls(int x, int y, int width) {
        addDrawableChild(ButtonWidget.builder(Text.literal("Open Minecraft key settings"), button -> {
            if (client != null) client.setScreen(new net.minecraft.client.gui.screen.option.ControlsOptionsScreen(this, client.options));
        }).dimensions(x, y, Math.min(260, width), 20).build());
    }

    private ButtonWidget toggle(int x, int y, int width, String label, boolean current, Consumer<Boolean> save) {
        return ButtonWidget.builder(Text.literal(label + ": " + (current ? "ON" : "OFF")), button -> {
            save.accept(!current);
            clearAndInit();
        }).dimensions(x, y, Math.min(260, width), 20).build();
    }

    private void addMapControls(int x, int y, int width) {
        MapCompatibility.Status status = MapCompatibility.status();
        if (ToolSetClient.isMapInternal()) {
            addDrawableChild(toggle(x, y, width, "World map overlay", ToolSetClient.mapWorldOverlayEnabled(),
                    ignored -> ToolSetClient.toggleMapWorldOverlay()));
            addDrawableChild(toggle(x, y + 24, width, "World map background", ToolSetClient.mapWorldBackgroundEnabled(),
                    ignored -> ToolSetClient.toggleMapWorldBackground()));
            addDrawableChild(toggle(x, y + 48, width, "Minimap background", ToolSetClient.mapMinimapBackgroundEnabled(),
                    ignored -> ToolSetClient.toggleMapMinimapBackground()));
            addDrawableChild(ButtonWidget.builder(Text.literal("Refresh map data"), button -> {
                ToolSetClient.refreshMap();
                DiagnosticLog.info("SIMMC map refresh requested from the Tool Set panel");
            }).dimensions(x, y + 72, Math.min(220, width), 20).build());
        }
        if (status.canEnableExperimental()) {
            addDrawableChild(ButtonWidget.builder(Text.literal("Enable experimental compatibility (restart)"), button -> {
                ToolSetSettings.setMapExperimentalEnabled(true);
                DiagnosticLog.info("Experimental Xaero map compatibility enabled for next restart");
                clearAndInit();
            }).dimensions(x, y + (ToolSetClient.isMapInternal() ? 96 : 0), Math.min(300, width), 20).build());
        }
        if (status.experimentalEnabled()) {
            addDrawableChild(ButtonWidget.builder(Text.literal("Disable experimental compatibility"), button -> {
                ToolSetSettings.setMapExperimentalEnabled(false);
                clearAndInit();
            }).dimensions(x, y + (ToolSetClient.isMapInternal() ? 120 : 24), Math.min(220, width), 20).build());
        }
    }

    private void addDiagnosticsControls(int x, int y, int width) {
        addDrawableChild(ButtonWidget.builder(Text.literal("Export local diagnostic log"), button -> {
            try {
                Path exported = DiagnosticLog.export();
                DiagnosticLog.info("Diagnostic log exported to " + exported.getFileName());
                clearAndInit();
            } catch (IOException error) {
                DiagnosticLog.error("Diagnostic log export failed", error);
            }
        }).dimensions(x, y, Math.min(220, width), 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xE0121720);
        context.fill(0, 0, NAV_WIDTH, height, 0xE01F2937);
        context.drawTextWithShadow(textRenderer, title, MARGIN, 8, 0xFFFFFF);
        int x = NAV_WIDTH + MARGIN;
        context.drawTextWithShadow(textRenderer, Text.literal(panel.title), x, 18, 0xFFFFFF);
        renderPanelText(context, x, 38, width - x - MARGIN);
        super.render(context, mouseX, mouseY, delta);
    }

    private void renderPanelText(DrawContext context, int x, int y, int usableWidth) {
        List<String> lines = switch (panel) {
            case OVERVIEW -> List.of(
                    "Every module has a mouse-click entry point.",
                    "Shortcuts: \\+1 HUD, \\+2 scrolls, \\+3 accessories, \\+4 brewing, \\+5 map, \\+` diagnostics.",
                    "Mouse clicks, scrolling, dragging, and native Screen controls remain available.",
                    "External compatible modules take priority over internal copies."
            );
            case ARCANE_HUD -> List.of(
                    "Arcane HUD is active only on play.simmc.cn.",
                    "Licensed Simes-derived display layer; Mana is excluded."
            );
            case SCROLL -> List.of(
                    ToolSetClient.scrollStatus(),
                    "The original settings format and material exclusion behavior are preserved.",
                    "The external bridge requires version 2.1.0-fabric or newer."
            );
            case ACCESSORY -> List.of(
                    ToolSetClient.accessoryStatus(),
                    "The original scanning, review, fitting, and local storage behavior is preserved.",
                    "The external bridge requires version 6.1.0-fabric or newer."
            );
            case BREWING -> List.of(
                    "Brewing and cookware hints are enabled by default.",
                    "The module does not invent timers or server state when its server interface is unavailable."
            );
            case MAP -> mapLines();
            case FLEX -> List.of("Reserved for a future authorized module.",
                    "No functionality, threads, or configuration are created in this panel.");
            case HOTKEYS -> List.of(
                    "Defaults: \\+1 HUD, \\+2 scrolls, \\+3 accessories, \\+4 brewing, \\+5 map, \\+` diagnostics.",
                    "Press and release \\ alone to open this page; main keyboard 0 remains accessory direct access.",
                    "F1-F12, navigation, SysRq, keypad, and arrows are not defaults but remain rebindable."
            );
            case DIAGNOSTICS -> DiagnosticLog.snapshot().isEmpty()
                    ? List.of("No local diagnostic records yet.") : DiagnosticLog.snapshot();
        };
        int lineY = y;
        for (String line : lines) {
            for (var wrapped : textRenderer.wrapLines(Text.literal(line), Math.max(80, usableWidth))) {
                context.drawTextWithShadow(textRenderer, wrapped, x, lineY, 0xD7DEE8);
                lineY += 12;
            }
            lineY += 4;
            if (lineY > height - 12) return;
        }
    }

    private List<String> mapLines() {
        MapCompatibility.Status status = MapCompatibility.status();
        List<String> lines = new ArrayList<>(List.of(
                "Map status: " + status.displayName(),
                status.detail(),
                "SIMMC Map is shown through Xaero World Map and Xaero Minimap; native zoom, drag, and mouse actions remain available.",
                "SIMMC Map source: YeShengQius/SIMMC-Xaero-Map (Apache-2.0).",
                "Xaero World Map and Xaero Minimap are external dependencies and are not bundled."
        ));
        if (status.canEnableExperimental() || status.experimentalEnabled()) {
            lines.add("Experimental compatibility is attempted only after restart; other modules remain available.");
        }
        return List.copyOf(lines);
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }
}
