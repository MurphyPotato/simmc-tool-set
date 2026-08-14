package com.murphypotato.simmctoolset.internal.accessory.storage;

import com.murphypotato.simmctoolset.internal.accessory.domain.AccessoryRecord;
import com.murphypotato.simmctoolset.internal.accessory.domain.AccessoryMetadata;

import java.time.Instant;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record AccessoryStore(
    String schema,
    int version,
    String savedAt,
    Settings settings,
    List<AccessoryRecord> accessories,
    Extensions extensions
) {
    public static final String SCHEMA = "travel-hunter-accessory-tool:v5-fabric-mc1.21.8/accessories";
    public static final int VERSION = 1;

    public AccessoryStore {
        accessories = accessories == null ? List.of() : List.copyOf(accessories);
        settings = settings == null ? new Settings(false) : settings;
        extensions = extensions == null ? Extensions.empty() : extensions;
    }

    public static AccessoryStore empty() {
        return create(new Settings(false), List.of(), Map.of());
    }

    public static AccessoryStore create(Settings settings, List<AccessoryRecord> accessories) {
        return create(settings, accessories, Map.of());
    }

    public static AccessoryStore create(
        Settings settings,
        List<AccessoryRecord> accessories,
        Map<String, AccessoryMetadata> metadata
    ) {
        return new AccessoryStore(SCHEMA, VERSION, Instant.now().toString(), settings, accessories, new Extensions(1, metadata));
    }

    public record Settings(boolean alwaysReview, boolean saveContainerLocations, String locationSalt) {
        public Settings {
            locationSalt = Objects.requireNonNullElse(locationSalt, "").strip();
            if (locationSalt.isEmpty()) locationSalt = UUID.randomUUID().toString().replace("-", "");
        }

        public Settings(boolean alwaysReview) {
            this(alwaysReview, true, "");
        }
    }

    public record Extensions(int version, Map<String, AccessoryMetadata> metadata) {
        public Extensions {
            version = version <= 0 ? 1 : version;
            metadata = metadata == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(metadata));
        }

        public static Extensions empty() {
            return new Extensions(1, Map.of());
        }
    }
}
