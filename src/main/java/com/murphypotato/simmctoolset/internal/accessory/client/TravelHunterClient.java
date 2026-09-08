package com.murphypotato.simmctoolset.internal.accessory.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;

/** Internal lifecycle owned exclusively by the Tool Set entrypoint. */
public final class TravelHunterClient {
    private ClientAccessoryController controller;
    private ContainerHighlightManager highlighter;
    private final ToolPreviewSelection previewSelection = new ToolPreviewSelection();

    public void initialize() {
        if (controller != null) return;
        MinecraftClient client = MinecraftClient.getInstance();
        controller = new ClientAccessoryController(client);
        highlighter = new ContainerHighlightManager(controller);
        controller.setGuidanceInvalidator(highlighter::endGuidance);
        ClientTickEvents.END_CLIENT_TICK.register(this::onEndTick);
        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((current, world) -> {
            highlighter.endGuidance();
            controller.resetClientSession();
        });
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            controller.recordBlockInteraction(world, hitResult);
            return ActionResult.PASS;
        });
        registerScreenKeyHandler();
    }

    private void onEndTick(MinecraftClient client) {
        controller.onClientTick();
    }

    private void registerScreenKeyHandler() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof HandledScreen<?> handled) controller.observeHandledScreen(handled);
            highlighter.attach(screen);
            ScreenKeyboardEvents.allowKeyPress(screen).register((current, input) -> true);
        });
    }

    public void open(MinecraftClient client, Screen parent) {
        if (controller == null) initialize();
        Screen current = client.currentScreen;
        if (current instanceof AccessoryToolScreen || current instanceof AccessoryEditorScreen) return;
        if (current instanceof HandledScreen<?> handled) {
            if (!handled.getScreenHandler().getCursorStack().isEmpty()) {
                client.inGameHud.setOverlayMessage(Text.literal("请先放下鼠标上的物品再打开工具"), false);
                return;
            }
            ToolSession session = ToolSession.handled(handled, highlighter, previewSelection);
            if (current instanceof InventoryScreen) controller.scanInventory();
            else {
                controller.captureContainer(handled);
                controller.scanCapturedContainer();
            }
            openTool(client, session);
            return;
        }
        controller.scanInventory();
        ToolSession session = parent == null
                ? ToolSession.world(highlighter, previewSelection)
                : ToolSession.external(parent, highlighter, previewSelection);
        openTool(client, session);
    }

    public void close() {
        if (controller != null) controller.close();
    }

    private void openTool(MinecraftClient client, ToolSession session) {
        Screen current = client.currentScreen;
        if (current instanceof AccessoryToolScreen || current instanceof AccessoryEditorScreen) return;
        client.setScreen(new AccessoryToolScreen(controller, session));
    }
}
