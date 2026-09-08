package com.murphypotato.simmctoolset.internal.simes;

import net.minecraft.client.gui.hud.ClientBossBar;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.text.Text;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ArcaneHudContractTest {
    @Test
    void parsesFullWidthColonSecondsAndResidualText() {
        ArcaneCooldownParser.Result result = ArcaneCooldownParser.parse(
                "火球术 冷却剩余：2.5s | 治愈术 冷却剩余: 1 秒 | ready");
        List<ArcaneCooldownParser.Value> values = result.values();
        assertEquals(2, values.size());
        assertEquals("火球术", values.get(0).name());
        assertEquals(2.5, values.get(0).remaining());
        assertEquals("治愈术", values.get(1).name());
        assertEquals("ready", result.residual());
    }

    @Test
    void parsesMixedFirstPacketAndPreservesInputAndMouseHints() {
        ArcaneCooldownParser.Result result = ArcaneCooldownParser.parse(
                "雷击 冷却剩余：4.7s 按 Shift 取消吟唱｜治愈术 冷却剩余: 54.1 秒 | [鼠标提示]");

        assertEquals(List.of(
                new ArcaneCooldownParser.Value("雷击", 4.7),
                new ArcaneCooldownParser.Value("治愈术", 54.1)), result.values());
        assertEquals("按 Shift 取消吟唱 | [鼠标提示]", result.residual());
    }

    @Test
    void acceptsLicensedSeparatorColonAndUnitVariantsWithoutSimilarTextFalsePositives() {
        ArcaneCooldownParser.Result variants = ArcaneCooldownParser.parse(
                "雷电射线冷却剩余:0.9S|火球术 冷却 剩余： 1 秒｜引力术冷却剩余：18s");
        assertEquals(List.of("雷电射线", "火球术", "引力术"),
                variants.values().stream().map(ArcaneCooldownParser.Value::name).toList());
        assertEquals("", variants.residual());

        for (String nonCooldown : List.of(
                "雷击 冷却：4.7s", "雷击 剩余：4.7s", "雷击 冷却剩余：约4.7s", "释放 御风术")) {
            ArcaneCooldownParser.Result result = ArcaneCooldownParser.parse(nonCooldown);
            assertTrue(result.values().isEmpty(), nonCooldown);
            assertEquals(nonCooldown, result.residual());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void firstMixedCooldownPacketCreatesVisibleStateForNextRenderFrame() throws Exception {
        ArcaneCooldownParser.Result firstPacket = ArcaneCooldownParser.parse(
                "雷击 冷却剩余：4.7s 按 Shift 取消吟唱");
        Method update = SimesArcaneHud.class.getDeclaredMethod("updateCooldowns", List.class);
        Field cooldownsField = SimesArcaneHud.class.getDeclaredField("COOLDOWNS");
        update.setAccessible(true);
        cooldownsField.setAccessible(true);
        Map<String, Object> cooldowns = (Map<String, Object>) cooldownsField.get(null);
        try {
            cooldowns.clear();
            update.invoke(null, firstPacket.values());

            Object cooldown = cooldowns.get("雷击");
            assertTrue(cooldown != null);
            Field createdAtField = cooldown.getClass().getDeclaredField("createdAt");
            Method remainingAt = cooldown.getClass().getDeclaredMethod("remainingAt", long.class);
            Method alpha = cooldown.getClass().getDeclaredMethod("alpha", long.class);
            createdAtField.setAccessible(true);
            remainingAt.setAccessible(true);
            alpha.setAccessible(true);
            long nextFrame = createdAtField.getLong(cooldown) + 16_666_667L;
            assertTrue((double) remainingAt.invoke(cooldown, nextFrame) > 4.6);
            assertTrue((float) alpha.invoke(cooldown, nextFrame) > 0.0f);
        } finally {
            cooldowns.clear();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void publicCooldownReleaseMessageRemainsAnIndependentPath() throws Exception {
        Field releasedField = SimesArcaneStatusHud.class.getDeclaredField("RELEASED");
        Field cooldownsField = SimesArcaneStatusHud.class.getDeclaredField("GLOBAL_COOLDOWNS");
        releasedField.setAccessible(true);
        cooldownsField.setAccessible(true);

        Pattern released = (Pattern) releasedField.get(null);
        Map<String, Double> globalCooldowns = (Map<String, Double>) cooldownsField.get(null);
        assertTrue(released.matcher("释放 御风术").matches());
        assertEquals(40.0, globalCooldowns.get("御风术"));
        assertTrue(ArcaneCooldownParser.parse("释放 御风术").values().isEmpty());
    }

    @Test
    void stripsOnlyArcaneInputHintsFromActionBarResidualText() {
        assertTrue(SimesArcaneHud.isArcaneInputHintOnly("上 Shift"));
        assertEquals("ready", SimesArcaneHud.stripArcaneInputHints("上 ready Shift"));
        assertEquals("ready | status", SimesArcaneHud.stripArcaneInputHints("ready | status"));
    }
    @Test
    void canonicalizesTheTwoKnownZhuhuaSpellNames() {
        assertEquals("蜘化术", ArcaneColors.canonicalName("蛛化术"));
        assertEquals(ArcaneColors.forName("蜘化术"), ArcaneColors.forName("蛛化术"));
        assertEquals(ArcaneColors.iconFile("蜘化术"), ArcaneColors.iconFile("蛛化术"));
        assertEquals(20, ArcaneColors.spellNames().size());
        assertNotEquals("21_red_barrier.png", ArcaneColors.iconFile("火球术"));
    }

    @Test
    void newConfigDefaultsToVanillaActionBarMode() {
        assertFalse(new ArcaneHudConfig().simesMode);
    }

    @Test
    void configIncludesManaLayoutWithoutForbiddenSimesFeatures() {
        Set<String> names = Arrays.stream(ArcaneHudConfig.class.getDeclaredFields())
                .map(field -> field.getName())
                .collect(Collectors.toSet());

        assertTrue(names.containsAll(Set.of(
                "manaHudEnabled", "manaHudX", "manaHudY", "manaHudScalePercent")));
        assertTrue(names.stream().noneMatch(name -> name.contains("market")
                || name.contains("value") || name.contains("balance") || name.contains("autoMessage")));
    }

    @Test
    void normalizesCoordinatesAndScale() {
        ArcaneHudConfig config = new ArcaneHudConfig();
        config.cooldownX = 4.0;
        config.cooldownY = Double.NaN;
        config.cooldownScalePercent = 1;
        config.arcaneStatusX = -4.0;
        config.arcaneStatusScalePercent = 999;
        config.normalize();
        assertEquals(-1.0, config.cooldownX);
        assertEquals(-1.0, config.cooldownY);
        assertEquals(50, config.cooldownScalePercent);
        assertEquals(-1.0, config.arcaneStatusX);
        assertEquals(200, config.arcaneStatusScalePercent);
    }

    @Test
    void normalizesManaLayoutUnderSchemaTwo() {
        ArcaneHudConfig config = new ArcaneHudConfig();
        config.manaHudX = Double.POSITIVE_INFINITY;
        config.manaHudY = -4.0;
        config.manaHudScalePercent = 999;

        config.normalize();

        assertEquals(2, ArcaneHudConfig.CURRENT_CONFIG_VERSION);
        assertEquals(-1.0, config.manaHudX);
        assertEquals(-1.0, config.manaHudY);
        assertEquals(200, config.manaHudScalePercent);
    }

    @Test
    void predictsManaFromPacketTimeWithoutExceedingBounds() {
        assertEquals(52.0, ManaHud.predictedMana(50.0, 2.0, 180.0,
                1_000_000_000L, 2_000_000_000L), 0.0001);
        assertEquals(180.0, ManaHud.predictedMana(179.0, 5.0, 180.0,
                1_000_000_000L, 2_000_000_000L), 0.0001);
        assertEquals(0.0, ManaHud.predictedMana(-5.0, 2.0, 180.0,
                0L, 1_000_000_000L), 0.0001);
    }

    @Test
    void losingTheWandClearsManaAnimationStateImmediately() throws Exception {
        Field held = ManaHud.class.getDeclaredField("wandHeld");
        Field mana = ManaHud.class.getDeclaredField("mana");
        Field displayed = ManaHud.class.getDeclaredField("displayedMana");
        Field trailing = ManaHud.class.getDeclaredField("trailingMana");
        Field updated = ManaHud.class.getDeclaredField("lastManaUpdateNanos");
        Field ready = ManaHud.class.getDeclaredField("manaReady");
        held.setAccessible(true);
        mana.setAccessible(true);
        displayed.setAccessible(true);
        trailing.setAccessible(true);
        updated.setAccessible(true);
        ready.setAccessible(true);
        try {
            held.setBoolean(null, true);
            mana.setDouble(null, 42.0);
            displayed.setDouble(null, 41.0);
            trailing.setDouble(null, 50.0);
            updated.setLong(null, 123L);
            ready.setBoolean(null, true);

            ManaHud.clearVisualState();

            assertFalse(held.getBoolean(null));
            assertEquals(0.0, mana.getDouble(null));
            assertEquals(0.0, displayed.getDouble(null));
            assertEquals(0.0, trailing.getDouble(null));
            assertEquals(0L, updated.getLong(null));
            assertFalse(ready.getBoolean(null));
        } finally {
            ManaHud.reset();
        }
    }

    @Test
    void switchingWandsClearsOldManaBeforeNewExperiencePacket() throws Exception {
        Method observe = ManaHud.class.getDeclaredMethod("observeWandSignature", int.class);
        Field held = ManaHud.class.getDeclaredField("wandHeld");
        Field mana = ManaHud.class.getDeclaredField("mana");
        Field ready = ManaHud.class.getDeclaredField("manaReady");
        observe.setAccessible(true);
        held.setAccessible(true);
        mana.setAccessible(true);
        ready.setAccessible(true);
        try {
            observe.invoke(null, 11);
            mana.setDouble(null, 42.0);
            ready.setBoolean(null, true);
            observe.invoke(null, 22);

            assertTrue(held.getBoolean(null));
            assertEquals(0.0, mana.getDouble(null));
            assertFalse(ready.getBoolean(null));
        } finally {
            ManaHud.reset();
        }
    }

    @Test
    void sameSlotReplacementUsesStackIdentityWithoutHashingDynamicComponents() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/murphypotato/simmctoolset/internal/simes/ManaHud.java"));
        assertTrue(source.contains("System.identityHashCode(stack);"));
        assertTrue(source.contains("server-driven dynamic data can change it every tick"));
        assertFalse(source.contains("stack.getComponents().hashCode()"));
    }

    @Test
    void recognizesOnlyDocumentedArcaneCodexComponentMarkers() {
        assertTrue(ManaHud.isArcaneCodexComponents("sim_magic:codex_item"));
        assertTrue(ManaHud.isArcaneCodexComponents("smccore:arcane_codex"));
        assertTrue(ManaHud.isArcaneCodexComponents("\"smc:id\":\"arcane_codex\""));
        assertTrue(ManaHud.isArcaneCodexComponents("注能杖，用来承载奥术"));
        assertFalse(ManaHud.isArcaneCodexComponents("minecraft:book"));
    }

    @Test
    void mixinSeparatesWandManaExperiencePackets() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/murphypotato/simmctoolset/mixins/client/SimesBossBarMixin.java"));
        String mana = Files.readString(Path.of(
                "src/main/java/com/murphypotato/simmctoolset/internal/simes/ManaHud.java"));
        assertTrue(source.contains("onExperienceBarUpdate"));
        assertTrue(source.contains("ManaHud.handleExperiencePacket"));
        assertTrue(source.contains("NetworkThreadUtils;forceMainThread"));
        assertFalse(source.contains("at = @At(\"HEAD\")"));
        assertTrue(mana.contains("if (!SimesFeatureController.arcaneEnabled()"));
        String transport = mana.substring(mana.indexOf("handleExperiencePacket"),
                mana.indexOf("public static boolean isArcaneCodex"));
        // Packet interception is independent of the render toggle: the handler
        // must consume wand packets while `manaHudEnabled` only controls drawing.
        assertTrue(transport.contains("shouldConsumeExperience"));
        assertFalse(transport.contains("manaHudEnabled"));
    }

    @Test
    void readsTheFirstThreeDistinctLoreSlotsInOrder() {
        assertEquals(List.of("治愈术", "火球术", "雷电射线"),
                SimesArcaneHud.arcaneNamesFromLore(List.of(
                        "左键 治愈术", "Shift 火球术", "重复 火球术", "右键 雷电射线", "忽略 御风术")));
    }

    @Test
    void cooldownHudUsesOnlyMainHandCodexAndFullComponentHash() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/murphypotato/simmctoolset/internal/simes/SimesArcaneHud.java"));
        String wandUpdate = source.substring(source.indexOf("private static void updateEquippedArcanes"),
                source.indexOf("private static List<String> extractEquippedArcanes"));
        assertTrue(source.contains("ManaHud.isArcaneCodex(stack)"));
        assertTrue(source.contains("stack.getComponents().hashCode()"));
        assertTrue(source.contains("equippedArcanes = List.copyOf(detected)"));
        assertFalse(wandUpdate.contains("client.player.getOffHandStack()"));
        assertTrue(source.contains("!seen.contains(cooldown.name) && cooldown.exitStarted == 0L)"));
    }

    @Test
    void statusHudOwnsTheNativePendingAndSuppressionLifecycle() throws IOException {
        Path directory = Path.of("src/main/java/com/murphypotato/simmctoolset/internal/simes");
        String source = Files.readString(directory.resolve("SimesArcaneStatusHud.java"));
        assertFalse(Files.exists(directory.resolve("ArcaneStatusState.java")));
        assertTrue(source.contains("Map<UUID, Status> STATUSES"));
        assertTrue(source.contains("Set<UUID> HIDDEN_ARCANE_LEVEL_BARS"));
        assertTrue(source.contains("Map<UUID, ClientBossBar> PENDING_BOSS_BARS"));
        assertTrue(source.contains("Map<UUID, ClientBossBar> HIDDEN_BOSS_BARS"));
        assertTrue(source.contains("new SuppressedBossBarIds()"));
        assertTrue(source.contains("Status.pending"));
        assertTrue(source.contains("Kind.PENDING"));
        assertTrue(source.contains("suppressExistingBossBar"));
        assertTrue(source.contains("SUPPRESSED_BOSS_BARS.release(id)"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void pendingBossBarPromotionPreservesTheExactServerSnapshot() throws Exception {
        Field pendingField = SimesArcaneStatusHud.class.getDeclaredField("PENDING_BOSS_BARS");
        Field hiddenField = SimesArcaneStatusHud.class.getDeclaredField("HIDDEN_BOSS_BARS");
        Field suppressedField = SimesArcaneStatusHud.class.getDeclaredField("SUPPRESSED_BOSS_BARS");
        Method suppress = SimesArcaneStatusHud.class.getDeclaredMethod("suppressExistingBossBar", UUID.class);
        pendingField.setAccessible(true);
        hiddenField.setAccessible(true);
        suppressedField.setAccessible(true);
        suppress.setAccessible(true);
        Map<UUID, ClientBossBar> pending = (Map<UUID, ClientBossBar>) pendingField.get(null);
        Map<UUID, ClientBossBar> hidden = (Map<UUID, ClientBossBar>) hiddenField.get(null);
        SuppressedBossBarIds suppressed = (SuppressedBossBarIds) suppressedField.get(null);
        UUID id = UUID.randomUUID();
        ClientBossBar original = new ClientBossBar(id, Text.literal("正在吟唱 火球术"), 0.42f,
                BossBar.Color.PURPLE, BossBar.Style.NOTCHED_10, true, true, true);
        try {
            pending.put(id, original);

            suppress.invoke(null, id);

            ClientBossBar promoted = hidden.get(id);
            assertSame(original, promoted);
            assertFalse(pending.containsKey(id));
            assertTrue(suppressed.contains(id));
            assertEquals("正在吟唱 火球术", promoted.getName().getString());
            assertEquals(0.42f, promoted.getPercent(), 0.0001f);
            assertEquals(BossBar.Color.PURPLE, promoted.getColor());
            assertEquals(BossBar.Style.NOTCHED_10, promoted.getStyle());
            assertTrue(promoted.shouldDarkenSky());
            assertTrue(promoted.hasDragonMusic());
            assertTrue(promoted.shouldThickenFog());
        } finally {
            SimesArcaneStatusHud.reset();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void visualResetReleasesCancelledBossBarIdsForVanillaRebuild() throws Exception {
        Field suppressedField = SimesArcaneStatusHud.class.getDeclaredField("SUPPRESSED_BOSS_BARS");
        Field hiddenField = SimesArcaneStatusHud.class.getDeclaredField("HIDDEN_ARCANE_LEVEL_BARS");
        suppressedField.setAccessible(true);
        hiddenField.setAccessible(true);
        SuppressedBossBarIds suppressed = (SuppressedBossBarIds) suppressedField.get(null);
        Set<UUID> hidden = (Set<UUID>) hiddenField.get(null);
        UUID statusId = UUID.randomUUID();
        UUID levelId = UUID.randomUUID();
        try {
            suppressed.suppress(statusId);
            hidden.add(levelId);

            SimesArcaneStatusHud.clearVisualState();

            assertFalse(suppressed.contains(statusId));
            assertFalse(hidden.contains(levelId));

            SimesArcaneStatusHud.reset();

            assertFalse(suppressed.contains(statusId));
            assertFalse(hidden.contains(levelId));
        } finally {
            SimesArcaneStatusHud.reset();
        }
    }

    @Test
    void runtimeLayoutClampsLargeScaleAndAllAnchoredEdges() {
        assertEquals(0.5f, SimesHudLayoutScreen.runtimeScale(2.0f, 44, 88), 0.0001f);
        assertEquals(0, SimesHudLayoutScreen.runtimeX(1, 44, 88, 0.5f));
        assertEquals(72, SimesHudLayoutScreen.runtimeX(100, 160, 88, 1.0f));
        assertEquals(0, SimesHudLayoutScreen.runtimeTopY(0, 100, 20, 2.0f));
        assertEquals(60, SimesHudLayoutScreen.runtimeTopY(100, 100, 20, 2.0f));
        assertEquals(80, SimesHudLayoutScreen.runtimeBottomY(0, 100, 40, 2.0f));
        assertEquals(100, SimesHudLayoutScreen.runtimeBottomY(100, 100, 40, 2.0f));
    }

    @Test
    void manaPacketHandlerStillConsumesWandExperienceWhenManaDisplayIsOff() throws Exception {
        Field configField = SimesArcaneHud.class.getDeclaredField("config");
        configField.setAccessible(true);
        Object previous = configField.get(null);
        ArcaneHudConfig config = new ArcaneHudConfig();
        config.manaHudEnabled = false;
        try {
            configField.set(null, config);
            assertTrue(ManaHud.shouldConsumeExperience(true, true, true));
            assertFalse(ManaHud.shouldConsumeExperience(true, true, false));
            assertFalse(ManaHud.shouldConsumeExperience(false, true, true));
        } finally {
            configField.set(null, previous);
        }
    }

    @Test
    void durationSamplesCountDownLocallyAndNewSamplesResetTheBaseline() {
        assertEquals(40, SimesArcaneStatusHud.remainingDurationTicks(
                40, 1_000_000_000L, 1_000_000_000L));
        assertEquals(38, SimesArcaneStatusHud.remainingDurationTicks(
                40, 1_000_000_000L, 1_100_000_001L));
        assertEquals(0, SimesArcaneStatusHud.remainingDurationTicks(
                2, 1_000_000_000L, 2_000_000_000L));
        assertEquals(20, SimesArcaneStatusHud.remainingDurationTicks(
                20, 2_000_000_000L, 2_000_000_000L));
        assertEquals(19, SimesArcaneStatusHud.remainingDurationTicks(
                20, 2_000_000_000L, 2_050_000_001L));
    }

    @Test
    void settingsUseVisualResetInsteadOfReleasingSuppressedBossBars() throws IOException {
        String settings = Files.readString(Path.of(
                "src/main/java/com/murphypotato/simmctoolset/internal/simes/SimesArcaneHudSettingsScreen.java"));
        assertTrue(settings.contains("SimesArcaneStatusHud.clearVisualState()"));
        assertFalse(settings.contains("SimesArcaneStatusHud.reset()"));
    }

    @Test
    void topLevelArcaneToggleRoutesThroughTheSimesLifecycle() throws IOException {
        String controller = Files.readString(Path.of(
                "src/main/java/com/murphypotato/simmctoolset/internal/simes/SimesFeatureController.java"));
        String screen = Files.readString(Path.of(
                "src/main/java/com/murphypotato/simmctoolset/client/ToolSetScreen.java"));
        assertTrue(controller.contains("if (!enabled) SimesArcaneStatusHud.clearVisualState()"));
        assertTrue(screen.contains("SimesFeatureController::setArcaneEnabled"));
        assertFalse(screen.contains("ToolSetSettings::setArcaneHudEnabled"));
    }

    @Test
    void arcaneLifecycleResetAlsoPerformsAFullStatusReset() throws IOException {
        String arcane = Files.readString(Path.of(
                "src/main/java/com/murphypotato/simmctoolset/internal/simes/SimesArcaneHud.java"));
        String reset = arcane.substring(arcane.indexOf("public static synchronized void reset()"),
                arcane.indexOf("private static void resetState()"));
        assertTrue(reset.contains("SimesArcaneStatusHud.reset()"));
    }

    @Test
    void legacyMigrationWhitelistsManaAndPreservesDisplayMode() throws IOException {
        Path legacy = Files.createTempFile("simes-hud", ".json");
        Files.writeString(legacy, """
                {
                  "simesMode": true,
                  "arcaneEnabled": false,
                  "arcaneStatusEnabled": false,
                  "manaHudEnabled": false,
                  "manaHudX": 0.25,
                  "manaHudY": 0.75,
                  "manaHudScalePercent": 170,
                  "marketTooltipEnabled": true,
                  "autoMessageX": 0.4
                }
                """);

        ArcaneHudConfig migrated = ArcaneHudConfig.readLegacy(legacy, new ArcaneHudConfig());

        assertTrue(migrated.simesMode);
        assertFalse(migrated.arcaneEnabled);
        assertFalse(migrated.arcaneStatusEnabled);
        assertFalse(migrated.manaHudEnabled);
        assertEquals(0.25, migrated.manaHudX);
        assertEquals(0.75, migrated.manaHudY);
        assertEquals(170, migrated.manaHudScalePercent);
        Files.deleteIfExists(legacy);
    }

    @Test
    void olderConfigSchemasAreMarkedForOneTimeRewrite() {
        assertTrue(ArcaneHudConfig.requiresSchemaWrite(1));
        assertFalse(ArcaneHudConfig.requiresSchemaWrite(2));
        assertFalse(ArcaneHudConfig.requiresSchemaWrite(3));
    }

    @Test
    void settingsExposeOnlyAuthorizedTogglesAndFourHudLayoutTargets() throws IOException {
        String settings = Files.readString(Path.of(
                "src/main/java/com/murphypotato/simmctoolset/internal/simes/SimesArcaneHudSettingsScreen.java"));
        String layout = Files.readString(Path.of(
                "src/main/java/com/murphypotato/simmctoolset/internal/simes/SimesHudLayoutScreen.java"));
        assertTrue(settings.contains("奥术冷却监听"));
        assertTrue(settings.contains("吟唱与持续状态"));
        assertTrue(settings.contains("法杖魔力 HUD"));
        assertTrue(settings.contains("奥术显示："));
        assertTrue(settings.contains("统一 HUD 布局与缩放"));
        assertTrue(settings.contains("new SimesHudLayoutScreen(this)"));
        assertTrue(layout.contains("MANA"));
        assertTrue(layout.contains("ManaHud.renderPreview"));
        assertTrue(layout.contains("for (Target target : Target.values())"));
        assertTrue(layout.contains("previewBounds(target)"));
        String combined = settings + layout;
        assertFalse(combined.contains("市场"));
        assertFalse(combined.contains("估值"));
        assertFalse(combined.contains("余额"));
        assertFalse(combined.contains("自动消息"));
    }

    @Test
    void drawsTheFullThirtyTwoPixelArcaneIconIntoTheSixteenPixelSlot() throws IOException {
        Path source = Path.of("src/main/java/com/murphypotato/simmctoolset/internal/simes");
        assertTrue(Files.readString(source.resolve("SimesArcaneHud.java")).contains(
                "ICON_SIZE, ICON_SIZE, 32, 32, 32, 32);"));
        assertTrue(Files.readString(source.resolve("SimesArcaneStatusHud.java")).contains(
                "ICON_SIZE, ICON_SIZE, 32, 32, 32, 32, (alpha << 24) | 0xFFFFFF);"));
    }

    @Test
    void tracksSuppressedBossBarIdsUntilReleaseOrClear() {
        SuppressedBossBarIds ids = new SuppressedBossBarIds();
        UUID id = UUID.randomUUID();
        ids.suppress(id);
        assertTrue(ids.contains(id));
        assertTrue(ids.release(id));
        assertFalse(ids.contains(id));
        ids.suppress(id);
        ids.clear();
        assertFalse(ids.contains(id));
    }
}
