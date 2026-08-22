package com.murphypotato.simmctoolset.internal.simes;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.ExperienceBarUpdateS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Separates SimMC wand Mana, transported through XP packets, from vanilla XP. */
public final class ManaHud {
    private static final Pattern MAX_MANA = Pattern.compile("魔力[：:]\\s*(\\d+(?:\\.\\d+)?)");
    private static final Pattern REGEN = Pattern.compile("魔力恢复[：:]\\s*(\\d+(?:\\.\\d+)?)/s");
    private static final int TOTAL_WIDTH = 126;
    private static final int TOTAL_HEIGHT = 20;
    private static final int[] GRADIENT = {0x72E7FF, 0x6FB7FF, 0x8C82FF, 0xC58CFF, 0xF0B8FF};
    private static final Identifier ID = Identifier.of("simmc_tool_set", "wand_mana");
    private static boolean initialized;
    private static boolean wandHeld;
    private static long lastWandSeenMillis;
    private static double maximum = 180.0;
    private static double regeneration = 0.8;
    private static double mana;
    private static double displayedMana;
    private static double trailingMana;
    private static long lastFrameNanos;
    private static long lastManaUpdateNanos;

    private ManaHud() {
    }

    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;
        ClientTickEvents.END_CLIENT_TICK.register(ManaHud::tick);
        HudElementRegistry.attachElementAfter(VanillaHudElements.FOOD_BAR, ID, ManaHud::render);
    }

    private static void tick(MinecraftClient client) {
        if (!SimesFeatureController.arcaneEnabled() || client.player == null) {
            wandHeld = false;
            return;
        }
        ItemStack stack = client.player.getMainHandStack();
        if (isArcaneCodex(stack)) {
            wandHeld = true;
            lastWandSeenMillis = System.currentTimeMillis();
            readStats(stack);
        } else if (System.currentTimeMillis() - lastWandSeenMillis > 300L) {
            wandHeld = false;
        }
    }

    public static boolean handleExperiencePacket(ExperienceBarUpdateS2CPacket packet) {
        ArcaneHudConfig config = SimesArcaneHud.config();
        if (config == null || !config.manaHudEnabled) return false;
        MinecraftClient client = MinecraftClient.getInstance();
        if (!SimesFeatureController.arcaneEnabled() || client.player == null) return false;
        ItemStack stack = client.player.getMainHandStack();
        if (!isArcaneCodex(stack)) return false;
        readStats(stack);
        double next = clamp(packet.getBarProgress() * maximum, 0.0, maximum);
        if (lastManaUpdateNanos == 0L) displayedMana = trailingMana = next;
        mana = next;
        lastManaUpdateNanos = System.nanoTime();
        wandHeld = true;
        lastWandSeenMillis = System.currentTimeMillis();
        return true;
    }

    public static boolean isArcaneCodex(ItemStack stack) {
        return stack != null && !stack.isEmpty() && isArcaneCodexComponents(stack.getComponents().toString());
    }

    static boolean isArcaneCodexComponents(String components) {
        if (components == null) return false;
        return components.contains("sim_magic:codex_item")
                || components.contains("smccore:arcane_codex")
                || components.contains("\"smc:id\":\"arcane_codex\"")
                || components.contains("注能杖，用来承载奥术");
    }

    private static void readStats(ItemStack stack) {
        String value = stack.getComponents().toString();
        Matcher max = MAX_MANA.matcher(value);
        if (max.find()) maximum = positive(max.group(1), maximum);
        Matcher regen = REGEN.matcher(value);
        if (regen.find()) regeneration = positive(regen.group(1), regeneration);
    }

    private static double positive(String value, double fallback) {
        try {
            double parsed = Double.parseDouble(value);
            return parsed > 0.0 ? parsed : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static void render(DrawContext context, net.minecraft.client.render.RenderTickCounter ticks) {
        ArcaneHudConfig config = SimesArcaneHud.config();
        if (!SimesFeatureController.arcaneEnabled() || config == null || !config.manaHudEnabled || !wandHeld) return;
        long now = System.nanoTime();
        if (lastFrameNanos == 0L) lastFrameNanos = now;
        double dt = Math.min(0.1, (now - lastFrameNanos) / 1_000_000_000.0);
        lastFrameNanos = now;
        double predicted = predictedMana(mana, regeneration, maximum, lastManaUpdateNanos, now);
        displayedMana += (predicted - displayedMana) * Math.min(1.0, dt / 0.10);
        if (trailingMana < displayedMana) trailingMana = displayedMana;
        else trailingMana += (displayedMana - trailingMana) * Math.min(1.0, dt / 0.35);
        MinecraftClient client = MinecraftClient.getInstance();
        int width = client.getWindow().getScaledWidth();
        float scale = SimesHudLayoutScreen.runtimeScale(config.manaHudScalePercent / 100.0f, width, TOTAL_WIDTH);
        int x = SimesHudLayoutScreen.runtimeX(configuredX(width), width, TOTAL_WIDTH, scale);
        renderPanel(context, x, configuredY(client.getWindow().getScaledHeight()), scale,
                displayedMana, trailingMana, maximum, regeneration, now);
    }

    static void renderPreview(DrawContext context, int x, int y, float scale) {
        renderPanel(context, x, y, scale, 50.8, 67.0, 180.0, 0.8, System.nanoTime());
    }

    private static void renderPanel(DrawContext context, int x, int y, float scale, double current,
                                    double trail, double max, double regen, long now) {
        MinecraftClient client = MinecraftClient.getInstance();
        context.getMatrices().pushMatrix();
        context.getMatrices().scale(scale, scale);
        int sx = Math.round(x / scale);
        int sy = Math.round(y / scale);
        String label = "Mana";
        String value = String.format(Locale.ROOT, "[%.1f / %.0f]", current, max);
        String regenText = String.format(Locale.ROOT, " +%.1f/s", regen);
        int textWidth = client.textRenderer.getWidth(label + value + regenText);
        int textX = sx + TOTAL_WIDTH - textWidth;
        context.drawText(client.textRenderer, Text.literal(label), textX, sy, 0xFFD8E7F0, false);
        int valueX = textX + client.textRenderer.getWidth(label);
        drawGradientText(context, client, value, valueX, sy, now);
        context.drawText(client.textRenderer, Text.literal(regenText), valueX + client.textRenderer.getWidth(value),
                sy, 0xFF72E7FF, false);
        int barX = sx;
        int barY = sy + 11;
        int barWidth = TOTAL_WIDTH;
        int barHeight = 7;
        context.fill(barX - 1, barY - 1, barX + barWidth + 1, barY + barHeight + 1, 0x99182232);
        context.fill(barX, barY, barX + barWidth, barY + barHeight, 0xBF111522);
        int trailWidth = (int) Math.round(barWidth * clamp(trail / Math.max(1.0, max), 0.0, 1.0));
        if (trailWidth > 0) context.fill(barX, barY, barX + trailWidth, barY + barHeight, 0x73E6D3FF);
        int fillWidth = (int) Math.round(barWidth * clamp(current / Math.max(1.0, max), 0.0, 1.0));
        for (int pixel = 0; pixel < fillWidth; pixel++) {
            int color = gradientColor(pixel / (double) Math.max(1, barWidth - 1));
            context.fill(barX + pixel, barY, barX + pixel + 1, barY + barHeight, 0xFF000000 | color);
        }
        if (fillWidth > 2 && current / Math.max(1.0, max) >= 0.1) {
            double phase = ((now / 1_000_000_000.0) % 3.2) / 3.2;
            int shine = barX + (int) Math.round((fillWidth + 14) * phase) - 7;
            for (int pixel = Math.max(barX, shine - 6); pixel < Math.min(barX + fillWidth, shine + 7); pixel++) {
                double strength = 1.0 - Math.abs(pixel - shine) / 7.0;
                int alpha = (int) Math.round(70 * Math.max(0.0, strength));
                context.fill(pixel, barY, pixel + 1, barY + barHeight, alpha << 24 | 0xE8F8FF);
            }
        }
        context.getMatrices().popMatrix();
    }

    private static void drawGradientText(DrawContext context, MinecraftClient client,
                                         String value, int x, int y, long now) {
        int cursor = x;
        double phase = (now / 1_000_000_000.0) * 0.18;
        for (int index = 0; index < value.length(); index++) {
            String character = value.substring(index, index + 1);
            int color = gradientColor((index / (double) Math.max(1, value.length() - 1) + phase) % 1.0);
            context.drawText(client.textRenderer, character, cursor, y, 0xFF000000 | color, false);
            cursor += client.textRenderer.getWidth(character);
        }
    }

    static double predictedMana(double base, double regen, double max, long updatedAt, long now) {
        if (updatedAt <= 0L || now <= updatedAt) return clamp(base, 0.0, max);
        double elapsedSeconds = (now - updatedAt) / 1_000_000_000.0;
        return clamp(base + Math.max(0.0, regen) * elapsedSeconds, 0.0, max);
    }

    private static int gradientColor(double value) {
        double position = clamp(value, 0.0, 1.0) * (GRADIENT.length - 1);
        int index = Math.min(GRADIENT.length - 2, (int) Math.floor(position));
        double fraction = position - index;
        int first = GRADIENT[index];
        int second = GRADIENT[index + 1];
        int red = (int) Math.round(((first >> 16) & 255) * (1 - fraction) + ((second >> 16) & 255) * fraction);
        int green = (int) Math.round(((first >> 8) & 255) * (1 - fraction) + ((second >> 8) & 255) * fraction);
        int blue = (int) Math.round((first & 255) * (1 - fraction) + (second & 255) * fraction);
        return red << 16 | green << 8 | blue;
    }

    static int configuredX(int width) {
        ArcaneHudConfig config = SimesArcaneHud.config();
        return config == null || config.manaHudX < 0 ? width / 2 + 91 - TOTAL_WIDTH
                : (int) Math.round(config.manaHudX * width);
    }

    static int configuredY(int height) {
        ArcaneHudConfig config = SimesArcaneHud.config();
        return config == null || config.manaHudY < 0 ? height - 61
                : (int) Math.round(config.manaHudY * height);
    }

    static int totalWidth() {
        return TOTAL_WIDTH;
    }

    static int totalHeight() {
        return TOTAL_HEIGHT;
    }

    static synchronized void reset() {
        wandHeld = false;
        lastWandSeenMillis = 0L;
        maximum = 180.0;
        regeneration = 0.8;
        mana = 0.0;
        displayedMana = 0.0;
        trailingMana = 0.0;
        lastFrameNanos = 0L;
        lastManaUpdateNanos = 0L;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
