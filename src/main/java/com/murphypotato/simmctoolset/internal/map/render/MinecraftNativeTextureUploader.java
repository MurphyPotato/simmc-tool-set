package com.murphypotato.simmctoolset.internal.map.render;

import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/** Owns the Minecraft texture registration and destruction lifecycle. */
final class MinecraftNativeTextureUploader implements NativeTextureUploader {
    private static final AtomicLong SEQUENCE = new AtomicLong();
    private final TextureManager textures;

    MinecraftNativeTextureUploader(TextureManager textures) {
        this.textures = Objects.requireNonNull(textures, "textures");
    }

    @Override public Object uploadArgb(String name, int width, int height, int[] argb) {
        if (width < 1 || height < 1 || argb.length != Math.multiplyExact(width, height)) {
            throw new IllegalArgumentException("invalid ARGB texture dimensions");
        }
        NativeImage image = new NativeImage(width, height, false);
        try {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) image.setColorArgb(x, y, argb[y * width + x]);
            }
            return register(name, image);
        } catch (RuntimeException failure) {
            image.close();
            throw failure;
        }
    }

    @Override public Object uploadPng(String name, byte[] png) throws IOException {
        NativeImage image = NativeImage.read(png);
        try {
            return register(name, image);
        } catch (RuntimeException failure) {
            image.close();
            throw failure;
        }
    }

    private Identifier register(String name, NativeImage image) {
        Identifier id = Identifier.of("simmcmap", "dynamic/" + SEQUENCE.incrementAndGet());
        NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> "SIMMC " + name, image);
        try {
            textures.registerTexture(id, texture);
            return id;
        } catch (RuntimeException failure) {
            texture.close();
            throw failure;
        }
    }

    @Override public void destroy(Object handle) {
        if (!(handle instanceof Identifier id)) {
            throw new IllegalArgumentException("unknown Minecraft texture handle");
        }
        textures.destroyTexture(id);
    }
}
