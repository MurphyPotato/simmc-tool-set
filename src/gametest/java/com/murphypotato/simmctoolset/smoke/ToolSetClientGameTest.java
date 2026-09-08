package com.murphypotato.simmctoolset.smoke;

import com.murphypotato.simmctoolset.client.DiagnosticLog;
import com.murphypotato.simmctoolset.client.ToolSetClient;
import com.murphypotato.simmctoolset.client.ToolSetKeyRouter;
import com.murphypotato.simmctoolset.client.ToolSetScreen;
import com.murphypotato.simmctoolset.internal.simes.SimesArcaneHudSettingsScreen;
import com.murphypotato.simmctoolset.internal.simes.SimesFeatureController;
import com.murphypotato.simmctoolset.internal.simes.SimesHudLayoutScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ClickableWidget;
import org.lwjgl.glfw.GLFW;

/** Development-only checks. This source set is never included in the release JAR. */
public final class ToolSetClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        context.waitForScreen(TitleScreen.class);
        Screen title = context.computeOnClient(client -> client.currentScreen);
        for (int[] size : new int[][] {{1280, 720}, {854, 480}}) {
            context.getInput().resizeWindow(size[0], size[1]);
            context.runOnClient(client -> {
                client.options.getGuiScale().setValue(2);
                client.onResolutionChanged();
            });
            String prefix = size[0] + "x" + size[1];
            for (ToolSetScreen.Panel panel : ToolSetScreen.Panel.values()) {
                context.setScreen(() -> new ToolSetScreen(title, panel));
                checkAndCapture(context, prefix + "-" + panel.name());
                context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
                context.runOnClient(client -> require(client.currentScreen == title, "Panel lost its parent"));
            }
            for (ToolSetKeyRouter.Target target : new ToolSetKeyRouter.Target[] {
                    ToolSetKeyRouter.Target.SCROLL, ToolSetKeyRouter.Target.ACCESSORY}) {
                context.runOnClient(client -> ToolSetClient.openTarget(target, title));
                checkAndCapture(context, prefix + "-tool-" + target.name());
                context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
                context.runOnClient(client -> require(client.currentScreen == title, "Tool lost its parent"));
            }
            context.setScreen(() -> new SimesArcaneHudSettingsScreen(title));
            checkAndCapture(context, prefix + "-simes-settings");
            context.setScreen(() -> new SimesHudLayoutScreen(title));
            checkAndCapture(context, prefix + "-four-hud-layout");
            context.runOnClient(client -> ToolSetClient.openHotkeys(title));
            checkAndCapture(context, prefix + "-hotkeys");
        }
        context.runOnClient(client -> {
            for (int i = 0; i < 150; i++) DiagnosticLog.info("Isolated diagnostic row " + i);
        });
        context.setScreen(() -> new ToolSetScreen(title, ToolSetScreen.Panel.DIAGNOSTICS));
        context.getInput().scroll(100);
        checkAndCapture(context, "diagnostics-scrolled");
        context.setScreen(() -> title);
        try (var world = context.worldBuilder().create()) {
            context.waitTicks(20);
            context.runOnClient(client -> require(!SimesFeatureController.active(),
                    "Target-server module activated in a local world"));
            context.getInput().pressKey(GLFW.GLFW_KEY_BACKSLASH);
            context.waitForScreen(ToolSetScreen.class);
            checkAndCapture(context, "world-prefix-entry");
            context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
            context.waitForScreen(null);
            context.getInput().pressKey(clientOptions -> clientOptions.inventoryKey);
            context.waitForScreen(InventoryScreen.class);
            checkAndCapture(context, "native-inventory");
            context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
            context.waitForScreen(null);
        }
        context.waitForScreen(TitleScreen.class);
    }

    private static void checkAndCapture(ClientGameTestContext context, String name) {
        context.waitTicks(2);
        context.runOnClient(client -> {
            Screen screen = client.currentScreen;
            require(screen != null, "Missing screen: " + name);
            for (var child : screen.children()) {
                if (child instanceof ClickableWidget widget && widget.visible) {
                    require(widget.getX() >= 0 && widget.getY() >= 0
                            && widget.getX() + widget.getWidth() <= screen.width
                            && widget.getY() + widget.getHeight() <= screen.height,
                            "Widget outside screen: " + name + " / " + widget.getMessage().getString());
                }
            }
        });
        context.takeScreenshot(name);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
