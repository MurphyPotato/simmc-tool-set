package com.murphypotato.simmctoolset.internal.map.gui;

import com.murphypotato.simmctoolset.internal.map.SimmcMapClient;
import com.murphypotato.simmctoolset.internal.map.config.SimmcMapConfig;
import com.murphypotato.simmctoolset.internal.map.model.MapPoint;
import com.murphypotato.simmctoolset.internal.map.model.MapSnapshot;
import com.murphypotato.simmctoolset.internal.map.model.OnlinePlayerEntry;
import com.murphypotato.simmctoolset.internal.map.model.SearchEntry;
import com.murphypotato.simmctoolset.internal.map.render.WorldMapOverlayRenderer;
import com.murphypotato.simmctoolset.internal.map.search.SearchIndex;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class WorldMapUiController {
    private static final int X = 6, TOP = 6, BUTTON_H = 20, PANEL_TOP = 31, PANEL_W = 230;
    private static final String[] BUTTONS = {"地图", "背景", "图层", "搜索", "收藏", "在线玩家", "刷新", "全图"};
    private static final int[] WIDTHS = {42, 42, 42, 42, 42, 66, 42, 42};
    private final MapToolbar toolbar = new MapToolbar();
    private final SearchOverlay search = new SearchOverlay();
    private final OnlinePlayersOverlay players = new OnlinePlayersOverlay();
    private final FavoritesOverlay favorites = new FavoritesOverlay();
    private final AtomicReference<MapPoint> pendingLocation = new AtomicReference<>();
    private volatile boolean fitRequested;
    private MapSnapshot snapshot = new MapSnapshot(List.of());
    private List<OnlinePlayerEntry> playerEntries = List.of();
    private boolean playersAvailable;

    public MapToolbar toolbar() { return toolbar; }
    public void applyConfig(SimmcMapConfig config) { toolbar.applyConfig(config); favorites.restoreStableKeys(config.favoriteKeys()); rebuild(); }
    public List<String> favoriteKeys() { return favorites.stableKeys(); }
    public void updateSnapshot(MapSnapshot snapshot) { this.snapshot = snapshot; rebuild(); }
    public void updatePlayers(List<OnlinePlayerEntry> values, boolean available) { playerEntries = List.copyOf(values); playersAvailable = available; players.update(values, available); rebuild(); }
    private void rebuild() { SearchIndex index = SearchIndex.build(snapshot, playersAvailable ? playerEntries : List.of()); search.update(index); favorites.rebind(index); }
    public Optional<MapPoint> consumeLocation() { return Optional.ofNullable(pendingLocation.getAndSet(null)); }
    public boolean consumeFitRequest() { boolean result = fitRequested; fitRequested = false; return result; }

    public void render(DrawContext context, int mouseX, int mouseY, WorldMapOverlayRenderer overlay, WorldMapOverlayRenderer.View view) {
        var text = MinecraftClient.getInstance().textRenderer;
        int x = X;
        for (int i = 0; i < BUTTONS.length; i++) { boolean active = buttonActive(i); context.fill(x, TOP, x + WIDTHS[i], TOP + BUTTON_H, active ? 0xCC2E7D32 : 0xCC202020); context.drawText(text, Text.literal(BUTTONS[i]), x + 5, TOP + 6, 0xFFFFFFFF, false); x += WIDTHS[i] + 3; }
        if (toolbar.openPanel() != MapToolbar.Panel.NONE) renderPanel(context);
        if (toolbar.detailPopup().isOpen()) renderDetail(context);
        else overlay.hitTest(mouseX, mouseY, view).flatMap(this::entryFor).ifPresent(entry -> { int w = text.getWidth(entry.displayName()) + 10; context.fill(mouseX + 8, mouseY + 8, mouseX + 8 + w, mouseY + 24, 0xE0000000); context.drawText(text, Text.literal(entry.displayName()), mouseX + 13, mouseY + 12, 0xFFFFFFFF, false); });
    }
    private boolean buttonActive(int i) { return switch (i) { case 0 -> toolbar.worldMapEnabled(); case 1 -> toolbar.worldBackgroundEnabled(); case 2 -> toolbar.openPanel() == MapToolbar.Panel.LAYERS; case 3 -> toolbar.openPanel() == MapToolbar.Panel.SEARCH; case 4 -> toolbar.openPanel() == MapToolbar.Panel.FAVORITES; case 5 -> toolbar.openPanel() == MapToolbar.Panel.PLAYERS; default -> false; }; }
    private void renderPanel(DrawContext c) { var t = MinecraftClient.getInstance().textRenderer; int bottom = Math.min(c.getScaledWindowHeight() - 8, 280); c.fill(X, PANEL_TOP, X + PANEL_W, bottom, 0xE0101010); int y = PANEL_TOP + 8; if (toolbar.openPanel() == MapToolbar.Panel.LAYERS) { for (LayerPanel.Layer layer : LayerPanel.LAYERS) { c.drawText(t, Text.literal((toolbar.layerPanel().isVisible(layer.id()) ? "☑ " : "☐ ") + layer.name()), X + 8, y, 0xFFFFFFFF, false); y += 20; } } else if (toolbar.openPanel() == MapToolbar.Panel.SEARCH) { c.drawText(t, Text.literal("搜索：" + search.query() + "_"), X + 8, y, 0xFFFFFF80, false); y += 20; for (SearchEntry entry : search.results()) { c.drawText(t, Text.literal(entry.displayName()), X + 8, y, entry.canNavigate() ? 0xFFFFFFFF : 0xFFAAAAAA, false); y += 18; } } else if (toolbar.openPanel() == MapToolbar.Panel.PLAYERS) { c.drawText(t, Text.literal(players.available() ? "在线玩家" : "在线名单暂不可用"), X + 8, y, 0xFFFFFF80, false); y += 20; for (OnlinePlayersOverlay.Row row : players.rows()) { c.drawText(t, Text.literal(row.name() + " · " + row.status()), X + 8, y, 0xFFFFFFFF, false); y += 18; } } else { c.drawText(t, Text.literal("收藏"), X + 8, y, 0xFFFFFF80, false); y += 20; for (var favorite : favorites.favorites()) { c.drawText(t, Text.literal(favorite.entry().map(SearchEntry::displayName).orElse("该地点当前不可用")), X + 8, y, favorite.entry().isPresent() ? 0xFFFFFFFF : 0xFFAAAAAA, false); y += 18; } } }
    private void renderDetail(DrawContext c) { var t = MinecraftClient.getInstance().textRenderer; int w = Math.min(520, c.getScaledWindowWidth() - 40), h = Math.min(340, c.getScaledWindowHeight() - 50), left = (c.getScaledWindowWidth() - w) / 2, top = (c.getScaledWindowHeight() - h) / 2; c.fill(left, top, left + w, top + h, 0xF0101010); int x = left + 12; for (DetailPopup.Page page : DetailPopup.Page.values()) { c.drawText(t, Text.literal(page.title()), x, top + 12, page == toolbar.detailPopup().page() ? 0xFFFFFF80 : 0xFFFFFFFF, false); x += t.getWidth(page.title()) + 18; } int y = top + 38; toolbar.detailPopup().setVisibleLineCapacity(Math.max(1, (h - 78) / 14)); for (String line : toolbar.detailPopup().visibleLines()) { c.drawText(t, Text.literal(line), left + 14, y, 0xFFFFFFFF, false); y += 14; } c.drawText(t, Text.literal("复制坐标  收藏  定位  创建路点"), left + 14, top + h - 24, 0xFF80D8FF, false); }

    public boolean click(double mouseX, double mouseY, int button, WorldMapOverlayRenderer overlay, WorldMapOverlayRenderer.View view) {
        if (button == 0 && mouseY >= TOP && mouseY < TOP + BUTTON_H) { int x = X; for (int i = 0; i < WIDTHS.length; i++) { if (mouseX >= x && mouseX < x + WIDTHS[i]) { toolbarAction(i); return true; } x += WIDTHS[i] + 3; } }
        if (button == 0 && toolbar.detailPopup().isOpen() && detailClick(mouseX, mouseY)) return true;
        if ((button == 0 || button == 1) && toolbar.openPanel() != MapToolbar.Panel.NONE && mouseX >= X && mouseX < X + PANEL_W && mouseY >= PANEL_TOP) {
            int row = (int) ((mouseY - PANEL_TOP - 8) / (toolbar.openPanel() == MapToolbar.Panel.LAYERS ? 20 : 18));
            if (toolbar.openPanel() == MapToolbar.Panel.LAYERS && button == 0 && row >= 0 && row < LayerPanel.LAYERS.size()) {
                toolbar.layerPanel().toggle(LayerPanel.LAYERS.get(row).id());
                SimmcMapClient.saveUserSettings();
            } else if (toolbar.openPanel() == MapToolbar.Panel.SEARCH && row > 0 && row - 1 < search.results().size()) {
                SearchEntry entry = search.results().get(row - 1);
                if (button == 0) locate(entry); else toolbar.detailPopup().open(entry);
            } else if (toolbar.openPanel() == MapToolbar.Panel.PLAYERS && row > 0 && row - 1 < players.rows().size()) {
                OnlinePlayersOverlay.Row selected = players.rows().get(row - 1);
                if (button == 0) selected.position().ifPresent(pendingLocation::set);
                else playerEntries.stream().filter(player -> player.name().equals(selected.name())).findFirst()
                        .ifPresent(player -> showStatus(SimmcMapClient.createWaypoint(player)));
            } else if (toolbar.openPanel() == MapToolbar.Panel.FAVORITES && row > 0 && row - 1 < favorites.favorites().size()) {
                favorites.favorites().get(row - 1).entry().ifPresent(entry -> {
                    if (button == 0) locate(entry); else toolbar.detailPopup().open(entry);
                });
            }
            return true;
        }
        Optional<SearchEntry> hit = overlay.hitTest(mouseX, mouseY, view).flatMap(this::entryFor); if (button == 1 && hit.isPresent()) { toolbar.detailPopup().open(hit.orElseThrow()); return true; } return false;
    }
    private void toolbarAction(int i) { switch (i) { case 0 -> { toolbar.toggleWorldMap(); SimmcMapClient.saveUserSettings(); } case 1 -> { toolbar.toggleWorldBackground(); SimmcMapClient.saveUserSettings(); } case 2 -> toolbar.openPanel(MapToolbar.Panel.LAYERS); case 3 -> toolbar.openPanel(MapToolbar.Panel.SEARCH); case 4 -> toolbar.openPanel(MapToolbar.Panel.FAVORITES); case 5 -> toolbar.openPanel(MapToolbar.Panel.PLAYERS); case 6 -> SimmcMapClient.requestRefresh(); case 7 -> fitRequested = true; default -> { } } }
    private boolean detailClick(double mouseX, double mouseY) {
        MinecraftClient client = MinecraftClient.getInstance();
        int screenW = client.getWindow().getScaledWidth(), screenH = client.getWindow().getScaledHeight();
        int w = Math.min(520, screenW - 40), h = Math.min(340, screenH - 50);
        int left = (screenW - w) / 2, top = (screenH - h) / 2;
        if (mouseX < left || mouseX >= left + w || mouseY < top || mouseY >= top + h) return false;
        if (mouseY < top + 34) {
            int x = left + 12;
            var renderer = client.textRenderer;
            for (DetailPopup.Page page : DetailPopup.Page.values()) {
                int right = x + renderer.getWidth(page.title()) + 12;
                if (mouseX >= x && mouseX < right) { toolbar.detailPopup().selectPage(page); return true; }
                x = right + 6;
            }
            return true;
        }
        if (mouseY >= top + h - 38) {
            SearchEntry entry = toolbar.detailPopup().entry().orElseThrow();
            int action = Math.min(3, Math.max(0, (int) ((mouseX - left) * 4 / w)));
            if (action == 0) toolbar.detailPopup().coordinateText().ifPresent(value -> client.keyboard.setClipboard(value));
            else if (action == 1 && entry.favoriteEligible()) { favorites.toggle(entry); SimmcMapClient.saveUserSettings(); }
            else if (action == 2) locate(entry);
            else if (action == 3) showStatus(SimmcMapClient.createWaypoint(entry));
            return true;
        }
        return true;
    }
    private static void showStatus(String value) { MinecraftClient client = MinecraftClient.getInstance(); if (client.player != null) client.player.sendMessage(Text.literal(value), false); }
    private void openOrLocate(List<SearchEntry> entries, int index) { if (index >= 0 && index < entries.size()) locate(entries.get(index)); }
    private void locate(SearchEntry entry) { if (entry.canNavigate()) entry.center().ifPresent(pendingLocation::set); else toolbar.detailPopup().open(entry); }
    public boolean key(int key) { if (key == GLFW.GLFW_KEY_ESCAPE) return toolbar.escape(); if (toolbar.openPanel() != MapToolbar.Panel.SEARCH) return false; if (key == GLFW.GLFW_KEY_BACKSPACE) return search.backspace(); if (key == GLFW.GLFW_KEY_UP) { search.move(-1); return true; } if (key == GLFW.GLFW_KEY_DOWN) { search.move(1); return true; } if (key == GLFW.GLFW_KEY_ENTER) { search.selected().ifPresent(this::locate); return true; } return false; }
    public boolean character(char value) { if (toolbar.openPanel() != MapToolbar.Panel.SEARCH) return false; search.type(value); return true; }
    public boolean scroll(double vertical) { if (!toolbar.detailPopup().isOpen()) return false; toolbar.detailPopup().scroll(vertical < 0 ? 1 : -1); return true; }
    public void clearScreenState() { toolbar.detailPopup().close(); toolbar.openPanel(MapToolbar.Panel.NONE); search.clear(); }
    private Optional<SearchEntry> entryFor(WorldMapOverlayRenderer.HitResult hit) { return SearchIndex.build(snapshot, playersAvailable ? playerEntries : List.of()).entries().stream().filter(e -> e.marker().filter(m -> m == hit.marker()).isPresent()).findFirst(); }
}
