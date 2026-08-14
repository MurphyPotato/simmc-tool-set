package com.murphypotato.simmctoolset.internal.accessory.client;

import com.murphypotato.simmctoolset.internal.accessory.domain.LoadoutResult;
import com.murphypotato.simmctoolset.internal.accessory.domain.PlanVariant;
import com.murphypotato.simmctoolset.internal.accessory.domain.WeaponMode;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;

public final class ToolSession {
    public enum View {
        REVIEW,
        POOL,
        PLAN
    }

    private final Screen returnScreen;
    private final ScreenHandler returnHandler;
    private final int returnSyncId;
    private final ContainerHighlightManager highlighter;
    private final ToolPreviewSelection selection;
    private View view = View.POOL;
    private boolean viewInitialized;

    private ToolSession(
        Screen returnScreen,
        ScreenHandler returnHandler,
        ContainerHighlightManager highlighter,
        ToolPreviewSelection selection
    ) {
        this.returnScreen = returnScreen;
        this.returnHandler = returnHandler;
        this.returnSyncId = returnHandler == null ? -1 : returnHandler.syncId;
        this.highlighter = highlighter;
        this.selection = selection;
    }

    public static ToolSession world(ContainerHighlightManager highlighter, ToolPreviewSelection selection) {
        return new ToolSession(null, null, highlighter, selection);
    }

    /** Creates a session opened from a non-container parent such as the Tool Set control screen. */
    public static ToolSession external(Screen parent, ContainerHighlightManager highlighter,
                                       ToolPreviewSelection selection) {
        return new ToolSession(parent, null, highlighter, selection);
    }

    public static ToolSession handled(
        HandledScreen<?> screen,
        ContainerHighlightManager highlighter,
        ToolPreviewSelection selection
    ) {
        return new ToolSession(screen, screen.getScreenHandler(), highlighter, selection);
    }

    public WeaponMode weapon() {
        return selection.weapon();
    }

    public void setWeapon(WeaponMode weapon) {
        selection.setWeapon(weapon);
    }

    public PlanVariant variant() {
        return selection.variant();
    }

    public void setVariant(PlanVariant variant) {
        selection.setVariant(variant);
    }

    public View view() {
        return view;
    }

    public void setView(View view) {
        this.view = view == null ? View.POOL : view;
        this.viewInitialized = true;
    }

    public void initializeView(boolean hasReviewItems) {
        if (viewInitialized) return;
        view = hasReviewItems ? View.REVIEW : View.POOL;
        viewInitialized = true;
    }

    public boolean hasReturnScreen() {
        return returnScreen != null;
    }

    public String returnButtonLabel(ClientAccessoryController controller) {
        if (selectedGuidancePlan(controller) != null) return "返回并开始配装指引";
        return hasReturnScreen() ? "返回原页面" : "返回游戏";
    }

    public boolean guidanceActive() {
        return highlighter.guidanceActive();
    }

    public void endGuidance() {
        highlighter.endGuidance();
    }

    public void returnToOrigin(MinecraftClient client, ClientAccessoryController controller) {
        LoadoutResult selected = selectedGuidancePlan(controller);
        if (selected != null) highlighter.startGuidance(selected, weapon(), variant());
        if (returnScreen == null) {
            client.setScreen(null);
            return;
        }
        if (returnHandler == null) {
            client.setScreen(returnScreen);
            return;
        }
        boolean valid = ToolSessionGuard.canRestore(
            client.player != null,
            client.player == null ? null : client.player.currentScreenHandler,
            returnHandler,
            returnHandler.syncId,
            returnSyncId
        );
        if (!valid) {
            client.setScreen(null);
            if (client.inGameHud != null) {
                client.inGameHud.setOverlayMessage(Text.literal("原容器已失效，无法恢复页面"), false);
            }
            return;
        }
        client.setScreen(returnScreen);
    }

    private LoadoutResult selectedGuidancePlan(ClientAccessoryController controller) {
        if (view != View.PLAN || controller.plansDirty() || controller.calculating()) return null;
        LoadoutResult selected = controller.loadout(weapon(), variant()).orElse(null);
        if (selected == null || selected.accessories().values().stream().allMatch(accessory -> accessory.isBlank())) {
            return null;
        }
        return selected;
    }
}
