package com.murphypotato.simmctoolset.client;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Small local state store for Tool Set switches; no player data leaves the machine. */
public final class ToolSetSettings {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("simmc-tool-set").resolve("settings.properties");
    private static final Properties VALUES = load();

    private ToolSetSettings() {
    }

    public static synchronized boolean arcaneHudEnabled() {
        return value("arcaneHudEnabled", true);
    }

    public static synchronized boolean brewingEnabled() {
        return fermentationEnabled() || cookwareEnabled();
    }

    public static synchronized boolean fermentationEnabled() {
        return value("fermentationEnabled", true);
    }

    public static synchronized boolean cookwareEnabled() {
        return value("cookwareEnabled", true);
    }

    public static synchronized boolean mapExperimentalEnabled() {
        return value("mapExperimentalEnabled", false);
    }

    public static synchronized void setArcaneHudEnabled(boolean enabled) {
        set("arcaneHudEnabled", enabled);
    }

    public static synchronized void setBrewingEnabled(boolean enabled) {
        setFermentationEnabled(enabled);
        setCookwareEnabled(enabled);
    }

    public static synchronized void setFermentationEnabled(boolean enabled) {
        set("fermentationEnabled", enabled);
    }

    public static synchronized void setCookwareEnabled(boolean enabled) {
        set("cookwareEnabled", enabled);
    }

    public static synchronized void setMapExperimentalEnabled(boolean enabled) {
        set("mapExperimentalEnabled", enabled);
    }

    private static boolean value(String key, boolean fallback) {
        return Boolean.parseBoolean(VALUES.getProperty(key, Boolean.toString(fallback)));
    }

    private static void set(String key, boolean enabled) {
        VALUES.setProperty(key, Boolean.toString(enabled));
        try {
            Files.createDirectories(FILE.getParent());
            try (Writer writer = Files.newBufferedWriter(FILE)) {
                VALUES.store(writer, "simMC Tool Set local settings");
            }
        } catch (IOException error) {
            DiagnosticLog.error("Could not save Tool Set settings", error);
        }
    }

    private static Properties load() {
        Properties values = new Properties();
        if (!Files.isRegularFile(FILE)) return values;
        try (Reader reader = Files.newBufferedReader(FILE)) {
            values.load(reader);
            if (!values.containsKey("fermentationEnabled") && values.containsKey("brewingEnabled")) {
                String legacy = values.getProperty("brewingEnabled");
                values.setProperty("fermentationEnabled", legacy);
                values.setProperty("cookwareEnabled", legacy);
            }
        } catch (IOException error) {
            DiagnosticLog.error("Could not read Tool Set settings", error);
        }
        return values;
    }
}
