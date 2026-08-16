package com.murphypotato.simmctoolset.internal.simes;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.network.packet.s2c.play.BossBarS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Simes-native casting, duration and global-cooldown surface. */
public final class SimesArcaneStatusHud {
    private static final Pattern CASTING = Pattern.compile("^\\s*正在吟唱\\s+(.+?)\\s*$");
    private static final Pattern DURATION = Pattern.compile("^\\s*(.+?)剩余\\s*[:：]\\s*(\\d+)\\s*tick\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern RELEASED = Pattern.compile("^\\s*释放\\s+(.+?)\\s*$");
    private static final Pattern GLOBAL_HINT = Pattern.compile("^\\s*.+?\\s+处于公共冷却中[，,]\\s*剩余\\s*([0-9.]+)\\s*秒\\s*$");
    private static final Identifier ID = Identifier.of("simmc_tool_set", "simes_arcane_status");
    private static final long FADE_NANOS = 250_000_000L;
    private static final Map<UUID, Status> STATUSES = new LinkedHashMap<>();
    private static final Map<String, Double> GLOBAL_TOTALS = Map.ofEntries(
            Map.entry("混乱射线", 0.5), Map.entry("腾云术", 11.0), Map.entry("火球术", 0.5),
            Map.entry("克敌先机", 0.5), Map.entry("引力术", 18.0), Map.entry("治愈术", 0.5),
            Map.entry("治疗射线", 0.5), Map.entry("冰刃术", 0.5), Map.entry("寒冰吐息", 0.5),
            Map.entry("跳跃术", 2.5), Map.entry("凌步术", 32.0), Map.entry("雷击", 0.5),
            Map.entry("斥力术", 0.0), Map.entry("激流术", 30.0), Map.entry("蜘化术", 3.5),
            Map.entry("火焰吐息", 0.5), Map.entry("火陨术", 0.5), Map.entry("御风术", 40.0),
            Map.entry("雷电射线", 0.5), Map.entry("后撤步", 0.0));
    private static GlobalCooldown global;
    private static boolean initialized;

    private SimesArcaneStatusHud() { }

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
        STATUSES.clear();
        global = null;
    }

    /** Returns true when the packet belongs to a recognized Simes arcane bar. */
    public static synchronized boolean handleBossBar(BossBarS2CPacket packet) {
        if (!enabled() || packet == null) return false;
        boolean[] suppress = {false};
        long now = System.nanoTime();
        packet.accept(new BossBarS2CPacket.Consumer() {
            @Override public void add(UUID id, Text name, float percent, BossBar.Color color, BossBar.Style style,
                                       boolean darkenSky, boolean dragonMusic, boolean thickenFog) {
                String raw = name == null ? "" : name.getString().trim();
                Matcher cast = CASTING.matcher(raw);
                Matcher duration = DURATION.matcher(raw);
                String spell = cast.matches() ? cast.group(1) : duration.matches() ? duration.group(1) : null;
                if (spell != null && known(spell)) {
                    STATUSES.put(id, new Status(ArcaneColors.canonicalName(spell), cast.matches() ? 0 : Integer.parseInt(duration.group(2)), percent, now));
                    suppress[0] = hideBars();
                }
            }
            @Override public void remove(UUID id) { if (STATUSES.remove(id) != null) suppress[0] = hideBars(); }
            @Override public void updateProgress(UUID id, float percent) { Status value = STATUSES.get(id); if (value != null) { value.progress = clamp(percent); suppress[0] = hideBars(); } }
            @Override public void updateStyle(UUID id, BossBar.Color color, BossBar.Style style) { if (STATUSES.containsKey(id)) suppress[0] = hideBars(); }
            @Override public void updateName(UUID id, Text name) {
                Status value = STATUSES.get(id); if (value == null) return;
                String raw = name == null ? "" : name.getString().trim(); Matcher cast = CASTING.matcher(raw); Matcher duration = DURATION.matcher(raw);
                if (cast.matches() && known(cast.group(1))) value.name = ArcaneColors.canonicalName(cast.group(1));
                else if (duration.matches() && known(duration.group(1))) { value.name = ArcaneColors.canonicalName(duration.group(1)); value.remainingTicks = Integer.parseInt(duration.group(2)); }
                else STATUSES.remove(id);
                suppress[0] = hideBars();
            }
            @Override public void updateProperties(UUID id, boolean darkenSky, boolean dragonMusic, boolean thickenFog) { if (STATUSES.containsKey(id)) suppress[0] = hideBars(); }
        });
        return suppress[0];
    }

    private static void acceptGameMessage(String raw) {
        if (!enabled() || raw == null) return;
        Matcher released = RELEASED.matcher(raw.trim());
        if (released.matches()) {
            String name = ArcaneColors.canonicalName(released.group(1));
            double total = GLOBAL_TOTALS.getOrDefault(name, 0.0);
            if (total > 0) global = new GlobalCooldown(name, total, total, System.nanoTime());
            return;
        }
        Matcher hint = GLOBAL_HINT.matcher(raw.trim());
        if (hint.matches()) {
            double remaining = Double.parseDouble(hint.group(1));
            if (global == null) global = new GlobalCooldown("公共冷却", remaining, remaining, System.nanoTime());
            else global.update(remaining, System.nanoTime());
        }
    }

