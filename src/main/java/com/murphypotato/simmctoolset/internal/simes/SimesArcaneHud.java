package com.murphypotato.simmctoolset.internal.simes;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Licensed Simes-derived compact cooldown and casting HUD. */
public final class SimesArcaneHud {
    private static final Pattern COOLDOWN = Pattern.compile(
            "(.+?)\\s*(?:冷却剩余|冷却剩余时间)\\s*[:：]\\s*([0-9]+(?:\\.[0-9]+)?)\\s*(?:秒|s)?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CASTING = Pattern.compile("正在吟唱\\s+(.+)");
    private static final Pattern DURATION = Pattern.compile("(.+?)剩余\\s*[:：]\\s*([0-9]+)\\s*tick", Pattern.CASE_INSENSITIVE);
    private static final Pattern RELEASED = Pattern.compile("释放\\s+(.+)");
    private static final Identifier ID = Identifier.of("simmc_tool_set", "simes_arcane_hud");
    private static final Map<String, Entry> ENTRIES = new LinkedHashMap<>();
    private static String status = "";
    private static long statusUntil;
    private static boolean initialized;

    private SimesArcaneHud() {
    }

    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> accept(message.getString()));
        HudElementRegistry.attachElementAfter(VanillaHudElements.ARMOR_BAR, ID, SimesArcaneHud::render);
    }

    public static synchronized void reset() {
        ENTRIES.clear();
        status = "";
        statusUntil = 0L;
    }

    private static synchronized void accept(String raw) {
        if (!SimesFeatureController.arcaneEnabled() || raw == null) return;
        String value = raw.trim();
        Matcher cooldown = COOLDOWN.matcher(value);
        boolean matched = false;
        while (cooldown.find()) {
            matched = true;
            ENTRIES.put(cooldown.group(1).trim(), new Entry(cooldown.group(1).trim(),
                    Double.parseDouble(cooldown.group(2)), System.nanoTime()));
        }
        Matcher casting = CASTING.matcher(value);
        Matcher duration = DURATION.matcher(value);
        Matcher released = RELEASED.matcher(value);
        if (casting.matches()) status = "Casting " + casting.group(1).trim();
        else if (duration.matches()) status = duration.group(1).trim() + " " + duration.group(2) + " tick";
        else if (released.matches()) status = "Released " + released.group(1).trim();
        else if (matched) status = "";
        if (!status.isEmpty()) statusUntil = System.currentTimeMillis() + 6000L;
    }

    private static synchronized void render(DrawContext context, net.minecraft.client.render.RenderTickCounter tickCounter) {
        if (!SimesFeatureController.arcaneEnabled()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        long now = System.nanoTime();
        ArrayList<Entry> visible = new ArrayList<>();
        Iterator<Entry> iterator = ENTRIES.values().iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next();
            if (entry.remaining(now) <= 0.05) iterator.remove();
            else visible.add(entry);
        }
        int y = 8;
        for (Entry entry : visible) {
            context.drawTextWithShadow(client.textRenderer,
                    Text.literal(entry.name + " " + format(entry.remaining(now))), 8, y, 0xFFFFFFFF);
            y += 11;
        }
        if (statusUntil > System.currentTimeMillis()) {
            context.drawTextWithShadow(client.textRenderer, Text.literal(status), 8, y + 2, 0xFFBDEBFF);
        }
    }

    private static String format(double seconds) {
        return seconds >= 10.0 ? String.format(Locale.ROOT, "%.0fs", Math.ceil(seconds))
                : String.format(Locale.ROOT, "%.1fs", seconds);
    }

    private record Entry(String name, double seconds, long updatedAt) {
        double remaining(long now) {
            return Math.max(0.0, seconds - (now - updatedAt) / 1_000_000_000.0);
        }
    }
}
