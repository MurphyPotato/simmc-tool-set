package com.murphypotato.simmctoolset.client;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/** Tool Set-only shortcut router. It deliberately does not register Minecraft KeyBindings. */
public final class ToolSetKeyRouter {
    private static final long PREFIX_WINDOW_MILLIS = 750;
    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("simmc-tool-set").resolve("shortcuts.properties");
    private static final Map<String, ShortcutBinding> SHORTCUTS = new LinkedHashMap<>();
    private static long prefixDeadline;
    private static boolean prefixDown;
    private static boolean prefixUsed;
    private static Screen observedScreen;

    public enum Target { ARCANE_HUD, SCROLL, ACCESSORY, BREWING, MAP, DIAGNOSTICS }

    private ToolSetKeyRouter() { }

    public static synchronized void register() {
        SHORTCUTS.clear();
        add("prefix", "组合键前缀", "按住后再按功能键；单独松开打开工具组总控", GLFW.GLFW_KEY_BACKSLASH, null);
        add("arcane_hud", "奥术 HUD", "打开奥术 HUD 设置", GLFW.GLFW_KEY_1, Target.ARCANE_HUD);
        add("scroll", "卷轴计算", "打开卷轴材料计算器", GLFW.GLFW_KEY_2, Target.SCROLL);
        add("accessory", "饰品配装", "打开饰品扫描与配装工具", GLFW.GLFW_KEY_3, Target.ACCESSORY);
        add("brewing", "发酵与厨具", "打开原生发酵与厨具助手", GLFW.GLFW_KEY_4, Target.BREWING);
        add("map", "SIMMC 网页地图", "打开地图状态与覆盖设置", GLFW.GLFW_KEY_5, Target.MAP);
        add("diagnostics", "诊断与日志", "打开本地诊断记录", GLFW.GLFW_KEY_GRAVE_ACCENT, Target.DIAGNOSTICS);
        add("accessory_direct", "饰品工具直达", "不使用组合前缀，直接打开饰品工具", GLFW.GLFW_KEY_0, Target.ACCESSORY);
        add("simes_settings", "Simes 设置兼容入口", "使用 O 打开奥术 HUD 设置", GLFW.GLFW_KEY_O, Target.ARCANE_HUD);
        load();
    }

    private static void add(String id, String label, String description, int defaultKey, Target target) {
        SHORTCUTS.put(id, new ShortcutBinding(id, label, description, defaultKey, defaultKey, 0, target));
    }

