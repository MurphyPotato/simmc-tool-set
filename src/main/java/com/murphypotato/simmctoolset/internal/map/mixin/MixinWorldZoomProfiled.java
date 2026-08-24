package com.murphypotato.simmctoolset.mixins.map;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.murphypotato.simmctoolset.internal.map.SimmcMapClient;
import com.murphypotato.simmctoolset.internal.map.integration.WorldRuntimeState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** World D extended zoom limits. Ordinary Xaero zoom remains unchanged. */
@Mixin(targets = "xaero.map.gui.GuiMap", priority = 1100, remap = false)
public abstract class MixinWorldZoomProfiled {
    @ModifyExpressionValue(method = "applyZoomLimits()V",
            at = @At(value = "CONSTANT", args = "doubleValue=0.0625"),
            require = 1, expect = 1, allow = 1, remap = false)
    private double simmc_tool_set$profiledBaseFloor(double original) {
        return WorldRuntimeState.current().extendedZoomEnabled() && SimmcMapClient.fullWorldMode()
                ? SimmcMapClient.worldMapZoomFloor(original) : original;
    }

    @ModifyExpressionValue(method = "applyZoomLimits()V",
            at = @At(value = "CONSTANT", args = "doubleValue=0.001953125"),
            require = 1, expect = 1, allow = 1, remap = false)
    private double simmc_tool_set$profiledExtendedFloor(double original) {
        return WorldRuntimeState.current().extendedZoomEnabled() && SimmcMapClient.fullWorldMode()
                ? SimmcMapClient.worldMapZoomFloor(original) : original;
    }
}
