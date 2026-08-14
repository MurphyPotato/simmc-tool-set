package com.murphypotato.simmctoolset.internal.map.gui;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class LayerPanel {
    public record Layer(String id, String name) { }
    public static final List<Layer> LAYERS = List.of(
            new Layer("squaremap-worldborder", "世界边界"), new Layer("essentials_warps", "RP 活动传送点"),
            new Layer("capitalareas", "国家首都"), new Layer("lands_world", "领地"),
            new Layer("transport_gateways", "港口驿站"), new Layer("religion", "宗教"),
            new Layer("war_regions", "战争领土"));
    private final LinkedHashSet<String> hidden = new LinkedHashSet<>();

    public LayerPanel() { }
    public LayerPanel(Set<String> hidden) { this.hidden.addAll(hidden); }
    public boolean isVisible(String id) { return !hidden.contains(id); }
    public void toggle(String id) { if (!hidden.remove(id)) hidden.add(id); }
    public void replaceHidden(Set<String> values) { hidden.clear(); hidden.addAll(values); }
    public Set<String> hiddenLayerIds() { return Set.copyOf(hidden); }
}
