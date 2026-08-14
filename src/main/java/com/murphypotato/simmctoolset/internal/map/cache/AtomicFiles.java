package com.murphypotato.simmctoolset.internal.map.cache;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.UUID;

final class AtomicFiles {
    private AtomicFiles() {}

    static void write(Path target, byte[] content) throws IOException {
        Path absolute = target.toAbsolutePath().normalize();
        Path parent = absolute.getParent();
        if (parent == null) throw new IOException("cache target needs a parent");
        Files.createDirectories(parent);
        Path temporary = parent.resolve("." + absolute.getFileName() + "." + UUID.randomUUID() + ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(content);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            try {
                Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    static boolean quarantine(Path path) {
        if (Files.notExists(path)) return true;
        Path parent = path.toAbsolutePath().normalize().getParent();
        if (parent == null) return false;
        String suffix = ".corrupt-" + Instant.now().toEpochMilli() + "-" + UUID.randomUUID();
        try {
            Files.move(path, parent.resolve(path.getFileName() + suffix));
            return true;
        } catch (IOException ignored) {
            return Files.notExists(path);
        }
    }
}
