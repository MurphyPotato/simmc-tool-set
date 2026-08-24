package com.murphypotato.simmctoolset.mixins.map;

import com.murphypotato.simmctoolset.internal.map.SimmcMapClient;
import com.murphypotato.simmctoolset.internal.map.integration.MinimapHealth;
import com.murphypotato.simmctoolset.internal.map.integration.MinimapRuntimeState;
import net.minecraft.client.gui.DrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.hud.minimap.module.MinimapRenderer;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.render.module.ModuleRenderContext;

@Mixin(value = MinimapRenderer.class, remap = false)
public abstract class MixinMinimapModuleRenderer {
    @Inject(method = "render",
            at = @At(value = "INVOKE",
                    target = "Lxaero/common/minimap/render/MinimapRenderer;renderOutsidePip",
                    shift = At.Shift.BEFORE),
            require = 1, expect = 1, allow = 1, remap = false)
    private void simmc_tool_set$renderOverlay(MinimapSession session, ModuleRenderContext renderContext,
                                        DrawContext context, float partialTick, CallbackInfo callback) {
        try {
            SimmcMapClient.renderMinimap(context, session, renderContext);
        } catch (LinkageError | RuntimeException failure) {
            // The runtime state is deliberately fail-closed; Xaero remains usable.
            MinimapRuntimeState.current().health().fail(MinimapHealth.Capability.COMMON_RENDER);
        }
    }
}