    private static void cleanup() {
        if (!enabled()) { reset(); return; }
        long now = System.nanoTime();
        STATUSES.entrySet().removeIf(entry -> entry.getValue().expired(now));
        if (global != null && global.remaining(now) <= 0) global = null;
    }

    private static void render(DrawContext context, net.minecraft.client.render.RenderTickCounter tickCounter) {
        if (!enabled() || !config().simesMode || (STATUSES.isEmpty() && global == null)) return;
        MinecraftClient client = MinecraftClient.getInstance();
        int x = configuredX(client.getWindow().getScaledWidth());
        int y = configuredY(client.getWindow().getScaledHeight());
        float scale = config().arcaneStatusScalePercent / 100.0f;
        context.getMatrices().pushMatrix(); context.getMatrices().scale(scale, scale);
        int sx = Math.round(x / scale), sy = Math.round(y / scale);
        int row = 0;
        for (Status value : STATUSES.values()) {
            if (value.expired(System.nanoTime())) continue;
            double remaining = value.remainingSeconds(System.nanoTime());
            double total = value.totalSeconds();
            drawRow(context, client, sx, sy - row++ * 19, value.name, value.kindLabel(), remaining, total, value.progress);
        }
        if (global != null && global.remaining(System.nanoTime()) >= 0.05) {
            double remaining = global.remaining(System.nanoTime());
            drawRow(context, client, sx, sy - row++ * 19, global.name, "公共冷却", remaining, global.total, (float) (remaining / global.total));
        }
        context.getMatrices().popMatrix();
    }

    private static void drawRow(DrawContext context, MinecraftClient client, int x, int y, String name, String label,
                                double remaining, double total, float progress) {
        int color = ArcaneColors.forName(name).primary();
        Identifier icon = Identifier.of("simmc_tool_set", "textures/gui/arcane/" + ArcaneColors.iconFile(name));
        context.drawTexture(RenderPipelines.GUI_TEXTURED, icon, x, y - 16, 0, 0, 16, 16, 16, 16, 32, 32);
        context.drawTextWithShadow(client.textRenderer, Text.literal(label), x + 19, y - 12, 0xFFFFFFFF);
        int barX = x + 110;
        context.fill(barX, y - 14, barX + 88, y - 2, 0xAA111111);
        int fill = Math.max(0, Math.min(82, Math.round(82 * clamp(progress))));
        if (fill > 0) context.fill(barX + 3, y - 11, barX + 3 + fill, y - 5, 0xFF000000 | color);
        if (remaining > 0.05) context.drawTextWithShadow(client.textRenderer, Text.literal(format(remaining)), barX + 30, y - 12, 0xFFFFFFFF);
    }

    private static boolean enabled() { return SimesFeatureController.arcaneEnabled() && config() != null && config().arcaneStatusEnabled; }
    private static ArcaneHudConfig config() { return SimesArcaneHud.config(); }
    private static boolean hideBars() { return config() != null && config().hideRecognizedArcaneBossBars && config().simesMode; }
    private static boolean known(String name) { return ArcaneColors.spellNames().contains(ArcaneColors.canonicalName(name)); }
    private static float clamp(float value) { return Math.max(0, Math.min(1, value)); }
    private static String format(double seconds) { return seconds >= 10 ? String.format(Locale.ROOT, "%.0fs", Math.ceil(seconds)) : String.format(Locale.ROOT, "%.1fs", seconds); }
    private static int configuredX(int width) { return config().arcaneStatusX < 0 ? width / 2 - 91 : (int) Math.round(config().arcaneStatusX * width); }
    private static int configuredY(int height) { return config().arcaneStatusY < 0 ? height - 118 : (int) Math.round(config().arcaneStatusY * height); }

    private static final class Status {
        private String name;
        private final int initialTicks;
        private int remainingTicks;
        private float progress;
        private final long createdAt;
        private long lastUpdate;
        private Status(String name, int ticks, float progress, long now) { this.name = name; this.initialTicks = ticks; this.remainingTicks = ticks; this.progress = clamp(progress); this.createdAt = now; this.lastUpdate = now; }
        private String kindLabel() { return initialTicks > 0 ? name + " 持续" : "吟唱 " + name; }
        private double totalSeconds() { return initialTicks > 0 ? initialTicks / 20.0 : 1.0; }
        private double remainingSeconds(long now) { if (initialTicks <= 0) return Math.max(0, (1.0 - (now - lastUpdate) / 1_000_000_000.0)); return Math.max(0, remainingTicks / 20.0 - (now - lastUpdate) / 1_000_000_000.0); }
        private boolean expired(long now) { return now - lastUpdate > (initialTicks > 0 ? Math.max(1, initialTicks) * 50_000_000L + FADE_NANOS : 1_000_000_000L); }
    }
    private static final class GlobalCooldown {
        private final String name; private double remaining; private double total; private long updatedAt;
        private GlobalCooldown(String name, double remaining, double total, long now) { this.name = name; this.remaining = remaining; this.total = Math.max(remaining, total); this.updatedAt = now; }
        private double remaining(long now) { return Math.max(0, remaining - (now - updatedAt) / 1_000_000_000.0); }
        private void update(double value, long now) { remaining = value; total = Math.max(total, value); updatedAt = now; }
    }
}
