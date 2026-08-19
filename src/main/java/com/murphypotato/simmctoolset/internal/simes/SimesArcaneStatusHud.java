package com.murphypotato.simmctoolset.internal.simes;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.network.packet.s2c.play.BossBarS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Simes-native casting, duration and global-cooldown surfaces. */
public final class SimesArcaneStatusHud {
    private static final Pattern RELEASED = Pattern.compile("^\\s*释放\\s+(.+?)\\s*$");
    private static final Pattern GLOBAL_HINT = Pattern.compile(
            "^\\s*.+?\\s+处于公共冷却中[，,]\\s*剩余\\s*([0-9.]+)\\s*秒\\s*$");
    private static final Identifier ID = Identifier.of("simmc_tool_set", "simes_arcane_status");
    private static final int ICON_SIZE = 16;
    private static final int LABEL_WIDTH = 96;
    private static final int BAR_WIDTH = 88;
    private static final int GLOBAL_LABEL_WIDTH = 72;
    private static final int GLOBAL_BAR_WIDTH = 48;
    private static final int ROW_HEIGHT = 19;
    private static final long FADE_NANOS = ArcaneStatusState.EXIT_NANOS;
    private static final Map<String, Double> GLOBAL_TOTALS = Map.ofEntries(
            Map.entry("混乱射线", 0.5), Map.entry("腾云术", 11.0), Map.entry("火球术", 0.5),
            Map.entry("克敌先机", 0.5), Map.entry("引力术", 18.0), Map.entry("治愈术", 0.5),
            Map.entry("治疗射线", 0.5), Map.entry("冰刃术", 0.5), Map.entry("寒冰吐息", 0.5),
            Map.entry("跳跃术", 2.5), Map.entry("凌步术", 32.0), Map.entry("雷击", 0.5),
            Map.entry("斥力术", 0.0), Map.entry("激流术", 30.0), Map.entry("蜘化术", 3.5),
            Map.entry("火焰吐息", 0.5), Map.entry("火陨术", 0.5), Map.entry("御风术", 40.0),
            Map.entry("雷电射线", 0.5), Map.entry("后撤步", 0.0));
    private static final ArcaneStatusState STATE = new ArcaneStatusState(Set.copyOf(ArcaneColors.spellNames()));
    private static GlobalCooldown global;
    private static boolean initialized;

