package com.murphypotato.simmctoolset.internal.simes;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Licensed Simes-derived Arcane cooldown HUD, limited to the authorized HUD scope. */
public final class SimesArcaneHud {
    private static final Pattern COMPLETE = Pattern.compile("^(.+?)\\s+冷却完成$");
    private static final int ICON_SIZE = 16;
    private static final int NAME_WIDTH = 52;
    private static final int BAR_WIDTH = 88;
    private static final int ROW_HEIGHT = 19;
    private static final int TOTAL_WIDTH = ICON_SIZE + 3 + NAME_WIDTH + 4 + BAR_WIDTH;
    private static final Identifier ID = Identifier.of("simmc_tool_set", "simes_arcane_hud");
    private static final Map<String, Cooldown> COOLDOWNS = new LinkedHashMap<>();
    private static List<String> equippedArcanes = List.of();
    private static ArcaneHudConfig config;
    private static boolean initialized;
    private static int lastStackIdentity;
    private static int lastLoreHash;

    private SimesArcaneHud() {
    }

    /** Keeps the original Tool Set lifecycle API. */
    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;
        config = ArcaneHudConfig.load();
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> accept(message.getString()));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!enabled()) {
                resetState();
                return;
            }
            updateEquippedArcanes(client);
            cleanup();
        });
        HudElementRegistry.attachElementAfter(VanillaHudElements.ARMOR_BAR, ID, SimesArcaneHud::render);
    }

    public static synchronized void reset() {
        resetState();
    }

    private static void resetState() {
        synchronized (COOLDOWNS) {
            COOLDOWNS.clear();
        }
        equippedArcanes = List.of();
        lastStackIdentity = 0;
        lastLoreHash = 0;
    }

    private static synchronized void accept(String raw) {
        if (!enabled() || raw == null) return;
        String value = raw.trim();
        if (value.isEmpty()) return;
        Matcher complete = COMPLETE.matcher(value);
        if (complete.matches()) {
            finish(ArcaneColors.canonicalName(complete.group(1)));
            return;
        }
        ArcaneCooldownParser.Result parsed = ArcaneCooldownParser.parse(value);
        if (!parsed.values().isEmpty()) updateCooldowns(parsed.values());
    }

    /** Handles the cancellable Action Bar packet path while preserving unrelated messages. */
    public static boolean handleActionBar(Text text) {
        if (!enabled() || text == null) return false;
        String raw = text.getString();
        ArcaneCooldownParser.Result parsed = ArcaneCooldownParser.parse(raw);
        if (parsed.values().isEmpty()) return false;
        accept(raw);
        return config != null && config.simesMode;
    }

    private static void updateCooldowns(List<ArcaneCooldownParser.Value> values) {
        long now = System.nanoTime();
        List<String> seen = new ArrayList<>();
        synchronized (COOLDOWNS) {
            for (ArcaneCooldownParser.Value parsed : values) {
                String name = ArcaneColors.canonicalName(parsed.name());
                seen.add(name);
                Cooldown old = COOLDOWNS.get(name);
                if (old == null || parsed.remaining() > old.remainingAt(now) + 0.35) {
                    double total = Math.ceil((parsed.remaining() + 0.05) * 5.0) / 5.0;
                    COOLDOWNS.put(name, new Cooldown(name, parsed.remaining(), Math.max(0.2, total), now));
                } else {
                    old.update(parsed.remaining(), now);
                }
            }
            for (Cooldown cooldown : COOLDOWNS.values()) {
                if (!seen.contains(cooldown.name) && cooldown.exitStarted == 0L) cooldown.exitStarted = now;
            }
        }
    }

    private static void finish(String name) {
        synchronized (COOLDOWNS) {
            Cooldown cooldown = COOLDOWNS.get(name);
            if (cooldown != null && cooldown.exitStarted == 0L) cooldown.exitStarted = System.nanoTime();
        }
    }

    private static void updateEquippedArcanes(MinecraftClient client) {
        ItemStack stack = client.player == null ? ItemStack.EMPTY : client.player.getMainHandStack();
        List<String> detected = extractEquippedArcanes(stack);
        ItemStack selectedStack = stack;
        if (detected.isEmpty() && client.player != null) {
            selectedStack = client.player.getOffHandStack();
            detected = extractEquippedArcanes(selectedStack);
        }
        LoreComponent lore = selectedStack.get(DataComponentTypes.LORE);
        int identity = System.identityHashCode(selectedStack);
        int loreHash = lore == null ? 0 : lore.lines().hashCode();
        if (identity == lastStackIdentity && loreHash == lastLoreHash) return;
        lastStackIdentity = identity;
        lastLoreHash = loreHash;
        equippedArcanes = List.copyOf(detected);
    }

    private static List<String> extractEquippedArcanes(ItemStack stack) {
        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore == null) return List.of();
        List<String> detected = new ArrayList<>(3);
        for (Text line : lore.lines()) {
            String plain = line.getString();
            for (String spell : ArcaneColors.spellNames()) {
                if (plain.contains(spell) && !detected.contains(spell)) {
                    detected.add(spell);
                    break;
                }
            }
            if (detected.size() == 3) break;
        }
        return detected;
    }

    private static void cleanup() {
        long now = System.nanoTime();
        synchronized (COOLDOWNS) {
            for (Cooldown cooldown : COOLDOWNS.values()) {
                if (cooldown.exitStarted == 0L && cooldown.remainingAt(now) <= 0.05) cooldown.exitStarted = now;
            }
            COOLDOWNS.values().removeIf(cooldown -> cooldown.exitStarted != 0L
                    && now - cooldown.exitStarted > 250_000_000L);
        }
    }

    private static void render(DrawContext context, net.minecraft.client.render.RenderTickCounter tickCounter) {
        if (!enabled() || !config.simesMode || equippedArcanes.isEmpty()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        int width = client.getWindow().getScaledWidth();
        int height = client.getWindow().getScaledHeight();
        renderRows(context, configuredX(width), configuredY(height), config.cooldownScalePercent / 100.0f,
                System.nanoTime(), equippedArcanes, false);
    }

    static void renderPreview(DrawContext context, int x, int y, float scale) {
        renderRows(context, x, y, scale, System.nanoTime(), List.of("治愈术", "火球术", "雷电射线"), true);
    }

    private static void renderRows(DrawContext context, int x, int baseY, float scale, long now,
                                   List<String> rows, boolean preview) {
        MinecraftClient client = MinecraftClient.getInstance();
        context.getMatrices().pushMatrix();
        context.getMatrices().scale(scale, scale);
        int sx = Math.round(x / scale);
        int sy = Math.round(baseY / scale);
        for (int index = 0; index < rows.size(); index++) {
            String name = ArcaneColors.canonicalName(rows.get(index));
            Cooldown cooldown;
            synchronized (COOLDOWNS) {
                cooldown = COOLDOWNS.get(name);
            }
            double remaining = preview ? (index == 0 ? 0.0 : index == 1 ? 1.2 : 0.6)
                    : cooldown == null ? 0.0 : cooldown.remainingAt(now);
            double total = preview ? (index == 0 ? 1.0 : index == 1 ? 2.0 : 1.6)
                    : cooldown == null ? 1.0 : cooldown.total;
            float alpha = preview ? (index == 0 ? 0.0f : 1.0f)
                    : cooldown == null ? 0.0f : cooldown.alpha(now);
            int rowY = sy - (rows.size() - 1 - index) * ROW_HEIGHT;
            drawRow(context, client, sx, rowY, name, remaining, total, alpha);
        }
        context.getMatrices().popMatrix();
    }

    private static void drawRow(DrawContext context, MinecraftClient client, int x, int y, String name,
                                double remaining, double total, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255.0f)));
        int barX = x + ICON_SIZE + 3 + NAME_WIDTH + 4;
        int barY = y - 14;
        int color = ArcaneColors.forName(name).primary();
        Identifier icon = Identifier.of("simmc_tool_set", "textures/gui/arcane/" + ArcaneColors.iconFile(name));
        context.drawTexture(RenderPipelines.GUI_TEXTURED, icon, x, y - 16, 0, 0,
                ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE, 32, 32);
        String shownName = client.textRenderer.trimToWidth(name, NAME_WIDTH);
        context.drawTextWithShadow(client.textRenderer, Text.literal(shownName), x + ICON_SIZE + 3,
                y - 12, (a << 24) | color);
        if (a == 0) return;
        context.fill(barX, barY, barX + BAR_WIDTH, barY + 12, (a << 24) | 0x111111);
        context.fill(barX + 1, barY + 1, barX + BAR_WIDTH - 1, barY + 11, (a << 24) | 0x555555);
        context.fill(barX + 3, barY + 3, barX + BAR_WIDTH - 3, barY + 9, (a << 24) | 0x241A12);
        int inner = BAR_WIDTH - 6;
        int fill = Math.max(0, Math.min(inner, (int) Math.round(inner * remaining / Math.max(total, 0.1))));
        if (fill > 0) context.fill(barX + 3, barY + 3, barX + 3 + fill, barY + 9, (a << 24) | color);
        if (remaining > 0.05) {
            String time = remaining >= 10.0 ? String.format(Locale.ROOT, "%.0fs", Math.ceil(remaining))
                    : String.format(Locale.ROOT, "%.1fs", remaining);
            int tx = barX + (BAR_WIDTH - client.textRenderer.getWidth(time)) / 2;
            context.drawTextWithShadow(client.textRenderer, Text.literal(time), tx, barY + 2,
                    (a << 24) | 0xFFFFFF);
        }
    }

    private static boolean enabled() {
        return config != null && config.arcaneEnabled && SimesFeatureController.arcaneEnabled();
    }

    public static ArcaneHudConfig config() {
        return config;
    }

    public static void openSettings(net.minecraft.client.gui.screen.Screen parent) {
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> client.setScreen(new SimesArcaneHudSettingsScreen(parent)));
    }

    static int totalWidth() {
        return TOTAL_WIDTH;
    }

    static int configuredX(int width) {
        return config == null || config.cooldownX < 0 ? width / 2 - 91 : (int) Math.round(config.cooldownX * width);
    }

    static int configuredY(int height) {
        return config == null || config.cooldownY < 0 ? height - 55 : (int) Math.round(config.cooldownY * height);
    }

    private static final class Cooldown {
        private final String name;
        private double serverRemaining;
        private double total;
        private long updatedAt;
        private final long createdAt;
        private long exitStarted;

        private Cooldown(String name, double remaining, double total, long now) {
            this.name = name;
            this.serverRemaining = remaining;
            this.total = total;
            this.updatedAt = now;
            this.createdAt = now;
        }

        private void update(double remaining, long now) {
            double predicted = remainingAt(now);
            serverRemaining = remaining <= 0.05 ? 0.0 : Math.min(remaining, predicted);
            updatedAt = now;
            total = Math.max(total, remaining);
            exitStarted = serverRemaining <= 0.05 ? now : 0L;
        }

        private double remainingAt(long now) {
            return Math.max(0.0, serverRemaining - (now - updatedAt) / 1_000_000_000.0);
        }

        private float alpha(long now) {
            if (exitStarted != 0L) return 1.0f - ease(Math.min(1.0f, (now - exitStarted) / 250_000_000.0f));
            return ease(Math.min(1.0f, (now - createdAt) / 180_000_000.0f));
        }

        private static float ease(float value) {
            float remaining = 1.0f - value;
            return 1.0f - remaining * remaining * remaining;
        }
    }

}
