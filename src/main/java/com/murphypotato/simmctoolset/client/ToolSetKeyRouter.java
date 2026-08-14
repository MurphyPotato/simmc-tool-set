package com.murphypotato.simmctoolset.client;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/** Prefix routing intentionally consumes only a recognized second keyboard key. */
public final class ToolSetKeyRouter {
    private static final long PREFIX_WINDOW_MILLIS = 750;

    public enum Target {
        HOTKEYS,
        ARCANE_HUD,
        SCROLL,
        ACCESSORY,
        BREWING,
        MAP,
        DIAGNOSTICS
    }

    private static KeyBinding prefix;
    private static KeyBinding arcaneHud;
    private static KeyBinding scroll;
    private static KeyBinding accessory;
    private static KeyBinding brewing;
    private static KeyBinding map;
    private static KeyBinding diagnostics;
    private static KeyBinding accessoryDirect;
    private static long prefixDeadline;
    private static boolean prefixDown;
    private static boolean prefixUsed;
    private static Screen observedScreen;

    private ToolSetKeyRouter() {
    }

    public static void register() {
        prefix = register("key.simmc_tool_set.prefix", GLFW.GLFW_KEY_BACKSLASH);
        arcaneHud = register("key.simmc_tool_set.arcane_hud", GLFW.GLFW_KEY_1);
        scroll = register("key.simmc_tool_set.scroll", GLFW.GLFW_KEY_2);
        accessory = register("key.simmc_tool_set.accessory", GLFW.GLFW_KEY_3);
        brewing = register("key.simmc_tool_set.brewing", GLFW.GLFW_KEY_4);
        map = register("key.simmc_tool_set.map", GLFW.GLFW_KEY_5);
        diagnostics = register("key.simmc_tool_set.diagnostics", GLFW.GLFW_KEY_GRAVE_ACCENT);
        accessoryDirect = register("key.simmc_tool_set.accessory_direct", GLFW.GLFW_KEY_0);
    }

    private static KeyBinding register(String translationKey, int defaultKey) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding(
                translationKey, InputUtil.Type.KEYSYM, defaultKey, "key.categories.simmc_tool_set"));
    }

    public static boolean onKey(long window, int keyCode, int scanCode, int action, int modifiers) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || textEntryActive(client.currentScreen)) {
            clear();
            return false;
        }
        boolean isPrefix = prefix.matchesKey(keyCode, scanCode);
        if (action == GLFW.GLFW_RELEASE && isPrefix) {
            boolean openHotkeys = prefixDown && !prefixUsed && System.currentTimeMillis() <= prefixDeadline;
            clear();
            if (openHotkeys) {
                Screen parent = client.currentScreen;
                client.execute(() -> ToolSetClient.openTarget(Target.HOTKEYS, parent));
            }
            return true;
        }
        if (action != GLFW.GLFW_PRESS) return false;
        if (isPrefix) {
            prefixDown = true;
            prefixUsed = false;
            prefixDeadline = System.currentTimeMillis() + PREFIX_WINDOW_MILLIS;
            return true;
        }
        if (prefixDown && (prefixDeadline == 0 || System.currentTimeMillis() > prefixDeadline)) {
            clear();
            return false;
        }
        if (!prefixDown && accessoryDirect.matchesKey(keyCode, scanCode)) {
            Screen parent = client.currentScreen;
            client.execute(() -> ToolSetClient.openTarget(Target.ACCESSORY, parent));
            return true;
        }
        if (!prefixDown) return false;
        Target target = targetFor(keyCode, scanCode);
        if (target == null) {
            clear();
            return false;
        }
        prefixUsed = true;
        clear();
        Screen parent = client.currentScreen;
        client.execute(() -> ToolSetClient.openTarget(target, parent));
        return true;
    }

    public static void onClientTick(MinecraftClient client) {
        if (client.currentScreen != observedScreen) {
            observedScreen = client.currentScreen;
            clear();
        }
        if (prefixDeadline != 0 && System.currentTimeMillis() > prefixDeadline) clear();
    }

    public static void clear() {
        prefixDeadline = 0;
        prefixDown = false;
        prefixUsed = false;
    }

    private static Target targetFor(int keyCode, int scanCode) {
        if (arcaneHud.matchesKey(keyCode, scanCode)) return Target.ARCANE_HUD;
        if (scroll.matchesKey(keyCode, scanCode)) return Target.SCROLL;
        if (accessory.matchesKey(keyCode, scanCode)) return Target.ACCESSORY;
        if (brewing.matchesKey(keyCode, scanCode)) return Target.BREWING;
        if (map.matchesKey(keyCode, scanCode)) return Target.MAP;
        if (diagnostics.matchesKey(keyCode, scanCode)) return Target.DIAGNOSTICS;
        return null;
    }

    private static boolean textEntryActive(Screen screen) {
        if (screen == null) return false;
        Element focused = screen.getFocused();
        if (focused instanceof TextFieldWidget) return true;
        String name = screen.getClass().getName();
        return name.endsWith("ChatScreen") || name.endsWith("BookEditScreen") || name.endsWith("SignEditScreen");
    }
}
