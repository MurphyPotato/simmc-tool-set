package com.murphypotato.simmctoolset.mixins.client;

import com.murphypotato.simmctoolset.internal.simes.SimesArcaneStatusHud;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.BossBarS2CPacket;
import net.minecraft.network.packet.s2c.play.OverlayMessageS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Reads Simes arcane status bars without replacing vanilla boss-bar behavior. */
@Mixin(ClientPlayNetworkHandler.class)
abstract class SimesBossBarMixin {
    @Inject(method = "onOverlayMessage", at = @At("HEAD"), cancellable = true)
    private void simmcToolSet$handleActionBar(OverlayMessageS2CPacket packet, CallbackInfo ci) {
        if (com.murphypotato.simmctoolset.internal.simes.SimesArcaneHud.handleActionBar(packet.text())) ci.cancel();
    }

    @Inject(method = "onBossBar", at = @At("HEAD"), cancellable = true)
    private void simmcToolSet$readArcaneStatus(BossBarS2CPacket packet, CallbackInfo ci) {
        if (SimesArcaneStatusHud.handleBossBar(packet)) ci.cancel();
    }
}
