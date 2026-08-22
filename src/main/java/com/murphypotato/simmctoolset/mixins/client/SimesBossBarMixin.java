package com.murphypotato.simmctoolset.mixins.client;

import com.murphypotato.simmctoolset.internal.simes.SimesArcaneStatusHud;
import com.murphypotato.simmctoolset.internal.simes.ManaHud;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.BossBarS2CPacket;
import net.minecraft.network.packet.s2c.play.ExperienceBarUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.OverlayMessageS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Reads Simes arcane status bars without replacing vanilla boss-bar behavior. */
@Mixin(ClientPlayNetworkHandler.class)
abstract class SimesBossBarMixin {
    private static final String FORCE_MAIN_THREAD = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread("
            + "Lnet/minecraft/network/packet/Packet;"
            + "Lnet/minecraft/network/listener/PacketListener;"
            + "Lnet/minecraft/util/thread/ThreadExecutor;)V";

    @Inject(method = "onOverlayMessage", at = @At(value = "INVOKE", target = FORCE_MAIN_THREAD,
            shift = At.Shift.AFTER), cancellable = true)
    private void simmcToolSet$handleActionBar(OverlayMessageS2CPacket packet, CallbackInfo ci) {
        if (com.murphypotato.simmctoolset.internal.simes.SimesArcaneHud.handleActionBar(packet.text())) ci.cancel();
    }

    @Inject(method = "onBossBar", at = @At(value = "INVOKE", target = FORCE_MAIN_THREAD,
            shift = At.Shift.AFTER), cancellable = true)
    private void simmcToolSet$readArcaneStatus(BossBarS2CPacket packet, CallbackInfo ci) {
        if (SimesArcaneStatusHud.handleBossBar(packet)) ci.cancel();
    }

    @Inject(method = "onExperienceBarUpdate", at = @At(value = "INVOKE", target = FORCE_MAIN_THREAD,
            shift = At.Shift.AFTER), cancellable = true)
    private void simmcToolSet$separateWandMana(ExperienceBarUpdateS2CPacket packet, CallbackInfo ci) {
        if (ManaHud.handleExperiencePacket(packet)) ci.cancel();
    }
}
