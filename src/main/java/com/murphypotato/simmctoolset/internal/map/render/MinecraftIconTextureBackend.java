package com.murphypotato.simmctoolset.internal.map.render;

import com.murphypotato.simmctoolset.internal.map.network.IconBytes;
import net.minecraft.client.texture.TextureManager;

import java.util.Objects;

/** Production registered-icon backend backed by Minecraft {@code NativeImage} textures. */
public final class MinecraftIconTextureBackend implements IconTextureBackend {
    private final NativeTextureUploader uploader;

    public MinecraftIconTextureBackend(TextureManager textureManager) {
        this(new MinecraftNativeTextureUploader(textureManager));
    }

    MinecraftIconTextureBackend(NativeTextureUploader uploader) {
        this.uploader = Objects.requireNonNull(uploader, "uploader");
    }

    @Override public Object upload(String registeredKey, IconBytes icon) throws Exception {
        Objects.requireNonNull(registeredKey, "registeredKey");
        Objects.requireNonNull(icon, "icon");
        return uploader.uploadPng("icon/" + registeredKey, icon.png());
    }

    @Override public void destroy(Object handle) {
        uploader.destroy(handle);
    }
}
