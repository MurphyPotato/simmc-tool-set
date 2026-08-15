package com.murphypotato.simmctoolset.client;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screen.Screen;

import java.util.function.Consumer;

/** Lazy ObjectShare lookup. A standalone module owns its own lifecycle when present. */
final class ExternalScreenBridge {
    private static final String ACCESSORY_ID = "simmc_travel_hunter_accessory_tool";
    private static final String SCROLL_ID = "simmc_arcane_scroll_calculator";
    private static final String SUFFIX = ":screen_opener_v1";
    private static final String ACCESSORY_MIN_VERSION = "6.1.0-fabric";
    private static final String SCROLL_MIN_VERSION = "2.1.0-fabric";

    private ExternalScreenBridge() {
    }

    static boolean isAvailable(String modId) {
        return opener(modId) != null;
    }

    static boolean isInstalledButIncompatible(String modId) {
        if (!FabricLoader.getInstance().isModLoaded(modId)) return false;
        return opener(modId) == null;
    }

    static String status(String modId, String displayName, String minimumVersion) {
        if (!FabricLoader.getInstance().isModLoaded(modId)) {
            return "当前使用内置版：" + displayName + "。";
        }
        if (isAvailable(modId)) {
            return "当前由外置版桥接接管：" + displayName + "。";
        }
        String actual = FabricLoader.getInstance().getModContainer(modId)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
        return "兼容性警告：" + displayName + " " + actual
                + " 版本不完全兼容，可能启动失败并导致游戏崩溃。请使用 " + displayName + " " + minimumVersion
                + " 或更新版本，并确认已提供 screen_opener_v1。";
    }

    static boolean open(String modId, Screen parent) {
        Consumer<Screen> opener = opener(modId);
        if (opener == null) {
            DiagnosticLog.info("External module has no compatible Tool Set screen bridge: " + modId);
            return false;
        }
        opener.accept(parent);
        return true;
    }

    @SuppressWarnings("unchecked")
    private static Consumer<Screen> opener(String modId) {
        if (!ACCESSORY_ID.equals(modId) && !SCROLL_ID.equals(modId)) return null;
        if (!FabricLoader.getInstance().isModLoaded(modId)) return null;
        if (!minimumVersionSatisfied(modId)) return null;
        Object value = FabricLoader.getInstance().getObjectShare().get(modId + SUFFIX);
        if (!(value instanceof Consumer<?> consumer)) return null;
        return (Consumer<Screen>) consumer;
    }

    private static boolean minimumVersionSatisfied(String modId) {
        String required = ACCESSORY_ID.equals(modId) ? ACCESSORY_MIN_VERSION : SCROLL_MIN_VERSION;
        return FabricLoader.getInstance().getModContainer(modId)
                .map(container -> compareVersion(container.getMetadata().getVersion().getFriendlyString(), required) >= 0)
                .orElse(false);
    }

    private static int compareVersion(String actual, String required) {
        int[] a = numericParts(actual);
        int[] b = numericParts(required);
        for (int index = 0; index < Math.max(a.length, b.length); index++) {
            int left = index < a.length ? a[index] : 0;
            int right = index < b.length ? b[index] : 0;
            if (left != right) return Integer.compare(left, right);
        }
        return 0;
    }

    private static int[] numericParts(String value) {
        String[] parts = value.replace('-', '.').split("\\.");
        int[] numbers = new int[parts.length];
        for (int index = 0; index < parts.length; index++) {
            String digits = parts[index].replaceAll("[^0-9].*", "");
            numbers[index] = digits.isEmpty() ? 0 : Integer.parseInt(digits);
        }
        return numbers;
    }
}
