package com.atakmap.map.layer.feature.style;

import com.atakmap.interop.Pointer;

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
     */
    public PatternStrokeStyle(long pattern, int patternLen, int color, float strokeWidth) {
        this(PatternStrokeStyle_create(pattern, patternLen, color, strokeWidth), null);
    }

    PatternStrokeStyle(Pointer pointer, Object owner) {
        super(pointer, owner);
    }

    public long getPattern() {
        this.rwlock.acquireRead();
        try {
            return PatternStrokeStyle_getPattern(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    public int getPatternLength() {
        this.rwlock.acquireRead();
        try {
            return PatternStrokeStyle_getPatternLength(this.pointer.raw);
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
}
