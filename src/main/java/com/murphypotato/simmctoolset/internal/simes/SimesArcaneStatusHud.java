package com.murphypotato.simmctoolset.internal.simes;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ClientBossBar;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.network.packet.s2c.play.BossBarS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Faithful Simes casting, duration and local global-cooldown HUD. */
public final class SimesArcaneStatusHud {
    private static final Pattern CASTING = Pattern.compile("^\\s*正在吟唱\\s+(.+?)\\s*$");
    private static final Pattern DURATION = Pattern.compile(
            "^\\s*(.+?)剩余\\s*[:：]\\s*(\\d+)\\s*tick\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern RELEASED = Pattern.compile("^\\s*释放\\s+(.+?)\\s*$");
    private static final Pattern GLOBAL_HINT = Pattern.compile(
            "^\\s*.+?\\s+处于公共冷却中[，,]\\s*剩余\\s*([0-9.]+)\\s*秒\\s*$");
    private static final Pattern ARCANE_LEVEL = Pattern.compile(
            "^\\s*(.+?)\\s+Lv\\s*5(?:\\s+MAX(?:/MAX)?)?\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Identifier ID = Identifier.of("simmc_tool_set", "simes_arcane_status");
    private static final int ICON_SIZE = 16;
    private static final int LABEL_WIDTH = 96;
    private static final int BAR_WIDTH = 88;
    private static final int GLOBAL_LABEL_WIDTH = 72;
    private static final int GLOBAL_BAR_WIDTH = 48;
    private static final int ROW_HEIGHT = 19;
    private static final long EXIT_NANOS = 220_000_000L;
    private static final Map<String, Double> GLOBAL_COOLDOWNS = Map.ofEntries(
            Map.entry("混乱射线", 0.5), Map.entry("腾云术", 11.0), Map.entry("火球术", 0.5),
            Map.entry("克敌先机", 0.5), Map.entry("引力术", 18.0), Map.entry("治愈术", 0.5),
            Map.entry("治疗射线", 0.5), Map.entry("冰刃术", 0.5), Map.entry("寒冰吐息", 0.5),
            Map.entry("跳跃术", 2.5), Map.entry("凌步术", 32.0), Map.entry("雷击", 0.5),
            Map.entry("斥力术", 0.0), Map.entry("激流术", 30.0), Map.entry("蜘化术", 3.5),
            Map.entry("火焰吐息", 0.5), Map.entry("火陨术", 0.5), Map.entry("御风术", 40.0),
            Map.entry("雷电射线", 0.5), Map.entry("后撤步", 0.0));
    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("腾云", "腾云术"), Map.entry("凌步", "凌步术"), Map.entry("御风", "御风术"),
            Map.entry("跳跃", "跳跃术"), Map.entry("蛛化", "蜘化术"), Map.entry("激流", "激流术"),
            Map.entry("斥力", "斥力术"), Map.entry("引力", "引力术"), Map.entry("火陨", "火陨术"));

    private static final Map<UUID, Status> STATUSES = new LinkedHashMap<>();
    private static final Set<UUID> HIDDEN_ARCANE_LEVEL_BARS = new HashSet<>();
    private static final Map<UUID, ClientBossBar> PENDING_BOSS_BARS = new LinkedHashMap<>();
    private static final Map<UUID, ClientBossBar> HIDDEN_BOSS_BARS = new LinkedHashMap<>();
    private static final SuppressedBossBarIds SUPPRESSED_BOSS_BARS = new SuppressedBossBarIds();
    private static GlobalCooldown globalCooldown;
    private static boolean initialized;

    private SimesArcaneStatusHud() {
    }

    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) handleGameMessage(message.getString());
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> cleanup());
        HudElementRegistry.attachElementAfter(VanillaHudElements.ARMOR_BAR, ID, SimesArcaneStatusHud::render);
    }

    public static synchronized void reset() {
        STATUSES.clear();
        HIDDEN_ARCANE_LEVEL_BARS.clear();
        PENDING_BOSS_BARS.clear();
        HIDDEN_BOSS_BARS.clear();
        SUPPRESSED_BOSS_BARS.clear();
        globalCooldown = null;
    }

    /** Clears rendered state and restores hidden bars so vanilla can resume updates. */
    static synchronized void clearVisualState() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.inGameHud != null) {
            for (ClientBossBar bar : HIDDEN_BOSS_BARS.values()) {
                client.inGameHud.getBossBarHud().handlePacket(BossBarS2CPacket.add(bar));
            }
        }
        STATUSES.clear();
        HIDDEN_ARCANE_LEVEL_BARS.clear();
        PENDING_BOSS_BARS.clear();
        HIDDEN_BOSS_BARS.clear();
        SUPPRESSED_BOSS_BARS.clear();
        globalCooldown = null;
    }

    public static synchronized boolean handleBossBar(BossBarS2CPacket packet) {
        if (packet == null || !SimesFeatureController.arcaneEnabled()) return false;
        boolean[] recognized = {false};
        boolean[] cancel = {false};
        long now = System.nanoTime();
        packet.accept(new BossBarS2CPacket.Consumer() {
            @Override
            public void add(UUID id, Text name, float percent, BossBar.Color color, BossBar.Style style,
                            boolean darkenSky, boolean dragonMusic, boolean thickenFog) {
                String raw = text(name);
                Matcher casting = CASTING.matcher(raw);
                Matcher level = ARCANE_LEVEL.matcher(raw);
                boolean hide = shouldHide();
                if (level.matches() && isKnownArcane(level.group(1))) {
                    if (hide) {
                        HIDDEN_ARCANE_LEVEL_BARS.add(id);
                        rememberHiddenBossBar(id, name, percent, color, style,
                                darkenSky, dragonMusic, thickenFog);
                        SUPPRESSED_BOSS_BARS.suppress(id);
                    }
                    recognized[0] = true;
                    cancel[0] = hide;
                } else if (casting.matches() && isKnownArcane(casting.group(1))) {
                    STATUSES.put(id, Status.casting(canonical(casting.group(1)), percent, now, hide));
                    if (hide) {
                        rememberHiddenBossBar(id, name, percent, color, style,
                                darkenSky, dragonMusic, thickenFog);
                        SUPPRESSED_BOSS_BARS.suppress(id);
                    }
                    recognized[0] = true;
                    cancel[0] = hide;
                } else if (raw.isBlank() && style == BossBar.Style.NOTCHED_10 && percent >= 0.99f) {
                    STATUSES.put(id, Status.pending(now));
                    PENDING_BOSS_BARS.put(id, new ClientBossBar(id, name, percent, color, style,
                            darkenSky, dragonMusic, thickenFog));
                }
            }

            @Override
            public void remove(UUID id) {
                PENDING_BOSS_BARS.remove(id);
                HIDDEN_BOSS_BARS.remove(id);
                if (SUPPRESSED_BOSS_BARS.release(id)) {
                    HIDDEN_ARCANE_LEVEL_BARS.remove(id);
                    Status value = STATUSES.get(id);
                    if (value != null) value.finish(now, value.kind == Kind.CASTING && value.progress < 0.995f);
                    recognized[0] = true;
                    cancel[0] = true;
                    return;
                }
                if (HIDDEN_ARCANE_LEVEL_BARS.remove(id)) {
                    recognized[0] = true;
                    cancel[0] = true;
                    return;
                }
                Status value = STATUSES.get(id);
                if (value != null) {
                    value.finish(now, value.kind == Kind.CASTING && value.progress < 0.995f);
                    recognized[0] = true;
                    cancel[0] = value.suppressed;
                }
            }

            @Override
            public void updateProgress(UUID id, float percent) {
                ClientBossBar pending = PENDING_BOSS_BARS.get(id);
                if (pending != null) pending.setPercent(percent);
                if (SUPPRESSED_BOSS_BARS.contains(id)) {
                    Status value = STATUSES.get(id);
                    if (value != null) value.progress = clamp(percent);
                    ClientBossBar bar = HIDDEN_BOSS_BARS.get(id);
                    if (bar != null) bar.setPercent(percent);
                    recognized[0] = true;
                    cancel[0] = true;
                    return;
                }
                if (HIDDEN_ARCANE_LEVEL_BARS.contains(id)) {
                    recognized[0] = true;
                    cancel[0] = true;
                    return;
                }
                Status value = STATUSES.get(id);
                if (value != null) {
                    value.progress = clamp(percent);
                    recognized[0] = true;
                    cancel[0] = value.suppressed;
                }
            }

            @Override
            public void updateName(UUID id, Text name) {
                ClientBossBar pending = PENDING_BOSS_BARS.get(id);
                if (pending != null) pending.setName(name);
                if (SUPPRESSED_BOSS_BARS.contains(id)) {
                    updateSuppressedName(id, name, now);
                    ClientBossBar bar = HIDDEN_BOSS_BARS.get(id);
                    if (bar != null) bar.setName(name);
                    recognized[0] = true;
                    cancel[0] = true;
                    return;
                }
                if (HIDDEN_ARCANE_LEVEL_BARS.contains(id)) {
                    recognized[0] = true;
                    cancel[0] = true;
                    return;
                }
                Status value = STATUSES.get(id);
                if (value == null) return;
                String raw = text(name);
                Matcher casting = CASTING.matcher(raw);
                Matcher duration = DURATION.matcher(raw);
                if (casting.matches() && isKnownArcane(casting.group(1))) {
                    boolean hide = shouldHide() && suppressExistingBossBar(id);
                    value.suppressed = hide;
                    value.activate(Kind.CASTING, canonical(casting.group(1)), 0, now);
                    if (!hide) PENDING_BOSS_BARS.remove(id);
                    recognized[0] = true;
                    cancel[0] = hide;
                } else if (duration.matches() && isKnownArcane(duration.group(1))) {
                    boolean hide = shouldHide() && suppressExistingBossBar(id);
                    value.suppressed = hide;
                    value.activate(Kind.DURATION, canonical(duration.group(1)),
                            Integer.parseInt(duration.group(2)), now);
                    if (!hide) PENDING_BOSS_BARS.remove(id);
                    recognized[0] = true;
                    cancel[0] = hide;
                } else if (value.kind != Kind.PENDING) {
                    recognized[0] = true;
                    cancel[0] = value.suppressed;
                } else {
                    STATUSES.remove(id);
                    PENDING_BOSS_BARS.remove(id);
                }
            }

            @Override
            public void updateStyle(UUID id, BossBar.Color color, BossBar.Style style) {
                ClientBossBar bar = trackedBossBar(id);
                if (bar != null) {
                    bar.setColor(color);
                    bar.setStyle(style);
                }
                updateOther(id);
            }

            @Override
            public void updateProperties(UUID id, boolean darkenSky, boolean dragonMusic, boolean thickenFog) {
                ClientBossBar bar = trackedBossBar(id);
                if (bar != null) {
                    bar.setDarkenSky(darkenSky);
                    bar.setDragonMusic(dragonMusic);
                    bar.setThickenFog(thickenFog);
                }
                updateOther(id);
            }

            private void updateOther(UUID id) {
                if (SUPPRESSED_BOSS_BARS.contains(id) || HIDDEN_ARCANE_LEVEL_BARS.contains(id)) {
                    recognized[0] = true;
                    cancel[0] = true;
                    return;
                }
                Status value = STATUSES.get(id);
                recognized[0] = value != null;
                cancel[0] = value != null && value.suppressed;
            }
        });
        return recognized[0] && cancel[0];
    }

    private static void updateSuppressedName(UUID id, Text name, long now) {
        Status value = STATUSES.get(id);
        if (value == null) return;
        String raw = text(name);
        Matcher casting = CASTING.matcher(raw);
        Matcher duration = DURATION.matcher(raw);
        if (casting.matches()) value.activate(Kind.CASTING, canonical(casting.group(1)), 0, now);
        else if (duration.matches()) value.activate(Kind.DURATION, canonical(duration.group(1)),
                Integer.parseInt(duration.group(2)), now);
    }

    private static String text(Text name) {
        return name == null ? "" : name.getString();
    }

    private static boolean isKnownArcane(String name) {
        return GLOBAL_COOLDOWNS.containsKey(canonical(name));
    }

    private static String canonical(String raw) {
        String name = raw == null ? "" : raw.trim();
        return ArcaneColors.canonicalName(ALIASES.getOrDefault(name, name));
    }

    private static boolean suppressExistingBossBar(UUID id) {
        ClientBossBar pending = PENDING_BOSS_BARS.remove(id);
        if (pending == null) return false;
        HIDDEN_BOSS_BARS.put(id, pending);
        SUPPRESSED_BOSS_BARS.suppress(id);
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.inGameHud != null) {
            client.inGameHud.getBossBarHud().handlePacket(BossBarS2CPacket.remove(id));
        }
        return true;
    }

    private static ClientBossBar trackedBossBar(UUID id) {
        ClientBossBar hidden = HIDDEN_BOSS_BARS.get(id);
        return hidden != null ? hidden : PENDING_BOSS_BARS.get(id);
    }

    private static void rememberHiddenBossBar(UUID id, Text name, float percent, BossBar.Color color,
                                              BossBar.Style style, boolean darkenSky, boolean dragonMusic,
                                              boolean thickenFog) {
        HIDDEN_BOSS_BARS.putIfAbsent(id, new ClientBossBar(id, name, percent, color, style,
                darkenSky, dragonMusic, thickenFog));
    }

    private static void handleGameMessage(String raw) {
        if (!enabled() || raw == null) return;
        Matcher released = RELEASED.matcher(raw);
        if (released.matches()) {
            String name = canonical(released.group(1));
            double seconds = GLOBAL_COOLDOWNS.getOrDefault(name, 0.0);
            globalCooldown = seconds > 0.0
                    ? new GlobalCooldown(name, seconds, seconds, System.nanoTime()) : null;
            return;
        }
        Matcher hint = GLOBAL_HINT.matcher(raw);
        if (hint.matches()) {
            double remaining = Double.parseDouble(hint.group(1));
            long now = System.nanoTime();
            if (globalCooldown == null) {
                globalCooldown = new GlobalCooldown("公共冷却", remaining, remaining, now);
            } else {
                globalCooldown.update(remaining, now);
            }
        }
    }

    private static void cleanup() {
        if (STATUSES.isEmpty() && globalCooldown == null) return;
        long now = System.nanoTime();
        STATUSES.entrySet().removeIf(entry -> {
            Status value = entry.getValue();
            if (value.kind == Kind.PENDING && now - value.createdAt > 1_000_000_000L) {
                PENDING_BOSS_BARS.remove(entry.getKey());
                return true;
            }
            return value.exitAt != 0L && now - value.exitAt > EXIT_NANOS;
        });
        if (globalCooldown != null && globalCooldown.remaining(now) <= 0.0) globalCooldown = null;
    }

    private static boolean enabled() {
        ArcaneHudConfig config = config();
        return SimesFeatureController.arcaneEnabled() && config != null && config.arcaneEnabled
                && config.arcaneStatusEnabled;
    }

    private static boolean shouldHide() {
        ArcaneHudConfig config = config();
        return enabled() && config.simesMode && config.hideRecognizedArcaneBossBars;
    }

    private static ArcaneHudConfig config() {
        return SimesArcaneHud.config();
    }

    private static void render(DrawContext context, net.minecraft.client.render.RenderTickCounter tickCounter) {
        ArcaneHudConfig config = config();
        if (!enabled() || !config.simesMode || (STATUSES.isEmpty() && globalCooldown == null)) return;
        long now = System.nanoTime();
        List<Row> statusRows = statusRows(now);
        List<Row> globalRows = globalRows(now);
        MinecraftClient client = MinecraftClient.getInstance();
        int width = client.getWindow().getScaledWidth();
        int height = client.getWindow().getScaledHeight();
        if (!statusRows.isEmpty()) {
            float scale = SimesHudLayoutScreen.runtimeScale(
                    config.arcaneStatusScalePercent / 100.0f, width, totalWidth());
            int x = SimesHudLayoutScreen.runtimeX(configuredX(width), width, totalWidth(), scale);
            renderRows(context, x, configuredY(height), scale, statusRows, LABEL_WIDTH, BAR_WIDTH);
        }
        if (!globalRows.isEmpty()) {
            float scale = SimesHudLayoutScreen.runtimeScale(
                    config.globalCooldownScalePercent / 100.0f, width, globalTotalWidth());
            int x = SimesHudLayoutScreen.runtimeX(configuredGlobalX(width), width, globalTotalWidth(), scale);
            renderRows(context, x, configuredGlobalY(height), scale,
                    globalRows, GLOBAL_LABEL_WIDTH, GLOBAL_BAR_WIDTH);
        }
    }

    static void renderPreview(DrawContext context, int x, int y, float scale) {
        renderRows(context, x, y, scale, List.of(
                new Row("火陨术", "吟唱 火陨术", 0.62f, false),
                new Row("御风术", "御风 持续 13.2s", 0.53f, false)), LABEL_WIDTH, BAR_WIDTH);
    }

    static void renderGlobalPreview(DrawContext context, int x, int y, float scale) {
        renderRows(context, x, y, scale,
                List.of(new Row("引力术", "公共冷却 8.4s", 0.47f, false)),
                GLOBAL_LABEL_WIDTH, GLOBAL_BAR_WIDTH);
    }

    private static List<Row> statusRows(long now) {
        List<Row> rows = new ArrayList<>();
        for (Status value : STATUSES.values()) {
            if (value.kind == Kind.PENDING) continue;
            float alpha = value.exitAt == 0L ? 1.0f
                    : 1.0f - Math.min(1.0f, (now - value.exitAt) / (float) EXIT_NANOS);
            String label = value.kind == Kind.CASTING ? "吟唱 " + value.name
                    : displayName(value.name) + " 持续 " + formatSeconds(value.remainingTicks(now) / 20.0);
            rows.add(new Row(value.name, label, value.progress, value.interrupted, alpha));
        }
        return rows;
    }

    private static List<Row> globalRows(long now) {
        if (globalCooldown == null) return List.of();
        double remaining = globalCooldown.remaining(now);
        if (remaining < 1.0) return List.of();
        return List.of(new Row(globalCooldown.name, "公共冷却 " + formatSeconds(remaining),
                (float) (remaining / globalCooldown.total), false));
    }

    private static void renderRows(DrawContext context, int x, int baseY, float scale, List<Row> rows,
                                   int labelWidth, int barWidth) {
        MinecraftClient client = MinecraftClient.getInstance();
        context.getMatrices().pushMatrix();
        context.getMatrices().scale(scale, scale);
        int scaledX = Math.round(x / scale);
        int scaledY = Math.round(baseY / scale);
        for (int index = 0; index < rows.size(); index++) {
            drawRow(context, client, scaledX, scaledY - index * ROW_HEIGHT,
                    rows.get(index), labelWidth, barWidth);
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
                ICON_SIZE, ICON_SIZE, 32, 32, 32, 32, (alpha << 24) | 0xFFFFFF);
        String label = client.textRenderer.trimToWidth(row.label, labelWidth);
        context.drawTextWithShadow(client.textRenderer, Text.literal(label), x + ICON_SIZE + 3,
                y - 12, (alpha << 24) | (color & 0xFFFFFF));
        int barX = x + ICON_SIZE + 3 + labelWidth + 4;
        int barY = y - 14;
        context.fill(barX, barY, barX + barWidth, barY + 12, (alpha << 24) | 0x111111);
        context.fill(barX + 1, barY + 1, barX + barWidth - 1, barY + 11, (alpha << 24) | 0x555555);
        context.fill(barX + 3, barY + 3, barX + barWidth - 3, barY + 9, (alpha << 24) | 0x241A12);
        int fill = Math.round((barWidth - 6) * clamp(row.progress));
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

    static int remainingDurationTicks(int sampleTicks, long sampleAt, long now) {
        if (sampleTicks <= 0) return 0;
        if (sampleAt <= 0L || now <= sampleAt) return sampleTicks;
        long elapsedTicks = (now - sampleAt) / 50_000_000L;
        if (elapsedTicks >= sampleTicks) return 0;
        return sampleTicks - (int) elapsedTicks;
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

    private enum Kind {
        PENDING,
        CASTING,
        DURATION
    }

    private static final class Status {
        private Kind kind;
        private String name;
        private float progress;
        private int totalTicks;
        private int remainingTicks;
        private long durationSampleAt;
        private final long createdAt;
        private long exitAt;
        private boolean interrupted;
        private boolean suppressed;

        private Status(Kind kind, String name, float progress, long now, boolean suppressed) {
            this.kind = kind;
            this.name = name;
            this.progress = clamp(progress);
            this.createdAt = now;
            this.suppressed = suppressed;
        }

        private static Status pending(long now) {
            return new Status(Kind.PENDING, "", 1.0f, now, false);
        }

        private static Status casting(String name, float progress, long now, boolean suppressed) {
            return new Status(Kind.CASTING, name, progress, now, suppressed);
        }

        private void activate(Kind newKind, String newName, int ticks, long now) {
            kind = newKind;
            name = newName;
            exitAt = 0L;
            interrupted = false;
            if (newKind == Kind.DURATION) {
                remainingTicks = ticks;
                durationSampleAt = now;
                totalTicks = Math.max(totalTicks, ticks);
                progress = totalTicks == 0 ? 0.0f : ticks / (float) totalTicks;
            } else {
                durationSampleAt = 0L;
            }
        }

        private int remainingTicks(long now) {
            return remainingDurationTicks(remainingTicks, durationSampleAt, now);
        }

        private void finish(long now, boolean wasInterrupted) {
            exitAt = now;
            interrupted = wasInterrupted;
        }
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

    private record Row(String arcaneName, String label, float progress, boolean interrupted, float alpha) {
        private Row(String arcaneName, String label, float progress, boolean interrupted) {
            this(arcaneName, label, progress, interrupted, 1.0f);
        }
    }
}
