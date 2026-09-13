package com.murphypotato.simmctoolset.internal.simes;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Licensed Simes-derived fermentation and cookware hints. */
public final class SimesBrewingCookwareHud {
    private static final long SCAN_MS = 250L;
    private static final long PENDING_MS = 1_500L;
    private static final long WITHDRAW_SETTLE_MS = 150L;
    private static final long DEPOSIT_CONFIRM_MS = 2_500L;
    private static final long DEPOSIT_SETTLE_MS = 150L;
    private static final long TARGET_RETENTION_MS = 5_000L;
    private static final long FERMENTER_RETENTION_MS = 5_000L;
    private static final long COOKER_RETENTION_MS = 3_000L;
    private static final int MAX_VISIBLE_ITEMS = 5;
    private static final int MAX_ITEMS_PER_TYPE = SimesFermenterLedger.MAX_ITEMS_PER_TYPE;
    private static final Identifier ID = Identifier.of("simmc_tool_set", "simes_brewing_cookware");

    /* These are the server's exact CraftEngine IDs, not display-name heuristics. */
    private static final Set<String> FERMENTATION_IDS = Set.of(
            "smc:kitchenware_3/fermentation_barrel",
            "kitchenware_3/fermentation_barrel");
    private static final Set<String> COOKWARE_IDS = Set.of(
            "smc:kitchenware_2/cookware",
            "smc:kitchenware_2/cookware_open",
            "smc:kitchenware_2/steamer",
            "smc:kitchenware_2/steamer_open",
            "smc:kitchenware_2/skillet",
            "smc:kitchenware_2/skillet_open",
            "kitchenware_2/cookware",
            "kitchenware_2/cookware_open",
            "kitchenware_2/steamer",
            "kitchenware_2/steamer_open",
            "kitchenware_2/skillet",
            "kitchenware_2/skillet_open");
    private static final Set<String> OPEN_COOKWARE_IDS = Set.of(
            "smc:kitchenware_2/cookware_open",
            "smc:kitchenware_2/steamer_open",
            "smc:kitchenware_2/skillet_open",
            "kitchenware_2/cookware_open",
            "kitchenware_2/steamer_open",
            "kitchenware_2/skillet_open");

    private static final Map<BlockPos, Fermenter> fermenters = new HashMap<>();
    private static final Map<BlockPos, SimesCookerState> cookers = new HashMap<>();
    private static final Map<BlockPos, List<ItemStack>> cookerContents = new HashMap<>();
    private static Map<UUID, Integer> cookerOutlines = Map.of();
    private static net.minecraft.client.world.ClientWorld outlineWorld;
    private static final Map<String, Integer> clockIngredients = new LinkedHashMap<>();
    private static final List<DepositIntent> depositIntents = new ArrayList<>();
    private static Map<String, InventoryEntry> depositBaseline = Map.of();
    private static final Map<String, Integer> depositAccounted = new HashMap<>();
    private static PendingWithdrawal pendingWithdrawal;
    private static BlockPos clockTarget;
    private static BlockPos lastFermentationTarget;
    private static long lastFermentationInteractionAt;
    private static long lastScan;
    private static boolean collectingIngredients;
    private static boolean initialized;
    private static volatile List<ProjectedPanel> projectedPanels = List.of();

    private SimesBrewingCookwareHud() {
    }

    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;

        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (player != client.player || hand != Hand.MAIN_HAND || !SimesFeatureController.brewingEnabled()) {
                return ActionResult.PASS;
            }

            BlockPos pos = hit.getBlockPos();
            boolean fermentationBarrel = isFermentationBarrelAt(client, pos);
            if (fermentationBarrel) {
                long now = System.currentTimeMillis();
                lastFermentationTarget = pos;
                lastFermentationInteractionAt = now;
                fermenters.computeIfAbsent(pos, Fermenter::new).touch(now);
            }

