package com.murphypotato.simmctoolset.internal.simes;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.murphypotato.simmctoolset.client.DiagnosticLog;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Arcane-only configuration with a one-way migration from the old Simes HUD file. */
public final class ArcaneHudConfig {
    public static final int CURRENT_CONFIG_VERSION = 2;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_DIRECTORY = "simmc-tool-set";
    private static final String CONFIG_NAME = "arcane-hud.json";

    public int configVersion = CURRENT_CONFIG_VERSION;
    public boolean simesMode;
    public boolean arcaneEnabled = true;
    public boolean arcaneStatusEnabled = true;
    public boolean hideRecognizedArcaneBossBars = true;
    public double cooldownX = -1.0;
    public double cooldownY = -1.0;
    public int cooldownScalePercent = 100;
    public double arcaneStatusX = -1.0;
    public double arcaneStatusY = -1.0;
    public int arcaneStatusScalePercent = 100;
    public double globalCooldownX = -1.0;
    public double globalCooldownY = -1.0;
    public int globalCooldownScalePercent = 100;
    public boolean manaHudEnabled = true;
    public double manaHudX = -1.0;
    public double manaHudY = -1.0;
    public int manaHudScalePercent = 100;

    public static ArcaneHudConfig load() {
        Path file = configFile();
        if (Files.isRegularFile(file)) return read(file);

        ArcaneHudConfig migrated = new ArcaneHudConfig();
        if (!FabricLoader.getInstance().isModLoaded("simes")) {
            Path oldFile = FabricLoader.getInstance().getConfigDir().resolve("simes").resolve("hud.json");
            if (Files.isRegularFile(oldFile)) {
                migrated = readLegacy(oldFile, migrated);
                migrated.save();
            }
        }
        migrated.normalize();
        return migrated;
    }

    private static ArcaneHudConfig read(Path file) {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            ArcaneHudConfig config = GSON.fromJson(reader, ArcaneHudConfig.class);
            if (config == null) config = new ArcaneHudConfig();
            boolean migrated = requiresSchemaWrite(config.configVersion);
            config.normalize();
            if (migrated) config.save();
            return config;
        } catch (Exception error) {
            DiagnosticLog.error("Could not read Arcane HUD config", error);
            return new ArcaneHudConfig();
        }
    }

    static ArcaneHudConfig readLegacy(Path file, ArcaneHudConfig target) {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            var root = JsonParser.parseReader(reader);
            JsonObject json = root.isJsonObject() ? root.getAsJsonObject() : new JsonObject();
            copyBoolean(json, "simesMode", value -> target.simesMode = value);
            copyBoolean(json, "arcaneEnabled", value -> target.arcaneEnabled = value);
            copyBoolean(json, "arcaneStatusEnabled", value -> target.arcaneStatusEnabled = value);
            copyBoolean(json, "hideRecognizedArcaneBossBars", value -> target.hideRecognizedArcaneBossBars = value);
            copyDouble(json, "x", value -> target.cooldownX = value);
            copyDouble(json, "y", value -> target.cooldownY = value);
            copyInt(json, "scalePercent", value -> target.cooldownScalePercent = value);
            copyDouble(json, "arcaneStatusX", value -> target.arcaneStatusX = value);
            copyDouble(json, "arcaneStatusY", value -> target.arcaneStatusY = value);
            copyInt(json, "arcaneStatusScalePercent", value -> target.arcaneStatusScalePercent = value);
            copyDouble(json, "globalCooldownX", value -> target.globalCooldownX = value);
            copyDouble(json, "globalCooldownY", value -> target.globalCooldownY = value);
            copyInt(json, "globalCooldownScalePercent", value -> target.globalCooldownScalePercent = value);
            copyBoolean(json, "manaHudEnabled", value -> target.manaHudEnabled = value);
            copyDouble(json, "manaHudX", value -> target.manaHudX = value);
            copyDouble(json, "manaHudY", value -> target.manaHudY = value);
            copyInt(json, "manaHudScalePercent", value -> target.manaHudScalePercent = value);
        } catch (Exception error) {
            DiagnosticLog.error("Could not migrate legacy Arcane HUD config", error);
        }
        target.normalize();
        return target;
    }

    private static void copyBoolean(JsonObject json, String key, BooleanConsumer consumer) {
        if (json.has(key) && json.get(key).isJsonPrimitive()) consumer.accept(json.get(key).getAsBoolean());
    }

    private static void copyDouble(JsonObject json, String key, DoubleConsumer consumer) {
        if (json.has(key) && json.get(key).isJsonPrimitive()) consumer.accept(json.get(key).getAsDouble());
    }

    private static void copyInt(JsonObject json, String key, IntConsumer consumer) {
        if (json.has(key) && json.get(key).isJsonPrimitive()) consumer.accept(json.get(key).getAsInt());
    }

    public void normalize() {
        cooldownX = coordinate(cooldownX);
        cooldownY = coordinate(cooldownY);
        arcaneStatusX = coordinate(arcaneStatusX);
        arcaneStatusY = coordinate(arcaneStatusY);
        globalCooldownX = coordinate(globalCooldownX);
        globalCooldownY = coordinate(globalCooldownY);
        manaHudX = coordinate(manaHudX);
        manaHudY = coordinate(manaHudY);
        cooldownScalePercent = scale(cooldownScalePercent);
        arcaneStatusScalePercent = scale(arcaneStatusScalePercent);
        globalCooldownScalePercent = scale(globalCooldownScalePercent);
        manaHudScalePercent = scale(manaHudScalePercent);
        configVersion = CURRENT_CONFIG_VERSION;
    }

    static double coordinate(double value) {
        return Double.isFinite(value) && value >= -1.0 && value <= 1.0 ? value : -1.0;
    }

    static int scale(int value) {
        return Math.max(50, Math.min(200, value));
    }

    static boolean requiresSchemaWrite(int version) {
        return version < CURRENT_CONFIG_VERSION;
    }

    public void save() {
        Path file = configFile();
        try {
            normalize();
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException error) {
            DiagnosticLog.error("Could not save Arcane HUD config", error);
        }
    }

    public void resetCooldownPosition() {
        cooldownX = -1.0;
        cooldownY = -1.0;
    }

    public void resetArcaneStatusPosition() {
        arcaneStatusX = -1.0;
        arcaneStatusY = -1.0;
    }

    public void resetGlobalCooldownPosition() {
        globalCooldownX = -1.0;
        globalCooldownY = -1.0;
    }

    public void resetManaHudPosition() {
        manaHudX = -1.0;
        manaHudY = -1.0;
    }

    static Path configFile() {
        return FabricLoader.getInstance().getConfigDir().resolve(CONFIG_DIRECTORY).resolve(CONFIG_NAME);
    }

    @FunctionalInterface
    private interface BooleanConsumer {
        void accept(boolean value);
    }

    @FunctionalInterface
    private interface DoubleConsumer {
        void accept(double value);
    }

    @FunctionalInterface
    private interface IntConsumer {
        void accept(int value);
    }
}
