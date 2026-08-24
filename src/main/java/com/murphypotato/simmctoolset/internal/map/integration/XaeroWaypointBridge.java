package com.murphypotato.simmctoolset.internal.map.integration;

import com.murphypotato.simmctoolset.internal.map.model.MapPoint;
import com.murphypotato.simmctoolset.internal.map.model.OnlinePlayerEntry;
import com.murphypotato.simmctoolset.internal.map.model.SearchEntry;
import xaero.common.XaeroMinimapSession;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.waypoint.WaypointColor;
import xaero.hud.minimap.waypoint.WaypointPurpose;

import java.io.IOException;
import java.util.Locale;
import java.util.Optional;

/** Creates real, persistent Xaero waypoints without command or chat fallbacks. */
public final class XaeroWaypointBridge {
    public static final String OVERWORLD = "minecraft:overworld";

    public record WaypointRequest(String name, int x, int y, int z, String dimension) { }

    public enum Result { CREATED, NO_ACTIVE_WORLD, WRONG_DIMENSION, XAERO_UNAVAILABLE, SAVE_FAILED }
    private static volatile WaypointHealth health = new WaypointHealth(false);

    private XaeroWaypointBridge() { }

    public static void initialize(WaypointHealth waypointHealth) {
        health = waypointHealth == null ? new WaypointHealth(false) : waypointHealth;
    }

    public static Optional<WaypointRequest> request(SearchEntry entry) {
        if (entry == null || !entry.canCreateWaypoint()) return Optional.empty();
        return entry.center().map(point -> request(entry.displayName(), point));
    }

    public static Optional<WaypointRequest> request(OnlinePlayerEntry player) {
        if (player == null || !"minecraft_overworld".equals(player.worldKey())) return Optional.empty();
        return player.position().map(point -> request(player.name(), point));
    }

    private static WaypointRequest request(String name, MapPoint point) {
        return new WaypointRequest(sanitizeName(name), (int) Math.round(point.x()), 64,
                (int) Math.round(point.z()), OVERWORLD);
    }

    public static Result create(WaypointRequest request) {
        if (request == null || !health.enabled()) return Result.XAERO_UNAVAILABLE;
        try {
            XaeroMinimapSession session = XaeroMinimapSession.getCurrentSession();
            if (session == null) return Result.XAERO_UNAVAILABLE;
            var minimapSession = session.getMinimapProcessor().getSession();
            if (minimapSession == null) return Result.XAERO_UNAVAILABLE;
            var world = minimapSession.getWorldManager().getCurrentWorld();
            if (world == null) return Result.NO_ACTIVE_WORLD;
            if (world.getDimId() == null || !"minecraft:overworld".equals(world.getDimId().getValue().toString())) {
                return Result.WRONG_DIMENSION;
            }
            var set = world.getCurrentWaypointSet();
            if (set == null) {
                world.addWaypointSet("waypoints");
                world.setCurrentWaypointSetId("waypoints");
                set = world.getCurrentWaypointSet();
            }
            if (set == null) return Result.NO_ACTIVE_WORLD;
            String initials = initials(request.name());
            Waypoint waypoint = new Waypoint(request.x(), request.y(), request.z(), request.name(), initials,
                    WaypointColor.AQUA, WaypointPurpose.NORMAL, false, true);
            set.add(waypoint);
            try {
                minimapSession.getWorldManagerIO().saveWorld(world);
            } catch (IOException exception) {
                set.remove(waypoint);
                return Result.SAVE_FAILED;
            }
            return Result.CREATED;
        } catch (LinkageError | RuntimeException failure) {
            health.fail();
            return Result.XAERO_UNAVAILABLE;
        }
    }

    public static String message(Result result) {
        return switch (result) {
            case CREATED -> "已创建 Xaero 路点";
            case NO_ACTIVE_WORLD -> "Xaero 当前世界尚未就绪";
            case WRONG_DIMENSION -> "只能在主世界创建该路点";
            case SAVE_FAILED -> "Xaero 路点保存失败";
            case XAERO_UNAVAILABLE -> "Xaero 路点接口不可用";
        };
    }

    static String sanitizeName(String value) {
        String cleaned = value == null ? "SIMMC 地点" : value.trim()
                .replace('|', ' ').replace(':', ' ').replace(',', ' ')
                .replaceAll("\\s+", " ");
        if (cleaned.isBlank()) cleaned = "SIMMC 地点";
        return cleaned.length() <= 40 ? cleaned : cleaned.substring(0, 40);
    }

    private static String initials(String name) {
        String compact = name.replaceAll("\\s+", "");
        if (compact.isEmpty()) return "S";
        int end = compact.offsetByCodePoints(0, Math.min(2, compact.codePointCount(0, compact.length())));
        return compact.substring(0, end).toUpperCase(Locale.ROOT);
    }
}
