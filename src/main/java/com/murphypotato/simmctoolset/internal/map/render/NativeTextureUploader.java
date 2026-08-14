package com.murphypotato.simmctoolset.internal.map.render;

/** Native texture allocation seam kept narrow so pixel transfer remains unit-testable. */
interface NativeTextureUploader {
    Object uploadArgb(String name, int width, int height, int[] argb) throws Exception;
    Object uploadPng(String name, byte[] png) throws Exception;
    void destroy(Object handle);
}
