package com.murphypotato.simmctoolset.internal.map.parse;

import com.murphypotato.simmctoolset.internal.map.model.SquaremapWorldSettings;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.util.Objects;

public final class SquaremapSettingsParser {
    private static final int DEFAULT_TILE_SIZE = 512;

    public SquaremapWorldSettings parse(String json, String worldKey) {
        Objects.requireNonNull(json, "json");
        Objects.requireNonNull(worldKey, "worldKey");
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonObject zoom = requiredObject(root, "zoom");
            JsonObject spawn = requiredObject(root, "spawn");
            int tileSize = root.has("tile_size") ? requiredPositiveInt(root, "tile_size") : DEFAULT_TILE_SIZE;
            JsonObject playerTracker = optionalObject(root, "player_tracker");

            return new SquaremapWorldSettings(
                    worldKey,
                    tileSize,
                    optionalNonNegativeInt(zoom, "min", 0),
                    requiredNonNegativeInt(zoom, "max", "zoom.max"),
                    requiredNonNegativeInt(zoom, "extra", "zoom.extra"),
                    requiredNonNegativeInt(zoom, zoom.has("def") ? "def" : "default", "zoom.def"),
                    requiredFiniteDouble(spawn, "x", "spawn.x"),
                    requiredFiniteDouble(spawn, "z", "spawn.z"),
                    optionalNonNegativeInt(root, "marker_update_interval", 0),
                    optionalNonNegativeInt(root, "tiles_update_interval", 0),
                    playerTracker != null && optionalBoolean(playerTracker, "enabled", false),
                    playerTracker == null ? 0 : optionalNonNegativeInt(playerTracker, "update_interval", 0)
            );
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (JsonParseException | IllegalStateException exception) {
            throw new IllegalArgumentException("Invalid squaremap settings JSON", exception);
        }
    }

    private static JsonObject requiredObject(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonObject()) {
            throw new IllegalArgumentException("Missing or invalid " + field);
        }
        return value.getAsJsonObject();
    }

    private static JsonObject optionalObject(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || value.isJsonNull()) {
            return null;
        }
        if (!value.isJsonObject()) {
            throw new IllegalArgumentException("Invalid " + field);
        }
        return value.getAsJsonObject();
    }

    private static int requiredPositiveInt(JsonObject object, String field) {
        int value = requiredInt(object, field, field);
        if (value <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }

    private static int requiredNonNegativeInt(JsonObject object, String field, String path) {
        int value = requiredInt(object, field, path);
        if (value < 0) {
            throw new IllegalArgumentException(path + " must be non-negative");
        }
        return value;
    }

    private static int requiredInt(JsonObject object, String field, String path) {
        JsonElement value = object.get(field);
        try {
            if (value == null || value.isJsonNull()) {
                throw new IllegalArgumentException("Missing " + path);
            }
            return value.getAsInt();
        } catch (NumberFormatException | UnsupportedOperationException exception) {
            throw new IllegalArgumentException("Invalid " + path, exception);
        }
    }

    private static int optionalNonNegativeInt(JsonObject object, String field, int defaultValue) {
        if (!object.has(field) || object.get(field).isJsonNull()) {
            return defaultValue;
        }
        return requiredNonNegativeInt(object, field, field);
    }

    private static double requiredFiniteDouble(JsonObject object, String field, String path) {
        JsonElement value = object.get(field);
        try {
            if (value == null || value.isJsonNull()) {
                throw new IllegalArgumentException("Missing " + path);
            }
            double number = value.getAsDouble();
            if (!Double.isFinite(number)) {
                throw new IllegalArgumentException(path + " must be finite");
            }
            return number;
        } catch (NumberFormatException | UnsupportedOperationException exception) {
            throw new IllegalArgumentException("Invalid " + path, exception);
        }
    }

    private static boolean optionalBoolean(JsonObject object, String field, boolean defaultValue) {
        if (!object.has(field) || object.get(field).isJsonNull()) {
            return defaultValue;
        }
        try {
            return object.get(field).getAsBoolean();
        } catch (UnsupportedOperationException exception) {
            throw new IllegalArgumentException("Invalid " + field, exception);
        }
    }
}
