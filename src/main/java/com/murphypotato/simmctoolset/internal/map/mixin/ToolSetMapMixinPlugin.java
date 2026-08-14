package com.murphypotato.simmctoolset.internal.map.mixin;

import com.murphypotato.simmctoolset.map.MapCompatibility;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Applies the Xaero ABI mixins only when the map module is allowed to start. */
public final class ToolSetMapMixinPlugin implements IMixinConfigPlugin {
    private static final String MAP_MIXIN_PREFIX = "com.murphypotato.simmctoolset.internal.map.mixin.";

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!mixinClassName.startsWith(MAP_MIXIN_PREFIX)) {
            return true;
        }
        return MapCompatibility.shouldApplyInternalMapMixins();
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
