package com.atakmap.map.layer.feature.style;

import com.atakmap.interop.Pointer;

/**
 * Basic fill style for polygon geometry. Performs a solid fill using the
 * specified color.
 *  
 * @author Developer
 */
public final class BasicFillStyle extends Style {

    /**
     * Creates a new instance with the specified fill color.
     * 
     * @param color The color
     */
    public BasicFillStyle(int color) {
        this(BasicFillStyle_create(color), null);
    }

    BasicFillStyle(Pointer pointer, Object owner) {
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
            return BasicFillStyle_getColor(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }
} // BasicFillStyle
