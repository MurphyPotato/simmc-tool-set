package com.murphypotato.simmctoolset.internal.map.gui;

import com.murphypotato.simmctoolset.internal.map.config.SimmcMapConfig;

public final class MapToolbar {
    public enum Panel { NONE, LAYERS, SEARCH, PLAYERS, FAVORITES }
    private boolean worldMapEnabled;
    private boolean worldBackgroundEnabled;
    private boolean minimapBackgroundEnabled;
    private Panel openPanel = Panel.NONE;
    private final LayerPanel layerPanel;
    private final DetailPopup detailPopup = new DetailPopup();
    public MapToolbar() { this(SimmcMapConfig.defaults()); }
    public MapToolbar(SimmcMapConfig config) {
        worldMapEnabled = config.worldMapEnabled();
        worldBackgroundEnabled = config.worldMapBackgroundEnabled();
        minimapBackgroundEnabled = config.minimapBackgroundEnabled();
        layerPanel = new LayerPanel(config.hiddenLayerIds());
    }
    public void applyConfig(SimmcMapConfig config) {
        worldMapEnabled = config.worldMapEnabled();
        worldBackgroundEnabled = config.worldMapBackgroundEnabled();
        minimapBackgroundEnabled = config.minimapBackgroundEnabled();
        layerPanel.replaceHidden(config.hiddenLayerIds());
    }
    public boolean worldMapEnabled() { return worldMapEnabled; }
    public boolean worldBackgroundEnabled() { return worldBackgroundEnabled; }
    public boolean minimapBackgroundEnabled() { return minimapBackgroundEnabled; }
    public void toggleWorldMap() { worldMapEnabled = !worldMapEnabled; }
    public void toggleWorldBackground() { worldBackgroundEnabled = !worldBackgroundEnabled; }
    public void toggleMinimapBackground() { minimapBackgroundEnabled = !minimapBackgroundEnabled; }
    public Panel openPanel() { return openPanel; }
    public void openPanel(Panel panel) { openPanel = panel == openPanel ? Panel.NONE : panel; }
    public LayerPanel layerPanel() { return layerPanel; }
    public DetailPopup detailPopup() { return detailPopup; }
    public boolean escape() { if (detailPopup.isOpen()) { detailPopup.close(); return true; } if (openPanel != Panel.NONE) { openPanel = Panel.NONE; return true; } return false; }
}
