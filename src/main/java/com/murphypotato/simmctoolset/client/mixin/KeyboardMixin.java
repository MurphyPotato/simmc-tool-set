package com.murphypotato.simmctoolset.mixins.client;

import com.murphypotato.simmctoolset.client.ToolSetKeyRouter;
import net.minecraft.client.Keyboard;
import net.minecraft.client.input.KeyInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
abstract class KeyboardMixin {
    @Inject(method = "onKey", at = @At("HEAD"), cancellable = true)
    private void simmcToolSet$routePrefix(long window, int action, KeyInput input, CallbackInfo ci) {
        if (ToolSetKeyRouter.onKey(window, input.key(), input.scancode(), action, input.modifiers())) ci.cancel();
    }
}
