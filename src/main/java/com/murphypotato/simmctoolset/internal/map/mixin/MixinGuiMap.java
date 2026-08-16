/*
 * Injection chronology adapted from JR1258/EarthMC-Map-Addon MixinGuiMap,
 * c85c5003855eb47868868b931624b951cffba74e, Apache-2.0.
 * Modified for SIMMC, Xaero World Map 1.39.13, and Minecraft 1.21.8 primitive input ABI.
 */
package com.murphypotato.simmctoolset.mixins.map;

import com.murphypotato.simmctoolset.internal.map.SimmcMapClient;
import com.murphypotato.simmctoolset.internal.map.integration.DimensionVisibility;
import com.murphypotato.simmctoolset.internal.map.integration.XaeroCompatibility;
import com.murphypotato.simmctoolset.internal.map.integration.XaeroScaleMath;
import com.murphypotato.simmctoolset.internal.map.integration.XaeroZoomDecision;
import com.murphypotato.simmctoolset.internal.map.render.WorldMapInputRouting;
import com.murphypotato.simmctoolset.internal.map.render.WorldMapOverlayRenderer;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import org.lwjgl.glfw.GLFW;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.map.render.util.GuiRenderUtil;
import xaero.map.animation.Animation;
import xaero.map.gui.MapMouseButtonPress;

@Mixin(targets = "xaero.map.gui.GuiMap", priority = 1100, remap = false)
public abstract class MixinGuiMap {
    private static final double XAERO_ZOOM_FLOOR = 0.0625;

    @Shadow(remap = false) private double cameraX;
    @Shadow(remap = false) private double cameraZ;
    @Shadow(remap = false) private double scale;
    @Shadow(remap = false) private double userScale;
    @Shadow(remap = false) private double screenScale;
    @Shadow(remap = false) private static double destScale;
    @Shadow(remap = false) private RegistryKey<World> lastViewedDimensionId;
    @Shadow(remap = false) private Animation zoomAnim;
    @Shadow(remap = false) private MapMouseButtonPress leftMouseButton;
    @Shadow(remap = false) private double getScaleMultiplier(int framebufferMin) {
        throw new AssertionError("mixin shadow");
    }

    @Inject(
            method = {"render", "method_25394"},
            at = @At(value = "FIELD", target = "Lxaero/map/settings/ModSettings;renderArrow:Z",
                    opcode = Opcodes.GETFIELD, ordinal = 0, shift = At.Shift.BEFORE),
            require = 1, expect = 1, allow = 1, remap = false
    )
    private void simmc_tool_set$beforeNativeArrow(DrawContext context, int mouseX, int mouseY,
                                             float delta, CallbackInfo ci) {
        if (!XaeroCompatibility.worldMapEnabled() || !DimensionVisibility.isVisible(lastViewedDimensionId)) return;
        try {
            WorldMapOverlayRenderer.View view = simmc_tool_set$view();
            if (view == null) return;
            SimmcMapClient.renderWorldMapSurface(context, view,
                    WorldMapInputRouting.requestContext(leftMouseButton != null && leftMouseButton.isDown));
            // 1.21.8 has no DrawContext.drawDeferredElements().
            GuiRenderUtil.flushGUI();
        } catch (Throwable failure) {
            XaeroCompatibility.disable("世界地图表面渲染失败", failure);
        }
    }

