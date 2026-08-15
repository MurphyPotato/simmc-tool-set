package com.murphypotato.simmctoolset.map;

import java.lang.reflect.Method;

/**
 * Build-time optional map facade. The Xaero-backed implementation is omitted
 * when the two local Xaero compile jars are unavailable, so the rest of the
 * Tool Set remains compilable and runnable.
 */
public final class MapModule {
    private static final String IMPLEMENTATION =
            "com.murphypotato.simmctoolset.internal.map.SimmcMapClient";
    private static final Class<?> TYPE = findImplementation();

    private MapModule() {
    }

    public static boolean isAvailable() {
        return TYPE != null;
    }

    public static void initialize() {
        invoke("initialize");
    }

    public static void close() {
        invoke("close");
    }

    public static void requestRefresh() {
        invoke("requestRefresh");
    }

    public static void toggleWorldMap() {
        invoke("toggleWorldMap");
    }

    public static void toggleWorldBackground() {
        invoke("toggleWorldBackground");
    }

    public static void toggleMinimapBackground() {
        invoke("toggleMinimapBackground");
    }

    public static boolean worldMapEnabled() {
        return booleanValue("worldMapEnabled");
    }

    public static boolean worldBackgroundEnabled() {
        return booleanValue("worldBackgroundEnabled");
    }

    public static boolean minimapBackgroundEnabled() {
        return booleanValue("minimapBackgroundEnabled");
    }

    public static String runtimeStatus() {
        if (TYPE == null) return "地图实现不可用";
        try {
            Method method = TYPE.getMethod("runtimeStatus");
            Object value = method.invoke(null);
            return value == null ? "等待地图运行时" : value.toString();
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return "地图运行时状态不可读";
        }
    }

    private static Class<?> findImplementation() {
        try {
            return Class.forName(IMPLEMENTATION, false, MapModule.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError ignored) {
            return null;
        }
    }

    private static void invoke(String name) {
        if (TYPE == null) return;
        try {
            Method method = TYPE.getMethod(name);
            method.invoke(null);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // A mismatched Xaero pair must not disable unrelated modules.
        }
    }

    private static boolean booleanValue(String name) {
        if (TYPE == null) return false;
        try {
            Method method = TYPE.getMethod(name);
            return Boolean.TRUE.equals(method.invoke(null));
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }
}
