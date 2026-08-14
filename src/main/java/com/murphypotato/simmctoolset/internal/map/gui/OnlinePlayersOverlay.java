package com.murphypotato.simmctoolset.internal.map.gui;

import com.murphypotato.simmctoolset.internal.map.model.MapPoint;
import com.murphypotato.simmctoolset.internal.map.model.OnlinePlayerEntry;
import java.util.List;
import java.util.Optional;

public final class OnlinePlayersOverlay {
    public record Row(String name, String status, Optional<MapPoint> position) {
        public boolean canLocate() { return position.isPresent(); }
        public boolean canCreateWaypoint() { return position.isPresent(); }
    }
    private List<Row> rows = List.of();
    private boolean available;
    public void update(List<OnlinePlayerEntry> players, boolean available) {
        this.available = available;
        rows = !available ? List.of() : players.stream().filter(p -> "minecraft_overworld".equals(p.worldKey()))
                .map(p -> new Row(p.name(), p.position().isPresent() ? "公开地图坐标" : "仅在线状态", p.position())).toList();
    }
    public List<Row> rows() { return rows; }
    public boolean available() { return available; }
}
