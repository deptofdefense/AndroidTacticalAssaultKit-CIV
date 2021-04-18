package com.atakmap.map.layer.feature.style;

import com.atakmap.interop.InteropCleaner;
import com.atakmap.interop.NativePeerManager;
import com.atakmap.interop.Pointer;
import com.atakmap.lang.ref.Cleaner;
import com.atakmap.util.Disposable;
import com.atakmap.util.ReadWriteLock;

/**
 * Defines the style for a feature. Implementations will provide instruction on
 * how geometries are to be rendered.
 * 
 * @author Developer
 */
public abstract class Style implements Disposable {
    final static NativePeerManager.Cleaner CLEANER = new InteropCleaner(Style.class);

    static StyleClasses styleClasses = null;

    final ReadWriteLock rwlock = new ReadWriteLock();
    private final Cleaner cleaner;
    Pointer pointer;
    Object owner;

    Style(Pointer pointer, Object owner) {
        cleaner = NativePeerManager.register(this, pointer, rwlock, null, CLEANER);
        this.pointer = pointer;
        this.owner = owner;
    }

    @Override
    public void dispose() {
        if(cleaner != null)
            cleaner.clean();
    }

    @Override
    public Style clone() {
        this.rwlock.acquireRead();
        try {
            return create(clone(this.pointer.raw), null);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    static Style create(Pointer pointer, Object owner) {
        if(pointer == null || pointer.raw == 0L)
            return null;

        if(styleClasses == null)
            styleClasses = new StyleClasses();

        final int styleClass = getClass(pointer.raw);
        if(styleClass == styleClasses.BasicStrokeStyle) {
            return new BasicStrokeStyle(pointer, owner);
        } else if(styleClass == styleClasses.BasicFillStyle) {
            return new BasicFillStyle(pointer, owner);
        } else if(styleClass == styleClasses.BasicPointStyle) {
            return new BasicPointStyle(pointer, owner);
        } else if(styleClass == styleClasses.IconPointStyle) {
            return new IconPointStyle(pointer, owner);
        } else if(styleClass == styleClasses.LabelPointStyle) {
            return new LabelPointStyle(pointer, owner);
        } else if(styleClass == styleClasses.CompositeStyle) {
            return new CompositeStyle(pointer, owner);
        } else if(styleClass == styleClasses.PatternStrokeStyle) {
            return new PatternStrokeStyle(pointer, owner);
        } else {
            return null;
        }
    }
    static long getPointer(Style style) {
        return style.pointer.raw;
    }

    static class StyleClasses {
        public int BasicStrokeStyle = getTESC_BasicStrokeStyle();
        public int BasicFillStyle = getTESC_BasicFillStyle();
        public int BasicPointStyle = getTESC_BasicPointStyle();
        public int IconPointStyle = getTESC_IconPointStyle();
        public int LabelPointStyle = getTESC_LabelPointStyle();
        public int CompositeStyle = getTESC_CompositeStyle();
        public int PatternStrokeStyle = getTESC_PatternStrokeStyle();
    }



    static native int getTESC_BasicStrokeStyle();
    static native int getTESC_BasicFillStyle();
    static native int getTESC_BasicPointStyle();
    static native int getTESC_IconPointStyle();
    static native int getTESC_LabelPointStyle();
    static native int getTESC_CompositeStyle();
    static native int getTESC_PatternStrokeStyle();

    static native void destruct(Pointer pointer);
    static native Pointer clone(long ptr);
    static native int getClass(long ptr);

    static native Pointer BasicFillStyle_create(int color);
    static native int BasicFillStyle_getColor(long ptr);

    static native Pointer BasicStrokeStyle_create(int color, float strokeWidth);
    static native int BasicStrokeStyle_getColor(long ptr);
    static native float BasicStrokeStyle_getStrokeWidth(long ptr);

    static native Pointer BasicPointStyle_create(int color, float size);
    static native int BasicPointStyle_getColor(long ptr);
    static native float BasicPointStyle_getSize(long ptr);

    static native Pointer IconPointStyle_create(int color, String uri, float width, float height, int halign, int valign, float rotation, boolean isRotationAbsolute);
    static native Pointer IconPointStyle_create(int color, String uri, float scale, int halign, int valign, float rotation, boolean isRotationAbsolute);
    static native int IconPointStyle_getColor(long ptr);
    static native String IconPointStyle_getUri(long ptr);
    static native float IconPointStyle_getWidth(long ptr);
    static native float IconPointStyle_getHeight(long ptr);
    static native int IconPointStyle_getHorizontalAlignment(long ptr);
    static native int IconPointStyle_getVerticalAlignment(long ptr);
    static native float IconPointStyle_getScaling(long ptr);
    static native float IconPointStyle_getRotation(long ptr);
    static native boolean IconPointStyle_isRotationAbsolute(long ptr);
    static native int getIconPointStyle_HorizontalAlignment_LEFT();
    static native int getIconPointStyle_HorizontalAlignment_H_CENTER();
    static native int getIconPointStyle_HorizontalAlignment_RIGHT();
    static native int getIconPointStyle_VerticalAlignment_ABOVE();
    static native int getIconPointStyle_VerticalAlignment_V_CENTER();
    static native int getIconPointStyle_VerticalAlignment_BELOW();

    static native Pointer LabelPointStyle_create(String text, int textColor, int bgColor, int scrollMode, float textSize, int halign, int valign, float rotation, boolean isRotationAbsolute, double labelMinRenderResolution, float scale);
    static native String LabelPointStyle_getText(long ptr);
    static native int LabelPointStyle_getTextColor(long ptr);
    static native int LabelPointStyle_getBackgroundColor(long ptr);
    static native int LabelPointStyle_getScrollMode(long ptr);
    static native float LabelPointStyle_getTextSize(long ptr);
    static native int LabelPointStyle_getHorizontalAlignment(long ptr);
    static native int LabelPointStyle_getVerticalAlignment(long ptr);
    static native float LabelPointStyle_getRotation(long ptr);
    static native boolean LabelPointStyle_isRotationAbsolute(long ptr);
    static native double LabelPointStyle_getLabelMinRenderResolution(long ptr);
    static native float LabelPointStyle_getLabelScale(long ptr);

    static native int getLabelPointStyle_HorizontalAlignment_LEFT();
    static native int getLabelPointStyle_HorizontalAlignment_H_CENTER();
    static native int getLabelPointStyle_HorizontalAlignment_RIGHT();
    static native int getLabelPointStyle_VerticalAlignment_ABOVE();
    static native int getLabelPointStyle_VerticalAlignment_V_CENTER();
    static native int getLabelPointStyle_VerticalAlignment_BELOW();
    static native int getLabelPointStyle_ScrollMode_DEFAULT();
    static native int getLabelPointStyle_ScrollMode_ON();
    static native int getLabelPointStyle_ScrollMode_OFF();

    static LabelPointStyle.ScrollMode getScrollMode(int cmode) {
        if(cmode == getLabelPointStyle_ScrollMode_DEFAULT())
            return LabelPointStyle.ScrollMode.DEFAULT;
        else if(cmode == getLabelPointStyle_ScrollMode_OFF())
            return LabelPointStyle.ScrollMode.OFF;
        else if(cmode == getLabelPointStyle_ScrollMode_ON())
            return LabelPointStyle.ScrollMode.ON;
        else
            throw new IllegalArgumentException();
    }

    static int getScrollMode(LabelPointStyle.ScrollMode mmode) {
        switch(mmode) {
            case OFF:
                return getLabelPointStyle_ScrollMode_OFF();
            case ON :
                return getLabelPointStyle_ScrollMode_ON();
            case DEFAULT:
                return getLabelPointStyle_ScrollMode_DEFAULT();
            default :
                throw new IllegalArgumentException();
        }
    }

    static native Pointer CompositeStyle_create(long[] stylePtrs);
    static native int CompositeStyle_getNumStyles(long ptr);
    static native Pointer CompositeStyle_getStyle(long ptr, int idx);

    static native Pointer PatternStrokeStyle_create(int factor, short pattern, int color, float width);
    static native short PatternStrokeStyle_getPattern(long pointer);
    static native int PatternStrokeStyle_getFactor(long pointer);
    static native int PatternStrokeStyle_getColor(long pointer);
    static native float PatternStrokeStyle_getStrokeWidth(long pointer);
}
