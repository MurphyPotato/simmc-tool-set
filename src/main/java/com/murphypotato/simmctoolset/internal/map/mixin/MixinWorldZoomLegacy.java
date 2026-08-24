package com.murphypotato.simmctoolset.mixins.map;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.murphypotato.simmctoolset.internal.map.SimmcMapClient;
import com.murphypotato.simmctoolset.internal.map.integration.WorldRuntimeState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** World A-C extended zoom constants. Normal Xaero zoom remains untouched. */
@Mixin(targets = "xaero.map.gui.GuiMap", priority = 1100, remap = false)
public abstract class MixinWorldZoomLegacy {
    @ModifyExpressionValue(method = "changeZoom(DI)V",
            at = @At(value = "CONSTANT", args = "doubleValue=0.0625"),
            require = 2, expect = 2, allow = 2, remap = false)
    private double simmc_tool_set$legacyFloor(double original) {
        return WorldRuntimeState.current().extendedZoomEnabled() && SimmcMapClient.fullWorldMode()
                ? SimmcMapClient.worldMapZoomFloor(original) : original;
    }
}