    @Inject(method = "renderPreDropdown", at = @At("HEAD"),
            require = 1, expect = 1, allow = 1, remap = false)
    private void simmc_tool_set$uiPass(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!XaeroCompatibility.worldMapEnabled() || !DimensionVisibility.isVisible(lastViewedDimensionId)) return;
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
        if (!XaeroCompatibility.worldMapEnabled() || !DimensionVisibility.isVisible(lastViewedDimensionId)) return;
        WorldMapOverlayRenderer.View view = simmc_tool_set$view();
        if (view != null && SimmcMapClient.onWorldMapClick(mouseX, mouseY, button, view)) {
            cir.setReturnValue(true);
        }
        simmc_tool_set$applyPendingNavigation(view);
    }

    @Inject(method = {"keyPressed", "method_25404"}, at = @At("HEAD"),
            cancellable = true, require = 1, expect = 1, allow = 1, remap = false)
    private void simmc_tool_set$keyPressed(int keyCode, int scanCode, int modifiers,
                                     CallbackInfoReturnable<Boolean> cir) {
        if (!XaeroCompatibility.worldMapEnabled() || !DimensionVisibility.isVisible(lastViewedDimensionId)) return;
        WorldMapOverlayRenderer.View view = simmc_tool_set$view();
        if (SimmcMapClient.onWorldMapKey(keyCode)) {
            simmc_tool_set$applyPendingNavigation(view);
            cir.setReturnValue(true);
            return;
        }
        if (keyCode == GLFW.GLFW_KEY_HOME && view != null) {
            if (SimmcMapClient.fullWorldMode()) {
                SimmcMapClient.leaveFullWorldMode();
                simmc_tool_set$clampToXaeroFloor();
            } else {
                WorldMapOverlayRenderer.View fitted = SimmcMapClient.fitWorldBorder(view);
                if (fitted != view) {
                    MinecraftClient client = MinecraftClient.getInstance();
                    int framebufferMin = Math.min(client.getWindow().getFramebufferWidth(),
                            client.getWindow().getFramebufferHeight());
                    double multiplier = getScaleMultiplier(framebufferMin);
                    XaeroZoomDecision.Update update = XaeroZoomDecision.fit(fitted.scale(),
                            simmc_tool_set$screenFactor(), multiplier);
                    cameraX = fitted.centerX();
                    cameraZ = fitted.centerZ();
                    simmc_tool_set$applyScaleUpdate(update);
                    SimmcMapClient.enterFullWorldMode(update.userScale());
                }
            }
            cir.setReturnValue(true);
        }
    }

    @Inject(method = {"charTyped", "method_25400"}, at = @At("HEAD"),
            cancellable = true, require = 1, expect = 1, allow = 1, remap = false)
    private void simmc_tool_set$charTyped(char character, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (XaeroCompatibility.worldMapEnabled() && DimensionVisibility.isVisible(lastViewedDimensionId)
                && SimmcMapClient.onWorldMapChar(character, modifiers)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = {"mouseScrolled", "method_25401"}, at = @At("HEAD"),
            cancellable = true, require = 1, expect = 1, allow = 1, remap = false)
    private void simmc_tool_set$mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical,
                                        CallbackInfoReturnable<Boolean> cir) {
        if (XaeroCompatibility.worldMapEnabled() && DimensionVisibility.isVisible(lastViewedDimensionId)
                && SimmcMapClient.onWorldMapScroll(vertical)) cir.setReturnValue(true);
    }

    @Inject(method = {"removed", "method_25432"}, at = @At("TAIL"),
            require = 1, expect = 1, allow = 1, remap = false)
    private void simmc_tool_set$removed(CallbackInfo ci) {
        SimmcMapClient.clearWorldMapScreenState();
        simmc_tool_set$clampToXaeroFloor();
    }

    @ModifyExpressionValue(method = "changeZoom(DI)V",
            at = @At(value = "CONSTANT", args = "doubleValue=0.0625"),
            require = 2, expect = 2, allow = 2, remap = false)
    private double simmc_tool_set$dynamicZoomFloor(double original) {
        if (!XaeroCompatibility.worldMapEnabled() || !DimensionVisibility.isVisible(lastViewedDimensionId)
                || !SimmcMapClient.fullWorldMode()) return original;
        return SimmcMapClient.worldMapZoomFloor(original);
    }

    private WorldMapOverlayRenderer.View simmc_tool_set$view() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) return null;
        int width = client.getWindow().getScaledWidth();
        int height = client.getWindow().getScaledHeight();
        double mapScale = XaeroScaleMath.viewScale(scale, simmc_tool_set$screenFactor());
        if (!Double.isFinite(mapScale) || mapScale <= 0 || width <= 0 || height <= 0) return null;
        return new WorldMapOverlayRenderer.View(cameraX, cameraZ, mapScale, width, height);
    }

    private double simmc_tool_set$screenFactor() {
        return Double.isFinite(screenScale) && screenScale > 0 ? screenScale : 1.0;
    }

    private void simmc_tool_set$clampToXaeroFloor() {
        if (!SimmcMapClient.fullWorldMode()) {
            MinecraftClient client = MinecraftClient.getInstance();
            int framebufferMin = Math.min(client.getWindow().getFramebufferWidth(),
                    client.getWindow().getFramebufferHeight());
            simmc_tool_set$applyScaleUpdate(XaeroZoomDecision.clamp(userScale, destScale,
                    getScaleMultiplier(framebufferMin)));
        }
    }

    private void simmc_tool_set$applyScaleUpdate(XaeroZoomDecision.Update update) {
        userScale = update.userScale();
        destScale = update.destScale();
        scale = update.renderScale();
        if (update.clearAnimation()) zoomAnim = null;
    }

    private void simmc_tool_set$applyPendingNavigation(WorldMapOverlayRenderer.View view) {
        SimmcMapClient.consumeWorldMapLocation().ifPresent(point -> { cameraX = point.x(); cameraZ = point.z(); });
        if (view != null && SimmcMapClient.consumeFitWorldRequest()) {
            WorldMapOverlayRenderer.View fitted = SimmcMapClient.fitWorldBorder(view);
            if (fitted != view) {
                MinecraftClient client = MinecraftClient.getInstance();
                int framebufferMin = Math.min(client.getWindow().getFramebufferWidth(), client.getWindow().getFramebufferHeight());
                XaeroZoomDecision.Update update = XaeroZoomDecision.fit(fitted.scale(), simmc_tool_set$screenFactor(), getScaleMultiplier(framebufferMin));
                cameraX = fitted.centerX(); cameraZ = fitted.centerZ(); simmc_tool_set$applyScaleUpdate(update); SimmcMapClient.enterFullWorldMode(update.userScale());
            }
        }
    }
}