    private SimesArcaneStatusHud() {
    }

    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) acceptGameMessage(message.getString());
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> cleanup());
        HudElementRegistry.attachElementAfter(VanillaHudElements.ARMOR_BAR, ID, SimesArcaneStatusHud::render);
    }

    public static synchronized void reset() {
        STATE.reset();
        global = null;
    }

    /** Returns true only when the complete packet must be hidden from vanilla. */
    public static synchronized boolean handleBossBar(BossBarS2CPacket packet) {
        if (packet == null) return false;
        boolean[] cancel = {false};
        long now = System.nanoTime();
        packet.accept(new BossBarS2CPacket.Consumer() {
            @Override
            public void add(UUID id, Text name, float percent, BossBar.Color color, BossBar.Style style,
                            boolean darkenSky, boolean dragonMusic, boolean thickenFog) {
                ArcaneStatusState.Decision decision = STATE.add(id, text(name), percent,
                        style == BossBar.Style.NOTCHED_10, now, shouldHide());
                cancel[0] = decision.cancel();
            }

            @Override
            public void remove(UUID id) {
                cancel[0] = STATE.remove(id, now).cancel();
            }

            @Override
            public void updateProgress(UUID id, float percent) {
                boolean wasSuppressed = STATE.isSuppressed(id);
                applyUpdate(id, wasSuppressed, STATE.updateProgress(id, percent, now, shouldHide()));
            }

            @Override
            public void updateStyle(UUID id, BossBar.Color color, BossBar.Style style) {
                boolean wasSuppressed = STATE.isSuppressed(id);
                applyUpdate(id, wasSuppressed, STATE.updateStyle(id, now, shouldHide()));
            }

            @Override
            public void updateName(UUID id, Text name) {
                boolean wasSuppressed = STATE.isSuppressed(id);
                applyUpdate(id, wasSuppressed, STATE.updateName(id, text(name), now, shouldHide()));
            }

            @Override
            public void updateProperties(UUID id, boolean darkenSky, boolean dragonMusic, boolean thickenFog) {
                boolean wasSuppressed = STATE.isSuppressed(id);
                applyUpdate(id, wasSuppressed, STATE.updateProperties(id, now, shouldHide()));
            }

            private void applyUpdate(UUID id, boolean wasSuppressed, ArcaneStatusState.Decision decision) {
                boolean newlySuppressed = !wasSuppressed && decision.cancel();
                cancel[0] = decision.cancel();
                if (newlySuppressed) removeExistingBossBar(id);
            }
        });
        return cancel[0];
    }

    private static String text(Text name) {
        return name == null ? "" : name.getString();
    }

    private static void acceptGameMessage(String raw) {
        if (!enabled() || raw == null) return;
        Matcher released = RELEASED.matcher(raw.trim());
        if (released.matches()) {
            String name = ArcaneColors.canonicalName(released.group(1));
            double total = GLOBAL_TOTALS.getOrDefault(name, 0.0);
            global = total > 0.0 ? new GlobalCooldown(name, total, total, System.nanoTime()) : null;
            return;
        }
        Matcher hint = GLOBAL_HINT.matcher(raw.trim());
        if (hint.matches()) {
            double remaining = Double.parseDouble(hint.group(1));
            long now = System.nanoTime();
            if (global == null) global = new GlobalCooldown("公共冷却", remaining, remaining, now);
            else global.update(remaining, now);
        }
    }

    private static boolean enabled() {
        ArcaneHudConfig config = config();
        return SimesFeatureController.arcaneEnabled() && config != null && config.arcaneStatusEnabled;
    }

    private static boolean shouldHide() {
        ArcaneHudConfig config = config();
        return enabled() && config.simesMode && config.hideRecognizedArcaneBossBars;
    }

    private static ArcaneHudConfig config() {
        return SimesArcaneHud.config();
    }

    private static void removeExistingBossBar(UUID id) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.inGameHud != null) {
            client.inGameHud.getBossBarHud().handlePacket(BossBarS2CPacket.remove(id));
        }
    }

    private static void cleanup() {
        STATE.tick(System.nanoTime());
        if (global != null && (!enabled() || global.remaining(System.nanoTime()) <= 0.0)) global = null;
    }

    private static void render(DrawContext context, net.minecraft.client.render.RenderTickCounter tickCounter) {
        ArcaneHudConfig config = config();
        if (!enabled() || !config.simesMode) return;
        long now = System.nanoTime();
        List<Row> statusRows = statusRows(now);
        List<Row> globalRows = globalRows(now);
        MinecraftClient client = MinecraftClient.getInstance();
        int width = client.getWindow().getScaledWidth();
        int height = client.getWindow().getScaledHeight();
        if (!statusRows.isEmpty()) {
            renderRows(context, configuredX(width), configuredY(height), config.arcaneStatusScalePercent / 100.0f,
                    statusRows, LABEL_WIDTH, BAR_WIDTH);
        }
        if (!globalRows.isEmpty()) {
            renderRows(context, configuredGlobalX(width), configuredGlobalY(height),
                    config.globalCooldownScalePercent / 100.0f, globalRows, GLOBAL_LABEL_WIDTH, GLOBAL_BAR_WIDTH);
        }
    }

    static void renderPreview(DrawContext context, int x, int y, float scale) {
        renderRows(context, x, y, scale, List.of(
                new Row("火陨术", "吟唱 火陨术", 0.62f, 1.0f, false),
                new Row("御风术", "御风 持续 13.2s", 0.53f, 1.0f, false)), LABEL_WIDTH, BAR_WIDTH);
    }

    static void renderGlobalPreview(DrawContext context, int x, int y, float scale) {
        renderRows(context, x, y, scale,
                List.of(new Row("引力术", "公共冷却 8.4s", 0.47f, 1.0f, false)),
                GLOBAL_LABEL_WIDTH, GLOBAL_BAR_WIDTH);
    }

    private static List<Row> statusRows(long now) {
        List<Row> rows = new ArrayList<>();
        for (ArcaneStatusState.Snapshot value : STATE.snapshots(now)) {
            double remaining = value.kind() == ArcaneStatusState.Kind.DURATION
                    ? value.remainingTicks() / 20.0 : 0.0;
            String label = value.kind() == ArcaneStatusState.Kind.CASTING
                    ? "吟唱 " + value.name()
                    : displayName(value.name()) + " 持续 " + formatSeconds(remaining);
            float alpha = value.exiting()
                    ? 1.0f - Math.min(1.0f, (now - value.updatedAt()) / (float) FADE_NANOS) : 1.0f;
            rows.add(new Row(value.name(), label, value.progress(), alpha, value.interrupted()));
        }
        return rows;
    }

    private static List<Row> globalRows(long now) {
        if (global == null) return List.of();
        double remaining = global.remaining(now);
        if (remaining < 1.0) return List.of();
        return List.of(new Row(global.name, "公共冷却 " + formatSeconds(remaining),
                (float) (remaining / global.total), 1.0f, false));
    }

    private static void renderRows(DrawContext context, int x, int baseY, float scale, List<Row> rows,
                                   int labelWidth, int barWidth) {
        MinecraftClient client = MinecraftClient.getInstance();
        context.getMatrices().pushMatrix();
        context.getMatrices().scale(scale, scale);
        int sx = Math.round(x / scale);
        int sy = Math.round(baseY / scale);
        for (int index = 0; index < rows.size(); index++) {
            drawRow(context, client, sx, sy - index * ROW_HEIGHT, rows.get(index), labelWidth, barWidth);
        }
        context.getMatrices().popMatrix();
    }

    private static void drawRow(DrawContext context, MinecraftClient client, int x, int y, Row row,
                                int labelWidth, int barWidth) {
        int alpha = Math.max(0, Math.min(255, Math.round(row.alpha * 255.0f)));
        int color = row.interrupted ? 0xEF3B3B : ArcaneColors.forName(row.arcaneName).primary();
        Identifier icon = Identifier.of("simmc_tool_set", "textures/gui/arcane/"
                + ArcaneColors.iconFile(row.arcaneName));
        context.drawTexture(RenderPipelines.GUI_TEXTURED, icon, x, y - 16, 0, 0,
                ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE, 32, 32);
        String label = client.textRenderer.trimToWidth(row.label, labelWidth);
        context.drawTextWithShadow(client.textRenderer, Text.literal(label), x + ICON_SIZE + 3,
                y - 12, (alpha << 24) | 0xFFFFFF);
        int barX = x + ICON_SIZE + 3 + labelWidth + 4;
        int barY = y - 14;
        context.fill(barX, barY, barX + barWidth, barY + 12, (alpha << 24) | 0x111111);
        context.fill(barX + 1, barY + 1, barX + barWidth - 1, barY + 11, (alpha << 24) | 0x555555);
        context.fill(barX + 3, barY + 3, barX + barWidth - 3, barY + 9, (alpha << 24) | 0x241A12);
        int fill = Math.max(0, Math.min(barWidth - 6, Math.round((barWidth - 6) * clamp(row.progress))));
        if (fill > 0) context.fill(barX + 3, barY + 3, barX + 3 + fill, barY + 9,
                (alpha << 24) | (color & 0xFFFFFF));
    }

    private static String displayName(String name) {
        return name.endsWith("术") ? name.substring(0, name.length() - 1) : name;
    }

    private static String formatSeconds(double seconds) {
        return seconds >= 10.0 ? String.format(Locale.ROOT, "%.0fs", Math.ceil(seconds))
                : String.format(Locale.ROOT, "%.1fs", seconds);
    }

    private static float clamp(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    static int totalWidth() {
        return ICON_SIZE + 3 + LABEL_WIDTH + 4 + BAR_WIDTH;
    }

    static int globalTotalWidth() {
        return ICON_SIZE + 3 + GLOBAL_LABEL_WIDTH + 4 + GLOBAL_BAR_WIDTH;
    }

    static int previewHeight() {
        return ROW_HEIGHT * 2;
    }

    static int globalPreviewHeight() {
        return ROW_HEIGHT;
    }

    static int configuredX(int width) {
        ArcaneHudConfig config = config();
        return config == null || config.arcaneStatusX < 0 ? width / 2 - 91
                : (int) Math.round(config.arcaneStatusX * width);
    }

    static int configuredY(int height) {
        ArcaneHudConfig config = config();
        return config == null || config.arcaneStatusY < 0 ? height - 118
                : (int) Math.round(config.arcaneStatusY * height);
    }

    static int configuredGlobalX(int width) {
        ArcaneHudConfig config = config();
        return config == null || config.globalCooldownX < 0 ? width / 2 - 91
                : (int) Math.round(config.globalCooldownX * width);
    }

    static int configuredGlobalY(int height) {
        ArcaneHudConfig config = config();
        return config == null || config.globalCooldownY < 0 ? height - 175
                : (int) Math.round(config.globalCooldownY * height);
    }

    private record Row(String arcaneName, String label, float progress, float alpha, boolean interrupted) {
    }

    private static final class GlobalCooldown {
        private final String name;
        private double remaining;
        private double total;
        private long updatedAt;

        private GlobalCooldown(String name, double remaining, double total, long now) {
            this.name = name;
            this.remaining = remaining;
            this.total = Math.max(remaining, total);
            this.updatedAt = now;
        }

        private double remaining(long now) {
            return Math.max(0.0, remaining - (now - updatedAt) / 1_000_000_000.0);
        }

        private void update(double value, long now) {
            remaining = value;
            total = Math.max(total, value);
            updatedAt = now;
        }
    }
}
