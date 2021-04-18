package com.atakmap.map.layer.feature.style;

import com.atakmap.interop.Pointer;

public final class LabelPointStyle extends Style {
    public enum ScrollMode {
        /** use the system wide setting */
        DEFAULT,
        /** label always scrolls if longer that a width fixed by the system */
        ON,
        /** label never scrolls and is always fully displayed */
        OFF,
    };

    
    /**
     * Creates a new label style, centered on the point at the default system size with
     * a relative rotation of <code>0f</code> (right side up).
     *
     * @param text      The label text
     * @param textColor The text color
     * @param bgColor   The background color for the text (0 for no background)
     * @param mode      The auto-scrolling mode to use for the label
     */
    public LabelPointStyle(String text, int textColor, int bgColor, ScrollMode mode) {
        this(text, textColor, bgColor, mode, 16f, 0, 0, 0, false, 13d);
    }

    /**
     * Creates a new icon style with the specified properties.
     *
     * @param text              The label text
     * @param textColor         The text color
     * @param bgColor           The background color for the text (0 for no background)
     * @param mode              The auto-scrolling mode to use for the label
     * @param textSize          The text size (in points). A value of 0.0f indicates that
     *                          the default system setting should be used.   At this time
     *                          textSize is not honored and it will always be the default
     *                          value.
     * @param alignX            The horizontal alignment of the label. Less than
     *                          <code>0</code> for to the left, <code>0</code>
     *                          for horizontally centered, greater than
     *                          <code>0</code> for to the right
     * @param alignY            The vertical alignment of the label. Less than
     *                          <code>0</code> for above, <code>0</code> for
     *                          vertically centered, greater than <code>0</code>
     *                          for below
     * @param rotation          The rotation of the label, in degrees counter-clockwise
     * @param rotationAbsolute  <code>true</code> if the rotation is absolute
     *                          (relative to North), <code>false</code> if the
     *                          rotation is relative.
     */
    public LabelPointStyle(String text, int textColor, int bgColor, ScrollMode mode, float textSize, int alignX, int alignY, float rotation, boolean rotationAbsolute) {
        this(text, textColor, bgColor, mode, textSize, alignX, alignY, rotation, rotationAbsolute, 14d);
    }

    /**
     * Creates a new icon style with the specified properties.
     *
     * @param text                The label text
     * @param textColor           The text color
     * @param bgColor             The background color for the text (0 for no background)
     * @param mode                The auto-scrolling mode to use for the label
     * @param textSize            The text size (in points). A value of 0.0f indicates that
     *                            the default system setting should be used.   At this time
     *                            textSize is not honored and it will always be the default
     *                            value.
     * @param alignX              The horizontal alignment of the label. Less than
     *                            <code>0</code> for to the left, <code>0</code>
     *                            for horizontally centered, greater than
     *                            <code>0</code> for to the right
     * @param alignY              The vertical alignment of the label. Less than
     *                            <code>0</code> for above, <code>0</code> for
     *                            vertically centered, greater than <code>0</code>
     *                            for below
     * @param rotation            The rotation of the label, in degrees counter-clockwise
     * @param rotationAbsolute    <code>true</code> if the rotation is absolute
     *                            (relative to North), <code>false</code> if the
     *                            rotation is relative.
     * @param minRenderResolution The minimum render resolution for the label.
     */
    public LabelPointStyle(String text, int textColor, int bgColor, ScrollMode mode, float textSize, int alignX, int alignY, float rotation, boolean rotationAbsolute, double minRenderResolution) {
        this(text, textColor, bgColor, mode, textSize, alignX, alignY, rotation, rotationAbsolute, minRenderResolution, 100.0f);
    }

    /**
     * Creates a new icon style with the specified properties.
     *
     * @param text                The label text
     * @param textColor           The text color
     * @param bgColor             The background color for the text (0 for no background)
     * @param mode                The auto-scrolling mode to use for the label
     * @param textSize            The text size (in points). A value of 0.0f indicates that
     *                            the default system setting should be used.   At this time
     *                            textSize is not honored and it will always be the default
     *                            value.
     * @param alignX              The horizontal alignment of the label. Less than
     *                            <code>0</code> for to the left, <code>0</code>
     *                            for horizontally centered, greater than
     *                            <code>0</code> for to the right
     * @param alignY              The vertical alignment of the label. Less than
     *                            <code>0</code> for above, <code>0</code> for
     *                            vertically centered, greater than <code>0</code>
     *                            for below
     * @param rotation            The rotation of the label, in degrees counter-clockwise
     * @param rotationAbsolute    <code>true</code> if the rotation is absolute
     *                            (relative to North), <code>false</code> if the
     *                            rotation is relative.
     * @param minRenderResolution The minimum render resolution for the label.
     * @param scale The scale to be applied to the label. 1.0f is unscaled
     */
    public LabelPointStyle(String text, int textColor, int bgColor, ScrollMode mode, float textSize, int alignX, int alignY, float rotation, boolean rotationAbsolute, double minRenderResolution, float scale) {
        this(LabelPointStyle_create(text,
                                    textColor,
                                    bgColor, getScrollMode(mode),
                                    textSize,
                                    (alignX == 0) ?
                                            getLabelPointStyle_HorizontalAlignment_H_CENTER() :
                                                ((alignX < 0) ?
                                                        getLabelPointStyle_HorizontalAlignment_LEFT() :
                                                        getLabelPointStyle_HorizontalAlignment_RIGHT()),
                                    (alignY == 0) ?
                                            getLabelPointStyle_VerticalAlignment_V_CENTER() :
                                            ((alignY < 0) ?
                                                    getLabelPointStyle_VerticalAlignment_ABOVE() :
                                                    getLabelPointStyle_VerticalAlignment_BELOW()),
                                    (float)Math.toRadians(rotation),
                                    rotationAbsolute,
                                    minRenderResolution,
                                    scale),
             null);
    }