            ItemStack held = player.getStackInHand(hand);
            if (isClock(held)) {
                clockTarget = fermentationBarrel ? pos : null;
                collectingIngredients = false;
                clockIngredients.clear();
            } else if (held.isEmpty() && fermentationBarrel && fermenters.containsKey(pos)) {
                finishDepositTracking();
                long now = System.currentTimeMillis();
                if (pendingWithdrawal != null && pendingWithdrawal.pos.equals(pos)
                        && now - pendingWithdrawal.lastInteractionAt <= PENDING_MS) {
                    pendingWithdrawal.lastInteractionAt = now;
                    pendingWithdrawal.interactions++;
                } else {
                    pendingWithdrawal = new PendingWithdrawal(pos, inventorySnapshot(player), now);
                }
            } else if (SimesFeatureController.fermentationEnabled()
                    && fermentationBarrel && !held.isEmpty()) {
                finishWithdrawalTracking();
                // Inventory deltas are only attributable to one barrel at a time.
                // Starting a new target closes the previous attribution window.
                if (depositIntents.stream().anyMatch(intent -> !intent.pos.equals(pos))) {
                    finishDepositTracking();
                }
                if (depositIntents.isEmpty()) {
                    depositBaseline = inventorySnapshot(player);
                    depositAccounted.clear();
                }
                String itemKey = details(held);
                DepositIntent existing = depositIntents.stream()
                        .filter(intent -> intent.pos.equals(pos) && intent.itemKey.equals(itemKey))
                        .findFirst().orElse(null);
                if (existing == null) {
                    Fermenter state = fermenters.computeIfAbsent(pos, Fermenter::new);
                    depositIntents.add(new DepositIntent(pos, itemKey, held.copyWithCount(1),
                            state.count(held), System.currentTimeMillis()));
                } else {
                    existing.at = System.currentTimeMillis();
                }
            }
            return ActionResult.PASS;
        });

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) acceptClockMessage(message.getString());
        });
        ClientTickEvents.END_CLIENT_TICK.register(SimesBrewingCookwareHud::tick);
        WorldRenderEvents.AFTER_ENTITIES.register(SimesBrewingCookwareHud::projectPanels);
        HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, ID,
                SimesBrewingCookwareHud::renderHud);
    }

    public static synchronized void reset() {
        fermenters.clear();
        cookers.clear();
        cookerContents.clear();
        cookerOutlines = Map.of();
        outlineWorld = null;
        clockIngredients.clear();
        depositIntents.clear();
        depositBaseline = Map.of();
        depositAccounted.clear();
        pendingWithdrawal = null;
        clockTarget = null;
        lastFermentationTarget = null;
        lastFermentationInteractionAt = 0L;
        lastScan = 0L;
        collectingIngredients = false;
        projectedPanels = List.of();
    }

    private static void tick(MinecraftClient client) {
        if (!SimesFeatureController.brewingEnabled()) {
            reset();
            return;
        }
        if (client.player == null || client.world == null) {
            reset();
            return;
        }

        long now = System.currentTimeMillis();
        confirmDeposit(client.player, now);
        confirmWithdrawal(client.player, now);
        if (now - lastScan < SCAN_MS) return;
        lastScan = now;
        discoverTargetedFermenter(client);
        scanCookers(client, now);
        removeStaleFermenters(now);
    }

    private static void discoverTargetedFermenter(MinecraftClient client) {
        if (!SimesFeatureController.fermentationEnabled()
                || !(client.crosshairTarget instanceof BlockHitResult target)) return;
        BlockPos pos = target.getBlockPos();
        if (!isFermentationBarrelAt(client, pos)) return;
        fermenters.computeIfAbsent(pos, Fermenter::new).touch(System.currentTimeMillis());
    }

    private static void confirmDeposit(net.minecraft.entity.player.PlayerEntity player, long now) {
        if (depositIntents.isEmpty()) return;
        Map<String, InventoryEntry> current = inventorySnapshot(player);
        for (Map.Entry<String, InventoryEntry> original : depositBaseline.entrySet()) {
            int currentCount = current.containsKey(original.getKey()) ? current.get(original.getKey()).count : 0;
            int totalDecrease = Math.max(0, original.getValue().count - currentCount);
            DepositIntent intent = null;
            for (int index = depositIntents.size() - 1; index >= 0; index--) {
                DepositIntent candidate = depositIntents.get(index);
                if (candidate.itemKey.equals(original.getKey())
                        && now - candidate.at >= DEPOSIT_SETTLE_MS
                        && now - candidate.at <= DEPOSIT_CONFIRM_MS) {
                    intent = candidate;
                    break;
                }
            }
            if (intent == null) continue;

            String sessionKey = intent.pos + "|" + intent.itemKey;
            int applied = depositAccounted.getOrDefault(sessionKey, 0);
            int desired = Math.min(totalDecrease,
                    Math.max(0, MAX_ITEMS_PER_TYPE - intent.initialCount));
            int correction = desired - applied;
            Fermenter state = fermenters.computeIfAbsent(intent.pos, Fermenter::new);
            state.touch(now);
            if (correction > 0) state.add(intent.item, correction);
            else if (correction < 0) state.remove(intent.item, -correction);
            depositAccounted.put(sessionKey, desired);
        }

        depositIntents.removeIf(intent -> now - intent.at > DEPOSIT_CONFIRM_MS);
        if (depositIntents.isEmpty()) {
            depositBaseline = Map.of();
            depositAccounted.clear();
        }
    }

    private static void confirmWithdrawal(net.minecraft.entity.player.PlayerEntity player, long now) {
        if (pendingWithdrawal == null) return;
        if (now - pendingWithdrawal.lastInteractionAt > PENDING_MS) {
            pendingWithdrawal = null;
            return;
        }
        if (now - pendingWithdrawal.startedAt < WITHDRAW_SETTLE_MS) return;

        Fermenter state = fermenters.get(pendingWithdrawal.pos);
        if (state == null) {
            pendingWithdrawal = null;
            return;
        }
        state.touch(now);
        Map<String, InventoryEntry> current = inventorySnapshot(player);
        Map<String, InventoryEntry> candidates = new HashMap<>(pendingWithdrawal.inventory);
        candidates.putAll(current);
        for (Map.Entry<String, InventoryEntry> entry : candidates.entrySet()) {
            InventoryEntry previous = pendingWithdrawal.inventory.get(entry.getKey());
            InventoryEntry latest = current.get(entry.getKey());
            int before = previous == null ? 0 : previous.count;
            int after = latest == null ? 0 : latest.count;
            int desired = Math.max(0, after - before);
            int applied = pendingWithdrawal.accounted.getOrDefault(entry.getKey(), 0);
            int correction = desired - applied;
            ItemStack stack = latest != null ? latest.stack : entry.getValue().stack;
            if (correction > 0) {
                int removed = state.remove(stack, correction);
                if (removed > 0) {
                    pendingWithdrawal.accounted.put(entry.getKey(), applied + removed);
                    pendingWithdrawal.items.put(entry.getKey(), stack.copyWithCount(1));
                }
            } else if (correction < 0 && applied > 0) {
                int restored = Math.min(-correction, applied);
                ItemStack recorded = pendingWithdrawal.items.getOrDefault(entry.getKey(), stack);
                state.addRecorded(recorded, restored);
                int remaining = applied - restored;
                if (remaining == 0) {
                    pendingWithdrawal.accounted.remove(entry.getKey());
                    pendingWithdrawal.items.remove(entry.getKey());
                } else {
                    pendingWithdrawal.accounted.put(entry.getKey(), remaining);
                }
            }
        }
    }

    private static void finishWithdrawalTracking() {
        pendingWithdrawal = null;
    }

    private static void finishDepositTracking() {
        depositIntents.clear();
        depositBaseline = Map.of();
        depositAccounted.clear();
    }

    private static Map<String, InventoryEntry> inventorySnapshot(net.minecraft.entity.player.PlayerEntity player) {
        PlayerInventory inventory = player.getInventory();
        Map<String, InventoryEntry> result = new HashMap<>();
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (stack.isEmpty()) continue;
            String key = details(stack);
            InventoryEntry previous = result.get(key);
            int count = stack.getCount() + (previous == null ? 0 : previous.count);
            result.put(key, new InventoryEntry(stack.copyWithCount(1), count));
        }
        return result;
    }

    private static void scanCookers(MinecraftClient client, long now) {
        if (!SimesFeatureController.cookwareEnabled()) {
            cookerOutlines = Map.of();
            outlineWorld = null;
            cookers.clear();
            cookerContents.clear();
            return;
        }
        Map<UUID, Integer> outlines = new HashMap<>();
        Box area = client.player.getBoundingBox().expand(20.0D);
        Map<BlockPos, List<ItemDisplayEntity>> groups = new HashMap<>();
        for (ItemDisplayEntity display : client.world.getEntitiesByType(
                TypeFilter.instanceOf(ItemDisplayEntity.class), area, entity -> !entity.isRemoved())) {
            ItemStack shown = display.getItemStack();
            if (shown.isEmpty()) continue;
            groups.computeIfAbsent(display.getBlockPos(), ignored -> new ArrayList<>()).add(display);
        }

        for (Map.Entry<BlockPos, List<ItemDisplayEntity>> group : groups.entrySet()) {
            List<ItemDisplayEntity> displays = group.getValue();
            if (displays.stream().anyMatch(display -> isFermentationBarrel(display.getItemStack()))) continue;
            ItemDisplayEntity vesselDisplay = displays.stream()
                    .filter(display -> isCookingVessel(display.getItemStack()))
                    .findFirst().orElse(null);
            if (vesselDisplay == null) continue;

            ItemStack vessel = vesselDisplay.getItemStack();
            List<ItemStack> contents = displays.stream()
                    .map(ItemDisplayEntity::getItemStack)
                    .filter(stack -> !isCookware(stack))
                    // The existing state/HUD ledger uses one key per unit, not per entity.
                    .flatMap(stack -> java.util.stream.IntStream.range(0, stack.getCount())
                            .mapToObj(index -> stack.copyWithCount(1)))
                    .toList();
            SimesCookerState state = cookers.computeIfAbsent(group.getKey(), ignored -> new SimesCookerState());
            state.observe(normalizedCookwareName(vessel), isOpenCookingVessel(vessel),
                    contents.stream().map(SimesBrewingCookwareHud::details).toList(), now);
            cookerContents.put(group.getKey(), contents);
            if (state.statusColor() != 0) outlines.put(vesselDisplay.getUuid(), state.statusColor());
        }
        cookerOutlines = Map.copyOf(outlines);
        outlineWorld = client.world;

        cookers.entrySet().removeIf(entry -> {
            boolean stale = now - entry.getValue().lastSeen() > COOKER_RETENTION_MS;
            if (stale) cookerContents.remove(entry.getKey());
            return stale;
        });
    }

    /** Render-state override only: never changes tracked entity data or teams. */
    public static int cookwareOutline(net.minecraft.entity.decoration.DisplayEntity entity) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!SimesFeatureController.cookwareEnabled() || client.player == null
                || client.world == null || client.world != outlineWorld || entity.isRemoved()
                || System.currentTimeMillis() - lastScan > 1_000L
                || !(entity instanceof ItemDisplayEntity display)
                || !isCookingVessel(display.getItemStack())
                || !client.player.getBoundingBox().expand(20.0D).intersects(entity.getBoundingBox())) return 0;
        return cookerOutlines.getOrDefault(entity.getUuid(), 0);
    }

    private static void removeStaleFermenters(long now) {
        fermenters.entrySet().removeIf(entry -> {
            Fermenter state = entry.getValue();
            if (!isFermentationBarrelAt(MinecraftClient.getInstance(), entry.getKey())) {
                clearFermenterState(entry.getKey());
                return true;
            }
            if (now - state.lastSeen() <= FERMENTER_RETENTION_MS || state.hasRetainedState()) return false;
            BlockPos stalePos = entry.getKey();
            if (stalePos.equals(clockTarget)) clockTarget = null;
            if (stalePos.equals(lastFermentationTarget)) {
                lastFermentationTarget = null;
                lastFermentationInteractionAt = 0L;
            }
            return true;
        });
    }

    private static void clearFermenterState(BlockPos pos) {
        if (pos.equals(clockTarget)) clockTarget = null;
        if (pos.equals(lastFermentationTarget)) {
            lastFermentationTarget = null;
            lastFermentationInteractionAt = 0L;
        }
    }

    private static void acceptClockMessage(String raw) {
        SimesBrewingClockParser.Event event = SimesBrewingClockParser.parse(raw);
        if (event instanceof SimesBrewingClockParser.Ignore) return;
        if (event instanceof SimesBrewingClockParser.MaterialsHeader) {
            if (clockMessageFermenter() != null) {
                collectingIngredients = true;
                clockIngredients.clear();
            }
            return;
        }
        if (event instanceof SimesBrewingClockParser.Ingredient ingredient) {
            if (collectingIngredients) clockIngredients.put(ingredient.name(), ingredient.count());
            return;
        }

        Fermenter state = clockMessageFermenter();
        if (state == null) return;
        state.touch(System.currentTimeMillis());
        if (event instanceof SimesBrewingClockParser.Invalidate invalidation) {
            applyClockIngredients(state);
            state.timer.invalidate(invalidation.status());
            state.status = invalidation.status();
            state.remaining = "";
            state.product = "";
            state.serverUpdatedAt = 0L;
            return;
        }
        if (event instanceof SimesBrewingClockParser.Remaining remaining) {
            state.remaining = remaining.remaining();
            state.product = remaining.product();
            if (FermentationCountdown.isServerComplete(remaining.remaining())) {
                state.timer.markServerComplete(System.nanoTime());
                state.status = "已完成";
            } else {
                state.timer.calibrate(remaining.remaining(), System.nanoTime());
                state.status = "发酵中";
            }
            state.serverUpdatedAt = System.currentTimeMillis();
            applyClockIngredients(state);
            return;
        }
        if (event instanceof SimesBrewingClockParser.Completed) {
            state.timer.markServerComplete(System.nanoTime());
            state.status = "已完成";
            state.remaining = "已完成";
            state.serverUpdatedAt = System.currentTimeMillis();
            applyClockIngredients(state);
        }
    }

    private static Fermenter clockMessageFermenter() {
        long now = System.currentTimeMillis();
        if (clockTarget != null && now - lastFermentationInteractionAt > TARGET_RETENTION_MS) {
            clockTarget = null;
        }
        if (clockTarget == null) {
            if (lastFermentationTarget == null || now - lastFermentationInteractionAt > TARGET_RETENTION_MS) {
                return null;
            }
            clockTarget = lastFermentationTarget;
        }
        Fermenter state = fermenters.computeIfAbsent(clockTarget, Fermenter::new);
        state.touch(now);
        return state;
    }

    private static void applyClockIngredients(Fermenter state) {
        if (collectingIngredients && !clockIngredients.isEmpty()) {
            state.replace(clockIngredients);
        }
        collectingIngredients = false;
        clockIngredients.clear();
    }

    private static void projectPanels(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!SimesFeatureController.brewingEnabled() || client.player == null || client.world == null
                || !(client.crosshairTarget instanceof BlockHitResult target)) {
            projectedPanels = List.of();
            return;
        }

        BlockPos targetPos = target.getBlockPos();
        if (targetPos.toCenterPos().squaredDistanceTo(client.player.getEntityPos()) > 100.0D) {
            projectedPanels = List.of();
            return;
        }
        Panel panel = null;
        if (SimesFeatureController.fermentationEnabled()) {
            Fermenter fermenter = fermenters.get(targetPos);
            if (fermenter != null) panel = fermenter.panel();
        }
        if (panel == null && SimesFeatureController.cookwareEnabled()) {
            SimesCookerState cooker = cookers.get(targetPos);
            if (cooker != null && cooker.hasContents()) panel = cookerPanel(targetPos, cooker);
        }
        if (panel == null) {
            projectedPanels = List.of();
            return;
        }

        // 1.21.11's world render context exposes render state rather than the
        // old camera and projection matrices. GameRenderer.project() performs
        // the same camera-relative NDC projection for the current frame.
        Vec3d projected = client.gameRenderer.project(targetPos.toCenterPos().add(0.0D, 0.85D, 0.0D));
        int width = client.getWindow().getScaledWidth();
        int height = client.getWindow().getScaledHeight();
        Panel selectedPanel = panel;
        SimesWorldProjection.project((float) projected.x, (float) projected.y, (float) projected.z, width, height)
                .ifPresentOrElse(screen -> projectedPanels = List.of(new ProjectedPanel(selectedPanel, screen.x(), screen.y())),
                        () -> projectedPanels = List.of());
    }

    private static void renderHud(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;
        for (ProjectedPanel projected : projectedPanels) {
            drawPanel(context, client, projected.panel, projected.x, projected.y);
        }
    }

    private static void drawPanel(DrawContext context, MinecraftClient client, Panel panel, int centerX, int anchorY) {
        int width = panel.lines.stream().mapToInt(line -> client.textRenderer.getWidth(line.text)
                + (line.stack.isEmpty() ? 0 : 20)).max().orElse(80);
        width = Math.max(width, client.textRenderer.getWidth(panel.title));
        width = Math.min(Math.max(96, width + 10), Math.max(96, client.getWindow().getScaledWidth() - 8));
        int height = 23 + panel.lines.size() * 18;
        int x = Math.max(4, Math.min(centerX - width / 2, client.getWindow().getScaledWidth() - width - 4));
        int y = Math.max(4, anchorY - height - 8);

        context.fill(x - 2, y - 2, x + width + 2, y + height + 2, 0xB0000000);
        context.fill(x, y, x + width, y + height, 0x73000000);
        context.fill(x, y, x + width, y + 1, panel.color);
        context.drawCenteredTextWithShadow(client.textRenderer, Text.literal(panel.title),
                x + width / 2, y + 5, panel.color);
        int lineY = y + 20;
        for (Line line : panel.lines) {
            int textX = x + 7;
            if (!line.stack.isEmpty()) {
                context.drawItem(line.stack, x + 4, lineY - 4);
                textX += 20;
            }
            context.drawTextWithShadow(client.textRenderer, Text.literal(line.text), textX, lineY, 0xFFFFFFFF);
            lineY += 18;
        }
    }

    private static Panel cookerPanel(BlockPos pos, SimesCookerState state) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String key : state.contents()) counts.merge(key, 1, Integer::sum);
        List<Line> lines = new ArrayList<>();
        Map<String, ItemStack> samples = new HashMap<>();
        for (ItemStack stack : cookerContents.getOrDefault(pos, List.of())) samples.put(details(stack), stack);
        int shown = 0;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (shown++ >= MAX_VISIBLE_ITEMS) break;
            ItemStack sample = samples.get(entry.getKey());
            String label = sample == null ? displayName(entry.getKey()) : sample.getName().getString();
            lines.add(new Line(sample == null ? ItemStack.EMPTY : sample.copyWithCount(1),
                    label + " ×" + entry.getValue()));
        }
        if (counts.size() > MAX_VISIBLE_ITEMS) {
            lines.add(new Line(ItemStack.EMPTY, "以及其他 " + (counts.size() - MAX_VISIBLE_ITEMS) + " 种食材"));
        }
        String timer = state.isFailed() ? "烹饪失败"
                : state.isCompleted() ? "烹饪完成"
                : state.status() == SimesCookerState.Status.READY ? "准备中：未开始计时"
                : state.remainingMillis(System.currentTimeMillis()) > 0L
                ? String.format(Locale.ROOT, "预计：%.1f 秒（本地）", state.remainingMillis(System.currentTimeMillis()) / 1_000D)
                : "预计时间已到，等待服务器";
        lines.add(new Line(ItemStack.EMPTY, timer));
        String title = state.isFailed() ? state.cookwareName() + " · 失败"
                : state.isCompleted() ? state.cookwareName() + " · 已完成"
                : state.status() == SimesCookerState.Status.READY ? state.cookwareName() + " · 准备中"
                : state.cookwareName() + " · 烹饪中";
        return new Panel(pos, title, state.statusColor() == 0 ? 0xFF74E6FF : state.statusColor(), lines);
    }

    private static boolean isClock(ItemStack stack) {
        if (stack.isEmpty()) return false;
        String value = details(stack);
        return value.contains("pocket_watch") || value.contains("cooking_clock")
                || value.contains("烹饪钟") || value.contains("厨房钟");
    }

    private static boolean isCookware(ItemStack stack) {
        return isFermentationBarrel(stack) || isCookingVessel(stack)
                || details(stack).contains("kitchenware")
                || details(stack).contains("蒸锅") || details(stack).contains("煮锅")
                || details(stack).contains("炖锅") || details(stack).contains("煎锅")
                || details(stack).contains("炒锅");
    }

    private static boolean isFermentationBarrelAt(MinecraftClient client, BlockPos pos) {
        if (client.world == null) return false;
        // Display entities for adjacent barrels can overlap the old +/-0.55 box.
        // Keep a narrow horizontal association and verify the entity center too.
        Box associationBox = new Box(pos).expand(0.4D, 1.0D, 0.4D);
        Vec3d center = pos.toCenterPos();
        return !client.world.getEntitiesByType(TypeFilter.instanceOf(ItemDisplayEntity.class), associationBox,
                display -> !display.isRemoved() && isFermentationBarrel(display.getItemStack())
                        && Math.abs(display.getX() - center.x) <= 0.4D
                        && Math.abs(display.getZ() - center.z) <= 0.4D
                        && display.getY() >= pos.getY() - 0.5D
                        && display.getY() <= pos.getY() + 2.0D).isEmpty();
    }

    private static boolean isFermentationBarrel(ItemStack stack) {
        String value = details(stack);
        return FERMENTATION_IDS.contains(craftEngineId(stack))
                || value.contains("kitchenware_3/fermentation_barrel");
    }

    private static boolean isCookingVessel(ItemStack stack) {
        String value = details(stack);
        return COOKWARE_IDS.contains(craftEngineId(stack))
                || value.contains("smc:kitchenware_2/cookware")
                || value.contains("smc:kitchenware_2/steamer")
                || value.contains("smc:kitchenware_2/skillet")
                || value.contains("kitchenware_2/cookware")
                || value.contains("kitchenware_2/steamer")
                || value.contains("kitchenware_2/skillet");
    }

    private static boolean isOpenCookingVessel(ItemStack stack) {
        String value = details(stack);
        return OPEN_COOKWARE_IDS.contains(craftEngineId(stack))
                || value.contains("kitchenware_2/cookware_open")
                || value.contains("kitchenware_2/steamer_open")
                || value.contains("kitchenware_2/skillet_open");
    }

    private static String normalizedCookwareName(ItemStack stack) {
        String id = details(stack);
        if (id.contains("kitchenware_2/cookware_open")) return "炖锅 无盖";
        if (id.contains("kitchenware_2/cookware")) return "炖锅";
        if (id.contains("kitchenware_2/skillet")) return "煎锅";
        if (id.contains("kitchenware_2/steamer")) return "蒸锅";
        return stack.getName().getString();
    }

    private static String craftEngineId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        NbtComponent custom = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (custom == null) return "";
        NbtCompound nbt = custom.copyNbt();
        String direct = nbt.getString("craftengine:id", "");
        if (!direct.isBlank()) return direct.toLowerCase(Locale.ROOT);
        NbtCompound nested = nbt.getCompoundOrEmpty("craftengine");
        return nested.getString("id", "").toLowerCase(Locale.ROOT);
    }

    private static String details(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        return (Registries.ITEM.getId(stack.getItem()) + "|" + stack.getName().getString()
                + "|" + stack.getComponents()).toLowerCase(Locale.ROOT);
    }

    private static String displayName(String key) {
        int first = key.indexOf('|');
        int second = first < 0 ? -1 : key.indexOf('|', first + 1);
        if (first >= 0 && second > first) return key.substring(first + 1, second);
        return key;
    }

    public static String statusSummary() {
        if (!fermenters.isEmpty() || !cookers.isEmpty()) return "已记录目标状态，准星指向目标查看信息卡";
        if (lastFermentationTarget != null) return "等待服务器发回烹饪钟状态";
        return "等待扫描发酵桶或厨具";
    }

    private record InventoryEntry(ItemStack stack, int count) {
    }

    private static final class DepositIntent {
        private final BlockPos pos;
        private final String itemKey;
        private final ItemStack item;
        private final int initialCount;
        private long at;

        private DepositIntent(BlockPos pos, String itemKey, ItemStack item, int initialCount, long at) {
            this.pos = pos;
            this.itemKey = itemKey;
            this.item = item;
            this.initialCount = initialCount;
            this.at = at;
        }
    }

    private static final class PendingWithdrawal {
        private final BlockPos pos;
        private final Map<String, InventoryEntry> inventory;
        private final long startedAt;
        private long lastInteractionAt;
        private int interactions = 1;
        private final Map<String, Integer> accounted = new HashMap<>();
        private final Map<String, ItemStack> items = new HashMap<>();

        private PendingWithdrawal(BlockPos pos, Map<String, InventoryEntry> inventory, long at) {
            this.pos = pos;
            this.inventory = inventory;
            this.startedAt = at;
            this.lastInteractionAt = at;
        }
    }

    private record Line(ItemStack stack, String text) {
    }

    private record Panel(BlockPos pos, String title, int color, List<Line> lines) {
    }

    private record ProjectedPanel(Panel panel, int x, int y) {
    }

    private static final class Fermenter {
        private final BlockPos pos;
        private final SimesFermenterLedger ledger = new SimesFermenterLedger();
        private final Map<String, ItemStack> samples = new HashMap<>();
        private final SimesFermentationTimer timer = new SimesFermentationTimer();
        private String remaining = "";
        private String product = "";
        private String status = "状态未知";
        private long serverUpdatedAt;
        private long lastSeen;

        private Fermenter(BlockPos pos) {
            this.pos = pos;
        }

        private void touch(long now) {
            lastSeen = now;
        }

        private long lastSeen() {
            return lastSeen;
        }

        private boolean hasRetainedState() {
            return ledger.hasItems() || timer.hasRetainedState(System.nanoTime());
        }

        private void add(ItemStack stack, int count) {
            if (stack.isEmpty() || count <= 0) return;
            String key = details(stack);
            ledger.add(key, count);
            samples.putIfAbsent(key, stack.copyWithCount(1));
        }

        private int count(ItemStack stack) {
            String key = trackedKey(stack);
            return key == null ? 0 : ledger.count(key);
        }

        private int remove(ItemStack stack, int count) {
            String key = trackedKey(stack);
            if (key == null) return 0;
            int removed = ledger.remove(key, count);
            if (ledger.count(key) == 0) samples.remove(key);
            return removed;
        }

        private String trackedKey(ItemStack stack) {
            String exact = details(stack);
            if (ledger.count(exact) > 0) return exact;
            String name = stack.getName().getString().toLowerCase(Locale.ROOT);
            for (String key : ledger.snapshot().keySet()) {
                if (key.equals(name) || key.contains("|" + name + "|")) return key;
            }
            return null;
        }

        private void addRecorded(ItemStack stack, int count) {
            String key = trackedKey(stack);
            if (key == null) add(stack, count);
            else ledger.add(key, count);
        }

        private void replace(Map<String, Integer> names) {
            ledger.replace(names);
            samples.clear();
        }

        private Panel panel() {
            List<Line> lines = new ArrayList<>();
            int shown = 0;
            Map<String, Integer> tracked = ledger.snapshot();
            for (Map.Entry<String, Integer> entry : tracked.entrySet()) {
                if (shown++ >= MAX_VISIBLE_ITEMS) break;
                ItemStack sample = samples.get(entry.getKey());
                String name = sample == null ? displayName(entry.getKey()) : sample.getName().getString();
                lines.add(new Line(sample == null ? ItemStack.EMPTY : sample.copyWithCount(1),
                        name + " ×" + entry.getValue()));
            }
            if (tracked.size() > MAX_VISIBLE_ITEMS) {
                lines.add(new Line(ItemStack.EMPTY, "以及其他 " + (tracked.size() - MAX_VISIBLE_ITEMS) + " 种食材"));
            }
            if (lines.isEmpty()) lines.add(new Line(ItemStack.EMPTY, "桶内物品：等待记录"));
            if (!product.isEmpty()) lines.add(new Line(ItemStack.EMPTY, "产物：" + product));
            lines.add(new Line(ItemStack.EMPTY, "时间：" + timer.displayAt(System.nanoTime())));
            lines.add(new Line(ItemStack.EMPTY, "更新：" + (serverUpdatedAt == 0L ? "未校准" : elapsed(serverUpdatedAt))));
            return new Panel(pos, "发酵桶 · " + status, 0xFFFFB45E, lines);
        }
    }

    private static String elapsed(long at) {
        long seconds = Math.max(0L, (System.currentTimeMillis() - at) / 1_000L);
        return seconds < 60L ? seconds + " 秒前" : seconds / 60L + " 分钟前";
    }
}
