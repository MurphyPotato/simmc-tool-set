package com.murphypotato.simmctoolset.internal.simes;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.ActionResult;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Licensed Simes-derived fermentation and cookware hints. */
public final class SimesBrewingCookwareHud {
    private static final Pattern REMAINING = Pattern.compile(".*剩余时间[：:]\\s*(.+?)(?:[。.]|正在腌制|$).*");
    private static final Pattern PRODUCT = Pattern.compile(".*正在腌制[：:]?\\s*\\[([^]]+)].*");
    private static final Identifier ID = Identifier.of("simmc_tool_set", "simes_brewing_cookware");
    private static volatile String fermentation = "";
    private static volatile String product = "";
    private static volatile String cookware = "";
    private static volatile long lastUpdate;
    private static boolean initialized;

    private SimesBrewingCookwareHud() {
    }

    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (player != MinecraftClient.getInstance().player || !SimesFeatureController.brewingEnabled()) return ActionResult.PASS;
            String path = world.getBlockState(hit.getBlockPos()).getBlock().toString().toLowerCase();
            if (path.contains("cook") || path.contains("pot") || path.contains("barrel") || path.contains("ferment")) {
                cookware = "Cookware: " + path.replace("block{", "").replace("}", "");
                lastUpdate = System.currentTimeMillis();
            }
            return ActionResult.PASS;
        });
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> accept(message.getString()));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!SimesFeatureController.brewingEnabled()) reset();
        });
        HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, ID, SimesBrewingCookwareHud::render);
    }

    public static void reset() {
        fermentation = "";
        product = "";
        cookware = "";
        lastUpdate = 0L;
    }

    private static void accept(String raw) {
        if (!SimesFeatureController.brewingEnabled() || raw == null) return;
        Matcher remaining = REMAINING.matcher(raw);
        Matcher productMatcher = PRODUCT.matcher(raw);
        if (remaining.matches()) fermentation = "Fermentation: " + remaining.group(1).trim();
        if (productMatcher.matches()) product = "Product: " + productMatcher.group(1).trim();
        if (remaining.matches() || productMatcher.matches()) lastUpdate = System.currentTimeMillis();
    }

    private static void render(DrawContext context, net.minecraft.client.render.RenderTickCounter tickCounter) {
        if (!SimesFeatureController.brewingEnabled() || lastUpdate == 0L
                || System.currentTimeMillis() - lastUpdate > 30_000L) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        int y = client.getWindow().getScaledHeight() - 48;
        if (!fermentation.isEmpty()) { context.drawTextWithShadow(client.textRenderer, Text.literal(fermentation), 8, y, 0xFFFFC266); y += 11; }
        if (!product.isEmpty()) { context.drawTextWithShadow(client.textRenderer, Text.literal(product), 8, y, 0xFFFFC266); y += 11; }
        if (!cookware.isEmpty()) context.drawTextWithShadow(client.textRenderer, Text.literal(cookware), 8, y, 0xFF8FE8FF);
    }
}
