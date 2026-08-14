/*
 * Derived in part from JR1258/EarthMC-Map-Addon, upstream commit
 * c85c5003855eb47868868b931624b951cffba74e, Apache-2.0.
 * Modified for generic SIMMC layers and full squaremap marker geometry.
 */
package com.murphypotato.simmctoolset.internal.map.parse;

import com.murphypotato.simmctoolset.internal.map.model.IconMarker;
import com.murphypotato.simmctoolset.internal.map.model.MapLayer;
import com.murphypotato.simmctoolset.internal.map.model.MapMarker;
import com.murphypotato.simmctoolset.internal.map.model.MapPoint;
import com.murphypotato.simmctoolset.internal.map.model.MapSnapshot;
import com.murphypotato.simmctoolset.internal.map.model.MarkerStyle;
import com.murphypotato.simmctoolset.internal.map.model.PolygonMarker;
import com.murphypotato.simmctoolset.internal.map.model.PolygonPart;
import com.murphypotato.simmctoolset.internal.map.model.PolylineMarker;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class SquaremapMarkersParser {
    private static final int DEFAULT_COLOR = 0xFFFFFF;

    public MapSnapshot parse(String json) {
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonArray()) {
                throw new IllegalArgumentException("markers JSON must be a top-level array");
            }
            List<MapLayer> layers = new ArrayList<>();
            for (JsonElement layerElement : root.getAsJsonArray()) {
                if (!layerElement.isJsonObject()) {
                    continue;
                }
                MapLayer layer = parseLayer(layerElement.getAsJsonObject());
                if (layer != null) {
                    layers.add(layer);
                }
            }
            return new MapSnapshot(layers);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid squaremap markers JSON", exception);
        }
    }

    private static MapLayer parseLayer(JsonObject layer) {
        try {
            String id = requiredString(layer, "id");
            String name = optionalString(layer, "name", id);
            JsonArray markerArray = requiredArray(layer, "markers");
            List<MapMarker> markers = new ArrayList<>();
            for (JsonElement markerElement : markerArray) {
                if (!markerElement.isJsonObject()) {
                    continue;
                }
                try {
                    MapMarker marker = parseMarker(id, markerElement.getAsJsonObject());
                    if (marker != null) {
                        markers.add(marker);
                    }
                } catch (RuntimeException ignoredMalformedMarker) {
                    // A single bad marker must not discard valid siblings or its layer.
                }
            }
            return new MapLayer(
                    id,
                    name,
                    optionalInt(layer, "order", 0),
                    optionalInt(layer, "z_index", 0),
                    optionalBoolean(layer, "hide", false),
                    optionalBoolean(layer, "control", false),
                    optionalLong(layer, "timestamp", 0),
                    markers
            );
        } catch (RuntimeException ignoredMalformedLayer) {
            return null;
        }
    }

    private static MapMarker parseMarker(String layerId, JsonObject marker) {
        String type = requiredString(marker, "type").toLowerCase(Locale.ROOT);
        return switch (type) {
            case "polygon" -> parsePolygon(layerId, marker);
            case "polyline" -> parsePolyline(marker);
            case "icon" -> parseIcon(marker);
            default -> null;
        };
    }

    private static PolygonMarker parsePolygon(String layerId, JsonObject marker) {
        JsonArray points = requiredArray(marker, "points");
        List<PolygonPart> parts = new ArrayList<>();
        if (!points.isEmpty() && points.get(0).isJsonArray()
                && !points.get(0).getAsJsonArray().isEmpty()
                && isPoint(points.get(0).getAsJsonArray().get(0))) {
            parts.add(parsePolygonPart(points));
        } else {
            for (JsonElement partElement : points) {
                if (!partElement.isJsonArray()) {
                    throw new IllegalArgumentException("polygon part must be an array");
                }
                parts.add(parsePolygonPart(partElement.getAsJsonArray()));
            }
        }
        if (parts.isEmpty()) {
            throw new IllegalArgumentException("polygon requires at least one part");
        }
        String rawPopup = text(marker, "popup");
        HtmlTextSanitizer.SanitizedDocument sanitizedPopup = HtmlTextSanitizer.parse(rawPopup);
        Optional<com.murphypotato.simmctoolset.internal.map.model.LandDetails> details = Optional.empty();
        if ("lands_world".equals(layerId)) {
            try {
                details = new LandDetailsParser().parse(sanitizedPopup);
            } catch (RuntimeException ignoredUnsafeDetails) {
                // Keep the safe generic marker text even if optional structured details cannot be parsed.
            }
        }
        return new PolygonMarker(
                parts,
                parseStyle(marker),
                sanitizedText(marker, "tooltip"),
                sanitizedPopup.text(),
                details
        );
    }

    private static PolygonPart parsePolygonPart(JsonArray part) {
        List<List<MapPoint>> rings = new ArrayList<>();
        for (JsonElement ringElement : part) {
            if (!ringElement.isJsonArray()) {
                throw new IllegalArgumentException("polygon ring must be an array");
            }
            List<MapPoint> ring = parsePoints(ringElement.getAsJsonArray());
            if (ring.size() < 3) {
                throw new IllegalArgumentException("polygon ring requires at least three points");
            }
            rings.add(ring);
        }
        if (rings.isEmpty()) {
            throw new IllegalArgumentException("polygon part requires an outer ring");
        }
        return new PolygonPart(rings);
    }

    private static boolean isPoint(JsonElement element) {
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            return object.has("x") && object.has("z");
        }
        if (!element.isJsonArray()) {
            return false;
        }
        JsonArray array = element.getAsJsonArray();
        return array.size() >= 2 && array.get(0).isJsonPrimitive() && array.get(1).isJsonPrimitive();
    }

    private static PolylineMarker parsePolyline(JsonObject marker) {
        List<MapPoint> points = parsePoints(requiredArray(marker, "points"));
        if (points.size() < 2) {
            throw new IllegalArgumentException("polyline requires at least two points");
        }
        return new PolylineMarker(
                points, parseStyle(marker), sanitizedText(marker, "tooltip"), sanitizedText(marker, "popup")
        );
    }

    private static IconMarker parseIcon(JsonObject marker) {
        JsonElement tooltipAnchor = marker.get("tooltip_anchor");
        return new IconMarker(
                parsePoint(requiredObject(marker, "point")),
                parsePoint(requiredObject(marker, "size")),
                parsePoint(requiredObject(marker, "anchor")),
                tooltipAnchor == null || tooltipAnchor.isJsonNull()
                        ? Optional.empty()
                        : Optional.of(parsePoint(tooltipAnchor)),
                requiredString(marker, "icon"),
                sanitizedText(marker, "tooltip"),
                sanitizedText(marker, "popup")
        );
    }

    private static List<MapPoint> parsePoints(JsonArray array) {
        List<MapPoint> points = new ArrayList<>();
        for (JsonElement element : array) {
            points.add(parsePoint(element));
        }
        return List.copyOf(points);
    }

    private static MapPoint parsePoint(JsonElement element) {
        if (element.isJsonObject()) {
            JsonObject point = element.getAsJsonObject();
            return new MapPoint(requiredFiniteDouble(point, "x"), requiredFiniteDouble(point, "z"));
        }
        if (element.isJsonArray() && element.getAsJsonArray().size() >= 2) {
            JsonArray point = element.getAsJsonArray();
            return new MapPoint(finiteDouble(point.get(0), "point.x"), finiteDouble(point.get(1), "point.z"));
        }
        throw new IllegalArgumentException("Invalid map point");
    }

    private static MarkerStyle parseStyle(JsonObject marker) {
        int stroke = color(marker, "color", DEFAULT_COLOR);
        int fill = color(marker, "fillColor", stroke);
        double weight = boundedDouble(marker, "weight", 1.0, 0, Double.MAX_VALUE);
        double opacity = boundedDouble(marker, "opacity", 1.0, 0, 1);
        double fillOpacity = boundedDouble(marker, "fillOpacity", 0.3, 0, 1);
        return new MarkerStyle(stroke, fill, weight, opacity, fillOpacity);
    }

    private static int color(JsonObject object, String field, int fallback) {
        String value = text(object, field);
        if (value == null) {
            return fallback;
        }
        String normalized = value.startsWith("#") ? value.substring(1) : value;
        if (normalized.length() != 6) {
            return fallback;
        }
        try {
            return Integer.parseInt(normalized, 16);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static double boundedDouble(JsonObject object, String field, double fallback, double min, double max) {
        JsonElement element = object.get(field);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        try {
            double value = element.getAsDouble();
            return Double.isFinite(value) && value >= min && value <= max ? value : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static JsonObject requiredObject(JsonObject object, String field) {
        JsonElement element = object.get(field);
        if (element == null || !element.isJsonObject()) {
            throw new IllegalArgumentException("Missing or invalid " + field);
        }
        return element.getAsJsonObject();
    }

    private static JsonArray requiredArray(JsonObject object, String field) {
        JsonElement element = object.get(field);
        if (element == null || !element.isJsonArray()) {
            throw new IllegalArgumentException("Missing or invalid " + field);
        }
        return element.getAsJsonArray();
    }

    private static String requiredString(JsonObject object, String field) {
        String value = text(object, field);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing or invalid " + field);
        }
        return value;
    }

    private static String optionalString(JsonObject object, String field, String fallback) {
        String value = text(object, field);
        return value == null ? fallback : value;
    }

    private static String text(JsonObject object, String field) {
        JsonElement element = object.get(field);
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()
                ? element.getAsString()
                : null;
    }

    private static String sanitizedText(JsonObject object, String field) {
        return HtmlTextSanitizer.sanitize(text(object, field));
    }

    private static int optionalInt(JsonObject object, String field, int fallback) {
        JsonElement element = object.get(field);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            return fallback;
        }
        try {
            return element.getAsInt();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static long optionalLong(JsonObject object, String field, long fallback) {
        JsonElement element = object.get(field);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            return fallback;
        }
        try {
            return element.getAsLong();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static boolean optionalBoolean(JsonObject object, String field, boolean fallback) {
        JsonElement element = object.get(field);
        return element != null
                && element.isJsonPrimitive()
                && element.getAsJsonPrimitive().isBoolean()
                ? element.getAsBoolean()
                : fallback;
    }

    private static double requiredFiniteDouble(JsonObject object, String field) {
        JsonElement element = object.get(field);
        if (element == null || element.isJsonNull()) {
            throw new IllegalArgumentException("Missing " + field);
        }
        return finiteDouble(element, field);
    }

    private static double finiteDouble(JsonElement element, String field) {
        double value = element.getAsDouble();
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(field + " must be finite");
        }
        return value;
    }
}
