package com.murphypotato.simmctoolset.mixins.map;

import com.murphypotato.simmctoolset.internal.map.SimmcMapClient;
import com.murphypotato.simmctoolset.internal.map.integration.DimensionVisibility;
import com.murphypotato.simmctoolset.internal.map.integration.WorldMapHealth;
import com.murphypotato.simmctoolset.internal.map.integration.WorldRuntimeState;
import com.murphypotato.simmctoolset.internal.map.integration.WorldViewAdapter;
import com.murphypotato.simmctoolset.internal.map.render.WorldMapInputRouting;
import com.murphypotato.simmctoolset.internal.map.render.WorldMapOverlayRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.map.gui.MapMouseButtonPress;

/** World D surface anchor. It has no link to legacy render utility classes. */
@Mixin(targets = "xaero.map.gui.GuiMap", priority = 1100, remap = false)
public abstract class MixinWorldSurfaceProfiled {
    @Shadow(remap = false) private double cameraX;
    @Shadow(remap = false) private double cameraZ;
    @Shadow(remap = false) private double scale;
    @Shadow(remap = false) private double screenScale;
    @Shadow(remap = false) private RegistryKey<World> lastViewedDimensionId;
    @Shadow(remap = false) private MapMouseButtonPress leftMouseButton;

    @Inject(method = {"render", "method_25394"}, at = @At(value = "FIELD",
            target = "Lxaero/map/common/config/option/WorldMapProfiledConfigOptions;ARROW:Lxaero/lib/common/config/option/BooleanConfigOption;",
            opcode = Opcodes.GETSTATIC, ordinal = 0, shift = At.Shift.BEFORE),
            require = 1, expect = 1, allow = 1, remap = false)
    private void simmc_tool_set$surface(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        WorldRuntimeState state = WorldRuntimeState.current();
        if (!state.surfaceEnabled() || !DimensionVisibility.isVisible(lastViewedDimensionId)) return;
        WorldMapOverlayRenderer.View view = view();
        if (view == null) return;
        try {
            SimmcMapClient.renderWorldMapSurface(context, view,
                    WorldMapInputRouting.requestContext(leftMouseButton != null && leftMouseButton.isDown));
            state.surface().flushGui();
        } catch (Throwable failure) {
            state.fail(WorldMapHealth.Capability.SURFACE);
        }
    }

    private WorldMapOverlayRenderer.View view() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) return null;
        return WorldViewAdapter.view(cameraX, cameraZ, scale, screenScale,
                client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight()).orElse(null);
    }
}
