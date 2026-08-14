package com.murphypotato.simmctoolset.internal.map.integration;

import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;

public final class DimensionVisibility {
    private DimensionVisibility() { }

    /** Uses the viewed dimension from the current Xaero frame, never the player's physical world. */
    public static boolean isVisible(RegistryKey<World> viewedDimension) {
        return World.OVERWORLD.equals(viewedDimension);
    }
}
