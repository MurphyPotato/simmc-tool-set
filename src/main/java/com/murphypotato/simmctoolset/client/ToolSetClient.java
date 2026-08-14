package com.murphypotato.simmctoolset.client;

import com.mojang.brigadier.Command;
import com.murphypotato.simmctoolset.internal.accessory.client.TravelHunterClient;
import com.murphypotato.simmctoolset.internal.scroll.client.ArcaneScrollCalculatorClient;
import com.murphypotato.simmctoolset.internal.simes.SimesFeatureController;
import com.murphypotato.simmctoolset.map.MapCompatibility;
import com.murphypotato.simmctoolset.map.MapModule;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

/** The only Fabric client entrypoint for the single-JAR Tool Set. */
public final class ToolSetClient implements ClientModInitializer {
    public static final String MOD_ID = "simmc_tool_set";

    private static ArcaneScrollCalculatorClient scrollClient;
    private static TravelHunterClient accessoryClient;
    private static boolean scrollInternal;
    private static boolean accessoryInternal;
    private static boolean mapInternal;

    @Override
    public void onInitializeClient() {
        DiagnosticLog.initialize();
        ToolSetKeyRouter.register();
        SimesFeatureController.initialize();

        scrollInternal = !FabricLoader.getInstance().isModLoaded("simmc_arcane_scroll_calculator");
        accessoryInternal = !FabricLoader.getInstance().isModLoaded("simmc_travel_hunter_accessory_tool");
        if (scrollInternal) {
            scrollClient = new ArcaneScrollCalculatorClient();
            scrollClient.initialize();
        }
        if (accessoryInternal) {
            accessoryClient = new TravelHunterClient();
            accessoryClient.initialize();
        }
        mapInternal = MapCompatibility.shouldInitializeInternalMap() && MapModule.isAvailable();
        if (mapInternal) MapModule.initialize();

        ClientTickEvents.END_CLIENT_TICK.register(ToolSetKeyRouter::onClientTick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(ignored -> {
            ToolSetKeyRouter.clear();
            SimesFeatureController.reset();
            if (scrollClient != null) scrollClient.close();
            if (accessoryClient != null) accessoryClient.close();
            if (mapInternal) MapModule.close();
        });
        registerCommand();
        DiagnosticLog.info("Tool Set initialized: scroll=" + moduleSource(scrollInternal, "simmc_arcane_scroll_calculator")
                + ", accessory=" + moduleSource(accessoryInternal, "simmc_travel_hunter_accessory_tool")
                + ", map=" + MapCompatibility.status().displayName());
    }

    private static String moduleSource(boolean internal, String externalId) {
        if (internal) return "internal";
        return ExternalScreenBridge.isAvailable(externalId) ? "external bridge" : "external incompatible";
    }

    private static void registerCommand() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommandManager.literal("simmctools").executes(context -> {
                    MinecraftClient client = context.getSource().getClient();
                    client.execute(() -> openPanel(ToolSetScreen.Panel.OVERVIEW, client.currentScreen));
                    return Command.SINGLE_SUCCESS;
                }).then(ClientCommandManager.literal("map")
                        .then(ClientCommandManager.literal("refresh").executes(context -> {
                            if (mapInternal) MapModule.requestRefresh();
                            return Command.SINGLE_SUCCESS;
                        }))
                        .then(ClientCommandManager.literal("toggle").executes(context -> {
                            if (mapInternal) MapModule.toggleWorldMap();
                            return Command.SINGLE_SUCCESS;
                        }))
                        .then(ClientCommandManager.literal("background").executes(context -> {
                            if (mapInternal) MapModule.toggleWorldBackground();
                            return Command.SINGLE_SUCCESS;
                        }))
                        .then(ClientCommandManager.literal("minimap").executes(context -> {
                            if (mapInternal) MapModule.toggleMinimapBackground();
                            return Command.SINGLE_SUCCESS;
                        })))
        ));
    }

    public static void openTarget(ToolSetKeyRouter.Target target, Screen parent) {
        MinecraftClient client = MinecraftClient.getInstance();
        switch (target) {
            case HOTKEYS -> openPanel(ToolSetScreen.Panel.HOTKEYS, parent);
            case ARCANE_HUD -> openPanel(ToolSetScreen.Panel.ARCANE_HUD, parent);
            case SCROLL -> openScroll(client, parent);
            case ACCESSORY -> openAccessory(client, parent);
            case BREWING -> openPanel(ToolSetScreen.Panel.BREWING, parent);
            case MAP -> openPanel(ToolSetScreen.Panel.MAP, parent);
            case DIAGNOSTICS -> openPanel(ToolSetScreen.Panel.DIAGNOSTICS, parent);
        }
    }

    public static void openPanel(ToolSetScreen.Panel panel, Screen parent) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen instanceof ToolSetScreen existing) {
            existing.select(panel);
            return;
        }
        client.setScreen(new ToolSetScreen(parent, panel));
    }

    public static void openScroll(MinecraftClient client, Screen parent) {
        if (scrollInternal) {
            scrollClient.open(client, parent);
            return;
        }
        if (!ExternalScreenBridge.open("simmc_arcane_scroll_calculator", parent)) {
            openPanel(ToolSetScreen.Panel.DIAGNOSTICS, parent);
        }
    }

    public static void openAccessory(MinecraftClient client, Screen parent) {
        if (accessoryInternal) {
            accessoryClient.open(client, parent);
            return;
        }
        if (!ExternalScreenBridge.open("simmc_travel_hunter_accessory_tool", parent)) {
            openPanel(ToolSetScreen.Panel.DIAGNOSTICS, parent);
        }
    }

    public static boolean isScrollInternal() {
        return scrollInternal;
    }

    public static String scrollStatus() {
        return ExternalScreenBridge.status(
                "simmc_arcane_scroll_calculator", "Arcane scroll calculator", "2.1.0-fabric");
    }

    public static boolean isAccessoryInternal() {
        return accessoryInternal;
    }

    public static String accessoryStatus() {
        return ExternalScreenBridge.status(
                "simmc_travel_hunter_accessory_tool", "Travel Hunter accessory tool", "6.1.0-fabric");
    }

    public static boolean isMapInternal() {
        return mapInternal;
    }

    public static boolean mapWorldOverlayEnabled() {
        return mapInternal && MapModule.worldMapEnabled();
    }

    public static boolean mapWorldBackgroundEnabled() {
        return mapInternal && MapModule.worldBackgroundEnabled();
    }

    public static boolean mapMinimapBackgroundEnabled() {
        return mapInternal && MapModule.minimapBackgroundEnabled();
    }

    public static void toggleMapWorldOverlay() {
        if (mapInternal) MapModule.toggleWorldMap();
    }

    public static void toggleMapWorldBackground() {
        if (mapInternal) MapModule.toggleWorldBackground();
    }

    public static void toggleMapMinimapBackground() {
        if (mapInternal) MapModule.toggleMinimapBackground();
    }

    public static void refreshMap() {
        if (mapInternal) MapModule.requestRefresh();
    }
}
