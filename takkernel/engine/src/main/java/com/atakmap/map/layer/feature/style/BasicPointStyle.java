package com.atakmap.map.layer.feature.style;

import com.atakmap.interop.Pointer;

/**
 * Basic style for point geometries. Renders the point as a solid dot of the
 * specified size using the specified color.
 * 
 * @author Developer
 *
 */
public final class BasicPointStyle extends Style {

    /**
     * Creates a new instance of the specified color and size.
     * 
     * @param color The point color
     * @param size  The point size, in pixels
     */
    public BasicPointStyle(int color, float size) {
        this(BasicPointStyle_create(color, size), null);
    }

    BasicPointStyle(Pointer pointer, Object owner) {
        super(pointer, owner);
    }

    /**
     * Returns the fill color, in ARGB order.
     * 
     * @return  The fill color
     */
    public int getColor() {
        this.rwlock.acquireRead();
        try {
            return BasicPointStyle_getColor(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    /**
     * Returns the point size, in pixels.
     * 
     * @return  The point size, in pixels.
     */
    public float getSize() {
        this.rwlock.acquireRead();
        try {
            return BasicPointStyle_getSize(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }
    
    // XXX - label as part of point style or use feature name???
}