    public static boolean onKey(long window, int keyCode, int scanCode, int action, int modifiers) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || textEntryActive(client.currentScreen)) {
            clear();
            return false;
        }
        ShortcutBinding prefix = SHORTCUTS.get("prefix");
        if (prefix == null) return false;
        boolean isPrefix = prefix.matches(keyCode, scanCode);
        if (action == GLFW.GLFW_RELEASE && isPrefix) {
            boolean openOverview = prefixDown && !prefixUsed && System.currentTimeMillis() <= prefixDeadline;
            clear();
            if (openOverview) {
                Screen parent = client.currentScreen;
                client.execute(() -> ToolSetClient.openPanel(ToolSetScreen.Panel.OVERVIEW, parent));
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
        if (prefixDown && System.currentTimeMillis() > prefixDeadline) {
            clear();
            return false;
        }
        if (!prefixDown) {
            ShortcutBinding direct = SHORTCUTS.get("accessory_direct");
            ShortcutBinding simes = SHORTCUTS.get("simes_settings");
            if (direct != null && direct.matches(keyCode, scanCode)) {
                open(client, direct.target());
                return true;
            }
            if (simes != null && simes.matches(keyCode, scanCode)) {
                open(client, simes.target());
                return true;
            }
            return false;
        }
        Target target = targetFor(keyCode, scanCode);
        if (target == null) {
            clear();
            return false;
        }
        prefixUsed = true;
        clear();
        open(client, target);
        return true;
    }

    private static void open(MinecraftClient client, Target target) {
        Screen parent = client.currentScreen;
        client.execute(() -> ToolSetClient.openTarget(target, parent));
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
        for (ShortcutBinding binding : SHORTCUTS.values()) {
            if (binding.target() != null && !binding.id().equals("accessory_direct")
                    && !binding.id().equals("simes_settings") && binding.matches(keyCode, scanCode)) {
                return binding.target();
            }
        }
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

    public static synchronized List<ShortcutBinding> bindings() { return List.copyOf(SHORTCUTS.values()); }

    public static synchronized boolean setBinding(ShortcutBinding binding, int keyCode, int scanCode) {
        if (binding == null || !SHORTCUTS.containsKey(binding.id())) return false;
        binding.set(keyCode, scanCode);
        save();
        DiagnosticLog.info("按键已重绑：" + binding.id() + " -> " + binding.displayName());
        return true;
    }

    public static synchronized void unbind(ShortcutBinding binding) {
        if (binding == null || !SHORTCUTS.containsKey(binding.id())) return;
        binding.set(GLFW.GLFW_KEY_UNKNOWN, 0);
        save();
        DiagnosticLog.info("按键已取消绑定：" + binding.id());
    }

    public static synchronized void resetBinding(ShortcutBinding binding) {
        if (binding == null || !SHORTCUTS.containsKey(binding.id())) return;
        binding.set(binding.defaultKey(), 0);
        save();
        DiagnosticLog.info("按键已恢复默认：" + binding.id());
    }

    public static synchronized boolean conflicts(ShortcutBinding selected, int keyCode, int scanCode) {
        return SHORTCUTS.values().stream().anyMatch(binding -> binding != selected && binding.matches(keyCode, scanCode));
    }

    private static void load() {
        if (!Files.isRegularFile(FILE)) return;
        Properties values = new Properties();
        try (Reader reader = Files.newBufferedReader(FILE)) {
            values.load(reader);
            for (ShortcutBinding binding : SHORTCUTS.values()) {
                binding.set(parse(values.getProperty(binding.id() + ".key"), binding.defaultKey()),
                        parse(values.getProperty(binding.id() + ".scan"), 0));
            }
        } catch (IOException error) {
            DiagnosticLog.error("Could not read Tool Set shortcuts", error);
        }
    }

    private static void save() {
        Properties values = new Properties();
        for (ShortcutBinding binding : SHORTCUTS.values()) {
            values.setProperty(binding.id() + ".key", Integer.toString(binding.keyCode()));
            values.setProperty(binding.id() + ".scan", Integer.toString(binding.scanCode()));
        }
        try {
            Files.createDirectories(FILE.getParent());
            try (Writer writer = Files.newBufferedWriter(FILE)) { values.store(writer, "simMC Tool Set shortcuts"); }
        } catch (IOException error) {
            DiagnosticLog.error("Could not save Tool Set shortcuts", error);
        }
    }

    private static int parse(String value, int fallback) {
        if (value == null) return fallback;
        try { return Integer.parseInt(value); } catch (NumberFormatException ignored) { return fallback; }
    }

    public static final class ShortcutBinding {
        private final String id;
        private final String label;
        private final String description;
        private final int defaultKey;
        private final Target target;
        private int keyCode;
        private int scanCode;

        private ShortcutBinding(String id, String label, String description, int defaultKey, int keyCode,
                                int scanCode, Target target) {
            this.id = id;
            this.label = label;
            this.description = description;
            this.defaultKey = defaultKey;
            this.keyCode = keyCode;
            this.scanCode = scanCode;
            this.target = target;
        }

        public String id() { return id; }
        public String label() { return label; }
        public String description() { return description; }
        public int defaultKey() { return defaultKey; }
        public int keyCode() { return keyCode; }
        public int scanCode() { return scanCode; }
        public Target target() { return target; }
        public boolean matches(int key, int scan) { return keyCode != GLFW.GLFW_KEY_UNKNOWN && keyCode == key && (scanCode == 0 || scanCode == scan); }
        public String displayName() { return keyName(keyCode); }
        private void set(int key, int scan) { keyCode = key; scanCode = scan; }
    }

    private static String keyName(int key) {
        if (key == GLFW.GLFW_KEY_UNKNOWN) return "未绑定";
        if (key == GLFW.GLFW_KEY_BACKSLASH) return "\\";
        if (key == GLFW.GLFW_KEY_GRAVE_ACCENT) return "`";
        if (key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9) return Integer.toString(key - GLFW.GLFW_KEY_0);
        if (key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z) return Character.toString((char) ('A' + key - GLFW.GLFW_KEY_A));
        return "键码 " + key;
    }
}
