package com.murphypotato.simmctoolset.internal.simes;

import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Stable spell palette and icon catalog copied from the licensed Simes HUD. */
public final class ArcaneColors {
    public record Palette(int primary, int secondary, int highlight) {
    }

    private static final Palette DEFAULT = new Palette(0xFFFFFF, 0xAAAAAA, 0xFFFFFF);
    private static final String UNKNOWN_ICON = "21_red_barrier.png";
    private static final Map<String, Palette> COLORS;
    private static final Map<String, String> ICONS;
    private static final Map<String, String> ALIASES = Map.of(
            "蛛化术", "蜘化术",
            "蛛化", "蜘化术");

    static {
        LinkedHashMap<String, Palette> colors = new LinkedHashMap<>();
        colors.put("混乱射线", new Palette(0xD92CFF, 0x7CFF4F, 0x32104F));
        colors.put("腾云术", new Palette(0xF4F7FF, 0xA9DCFF, 0xFFF1AE));
        colors.put("火球术", new Palette(0xFF5A1F, 0xFFB21C, 0xFFF3C4));
        colors.put("克敌先机", new Palette(0xFFD447, 0xFF9E2C, 0xFFF8D5));
        colors.put("引力术", new Palette(0x6B32D9, 0x281A64, 0x080712));
        colors.put("治愈术", new Palette(0x38D878, 0xA8FFB8, 0xF1FFF3));
        colors.put("治疗射线", new Palette(0x58F2C2, 0xFFECA0, 0xEFFFFA));
        colors.put("冰刃术", new Palette(0x70E5FF, 0x2489D8, 0xECFCFF));
        colors.put("寒冰吐息", new Palette(0xBCEFFF, 0xA8BFFF, 0xF5FDFF));
        colors.put("跳跃术", new Palette(0xA8F238, 0xF3FF55, 0xF8FFE2));
        colors.put("凌步术", new Palette(0x5865F2, 0x9A73FF, 0xE5E9FF));
        colors.put("雷击", new Palette(0xFFE94A, 0x76DFFF, 0xFFFFFF));
        colors.put("斥力术", new Palette(0x8EAAC4, 0x70D7FF, 0xF2FBFF));
        colors.put("激流术", new Palette(0x168CD8, 0x20D6DC, 0xE6FFFF));
        colors.put("蜘化术", new Palette(0x54205F, 0xB82E46, 0x72C94A));
        colors.put("火焰吐息", new Palette(0xE93224, 0xFF8A22, 0x3D2824));
        colors.put("火陨术", new Palette(0x9E1B18, 0xFFB000, 0x24120E));
        colors.put("御风术", new Palette(0x55D9C0, 0xA6F2E5, 0xF2FFFD));
        colors.put("雷电射线", new Palette(0x715CFF, 0x27E5FF, 0xFFFFFF));
        colors.put("后撤步", new Palette(0x557784, 0x2A4F59, 0xDDECEF));
        COLORS = Collections.unmodifiableMap(colors);

        String[] names = colors.keySet().toArray(String[]::new);
        String[] files = {
                "01_purple_orb.png", "02_cyan_eye.png", "03_pink_shard.png", "04_yellow_bolt_rune.png",
                "05_purple_chain.png", "06_cyan_plus.png", "07_blue_cross_sword.png", "08_blue_crescent.png",
                "09_blue_streaks.png", "10_cyan_cursor.png", "11_cyan_hook.png", "12_golden_symbol.png",
                "13_purple_x.png", "14_blue_crystal.png", "15_green_grid.png", "16_pink_slash.png",
                "17_pink_hook.png", "18_cyan_wing.png", "19_yellow_lightning.png", "20_orange_rune.png"
        };
        LinkedHashMap<String, String> icons = new LinkedHashMap<>();
        for (int index = 0; index < names.length; index++) icons.put(names[index], files[index]);
        ICONS = Collections.unmodifiableMap(icons);
    }

    private ArcaneColors() {
    }

    public static String canonicalName(String name) {
        String value = name == null ? "" : name.trim();
        return ALIASES.getOrDefault(value, value);
    }

    public static Palette forName(String name) {
        return COLORS.getOrDefault(canonicalName(name), DEFAULT);
    }

    public static String iconFile(String name) {
        return ICONS.getOrDefault(canonicalName(name), UNKNOWN_ICON);
    }

    public static List<String> spellNames() {
        return List.copyOf(COLORS.keySet());
    }

    public static Map<String, String> iconFiles() {
        return ICONS;
    }
}
