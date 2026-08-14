package com.murphypotato.simmctoolset.internal.accessory.storage;

public record StorageLoadResult(
    AccessoryStore store,
    boolean corrupted,
    String error
) {
}
