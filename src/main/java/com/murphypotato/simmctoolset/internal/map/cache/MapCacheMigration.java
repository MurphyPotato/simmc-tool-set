package com.murphypotato.simmctoolset.internal.map.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.StandardCopyOption;

/** Copies the standalone cache into the new namespace without mutating the source cache. */
public final class MapCacheMigration {
    private static final Logger LOGGER = LoggerFactory.getLogger(MapCacheMigration.class);

    private MapCacheMigration() {
    }

    public static void importIfAbsent(Path target, Path legacy) {
        if (Files.exists(target) || Files.notExists(legacy)
                || !Files.isDirectory(legacy, LinkOption.NOFOLLOW_LINKS)) return;
        try {
            Files.createDirectories(target);
            Files.walkFileTree(legacy, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
                    Path relative = legacy.relativize(directory);
                    Files.createDirectories(target.resolve(relative));
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                    if (attributes.isRegularFile()) {
                        Path destination = target.resolve(legacy.relativize(file));
                        Files.createDirectories(destination.getParent());
                        Files.copy(file, destination, StandardCopyOption.REPLACE_EXISTING,
                                StandardCopyOption.COPY_ATTRIBUTES);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Failed to import legacy SIMMC map cache from {}", legacy, exception);
        }
    }
}
