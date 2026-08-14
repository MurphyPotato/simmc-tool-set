package com.murphypotato.simmctoolset.internal.map.render;

import java.util.Objects;

public record TileFetchResult(Status status, byte[] bytes, TileValidators validators) {
    public enum Status { FOUND, NOT_MODIFIED, NOT_FOUND, FAILED }

    public TileFetchResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(validators, "validators");
        bytes = bytes == null ? null : bytes.clone();
        if (status == Status.FOUND && bytes == null) throw new IllegalArgumentException("found tile needs bytes");
        if (status != Status.FOUND && bytes != null) throw new IllegalArgumentException("absent tile cannot have bytes");
    }

    public TileFetchResult(Status status, byte[] bytes) {
        this(status, bytes, TileValidators.EMPTY);
    }

    @Override public byte[] bytes() { return bytes == null ? null : bytes.clone(); }
    public static TileFetchResult found(byte[] bytes) { return found(bytes, TileValidators.EMPTY); }
    public static TileFetchResult found(byte[] bytes, TileValidators validators) {
        return new TileFetchResult(Status.FOUND, bytes, validators);
    }
    public static TileFetchResult notModified(TileValidators validators) {
        return new TileFetchResult(Status.NOT_MODIFIED, null, validators);
    }
    public static TileFetchResult notFound() { return notFound(TileValidators.EMPTY); }
    public static TileFetchResult notFound(TileValidators validators) {
        return new TileFetchResult(Status.NOT_FOUND, null, validators);
    }
    public static TileFetchResult failed() { return failed(TileValidators.EMPTY); }
    public static TileFetchResult failed(TileValidators validators) {
        return new TileFetchResult(Status.FAILED, null, validators);
    }
}
