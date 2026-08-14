package com.murphypotato.simmctoolset.internal.map.network;

import java.util.Objects;

public record HttpResult(HttpStatus status, byte[] body, HttpValidators validators, String contentType) {
    public HttpResult {
        Objects.requireNonNull(status, "status");
        body = body == null ? new byte[0] : body.clone();
        validators = validators == null ? HttpValidators.EMPTY : validators;
    }

    @Override
    public byte[] body() {
        return body.clone();
    }
}
