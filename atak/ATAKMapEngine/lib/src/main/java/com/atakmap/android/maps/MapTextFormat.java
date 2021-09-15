
package com.atakmap.android.maps;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.map.opengl.GLRenderGlobals;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;

/**
 * Immutable text style and metrics details for map object.
 *
 */
public final class MapTextFormat {

    private final static int COMMON_CHAR_START = 32; // first character (ASCII Code)
    private final static int COMMON_CHAR_END = 126; // last character (ASCII Code)

    private final static Map<Long, Impl> SHARED_IMPL = new HashMap<>();

    private final Impl impl;

    private int textOptions = 0;

    static private HashMap<Typeface, String> typefaceFamilyNameMap = new HashMap<>();

    static
    {
        initTypefaceFamilyMap();
    }
    /**
     * @deprecated use {@link #MapTextFormat(Typeface, int)} instead
     */
    @Deprecated
    @DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.4")
    public MapTextFormat(Typeface typeface, float densityAdjustedTextSize, boolean legacy) {
        this(typeface, (int) (densityAdjustedTextSize / GLRenderGlobals.getRelativeScaling()));
    }

    public MapTextFormat(Typeface typeface, int textSize, int textOptions)
    {
        this(typeface, false, textSize);
        this.textOptions = textOptions;
    }

    public MapTextFormat(Typeface typeface, int textSize) {
        this(typeface, false, textSize);
    }

    public MapTextFormat(Typeface typeface, boolean outlined, int textSize) {
        Impl i = null;
        long key = -1;
        synchronized (MapTextFormat.class) {
            if (typeface != null) {
                key = ((long) typeface.hashCode() << 32L) | (long) ((textSize & 0x7FFFFFFF) << 1) | (outlined ? 0x1L : 0x0L);
                i = SHARED_IMPL.get(key);

            }
            if (i == null) {
                SHARED_IMPL.put(key, i = new Impl(typeface, textSize, outlined));
            }
        }

        this.impl = i;
    }
    
    public boolean isOutlined() {
        return this.impl.outlined;
    }

    public int getFontSize() {
        return this.impl.fontSize;
    }

    public float getDensityAdjustedFontSize() {
        return this.impl.densityAdjustedSize;
    }

    /**
     * Returns the typeface described by this MapTextFormat.
     * @return the typeface
     */
    public Typeface getTypeface() {
        return this.impl.face;
    }

    /**
     * Returns true if the MapTextFormat has strikethrough set.
     * @return true if strikethrough is set
     */
    public boolean isStrikethrough() {
        return (textOptions & Paint.STRIKE_THRU_TEXT_FLAG) > 0;
    }

    /**
     * Returns true if the MapTextFormat has underline set.
     * @return true if underline is set
     */
    public boolean isUnderlined() {
        return (textOptions & Paint.UNDERLINE_TEXT_FLAG) > 0;
    }

    /**************************************************************************/

    // NOTE: while some of the below are not thread-safe, the values being
    // assigned are effectively constants and have a low computation cost. The
    // main goal here is to avoid duplicating the objects that legacy associated
    // with a MapTextFormat instance.

    /**
     * Measure the width in pixels required to frame a block of text.
     * 
     * @param text the text string to be passed in
     * @return the measurment in pixels of the text string
     */
    public int measureTextWidth(final String text) {
        double maxWidth = 0.0;
        double currWidth = 0.0;
        final char[] carr = new char[1];

        int numLines = 1;
        final int len = text.length();

        for (int i = 0; i < len; ++i) {
            char c = text.charAt(i);
            if (c != '\n') {
                if (c >= COMMON_CHAR_START && c <= COMMON_CHAR_END) {
                    currWidth += this.impl.commonCharWidths[c - COMMON_CHAR_START];
                } else {
                    carr[0] = c;
                    currWidth += this.impl._textPaint.measureText(carr, 0, 1);
                }
            } else {
                if (currWidth > maxWidth)
                    maxWidth = currWidth;
                currWidth = 0.0f;
                numLines++;
            }
        }
        if (currWidth > maxWidth)
            maxWidth = currWidth;
        return (int) Math.ceil(maxWidth);
    }

    /**
     * Computes the width of the character on the screen.
     * @return the width as a floating point number for a specific character
     */
    public float getCharWidth(final int c) {
        if (c >= COMMON_CHAR_START && c <= COMMON_CHAR_END)
            return this.impl.commonCharWidths[c - COMMON_CHAR_START];
        else
            return this.impl._textPaint.measureText(new char[] {
                    (char) c
            }, 0, 1);
    }

    /**
     * @param text the string height to measure taking into account the number of newlines.
     * @return the maximum size of the text.
     */
    public int measureTextHeight(final String text) {
        final int textLenMinus1 = text.length() - 1;
        if (textLenMinus1 < 0)
            return 0;

        int numLines = 1;
        for (int i = 0; i < textLenMinus1; i++)
            if (text.charAt(i) == '\n')
                numLines++;

        /*
         * Text must not exceed bounds, so we use bottom and top for the first line. Every
         * additional line is spaced using ascent and descent so spacing looks natural
         */
        return (getTallestGlyphHeight() + getBaselineSpacing() * (numLines - 1));
    }

