/*
 * Derived in part from JR1258/EarthMC-Map-Addon, upstream commit
 * c85c5003855eb47868868b931624b951cffba74e, Apache-2.0.
 * Modified for generic SIMMC worlds and privacy-preserving player entries.
 */
package com.murphypotato.simmctoolset.internal.map.parse;

import com.murphypotato.simmctoolset.internal.map.model.MapPoint;
import com.murphypotato.simmctoolset.internal.map.model.OnlinePlayerEntry;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class PlayersParser {
    public List<OnlinePlayerEntry> parse(String json, String worldKey) {
        Objects.requireNonNull(json, "json");
        Objects.requireNonNull(worldKey, "worldKey");
        try {
            JsonElement root = JsonParser.parseString(json);
            JsonArray players = playerArray(root);
            List<OnlinePlayerEntry> entries = new ArrayList<>();
            for (JsonElement element : players) {
                if (!element.isJsonObject()) {
                    continue;
                }
                OnlinePlayerEntry entry = parseEntry(element.getAsJsonObject(), worldKey);
                if (entry != null) {
                    entries.add(entry);
                }
            }
            return List.copyOf(entries);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid squaremap players JSON", exception);
        }
    }

    private static JsonArray playerArray(JsonElement root) {
        if (root.isJsonArray()) {
            return root.getAsJsonArray();
        }
        if (root.isJsonObject()) {
            JsonElement players = root.getAsJsonObject().get("players");
            if (players != null && players.isJsonArray()) {
                return players.getAsJsonArray();
            }
        }
        throw new IllegalArgumentException("players JSON must contain a players array");
    }

    private static OnlinePlayerEntry parseEntry(JsonObject player, String requestedWorldKey) {
        try {
            if (isHiddenOrMalformed(player)) {
                return null;
            }
            String world = string(player, "world");
            if (!requestedWorldKey.equals(world)) {
                return null;
            }
            String name = string(player, "name");
            String uuid = string(player, "uuid");
            if (name == null || name.isBlank() || uuid == null || uuid.isBlank()) {
                return null;
            }
            return new OnlinePlayerEntry(name, uuid, requestedWorldKey, position(player));
        } catch (RuntimeException ignoredMalformedEntry) {
            return null;
        }
    }

    private static Optional<MapPoint> position(JsonObject player) {
        JsonElement nested = player.get("position");
        if (nested != null && nested.isJsonObject()) {
            Optional<MapPoint> point = finitePoint(nested.getAsJsonObject());
            if (point.isPresent()) {
                return point;
            }
        }
        return finitePoint(player);
    }

    private static Optional<MapPoint> finitePoint(JsonObject object) {
        JsonElement xElement = object.get("x");
        JsonElement zElement = object.get("z");
        if (xElement == null || zElement == null || xElement.isJsonNull() || zElement.isJsonNull()) {
            return Optional.empty();
        }
        try {
            double x = xElement.getAsDouble();
            double z = zElement.getAsDouble();
            return Double.isFinite(x) && Double.isFinite(z)
                    ? Optional.of(new MapPoint(x, z))
                    : Optional.empty();
        } catch (RuntimeException ignoredInvalidCoordinate) {
            return Optional.empty();
        }
    }

    private static String string(JsonObject object, String field) {
        JsonElement element = object.get(field);
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()
                ? element.getAsString()
                : null;
    }

    private static boolean isHiddenOrMalformed(JsonObject player) {
        JsonElement hidden = player.get("hidden");
        if (hidden == null) {
            return false;
        }
        return !hidden.isJsonPrimitive()
                || !hidden.getAsJsonPrimitive().isBoolean()
                || hidden.getAsBoolean();
    }
}
