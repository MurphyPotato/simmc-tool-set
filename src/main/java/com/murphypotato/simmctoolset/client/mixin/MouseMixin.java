package com.murphypotato.simmctoolset.mixins.client;

import com.murphypotato.simmctoolset.client.ToolSetKeyRouter;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
abstract class MouseMixin {
    @Inject(method = "onMouseButton", at = @At("HEAD"))
    private void simmcToolSet$clearPrefix(long window, int button, int action, int modifiers, CallbackInfo ci) {
        ToolSetKeyRouter.clear();
    }
}
