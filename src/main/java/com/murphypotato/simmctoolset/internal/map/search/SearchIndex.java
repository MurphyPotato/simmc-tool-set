package com.murphypotato.simmctoolset.internal.map.search;

import com.murphypotato.simmctoolset.internal.map.model.IconMarker;
import com.murphypotato.simmctoolset.internal.map.model.MapLayer;
import com.murphypotato.simmctoolset.internal.map.model.MapMarker;
import com.murphypotato.simmctoolset.internal.map.model.MapPoint;
import com.murphypotato.simmctoolset.internal.map.model.MapSnapshot;
import com.murphypotato.simmctoolset.internal.map.model.OnlinePlayerEntry;
import com.murphypotato.simmctoolset.internal.map.model.PolygonMarker;
import com.murphypotato.simmctoolset.internal.map.model.PolylineMarker;
import com.murphypotato.simmctoolset.internal.map.model.SearchEntry;
import com.murphypotato.simmctoolset.internal.map.render.MapPoint2D;
import com.murphypotato.simmctoolset.internal.map.render.PolygonRenderCache;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class SearchIndex {
    public static final String ONLINE_PLAYERS_LAYER_ID = "online_players";
    private static final String OVERWORLD_KEY = "minecraft_overworld";
    private static final List<String> LAYER_PRIORITY = List.of(
            "lands_world",
            "capitalareas",
            "transport_gateways",
            "essentials_warps",
            "religion",
            "war_regions",
            ONLINE_PLAYERS_LAYER_ID
    );
    private static final Set<String> MARKER_LAYERS = Set.copyOf(
            LAYER_PRIORITY.subList(0, LAYER_PRIORITY.size() - 1)
    );
    private static final Map<String, Integer> PRIORITY_BY_LAYER = buildPriorities();
    private static final Comparator<SearchEntry> ENTRY_ORDER = Comparator
            .comparingInt((SearchEntry entry) -> PRIORITY_BY_LAYER.getOrDefault(entry.layerId(), Integer.MAX_VALUE))
            .thenComparing(SearchEntry::displayName)
            .thenComparing(SearchEntry::stableKey);

    private final List<SearchEntry> entries;
    private final Map<String, SearchEntry> entriesByStableKey;

    private SearchIndex(LinkedHashMap<String, SearchEntry> entriesByStableKey) {
        this.entries = List.copyOf(entriesByStableKey.values());
        this.entriesByStableKey = Map.copyOf(entriesByStableKey);
    }

    public static SearchIndex build(MapSnapshot snapshot, List<OnlinePlayerEntry> players) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(players, "players");
        LinkedHashMap<String, SearchEntry> entries = new LinkedHashMap<>();
        for (MapLayer layer : snapshot.layers()) {
            if (!MARKER_LAYERS.contains(layer.id())) {
                continue;
            }
            for (MapMarker marker : layer.markers()) {
                markerEntry(layer.id(), marker).ifPresent(entry -> entries.putIfAbsent(entry.stableKey(), entry));
            }
        }
        for (OnlinePlayerEntry player : players) {
            playerEntry(player).ifPresent(entry -> entries.putIfAbsent(entry.stableKey(), entry));
        }
        return new SearchIndex(entries);
    }

    public List<SearchEntry> entries() {
        return entries;
    }

    public List<SearchEntry> search(String query) {
        String normalizedQuery = normalize(query);
        if (normalizedQuery.isEmpty()) {
            return List.of();
        }
        return entries.stream()
                .filter(entry -> entry.normalizedName().contains(normalizedQuery))
                .sorted(Comparator
                        .comparingInt((SearchEntry entry) -> matchKind(entry.normalizedName(), normalizedQuery))
                        .thenComparing(ENTRY_ORDER))
                .toList();
    }

    public Optional<SearchEntry> exact(String name) {
        String normalizedName = normalize(name);
        if (normalizedName.isEmpty()) {
            return Optional.empty();
        }
        return entries.stream()
                .filter(entry -> entry.normalizedName().equals(normalizedName))
                .sorted(ENTRY_ORDER)
                .findFirst();
    }

    public Optional<SearchEntry> resolve(String stableKey) {
        if (stableKey == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(entriesByStableKey.get(stableKey));
    }

    public static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static Optional<SearchEntry> markerEntry(String layerId, MapMarker marker) {
        String displayName = displayName(layerId, marker);
        if (displayName.isEmpty()) {
            return Optional.empty();
        }
        try {
            MapPoint center = center(marker);
            String normalizedName = normalize(displayName);
            String stableKey = markerStableKey(layerId, normalizedName, center);
            return Optional.of(new SearchEntry(
                    displayName,
                    normalizedName,
                    layerId,
                    Optional.of(center),
                    stableKey,
                    true,
                    Optional.of(marker)
            ));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private static Optional<SearchEntry> playerEntry(OnlinePlayerEntry player) {
        String displayName = player.name().trim();
        if (displayName.isEmpty()) {
            return Optional.empty();
        }
        String normalizedName = normalize(displayName);
        Optional<MapPoint> destination = OVERWORLD_KEY.equals(player.worldKey())
                ? player.position()
                : Optional.empty();
        String stableKey = ONLINE_PLAYERS_LAYER_ID + ":" + normalizedName + ":" + player.uuid();
        return Optional.of(new SearchEntry(
                displayName,
                normalizedName,
                ONLINE_PLAYERS_LAYER_ID,
                destination,
                stableKey,
                false,
                Optional.empty()
        ));
    }

    private static String displayName(String layerId, MapMarker marker) {
        if ("lands_world".equals(layerId) && marker instanceof PolygonMarker polygon) {
            Optional<String> landName = polygon.landDetails()
                    .flatMap(details -> details.landName())
                    .map(String::trim)
                    .filter(name -> !name.isEmpty());
            if (landName.isPresent()) {
                return landName.get();
            }
        }
        return marker.tooltip().trim();
    }

    private static MapPoint center(MapMarker marker) {
        if (marker instanceof IconMarker icon) {
            return icon.point();
        }
        if (marker instanceof PolygonMarker polygon) {
            MapPoint2D center = PolygonRenderCache.centerOf(polygon);
            return new MapPoint(center.x(), center.z());
        }
        if (marker instanceof PolylineMarker polyline) {
            if (polyline.points().stream().distinct().limit(2).count() < 2) {
                throw new IllegalArgumentException("Polyline has fewer than two distinct points");
            }
            double minX = Double.POSITIVE_INFINITY;
            double minZ = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY;
            double maxZ = Double.NEGATIVE_INFINITY;
            for (MapPoint point : polyline.points()) {
                minX = Math.min(minX, point.x());
                minZ = Math.min(minZ, point.z());
                maxX = Math.max(maxX, point.x());
                maxZ = Math.max(maxZ, point.z());
            }
            return new MapPoint(minX * 0.5 + maxX * 0.5, minZ * 0.5 + maxZ * 0.5);
        }
        throw new IllegalArgumentException("Unsupported marker type");
    }

    private static String markerStableKey(String layerId, String normalizedName, MapPoint center) {
        // Math.round is the stable half-tie rule: for example, -12.5 -> -12 and 12.5 -> 13.
        return layerId + ":" + normalizedName + ":" + Math.round(center.x()) + ":" + Math.round(center.z());
    }

    private static int matchKind(String normalizedName, String query) {
        if (normalizedName.equals(query)) {
            return 0;
        }
        return normalizedName.startsWith(query) ? 1 : 2;
    }

    private static Map<String, Integer> buildPriorities() {
        LinkedHashMap<String, Integer> priorities = new LinkedHashMap<>();
        for (int index = 0; index < LAYER_PRIORITY.size(); index++) {
            priorities.put(LAYER_PRIORITY.get(index), index);
        }
        return Map.copyOf(priorities);
    }
}
