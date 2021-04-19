package com.atakmap.map.layer.feature.style;

import com.atakmap.interop.Pointer;

/**
 * Icon style for point geometries. Renders the associated point geometry
 * using the specified icon.
 *  
 * @author Developer
 */
public final class IconPointStyle extends Style {

    /**
     * Creates a new icon style, centered on the point at original size with
     * a relative rotation of <code>0f</code> (right side up).
     * 
     * @param color     The icon color
     * @param iconUri   The icon URI
     */
    public IconPointStyle(int color, String iconUri) {
        this(color, iconUri, 0f, 0f, 0, 0, 0f, false);
    }
    
    /**
     * Creates a new icon style with the specified properties.
     * 
     * @param color             The icon color
     * @param iconUri           The icon URI
     * @param iconWidth         The icon render width. Use <code>0f</code> to
     *                          render at original width.
     * @param iconHeight        The icon render height. Use <code>0f</code> to
     *                          render at original height.
     * @param alignX            The horizontal alignment of the icon. Less than
     *                          <code>0</code> for to the left, <code>0</code>
     *                          for horizontal centered, greater than 
     *                          <code>0</code> for to the right
     * @param alignY            The vertical alignment of the icon. Less than
     *                          <code>0</code> for above, <code>0</code> for
     *                          vertically centered, greater than <code>0</code>
     *                          for below
     * @param rotation          The rotation of the icon, in radians
     * @param rotationAbsolute  <code>true</code> if the rotation is absolute
     *                          (relative to North), <code>false</code> if the
     *                          rotation is relative.
     */
    public IconPointStyle(int color, String iconUri, float iconWidth, float iconHeight, int alignX, int alignY, float rotation, boolean rotationAbsolute) {
        this(IconPointStyle_create(color,
                iconUri,
                iconWidth,
                iconHeight,
                (alignX == 0) ? getIconPointStyle_HorizontalAlignment_H_CENTER() : (alignX > 0 ? getIconPointStyle_HorizontalAlignment_RIGHT() : getIconPointStyle_HorizontalAlignment_LEFT()),
                (alignY == 0) ? getIconPointStyle_VerticalAlignment_V_CENTER() : (alignY > 0 ? getIconPointStyle_VerticalAlignment_BELOW() : getIconPointStyle_VerticalAlignment_ABOVE()),
                rotation,
        rotationAbsolute), null);
    }

    /**
     * Creates a new icon style with the specified properties.
     *
     * @param color             The icon color
     * @param iconUri           The icon URI
     * @param scale             scaling to apply to the icon when rendered
     * @param alignX            The horizontal alignment of the icon. Less than
     *                          <code>0</code> for to the left, <code>0</code>
     *                          for horizontal centered, greater than
     *                          <code>0</code> for to the right
     * @param alignY            The vertical alignment of the icon. Less than
     *                          <code>0</code> for above, <code>0</code> for
     *                          vertically centered, greater than <code>0</code>
     *                          for below
     * @param rotation          The rotation of the icon, in radians
     * @param rotationAbsolute  <code>true</code> if the rotation is absolute
     *                          (relative to North), <code>false</code> if the
     *                          rotation is relative.
     */
    public IconPointStyle(int color, String iconUri, float scale, int alignX, int alignY, float rotation, boolean rotationAbsolute) {
        this(IconPointStyle_create(color,
                iconUri,
                scale,
                (alignX == 0) ? getIconPointStyle_HorizontalAlignment_H_CENTER() : (alignX > 0 ? getIconPointStyle_HorizontalAlignment_RIGHT() : getIconPointStyle_HorizontalAlignment_LEFT()),
                (alignY == 0) ? getIconPointStyle_VerticalAlignment_V_CENTER() : (alignY > 0 ? getIconPointStyle_VerticalAlignment_BELOW() : getIconPointStyle_VerticalAlignment_ABOVE()),
                rotation,
                rotationAbsolute),
  null);
    }

    IconPointStyle(Pointer pointer, Object owner) {
        super(pointer, owner);
    }

