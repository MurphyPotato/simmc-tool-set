package com.murphypotato.simmctoolset.internal.map.render;

/** Immutable already-clipped GUI rectangle with one ARGB color. */
public record ColoredQuad(int left, int top, int right, int bottom, int argb) {
    public ColoredQuad {
        if (right <= left || bottom <= top) {
            throw new IllegalArgumentException("colored quad must have positive area");
        }
    }
}
