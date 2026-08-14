package com.murphypotato.simmctoolset.internal.scroll.client;

import com.murphypotato.simmctoolset.internal.scroll.config.SettingsStorage;
import com.murphypotato.simmctoolset.internal.scroll.domain.GameData;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

public final class ArcaneScrollCalculatorClient {
    private ArcaneController controller;

    public void initialize() {
        if (controller != null) return;
        GameData data = GameData.load();
        SettingsStorage storage = new SettingsStorage(
            FabricLoader.getInstance().getConfigDir()
                .resolve("simmc-arcane-scroll-calculator")
                .resolve("settings-v1.1.1-fabric.json"),
            data
        );
        controller = new ArcaneController(data, storage);
    }

    public void open(MinecraftClient client, Screen parent) {
        if (controller == null) initialize();
        if (client.currentScreen instanceof CalculatorScreen) return;
        client.setScreen(new CalculatorScreen(controller, parent));
    }

    public void close() {
        if (controller != null) controller.close();
    }
}
