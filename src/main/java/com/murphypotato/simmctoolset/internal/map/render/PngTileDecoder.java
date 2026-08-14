package com.murphypotato.simmctoolset.internal.map.render;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

public final class PngTileDecoder implements TileDecoder {
    private final int maxBytes;
    private final long maxPixels;
    private final ProbeFactory probeFactory;

    public PngTileDecoder(int maxBytes, long maxPixels) {
        this(maxBytes, maxPixels, PngTileDecoder::openImageIoProbe);
    }

    PngTileDecoder(int maxBytes, long maxPixels, ProbeFactory probeFactory) {
        if (maxBytes < 8 || maxPixels < 1) throw new IllegalArgumentException("invalid decoder limits");
        this.maxBytes = maxBytes;
        this.maxPixels = maxPixels;
        this.probeFactory = java.util.Objects.requireNonNull(probeFactory, "probeFactory");
    }

    @Override public DecodedTile decode(byte[] png, int expectedTileSize) throws TileDecodeException {
        if (png == null || png.length < 8 || png.length > maxBytes) throw new TileDecodeException("PNG byte limit exceeded");
        if (expectedTileSize < 1 || (long) expectedTileSize * expectedTileSize > maxPixels) {
            throw new TileDecodeException("tile pixel limit exceeded");
        }
        if (!hasPngSignature(png)) throw new TileDecodeException("tile is not PNG");
        try (ImageProbe probe = probeFactory.open(png)) {
            int width = probe.width();
            int height = probe.height();
            if (width <= 0 || height <= 0 || width != expectedTileSize || height != expectedTileSize) {
                throw new TileDecodeException("PNG dimensions do not match squaremap tile_size");
            }
            if ((long) width * height > maxPixels) throw new TileDecodeException("tile pixel limit exceeded");
            BufferedImage image = probe.read();
            if (image == null) throw new TileDecodeException("PNG decode failed");
            if (image.getWidth() != width || image.getHeight() != height) {
                image.flush();
                throw new TileDecodeException("decoded PNG dimensions changed");
            }
            return new BufferedDecodedTile(image);
        } catch (TileDecodeException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new TileDecodeException("PNG decode failed", exception);
        } catch (RuntimeException exception) {
            throw new TileDecodeException("PNG decode failed", exception);
        }
    }

    private static ImageProbe openImageIoProbe(byte[] png) throws IOException {
        ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(png));
        if (input == null) throw new IOException("PNG input unavailable");
        java.util.Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
        if (!readers.hasNext()) {
            input.close();
            throw new IOException("no PNG reader");
        }
        ImageReader reader = readers.next();
        try {
            reader.setInput(input, true, true);
            return new ImageIoProbe(input, reader);
        } catch (RuntimeException failure) {
            reader.dispose();
            input.close();
            throw failure;
        }
    }

    @FunctionalInterface
    interface ProbeFactory {
        ImageProbe open(byte[] png) throws IOException;
    }

    interface ImageProbe extends AutoCloseable {
        int width() throws IOException;
        int height() throws IOException;
        BufferedImage read() throws IOException;
        @Override void close() throws IOException;
    }

    private record ImageIoProbe(ImageInputStream input, ImageReader reader) implements ImageProbe {
        @Override public int width() throws IOException { return reader.getWidth(0); }
        @Override public int height() throws IOException { return reader.getHeight(0); }
        @Override public BufferedImage read() throws IOException { return reader.read(0); }
        @Override public void close() throws IOException {
            reader.dispose();
            input.close();
        }
    }

    private static boolean hasPngSignature(byte[] bytes) {
        byte[] signature = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
        for (int i = 0; i < signature.length; i++) if (bytes[i] != signature[i]) return false;
        return true;
    }

    private static final class BufferedDecodedTile implements PixelDecodedTile {
        private BufferedImage image;
        private BufferedDecodedTile(BufferedImage image) { this.image = image; }
        @Override public int width() { return requireOpen().getWidth(); }
        @Override public int height() { return requireOpen().getHeight(); }
        @Override public int argb(int x, int y) { return requireOpen().getRGB(x, y); }
        @Override public void close() {
            if (image != null) image.flush();
            image = null;
        }
        private BufferedImage requireOpen() {
            if (image == null) throw new IllegalStateException("decoded tile is closed");
            return image;
        }
    }
}
