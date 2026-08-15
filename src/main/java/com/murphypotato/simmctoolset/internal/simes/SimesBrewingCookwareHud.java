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
import java.util.ArrayList;
import java.util.List;

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
            if (SimesFeatureController.cookwareEnabled()
                    && (path.contains("cook") || path.contains("pot") || path.contains("barrel") || path.contains("ferment"))) {
                cookware = "厨具：" + path.replace("block{", "").replace("}", "");
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
        if (!SimesFeatureController.fermentationEnabled() || raw == null) return;
        Matcher remaining = REMAINING.matcher(raw);
        Matcher productMatcher = PRODUCT.matcher(raw);
        if (remaining.matches()) fermentation = "发酵剩余：" + remaining.group(1).trim();
        if (productMatcher.matches()) product = "产物：" + productMatcher.group(1).trim();
        if (remaining.matches() || productMatcher.matches()) lastUpdate = System.currentTimeMillis();
    }

    private static void render(DrawContext context, net.minecraft.client.render.RenderTickCounter tickCounter) {
        if (!SimesFeatureController.brewingEnabled() || lastUpdate == 0L
                || System.currentTimeMillis() - lastUpdate > 30_000L) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        List<String> lines = new ArrayList<>();
        if (SimesFeatureController.fermentationEnabled()) {
            if (!fermentation.isEmpty()) lines.add(fermentation);
            if (!product.isEmpty()) lines.add(product);
        }
        if (SimesFeatureController.cookwareEnabled() && !cookware.isEmpty()) lines.add(cookware);
        if (lines.isEmpty()) return;
        int maxWidth = lines.stream().mapToInt(client.textRenderer::getWidth).max().orElse(80);
        int x = Math.max(8, client.getWindow().getScaledWidth() - maxWidth - 12);
        int y = 8;
        context.fill(x - 5, y - 4, x + maxWidth + 5, y + lines.size() * 11 + 3, 0xA810141B);
        for (String line : lines) {
            int color = line.startsWith("厨具") ? 0xFF8FE8FF : 0xFFFFC266;
            context.drawTextWithShadow(client.textRenderer, Text.literal(line), x, y, color);
            y += 11;
        }
    }

    public static String statusSummary() {
        if (lastUpdate == 0L) return "等待点击发酵桶或厨具";
        if (System.currentTimeMillis() - lastUpdate > 30_000L) return "最近状态已过期，请重新点击目标";
        if (!fermentation.isEmpty() || !product.isEmpty() || !cookware.isEmpty()) return "已收到最近一次目标状态";
        return "已激活，等待服务器提示";
    }
}
