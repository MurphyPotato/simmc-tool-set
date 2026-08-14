package com.murphypotato.simmctoolset.internal.accessory.client;

import com.murphypotato.simmctoolset.internal.accessory.domain.AccessorySource;
import com.murphypotato.simmctoolset.internal.accessory.domain.ContainerLocation;
import net.minecraft.item.ItemStack;

import java.util.Objects;

public record CapturedStack(
    ItemStack stack,
    AccessorySource source,
    String displayContainerTitle,
    String sourceGroupLabel,
    ContainerLocation location
) {
    public CapturedStack {
        stack = stack.copy();
        displayContainerTitle = Objects.requireNonNullElse(displayContainerTitle, source.containerTitle()).strip();
        if (displayContainerTitle.isEmpty()) displayContainerTitle = source.containerTitle();
        if (displayContainerTitle.length() > 200) displayContainerTitle = displayContainerTitle.substring(0, 200);
        sourceGroupLabel = Objects.requireNonNullElse(sourceGroupLabel, displayContainerTitle).strip();
        if (sourceGroupLabel.isEmpty()) sourceGroupLabel = displayContainerTitle;
        if (sourceGroupLabel.length() > 220) sourceGroupLabel = sourceGroupLabel.substring(0, 220);
        location = location == null ? ContainerLocation.unlocated("未定位容器") : location;
    }
}
