package com.murphypotato.simmctoolset.mixins.client;

import com.murphypotato.simmctoolset.client.ToolSetKeyRouter;
import net.minecraft.client.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
abstract class KeyboardMixin {
    @Inject(method = "onKey", at = @At("HEAD"), cancellable = true)
    private void simmcToolSet$routePrefix(long window, int keyCode, int scanCode, int action,
                                          int modifiers, CallbackInfo ci) {
        if (ToolSetKeyRouter.onKey(window, keyCode, scanCode, action, modifiers)) ci.cancel();
    }
}
