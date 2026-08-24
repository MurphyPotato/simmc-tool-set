/*
 * Injection chronology adapted from JR1258/EarthMC-Map-Addon MixinGuiMap,
 * c85c5003855eb47868868b931624b951cffba74e, Apache-2.0.
 * Modified for SIMMC, Xaero World Map 1.39.13, and Minecraft 1.21.8 primitive input ABI.
 */
package com.murphypotato.simmctoolset.mixins.map;

import com.murphypotato.simmctoolset.internal.map.SimmcMapClient;
import com.murphypotato.simmctoolset.internal.map.integration.DimensionVisibility;
import com.murphypotato.simmctoolset.internal.map.integration.XaeroCompatibility;
import com.murphypotato.simmctoolset.internal.map.integration.WorldRuntimeState;
import com.murphypotato.simmctoolset.internal.map.integration.WorldViewAdapter;
import com.murphypotato.simmctoolset.internal.map.render.WorldMapOverlayRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "xaero.map.gui.GuiMap", priority = 1100, remap = false)
public abstract class MixinGuiMap {

    @Shadow(remap = false) private double cameraX;
    @Shadow(remap = false) private double cameraZ;
    @Shadow(remap = false) private double scale;
    @Shadow(remap = false) private double screenScale;
    @Shadow(remap = false) private RegistryKey<World> lastViewedDimensionId;

    @Inject(method = "renderPreDropdown", at = @At("HEAD"),
            require = 1, expect = 1, allow = 1, remap = false)
    private void simmc_tool_set$uiPass(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!XaeroCompatibility.worldMapEnabled() || !WorldRuntimeState.current().uiEnabled()
                || !DimensionVisibility.isVisible(lastViewedDimensionId)) return;
        try {
            SimmcMapClient.renderWorldMapUi(context, mouseX, mouseY, simmc_tool_set$view());
        } catch (Throwable failure) {
            XaeroCompatibility.reportUiFailure(failure);
        }
    }

    @Inject(method = {"mouseClicked", "method_25402"}, at = @At("HEAD"),
            cancellable = true, require = 1, expect = 1, allow = 1, remap = false)
    private void simmc_tool_set$mouseClicked(double mouseX, double mouseY, int button,
                                       CallbackInfoReturnable<Boolean> cir) {
        if (!XaeroCompatibility.worldMapEnabled() || !WorldRuntimeState.current().uiEnabled()
                || !DimensionVisibility.isVisible(lastViewedDimensionId)) return;
        WorldMapOverlayRenderer.View view = simmc_tool_set$view();
        if (SimmcMapClient.onWorldMapClick(mouseX, mouseY, button, view)) {
            cir.setReturnValue(true);
        }
        simmc_tool_set$applyPendingNavigation(view);
    }

    @Inject(method = {"keyPressed", "method_25404"}, at = @At("HEAD"),
            cancellable = true, require = 1, expect = 1, allow = 1, remap = false)
    private void simmc_tool_set$keyPressed(int keyCode, int scanCode, int modifiers,
                                     CallbackInfoReturnable<Boolean> cir) {
        if (!XaeroCompatibility.worldMapEnabled() || !WorldRuntimeState.current().uiEnabled()
                || !DimensionVisibility.isVisible(lastViewedDimensionId)) return;
        WorldMapOverlayRenderer.View view = simmc_tool_set$view();
        if (SimmcMapClient.onWorldMapKey(keyCode)) {
            simmc_tool_set$applyPendingNavigation(view);
            cir.setReturnValue(true);
            return;
        }
        if (keyCode == GLFW.GLFW_KEY_HOME && view != null) {
            if (!WorldRuntimeState.current().navigationEnabled()) return;
            if (SimmcMapClient.fullWorldMode()) {
                SimmcMapClient.leaveFullWorldMode();
            } else {
                WorldMapOverlayRenderer.View fitted = SimmcMapClient.fitWorldBorder(view);
                if (fitted != view) SimmcMapClient.enterFullWorldMode(fitted.scale());
            }
            cir.setReturnValue(true);
        }
    }

    @Inject(method = {"charTyped", "method_25400"}, at = @At("HEAD"),
            cancellable = true, require = 1, expect = 1, allow = 1, remap = false)
    private void simmc_tool_set$charTyped(char character, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (XaeroCompatibility.worldMapEnabled() && WorldRuntimeState.current().uiEnabled()
                && DimensionVisibility.isVisible(lastViewedDimensionId)
                && SimmcMapClient.onWorldMapChar(character, modifiers)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = {"mouseScrolled", "method_25401"}, at = @At("HEAD"),
            cancellable = true, require = 1, expect = 1, allow = 1, remap = false)
    private void simmc_tool_set$mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical,
                                        CallbackInfoReturnable<Boolean> cir) {
        if (XaeroCompatibility.worldMapEnabled() && WorldRuntimeState.current().uiEnabled()
                && DimensionVisibility.isVisible(lastViewedDimensionId)
                && SimmcMapClient.onWorldMapScroll(vertical)) cir.setReturnValue(true);
    }

    @Inject(method = {"removed", "method_25432"}, at = @At("TAIL"),
            require = 1, expect = 1, allow = 1, remap = false)
    private void simmc_tool_set$removed(CallbackInfo ci) {
        SimmcMapClient.clearWorldMapScreenState();
    }

    private WorldMapOverlayRenderer.View simmc_tool_set$view() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) return null;
        int width = client.getWindow().getScaledWidth();
        int height = client.getWindow().getScaledHeight();
        return WorldViewAdapter.view(cameraX, cameraZ, scale, simmc_tool_set$screenFactor(), width, height).orElse(null);
    }

    private double simmc_tool_set$screenFactor() {
        return Double.isFinite(screenScale) && screenScale > 0 ? screenScale : 1.0;
    }

    private void simmc_tool_set$applyPendingNavigation(WorldMapOverlayRenderer.View view) {
        if (!WorldRuntimeState.current().navigationEnabled()) return;
        SimmcMapClient.consumeWorldMapLocation().ifPresent(point -> { cameraX = point.x(); cameraZ = point.z(); });
        if (view != null && SimmcMapClient.consumeFitWorldRequest()) {
            WorldMapOverlayRenderer.View fitted = SimmcMapClient.fitWorldBorder(view);
            if (fitted != view) { cameraX = fitted.centerX(); cameraZ = fitted.centerZ(); SimmcMapClient.enterFullWorldMode(fitted.scale()); }
        }
    }
}
