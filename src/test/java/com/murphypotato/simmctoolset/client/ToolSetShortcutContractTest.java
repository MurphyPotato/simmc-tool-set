package com.murphypotato.simmctoolset.client;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ToolSetShortcutContractTest {
    private static final Path CLIENT = Path.of("src/main/java/com/murphypotato/simmctoolset/client");

    @Test
    void keepsOnlyDirectKeysNativeAndOnlySubkeysCustom() throws IOException {
        String router = Files.readString(CLIENT.resolve("ToolSetKeyRouter.java"));

        assertEquals(3, count(router, "registerNative(\"key.simmc_tool_set."));
        assertEquals(6, count(router, "\n        add(\""));
        assertFalse(router.contains("add(\"prefix\""));
        assertFalse(router.contains("add(\"accessory_direct\""));
        assertFalse(router.contains("add(\"simes_settings\""));
        assertTrue(router.contains("if (!prefixDown)"));
    }

    @Test
    void customScreensUseOpaqueTextColors() throws IOException {
        for (String file : new String[] {"ToolSetScreen.java", "ToolSetHotkeyScreen.java"}) {
            String source = Files.readString(CLIENT.resolve(file));
            assertFalse(source.contains(", 0xFFFFFF)"), file);
            assertFalse(source.contains(", 0xB8C5D6)"), file);
            assertFalse(source.contains(", 0xD7DEE8)"), file);
        }
    }

    @Test
    void overviewUsesCurrentBindingsForShortcutSummary() throws IOException {
        String router = Files.readString(CLIENT.resolve("ToolSetKeyRouter.java"));
        String screen = Files.readString(CLIENT.resolve("ToolSetScreen.java"));
        int start = router.indexOf("currentShortcutSummary()");
        int end = router.indexOf("public static synchronized boolean setBinding", start);
        String formatter = router.substring(start, end);

        assertEquals(2, count(screen, "ToolSetKeyRouter.currentShortcutSummary()"));
        assertTrue(formatter.contains("SHORTCUTS.values()"));
        assertTrue(formatter.contains("binding.displayName()"));
        assertTrue(formatter.contains("binding.label()"));
        assertFalse(formatter.contains("defaultKey()"));
    }

    @Test
    void usesShortScrollDescription() {
        assertEquals("用于计算奥术卷轴材料配比，支持材料排除与轮换方案。",
                ToolSetScreen.scrollPanelDescription());
    }

    @Test
    void usesOneWidthForMapControls() {
        assertEquals(140, ToolSetScreen.mapControlWidth(140));
        assertEquals(260, ToolSetScreen.mapControlWidth(260));
        assertEquals(260, ToolSetScreen.mapControlWidth(400));
    }

    @Test
    void reservesBottomSafeAreaForDiagnostics() {
        assertEquals(308, ToolSetScreen.diagnosticControlsTop(360));
        assertEquals(300, ToolSetScreen.diagnosticContentBottom(360));
        assertEquals(21, ToolSetScreen.diagnosticVisibleLines(38, 360));
    }

    @Test
    void diagnosticsStartAtLatestAndClampWheelMovement() {
        int max = ToolSetScreen.diagnosticMaxScroll(50, 10);
        assertEquals(40, max);
        assertEquals(max, ToolSetScreen.diagnosticInitialScroll(50, 10));
        assertEquals(37, ToolSetScreen.diagnosticScrollFor(ToolSetScreen.Panel.DIAGNOSTICS, 40, 1, max));
        assertEquals(40, ToolSetScreen.diagnosticScrollFor(ToolSetScreen.Panel.DIAGNOSTICS, 39, -1, max));
        assertEquals(0, ToolSetScreen.diagnosticScrollFor(ToolSetScreen.Panel.DIAGNOSTICS, 1, 1, max));
        assertEquals(40, ToolSetScreen.diagnosticScrollFor(ToolSetScreen.Panel.DIAGNOSTICS, 39, -1, max));
    }

    @Test
    void nonDiagnosticsPanelsDoNotConsumeDiagnosticScroll() {
        assertEquals(12, ToolSetScreen.diagnosticScrollFor(ToolSetScreen.Panel.MAP, 12, 1, 40));
        assertEquals(12, ToolSetScreen.diagnosticScrollFor(ToolSetScreen.Panel.OVERVIEW, 12, -1, 40));
    }

    private static int count(String value, String needle) {
        int result = 0;
        for (int at = value.indexOf(needle); at >= 0; at = value.indexOf(needle, at + needle.length())) result++;
        return result;
    }
}
