package com.atakmap.map.layer.feature.style;


import com.atakmap.interop.Pointer;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

/**
 * A {@link Style} that is the composition of one or more {@link Style}
 * instances. Composition allows for complex styling to achieved by sequentially
 * rendering the geometry using separate styles. Styles are always applied in
 * FIFO order.
 * 
 * <P>For example, the following:
 * <PRE><code>
 *     BasicStrokeStyle bg = new BasicStrokeStyle(0xFFFFFFFF, 3);
 *     BasicStrokeStyle fg = new BasicStrokeStyle(0xFF000000, 1); 
 *     CompositeStyle outlined = new CompositeStyle(new Style[] {bg, fg});
 * </code></PRE>
 * 
 * <P>Would create an outline stroking effect. The geometry would first get
 * stroked using a white line of 3 pixels, and then an additional black stroke
 * of 1 pixels would be applied.
 * 
 * <P>It should be assumed that all child styles are applicable for the geometry
 * that is associated.
 * 
 * @author Developer
 */
public final class CompositeStyle extends Style {

    private Map<Pointer, WeakReference<Style>> cache;
    
    public CompositeStyle(Style[] styles) {
        this(CompositeStyle_create(getStylePointers(styles)), null);
    }
    
    CompositeStyle(Pointer pointer, Object owner) {
        super(pointer, owner);

        this.cache = new HashMap<Pointer, WeakReference<Style>>();
    }
    /**
     * Returns the number of styles.
     * 
     * @return  The number of styles.
     */
    public int getNumStyles() {
        this.rwlock.acquireRead();
        try {
            return CompositeStyle_getNumStyles(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }
    
    /**
     * Returns the style at the specified index.
     * 
     * @param index The index
     * 
     * @return  The style at the specified index
     * 
     * @throws IndexOutOfBoundsException    If <code>style</code> is less than
     *                                      <code>0</code> or
     *                                      greater-than-or-equal-to
     *                                      {@link #getNumStyles()}.
     */
    public Style getStyle(int index) {
        this.rwlock.acquireRead();
        try {
            final Pointer pointer = CompositeStyle_getStyle(this.pointer.raw, index);
            synchronized(this.cache) {
                WeakReference<Style> ref = this.cache.get(pointer);
                if(ref != null) {
                    final Style retval = ref.get();
                    if (retval != null)
                        return retval;
                }
                final Style retval = create(pointer, this);
                if(retval != null) {
                    this.cache.put(pointer, new WeakReference<Style>(retval));
                }
                return retval;
            }
        } finally {
            this.rwlock.releaseRead();
        }

    }
    
    public static Style find(CompositeStyle composite, Class<? extends Style> clazz) {
        for(int i = 0; i < composite.getNumStyles(); i++) {
            Style s = composite.getStyle(i);
            if (s != null) { 
                if(clazz.isAssignableFrom(s.getClass())) {
                    return s;
                } else if(s instanceof CompositeStyle) {
                    Style result = find((CompositeStyle)s, clazz);
                    if(result != null)
                        return result;
                }
            }
        }
        return null;
    }

    private static long[] getStylePointers(Style[] styles) {
        long[] retval = new long[styles.length];
        for(int i = 0; i < styles.length; i++)
            retval[i] = (styles[i] != null) ? styles[i].pointer.raw : 0L;
        return retval;
    }
} // CompositeStyle
