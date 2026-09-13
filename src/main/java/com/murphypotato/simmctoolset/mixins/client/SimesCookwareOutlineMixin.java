package com.murphypotato.simmctoolset.mixins.client;

import com.murphypotato.simmctoolset.internal.simes.SimesBrewingCookwareHud;
import net.minecraft.client.render.entity.DisplayEntityRenderer;
import net.minecraft.client.render.entity.state.DisplayEntityRenderState;
import net.minecraft.entity.decoration.DisplayEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Uses the native outline render pass without changing entity tracked data. */
@Mixin(DisplayEntityRenderer.class)
abstract class SimesCookwareOutlineMixin {
    @Inject(method = "updateRenderState(Lnet/minecraft/entity/decoration/DisplayEntity;"
            + "Lnet/minecraft/client/render/entity/state/DisplayEntityRenderState;F)V", at = @At("TAIL"))
    private void simmcToolSet$cookwareOutline(DisplayEntity entity, DisplayEntityRenderState state,
                                             float tickProgress, CallbackInfo ci) {
        int color = SimesBrewingCookwareHud.cookwareOutline(entity);
        // Vanilla refreshes this field every frame. No override preserves other mods/server glow.
        if (color != 0) state.outlineColor = color;
    }
}