    /**
     * Returns the color to be modulated with the icon. Each pixel value in the
     * icon is multiplied by the returned color; a value of
     * <code>0xFFFFFFFF</code> should be used to render the icon using its
     * original color.
     * 
     * @return  The icon color.
     */
    public int getColor() {
        this.rwlock.acquireRead();
        try {
            return IconPointStyle_getColor(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }
    
    /**
     * Returns the scaling to be applied to the icon when rendering.
     * If this returns 0, no scaling is to be applied
     *
     * @return  Scale factor to be applied when rendering the icon
     */
    public float getIconScaling() {
        this.rwlock.acquireRead();
        try {
            return IconPointStyle_getScaling(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    /**
     * Returns the rotation to be applied to the icon. The value of
     * {@link #isRotationAbsolute()} should be evaluated to correctly interpret
     * the rotation value.
     * 
     * @return  The rotation of the icon, in radians.
     * 
     * @see #isRotationAbsolute()
     */
    public float getIconRotation() {
        this.rwlock.acquireRead();
        try {
            return IconPointStyle_getRotation(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }
    
    /**
     * Returns <code>true</code> if the rotation is absolute (relative to North)
     * or <code>false</code> if the rotation is relative.
     * 
     * @return  <code>true</code> if the rotation is absolute
     *          (relative to North) or <code>false</code> if the rotation is
     *          relative.
     */
    public boolean isRotationAbsolute() {
        this.rwlock.acquireRead();
        try {
            return IconPointStyle_isRotationAbsolute(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    /**
     * Returns the URI for the icon.
     * 
     * @return
     */
    public String getIconUri() {
        this.rwlock.acquireRead();
        try {
            return IconPointStyle_getUri(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }
    
    /**
     * Returns the horizontal alignment for the icon. A value of less-than
     * <code>0</code> indicates to the left of the point, a value of
     * <code>0</code> is horizontally centered on the point and a value greater
     * than <code>0</code> indicates to the right of the point.
     * 
     * @return  The horizontal alignment for the icon with respect to the point.
     */
    public int getIconAlignmentX() {
        final int halign;
        this.rwlock.acquireRead();
        try {
            halign = IconPointStyle_getHorizontalAlignment(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }

        if(halign == getIconPointStyle_HorizontalAlignment_H_CENTER())
            return 0;
        else if(halign == getIconPointStyle_HorizontalAlignment_LEFT())
            return -1;
        else if(halign == getIconPointStyle_HorizontalAlignment_RIGHT())
            return 1;
        else
            throw new IllegalStateException();
    }

    /**
     * Returns the vertical alignment for the icon. A value of less-than
     * <code>0</code> indicates above the point, a value of <code>0</code> is
     * centered vertically on the point and a value greater than <code>0</code>
     * indicates below the point.
     *  
     * @return  The vertical alignment for the icon with respect to the point.
     */
    public int getIconAligmnentY() {
        final int valign;
        this.rwlock.acquireRead();
        try {
            valign = IconPointStyle_getVerticalAlignment(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }

        if(valign == getIconPointStyle_VerticalAlignment_V_CENTER())
            return 0;
        else if(valign == getIconPointStyle_VerticalAlignment_ABOVE())
            return -1;
        else if(valign == getIconPointStyle_VerticalAlignment_BELOW())
            return 1;
        else
            throw new IllegalStateException();
    }

    /**
     * Returns the rendered width for the icon. If non-zero, the icon will
     * be horizontally scaled to the specified width.
     * 
     * @return  The rendered width for the icon. If <code>0f</code> the icon
     *          will be rendered at its original width.
     */
    public float getIconWidth() {
        this.rwlock.acquireRead();
        try {
            return IconPointStyle_getWidth(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    /**
     * Returns the rendered height for the icon. If non-zero, the icon will be
     * vertically scaled to the specified height.
     * 
     * @return  The rendered height for the icon. If <code>0f</code> the icon
     *          will be rendered at its original height.
     */
    public float getIconHeight() {
        this.rwlock.acquireRead();
        try {
            return IconPointStyle_getHeight(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }
} // IconPointStyle
