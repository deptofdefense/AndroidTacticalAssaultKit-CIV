package com.atakmap.map.layer.feature.style;

import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.interop.Pointer;
import com.atakmap.map.opengl.GLRenderGlobals;

public class PatternStrokeStyle extends Style {
    /**
     * Creates a new instance.
     *
     * @param pattern       A bitwise representation of the pixel pattern, with
     *                      the least-significant bit representing the first
     *                      pixel, the second least-significant bit
     *                      representing the second pixel and so on
     * @param patternLen    The number of bits the represent the pattern
     * @param color         The stroke color
     * @param strokeWidth   The stroke width
     *
     * @deprecated use {@link #PatternStrokeStyle(int, short, float, float, float, float, float)}
     */
    @Deprecated
    @DeprecatedApi(since = "4.2", forRemoval = true, removeAt = "4.5")
    public PatternStrokeStyle(long pattern, int patternLen, int color, float strokeWidth) {
        this(PatternStrokeStyle_create((int)Math.ceil(GLRenderGlobals.getRelativeScaling()), computePattern(pattern, patternLen), color, strokeWidth*GLRenderGlobals.getRelativeScaling()), null);
    }

    /**
     * Creates a new instance.
     *
     * @param factor        The number of pixels to be drawn for each bit in
     *                      the pattern
     * @param pattern       A bitwise representation of the pixel pattern, with
     *                      the least-significant bit representing the first
     *                      pixel, the second least-significant bit
     *                      representing the second pixel and so on
     * @param r             The red component of the stroke color
     * @param g             The green component of the stroke color
     * @param b             The blue component of the stroke color
     * @param a             The alpha component of the stroke color
     * @param strokeWidth   The stroke width
     */
    public PatternStrokeStyle(int factor, short pattern, float r, float g, float b, float a, float strokeWidth) {
        this(PatternStrokeStyle_create(
                factor,
                pattern,
                ((int)(a*255f))<<24 |
                      ((int)(r*255f))<<16 |
                      ((int)(g*255f))<<8 |
                      ((int)(b*255f)),
                strokeWidth),
     null);
    }

    PatternStrokeStyle(Pointer pointer, Object owner) {
        super(pointer, owner);
    }

    /**
     * Returns the pattern. The low-order 16-bits compose the pattern; other
     * bits are ignored.
     *
     * @return
     */
    public long getPattern() {
        this.rwlock.acquireRead();
        try {
            return PatternStrokeStyle_getPattern(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    /**
     * @deprecated removed without replacemnet
     */
    @Deprecated
    @DeprecatedApi(since = "4.2", forRemoval = true, removeAt = "4.5")
    public int getPatternLength() {
        return 16;
    }

    public int getFactor() {
        this.rwlock.acquireRead();
        try {
            return PatternStrokeStyle_getFactor(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    /**
     * Returns the stroke color.
     *
     * @return  The stroke color.
     */
    public int getColor() {
        this.rwlock.acquireRead();
        try {
            return PatternStrokeStyle_getColor(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    /**
     * Returns the stroke width, in pixels.
     *
     * @return  The stroke width, in pixels.
     */
    public float getStrokeWidth() {
        this.rwlock.acquireRead();
        try {
            return PatternStrokeStyle_getStrokeWidth(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    private static short computePattern(long pattern, int len) {
        short retval = 0;
        if(len > 16) {
            final double ratio = (double)len / 16d;
            for(int i = 0; i < 16; i++) {
                // subsample
                final short b = (short)(pattern>>>(int)(i*ratio));
                retval |= (b << i);
            }
        } else if(len < 16) {
            for(int i = 0; i < 16; i++) {
                // repeat
                final short b = (short)(pattern>>>(i%len));
                retval |= (b << i);
            }
        } else {
            retval = (short)pattern;
        }
        return retval;
    }
}
