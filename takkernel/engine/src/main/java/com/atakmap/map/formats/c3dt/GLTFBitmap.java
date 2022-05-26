package com.atakmap.map.formats.c3dt;

import java.nio.ByteBuffer;

import gov.tak.api.annotation.DontObfuscate;

@DontObfuscate
final class GLTFBitmap {
    int width;
    int height;
    int bits;
    int component;
    int pixelType;
    ByteBuffer bytes;
}
