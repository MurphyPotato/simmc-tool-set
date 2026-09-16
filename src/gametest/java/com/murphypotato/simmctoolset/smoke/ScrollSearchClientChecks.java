package com.murphypotato.simmctoolset.smoke;

import com.murphypotato.simmctoolset.internal.scroll.client.ArcaneController;
import com.murphypotato.simmctoolset.internal.scroll.client.CalculatorScreen;
import com.murphypotato.simmctoolset.internal.scroll.client.CalculationResult;
import com.murphypotato.simmctoolset.internal.scroll.config.SettingsStorage;
import com.murphypotato.simmctoolset.internal.scroll.domain.GameData;
import com.murphypotato.simmctoolset.internal.scroll.domain.SearchBudget;
import com.murphypotato.simmctoolset.internal.scroll.domain.PlanningStatus;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.UUID;

/** Isolated client smoke checks for the nonlinear scroll search screen. */
final class ScrollSearchClientChecks {
    private ScrollSearchClientChecks() {}

    static void run(ClientGameTestContext context, Screen parent, String prefix) {
        ArcaneController controller = context.computeOnClient(client -> {
            // Keep all generated files below the isolated Fabric gametest run directory.
            Path root = client.runDirectory.toPath()
                .resolve("scroll-search-smoke-" + UUID.randomUUID());
            GameData data = GameData.load();
            SettingsStorage storage = new SettingsStorage(root.resolve("scroll-settings.json"), data);
            return new ArcaneController(data, storage, root.resolve("scroll-usage.json"));
        });
        try {
            for (int scenario = 0; scenario < 3; scenario++) {
                int quantity = scenario == 1 ? 64 : 1;
                String recipe = scenario == 2 ? "混乱射线卷轴" : "治愈术卷轴";
                context.runOnClient(client -> {
                    controller.updateSettings(controller.settings().withSelectedRecipe(recipe)
                        .withInputs(quantity, true, controller.settings().repeatThreshold())
                        .withSearchBudget(SearchBudget.FAST));
                    CalculatorScreen screen = new CalculatorScreen(controller, parent);
                    client.setScreen(screen);
                    require(client.currentScreen == screen, "Calculator screen was not opened");
                    requireWidgetsInside(screen);
                    clickCalculate(screen);
                });
                for (int tick = 0; tick < 200; tick++) {
                    context.waitTicks(2);
                    if (context.computeOnClient(client -> !controller.calculating())) break;
                }
                context.runOnClient(client -> {
                    require(!controller.calculating(), "Search did not finish: " + controller.status());
                    Screen screen = client.currentScreen;
                    Object result = privateField(screen, "result");
                    require(result instanceof CalculationResult, "Calculator result was not populated");
                    CalculationResult calculation = (CalculationResult) result;
                    require(calculation.planning() != null, "Planning result missing");
                    if (recipe.equals("混乱射线卷轴")) {
                        require(calculation.planning().status() == PlanningStatus.NO_FEASIBLE_PLAN,
                            "Ratio conflict was not identified");
                        require(!calculation.planning().explanation().isBlank(), "Missing conflict explanation");
                        require(calculation.planning().plan().batches().isEmpty(), "Infeasible plan shown as executable");
                    } else {
                        require(calculation.planning().plan().complete(), "Healing plan incomplete: " + controller.status());
                        require(calculation.planning().plan().plannedCrafts() == quantity, "Wrong target count");
                    }
                    requireWidgetsInside(screen);
                });
                context.takeScreenshot(prefix + "-scroll-search-" + scenario);
                context.getInput().pressKey(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE);
                context.runOnClient(client -> require(client.currentScreen == parent, "Calculator lost its parent"));
            }
        } finally {
            context.runOnClient(client -> {
                controller.close();
                client.setScreen(parent);
            });
        }
    }

    private static void clickCalculate(CalculatorScreen screen) {
        for (var child : screen.children()) {
            if (child instanceof ButtonWidget button
                && Text.literal("计算").getString().equals(button.getMessage().getString())) {
                button.onPress(new net.minecraft.client.input.KeyInput(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, 0, 0));
                return;
            }
        }
        throw new AssertionError("计算按钮未找到");
    }

    private static Object privateField(Object target, String name) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("无法读取计算器结果", error);
        }
    }

    private static void requireWidgetsInside(Screen screen) {
        for (var child : screen.children()) {
            if (child instanceof net.minecraft.client.gui.widget.ClickableWidget widget && widget.visible) {
                require(widget.getX() >= 0 && widget.getY() >= 0
                    && widget.getX() + widget.getWidth() <= screen.width
                    && widget.getY() + widget.getHeight() <= screen.height,
                    "卷轴计算器控件越界：" + widget.getMessage().getString());
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
