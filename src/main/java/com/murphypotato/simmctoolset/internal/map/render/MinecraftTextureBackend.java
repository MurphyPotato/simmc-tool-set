package com.murphypotato.simmctoolset.internal.map.render;

import net.minecraft.client.texture.TextureManager;

import java.util.Objects;

/** Production squaremap tile backend backed by Minecraft {@code NativeImage} textures. */
public final class MinecraftTextureBackend implements TextureBackend {
    private final NativeTextureUploader uploader;

    public MinecraftTextureBackend(TextureManager textureManager) {
        this(new MinecraftNativeTextureUploader(textureManager));
    }

    MinecraftTextureBackend(NativeTextureUploader uploader) {
        this.uploader = Objects.requireNonNull(uploader, "uploader");
    }

    @Override public Object upload(TileKey key, DecodedTile image) throws Exception {
        Objects.requireNonNull(key, "key");
        if (!(image instanceof PixelDecodedTile pixels)) {
            throw new IllegalArgumentException("production tile upload requires decoded pixels");
        }
        int width = pixels.width();
        int height = pixels.height();
        int[] argb = new int[Math.multiplyExact(width, height)];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) argb[y * width + x] = pixels.argb(x, y);
        }
        return uploader.uploadArgb("tile/" + key.canonical(), width, height, argb);
    }

    @Override public void destroy(Object handle) {
        uploader.destroy(handle);
    }
}