    LabelPointStyle(Pointer pointer, Object owner) {
        super(pointer, owner);
    }

    /**
     * Returns the color to be modulated with the text. Each pixel value in the
     * icon is multiplied by the returned color; a value of
     * <code>0xFFFFFFFF</code> should be used to render the icon using its
     * original color.
     *
     * @return  The icon color.
     */
    public int getTextColor() {
        this.rwlock.acquireRead();
        try {
            return LabelPointStyle_getTextColor(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    /**
    * Returns the color to be for the background of the text. If
    * <code>0</code>, no background is drawn.
    *
    * @return  The background color.
    */
    public int getBackgroundColor() {
        this.rwlock.acquireRead();
        try {
            return LabelPointStyle_getBackgroundColor(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    /**
     * Returns the auto-scroll mode for the label.
     *
     * @return The auto-scroll mode for the label
     */
    public ScrollMode getScrollMode() {
        this.rwlock.acquireRead();
        try {
            return getScrollMode(LabelPointStyle_getScrollMode(this.pointer.raw));
        } finally {
            this.rwlock.releaseRead();
        }
    }

    /**
     * Returns the rotation to be applied to the label. The value of
     * {@link #isRotationAbsolute()} should be evaluated to correctly interpret
     * the rotation value.
     *
     * @return  The rotation of the icon, in radians counter-clockwise.
     *
     * @see #isRotationAbsolute()
     */
    public float getLabelRotation() {
        this.rwlock.acquireRead();
        try {
            return (float)Math.toDegrees(LabelPointStyle_getRotation(this.pointer.raw));
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
            return LabelPointStyle_isRotationAbsolute(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    /**
     * Returns the text string for the label.
     *
     * @return
     */
    public String getText() {
        this.rwlock.acquireRead();
        try {
            return LabelPointStyle_getText(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    /**
     * Returns the horizontal alignment for the label. A value of less-than
     * <code>0</code> indicates to the left of the point, a value of
     * <code>0</code> is horizontally centered on the point and a value greater
     * than <code>0</code> indicates to the right of the point.
     *
     * @return  The horizontal alignment for the label with respect to the point.
     */
    public int getLabelAlignmentX() {
        final int halign;
        this.rwlock.acquireRead();
        try {
            halign = LabelPointStyle_getHorizontalAlignment(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
        if(halign == getLabelPointStyle_HorizontalAlignment_LEFT())
            return -1;
        else if(halign == getLabelPointStyle_HorizontalAlignment_H_CENTER())
            return 0;
        else if(halign == getLabelPointStyle_HorizontalAlignment_RIGHT())
            return 1;
        else
            throw new IllegalStateException();
    }

    /**
     * Returns the vertical alignment for the label. A value of less-than
     * <code>0</code> indicates above the point, a value of <code>0</code> is
     * centered vertically on the point and a value greater than <code>0</code>
     * indicates below the point.
     *
     * @return  The vertical alignment for the label with respect to the point.
     */
    public int getLabelAlignmentY() {
        final int valign;
        this.rwlock.acquireRead();
        try {
            valign = LabelPointStyle_getVerticalAlignment(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
        if(valign == getLabelPointStyle_VerticalAlignment_ABOVE())
            return -1;
        else if(valign == getLabelPointStyle_VerticalAlignment_V_CENTER())
            return 0;
        else if(valign == getLabelPointStyle_VerticalAlignment_BELOW())
            return 1;
        else
            throw new IllegalStateException();
    }

    /**
     * Returns the text size of the label, in points. A value of 0.0f indicates
     * the default system setting.
     *
     * @return The text size of the label, in points.
     */
    public float getTextSize() {
        this.rwlock.acquireRead();
        try {
            return LabelPointStyle_getTextSize(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }


    /**
     * Returns the label render scale to be applied to the label.
     *
     * @return  The render scale to to be applied to the label.
     *
     */
    public double getLabelMinRenderResolution() {
        this.rwlock.acquireRead();
        try {
            return LabelPointStyle_getLabelMinRenderResolution(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    /**
     * Returns the scaling factor to be applied to the rendering of the label.
     *
     * @return  The render scaling factor for the label
     *
     */
    public float getLabelScale() {
        this.rwlock.acquireRead();
        try {
            return LabelPointStyle_getLabelScale(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }
}
