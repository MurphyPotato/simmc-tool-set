package com.murphypotato.simmctoolset.internal.accessory.client;

import com.murphypotato.simmctoolset.internal.accessory.domain.AccessoryRecord;
import com.murphypotato.simmctoolset.internal.accessory.domain.LoadoutResult;
import com.murphypotato.simmctoolset.internal.accessory.domain.PlanVariant;
import com.murphypotato.simmctoolset.internal.accessory.domain.WeaponMode;
import com.murphypotato.simmctoolset.mixins.accessory.HandledScreenAccessor;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class ContainerHighlightManager {
    private static final int BOW_COLOR = 0xFF45C7F0;
    private static final int SWORD_COLOR = 0xFFFF6B6B;
    private static final int BANNER_BACKGROUND = 0xE810141B;
    private static final int BUTTON_BACKGROUND = 0xFF343E4B;
    private static final int BUTTON_HOVER = 0xFF4B596A;
    private static final int TEXT_COLOR = 0xFFFFFFFF;

    private final ClientAccessoryController controller;
    private final GuidanceSession guidance = new GuidanceSession();
    private final Map<Screen, ScreenState> states = new IdentityHashMap<>();
    private Map<String, String> selectedAccessoryIds = Map.of();

    public ContainerHighlightManager(ClientAccessoryController controller) {
        this.controller = controller;
    }

    public void attach(Screen screen) {
        if (!(screen instanceof HandledScreen<?> handled)) return;
        ScreenState state = new ScreenState();
        states.put(screen, state);
        ScreenEvents.afterRender(screen).register((current, context, mouseX, mouseY, tickDelta) ->
            render(handled, state, context, mouseX, mouseY)
        );
        ScreenMouseEvents.allowMouseClick(screen).register((current, click) -> {
            double mouseX = click.x();
            double mouseY = click.y();
            int button = click.button();
            if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || !guidance.active() || !state.stopButton.contains(mouseX, mouseY)) {
                return true;
            }
            endGuidance();
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.inGameHud != null) {
                client.inGameHud.setOverlayMessage(Text.literal("已结束配装指引"), false);
            }
            return false;
        });
        ScreenEvents.remove(screen).register(removed -> states.remove(removed));
    }

    public void startGuidance(LoadoutResult loadout, WeaponMode weapon, PlanVariant variant) {
        List<String> fingerprints = new ArrayList<>();
        Map<String, String> ids = new LinkedHashMap<>();
        for (AccessoryRecord accessory : loadout.accessories().values()) {
            if (!accessory.isBlank()) {
                fingerprints.add(accessory.fingerprint());
                ids.put(accessory.fingerprint(), accessory.id());
            }
        }
        selectedAccessoryIds = Map.copyOf(ids);
        guidance.start(weapon, variant, fingerprints, controller.guidanceSources(loadout));
        states.values().forEach(ScreenState::invalidate);
    }

    public void endGuidance() {
        guidance.end();
        selectedAccessoryIds = Map.of();
        states.values().forEach(ScreenState::invalidate);
    }

    public boolean guidanceActive() {
        return guidance.active();
    }

    private void render(
        HandledScreen<?> screen,
        ScreenState state,
        DrawContext context,
        int mouseX,
        int mouseY
    ) {
        if (!guidance.active()) {
            state.stopButton = Bounds.EMPTY;
            return;
        }
        if (controller.plansDirty()) {
            endGuidance();
            return;
        }

        Set<Integer> changedSlots = updateStackSnapshot(screen, state);
        if (state.guidanceRevision != guidance.revision() || !changedSlots.isEmpty()) {
            recompute(screen, state, changedSlots);
        }

        HandledScreenAccessor accessor = (HandledScreenAccessor) screen;
        int color = guidance.weapon() == WeaponMode.BOW ? BOW_COLOR : SWORD_COLOR;
        for (Slot slot : screen.getScreenHandler().slots) {
            if (!state.match.slotIds().contains(slot.id)) continue;
            drawBorder(context, accessor.travelHunter$getX() + slot.x, accessor.travelHunter$getY() + slot.y, color);
        }
        drawBanner(screen, state, context, mouseX, mouseY, color);
    }

    private Set<Integer> updateStackSnapshot(HandledScreen<?> screen, ScreenState state) {
        Map<Integer, ItemStack> current = new HashMap<>();
        for (Slot slot : screen.getScreenHandler().slots) {
            if (!slot.isEnabled() || !slot.hasStack()) continue;
            current.put(slot.id, slot.getStack());
        }
        LinkedHashSet<Integer> changed = new LinkedHashSet<>();
        changed.addAll(state.stackSnapshot.keySet());
        changed.addAll(current.keySet());
        changed.removeIf(slotId -> {
            ItemStack previous = state.stackSnapshot.get(slotId);
            ItemStack next = current.get(slotId);
            return previous != null && next != null && ItemStack.areEqual(previous, next);
        });
        state.stackSnapshot = copyStacks(current);
        return changed;
    }

    private void recompute(HandledScreen<?> screen, ScreenState state, Set<Integer> changedSlots) {
        Map<Integer, Slot> slotsById = new HashMap<>();
        for (Slot slot : screen.getScreenHandler().slots) slotsById.put(slot.id, slot);
        for (int slotId : changedSlots) {
            Slot slot = slotsById.get(slotId);
            if (slot == null || !slot.isEnabled() || !slot.hasStack()) {
                state.fingerprintCache.remove(slotId);
                state.failedFingerprintSlots.remove(slotId);
                continue;
            }
            ClientAccessoryController.FingerprintRead fingerprint = controller.readFingerprint(slot.getStack());
            if (!fingerprint.succeeded() || !fingerprint.reliable()) state.failedFingerprintSlots.add(slotId);
            else state.failedFingerprintSlots.remove(slotId);
            if (fingerprint.fingerprint().isPresent()) state.fingerprintCache.put(slotId, fingerprint.fingerprint().get());
            else state.fingerprintCache.remove(slotId);
        }

        controller.observeVisiblePage(screen, state.fingerprintCache, state.failedFingerprintSlots);
        state.pageSource = controller.resolvePageSource(screen).displayTitle();
        for (String fingerprint : guidance.recommendedFingerprintSet()) {
            guidance.replaceSource(
                fingerprint,
                controller.guidanceSourceForAccessory(selectedAccessoryIds.getOrDefault(fingerprint, "")).orElse("")
            );
        }
        state.match = guidance.match(state.fingerprintCache);
        state.guidanceRevision = guidance.revision();
    }

    private void drawBanner(
        HandledScreen<?> screen,
        ScreenState state,
        DrawContext context,
        int mouseX,
        int mouseY,
        int accent
    ) {
        TextRenderer renderer = screen.getTextRenderer();
        int left = 4;
        int right = Math.max(left + 120, screen.width - 4);
        String buttonText = "结束配装指引";
        int buttonWidth = renderer.getWidth(buttonText) + 12;

        Map<String, Integer> otherSources = guidance.otherSourceCounts(
            state.match.foundFingerprints(), state.pageSource
        );
        String otherText = formatOtherSources(otherSources);
        String mainText = "配装指引：" + guidance.planLabel() + "  当前页 "
            + state.match.foundAccessoryCount() + "/" + state.match.requiredAccessoryCount();
        GuidanceBannerLayout layout = GuidanceBannerLayout.compute(
            screen.width,
            renderer.getWidth(mainText),
            buttonWidth,
            !otherText.isEmpty()
        );
        context.fill(left, 3, right, layout.backgroundBottom(), BANNER_BACKGROUND);
        context.drawCenteredTextWithShadow(
            renderer,
            renderer.trimToWidth(mainText, screen.width - 8),
            layout.mainCenterX(),
            layout.mainY(),
            accent
        );
        if (!otherText.isEmpty()) {
            context.drawCenteredTextWithShadow(
                renderer,
                renderer.trimToWidth(otherText, screen.width - 8),
                layout.detailsCenterX(),
                layout.detailsY(),
                TEXT_COLOR
            );
        }

        boolean hovered = mouseX >= layout.buttonLeft() && mouseX < layout.buttonRight()
            && mouseY >= layout.buttonTop() && mouseY < layout.buttonBottom();
        context.fill(
            layout.buttonLeft(), layout.buttonTop(), layout.buttonRight(), layout.buttonBottom(),
            hovered ? BUTTON_HOVER : BUTTON_BACKGROUND
        );
        context.drawCenteredTextWithShadow(
            renderer,
            buttonText,
            (layout.buttonLeft() + layout.buttonRight()) / 2,
            layout.buttonTop() + 2,
            TEXT_COLOR
        );
        state.stopButton = new Bounds(
            layout.buttonLeft(), layout.buttonTop(), layout.buttonRight(), layout.buttonBottom()
        );
    }

    private static String formatOtherSources(Map<String, Integer> sources) {
        if (sources.isEmpty()) return "";
        return "其他来源：" + sources.entrySet().stream()
            .map(entry -> entry.getKey() + "有" + entry.getValue() + "件")
            .reduce((left, right) -> left + "，" + right)
            .orElse("");
    }

    private static Map<Integer, ItemStack> copyStacks(Map<Integer, ItemStack> source) {
        Map<Integer, ItemStack> copy = new HashMap<>();
        source.forEach((slot, stack) -> copy.put(slot, stack.copy()));
        return copy;
    }

    private static void drawBorder(DrawContext context, int x, int y, int color) {
        context.fill(x - 2, y - 2, x + 18, y, color);
        context.fill(x - 2, y + 16, x + 18, y + 18, color);
        context.fill(x - 2, y, x, y + 16, color);
        context.fill(x + 16, y, x + 18, y + 16, color);
    }

    private static final class ScreenState {
        private Map<Integer, ItemStack> stackSnapshot = Map.of();
        private final Map<Integer, String> fingerprintCache = new LinkedHashMap<>();
        private final Set<Integer> failedFingerprintSlots = new LinkedHashSet<>();
        private GuidanceSession.PageMatch match = new GuidanceSession.PageMatch(Set.of(), Set.of(), 0, 0);
        private String pageSource = "";
        private long guidanceRevision = -1;
        private Bounds stopButton = Bounds.EMPTY;

        private void invalidate() {
            guidanceRevision = -1;
            match = new GuidanceSession.PageMatch(Set.of(), Set.of(), 0, 0);
            stopButton = Bounds.EMPTY;
        }
    }

    private record Bounds(int left, int top, int right, int bottom) {
        private static final Bounds EMPTY = new Bounds(0, 0, 0, 0);

        private boolean contains(double x, double y) {
            return x >= left && x < right && y >= top && y < bottom;
        }
    }
}
