package com.murphypotato.simmctoolset.mixins.accessory;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(HandledScreen.class)
public interface HandledScreenAccessor {
    @Accessor("x")
    int travelHunter$getX();

    @Accessor("y")
    int travelHunter$getY();
}
