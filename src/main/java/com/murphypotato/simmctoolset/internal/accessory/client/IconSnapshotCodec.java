package com.murphypotato.simmctoolset.internal.accessory.client;

import com.murphypotato.simmctoolset.internal.accessory.domain.IconSnapshot;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Optional;

public final class IconSnapshotCodec {
    private IconSnapshotCodec() {
    }

    public static Optional<IconSnapshot> capture(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Optional.empty();
        try {
            String itemId = Registries.ITEM.getId(stack.getItem()).toString();
            Identifier itemModel = stack.get(DataComponentTypes.ITEM_MODEL);
            CustomModelDataComponent customModel = stack.get(DataComponentTypes.CUSTOM_MODEL_DATA);
            DyedColorComponent dyedColor = stack.get(DataComponentTypes.DYED_COLOR);
            Boolean glint = stack.get(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE);
            IconSnapshot snapshot = new IconSnapshot(
                itemId,
                itemModel == null ? "" : itemModel.toString(),
                customModel == null ? List.of() : customModel.floats(),
                customModel == null ? List.of() : customModel.flags(),
                customModel == null ? List.of() : customModel.strings(),
                customModel == null ? List.of() : customModel.colors(),
                dyedColor == null ? null : dyedColor.rgb(),
                glint
            );
            return snapshot.valid() ? Optional.of(snapshot) : Optional.empty();
        } catch (RuntimeException error) {
            return Optional.empty();
        }
    }

    public static Optional<ItemStack> rebuild(IconSnapshot snapshot) {
        if (snapshot == null || !snapshot.valid()) return Optional.empty();
        try {
            Identifier itemId = Identifier.tryParse(snapshot.itemId());
            if (itemId == null || !Registries.ITEM.containsId(itemId)) return Optional.empty();
            Item item = Registries.ITEM.get(itemId);
            ItemStack stack = item.getDefaultStack();

            if (!snapshot.itemModel().isBlank()) {
                Identifier model = Identifier.tryParse(snapshot.itemModel());
                if (model == null) return Optional.empty();
                stack.set(DataComponentTypes.ITEM_MODEL, model);
            }
            if (!snapshot.customModelFloats().isEmpty()
                || !snapshot.customModelFlags().isEmpty()
                || !snapshot.customModelStrings().isEmpty()
                || !snapshot.customModelColors().isEmpty()) {
                stack.set(DataComponentTypes.CUSTOM_MODEL_DATA, new CustomModelDataComponent(
                    snapshot.customModelFloats(),
                    snapshot.customModelFlags(),
                    snapshot.customModelStrings(),
                    snapshot.customModelColors()
                ));
            }
            if (snapshot.dyedColor() != null) {
                stack.set(DataComponentTypes.DYED_COLOR, new DyedColorComponent(snapshot.dyedColor()));
            }
            if (snapshot.enchantmentGlint() != null) {
                stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, snapshot.enchantmentGlint());
            }
            return Optional.of(stack);
        } catch (RuntimeException error) {
            return Optional.empty();
        }
    }

    public static IconSnapshot preferSnapshot(
        IconSnapshot previous,
        IconSnapshot candidate,
        boolean allowDowngrade
    ) {
        if (candidate == null) return previous;
        if (!allowDowngrade && previous != null && previous.hasVisualComponents() && !previous.equals(candidate)) {
            return previous;
        }
        return candidate;
    }
}