    /**
     * @return the height of the largest font glyph
     */
    public int getTallestGlyphHeight() {
        return this.impl.fontHeight;
    }

    public int getBaselineOffsetFromBottom() {
        return this.impl.baselineOffset;
    }

    /**
     * The positive Y offset to between baselines for spacing
     * 
     * @return the y offset between baselines.
     */
    public int getBaselineSpacing() {
        return this.impl.baselineSpacing;
    }

    /**
     * Draw the glyph to a Bitmap
     * 
     * @param canvas the canvas to draw the glyph to
     * @param s the string that contains the glyph
     * @param off the offset for the glypth
     */
    public void renderGlyph(final Canvas canvas,
            final String s,
            final int off,
            final float x, final float y) {
        
        canvas.drawText(s, off, off + 1, x,
                y - this.impl._fontMetricsInt.top, this.impl._textPaint);
        if(this.impl.outlined)
            canvas.drawText(s, off, off + 1, x,
                    y - this.impl._fontMetricsInt.top, this.impl._outlinePaint);
    }

    public String getTypefaceFamilyName()
    {
        return impl.familyName;
    }


    /**************************************************************************/
    
    public static synchronized void invalidate() {
        // reinitialize
        for(Impl impl : SHARED_IMPL.values())
            impl.init();
    }

    private static Map<String, Typeface> getSSystemFontMap() throws NoSuchFieldException, IllegalAccessException {
        Map<String, Typeface> sSystemFontMap = null;
        Typeface typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL);
        Field f = Typeface.class.getDeclaredField("sSystemFontMap");
        f.setAccessible(true);
        sSystemFontMap = (Map<String, Typeface>) f.get(typeface);
        return sSystemFontMap;
    }

    private static String initTypefaceFamilyMap() {
        try {
            Map map = getSSystemFontMap();
            Set set = map.entrySet();
            for (Object obj : set) {
                Map.Entry entry = (Map.Entry) obj;
                typefaceFamilyNameMap.put((Typeface)entry.getValue(), (String)entry.getKey());
            }
        }
        catch(Exception e){

        }
        return null;
    }


    /**************************************************************************/
    /** shared data container */

    static class Impl {
        Typeface face;
        String familyName;
        int fontSize;
        float densityAdjustedSize;
        boolean outlined;

        float[] commonCharWidths;

        int baselineSpacing;
        int fontHeight;
        int baselineOffset;

        Paint _textPaint;
        Paint _outlinePaint;
        Paint.FontMetricsInt _fontMetricsInt;

        private Impl(Typeface typeface, int textSize, boolean outlined) {
            this.face = typeface;
            this.fontSize = textSize;
            this.outlined = outlined;
            this.familyName = typefaceFamilyNameMap.get(this.face);

            this.commonCharWidths = new float[COMMON_CHAR_END - COMMON_CHAR_START + 1];
            this.densityAdjustedSize = 0.0f;
            this.init();
        }
        
        void init() {
            final float relativeScale = GLRenderGlobals.getRelativeScaling();
            if(Float.compare(this.densityAdjustedSize,this.fontSize*relativeScale) == 0)
                return;

            this.densityAdjustedSize = this.fontSize * relativeScale;

            final Paint textPaint = new Paint();
            textPaint.setStyle(Paint.Style.FILL_AND_STROKE);
            textPaint.setTextAlign(Paint.Align.LEFT);
            textPaint.setTypeface(this.face);
            textPaint.setTextSize(this.densityAdjustedSize);
            textPaint.setAntiAlias(true);
            textPaint.setARGB(0xff, 0xff, 0xff, 0xff);
            
            _textPaint = textPaint;
            
            _outlinePaint = new Paint(textPaint);
            _outlinePaint.setStyle(Paint.Style.STROKE);
            _outlinePaint.setARGB(0xff, 0x0, 0x0, 0x0);

            _fontMetricsInt = _textPaint.getFontMetricsInt();
            this.baselineSpacing = (-_fontMetricsInt.ascent + _fontMetricsInt.descent);
            this.fontHeight = (-_fontMetricsInt.top + _fontMetricsInt.bottom);
            this.baselineOffset = _fontMetricsInt.bottom;

            
            // determine the width of each character and the maximum character width
            char[] text = new char[1]; // character
            float[] widths = new float[1];
            int cnt = 0;
            for (char c = COMMON_CHAR_START; c <= COMMON_CHAR_END; c++) {
                text[0] = c;
                _textPaint.getTextWidths(text, 0, 1, widths);
                this.commonCharWidths[cnt++] = widths[0];
            }
        }
    }
}
