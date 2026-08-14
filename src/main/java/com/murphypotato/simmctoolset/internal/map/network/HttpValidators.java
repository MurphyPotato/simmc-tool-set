package com.murphypotato.simmctoolset.internal.map.network;

public record HttpValidators(String etag, String lastModified) {
    public static final HttpValidators EMPTY = new HttpValidators(null, null);
}
