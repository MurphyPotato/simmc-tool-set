package com.murphypotato.simmctoolset.client;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

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
        if (screen instanceof ToolSetHotkeyScreen) return true;
        Element focused = screen.getFocused();
        if (focused instanceof TextFieldWidget) return true;
        String name = screen.getClass().getName();
        return name.endsWith("ChatScreen") || name.endsWith("BookEditScreen") || name.endsWith("SignEditScreen");
    }

    public record BindingEntry(String id, String label, String description, KeyBinding binding) {
    }

    public static List<BindingEntry> bindings() {
        List<BindingEntry> result = new ArrayList<>();
        if (prefix == null) return result;
        result.add(new BindingEntry("prefix", "组合键前缀", "按住后再按功能键；单独松开打开本页", prefix));
        result.add(new BindingEntry("arcane_hud", "奥术 HUD", "打开奥术 HUD 状态页", arcaneHud));
        result.add(new BindingEntry("scroll", "卷轴计算", "打开卷轴材料计算器", scroll));
        result.add(new BindingEntry("accessory", "饰品配装", "打开饰品扫描与配装工具", accessory));
        result.add(new BindingEntry("brewing", "发酵与厨具", "打开发酵和厨具助手设置", brewing));
        result.add(new BindingEntry("map", "SIMMC 网页地图", "打开地图状态与覆盖设置", map));
        result.add(new BindingEntry("diagnostics", "诊断与日志", "打开本地诊断记录", diagnostics));
        result.add(new BindingEntry("accessory_direct", "饰品工具直达", "不使用组合前缀，直接打开饰品工具", accessoryDirect));
        return List.copyOf(result);
    }

    public static boolean setBinding(BindingEntry entry, int keyCode, int scanCode) {
        if (entry == null || entry.binding() == null) return false;
        entry.binding().setBoundKey(InputUtil.fromKeyCode(keyCode, scanCode));
        KeyBinding.updateKeysByCode();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.options != null) client.options.write();
        DiagnosticLog.info("按键已重绑：" + entry.id() + " -> " + entry.binding().getBoundKeyLocalizedText().getString());
        return true;
    }

    public static void unbind(BindingEntry entry) {
        if (entry == null || entry.binding() == null) return;
        entry.binding().setBoundKey(InputUtil.UNKNOWN_KEY);
        KeyBinding.updateKeysByCode();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.options != null) client.options.write();
        DiagnosticLog.info("按键已取消绑定：" + entry.id());
    }

    public static void resetBinding(BindingEntry entry) {
        if (entry == null || entry.binding() == null) return;
        entry.binding().setBoundKey(entry.binding().getDefaultKey());
        KeyBinding.updateKeysByCode();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.options != null) client.options.write();
        DiagnosticLog.info("按键已恢复默认：" + entry.id());
    }

    public static boolean conflicts(BindingEntry selected, int keyCode, int scanCode) {
        for (BindingEntry entry : bindings()) {
            if (entry.binding() != selected.binding() && entry.binding().matchesKey(keyCode, scanCode)) return true;
        }
        return false;
    }
}
