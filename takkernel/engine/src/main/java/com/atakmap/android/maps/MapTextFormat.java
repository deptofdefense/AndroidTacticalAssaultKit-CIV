
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

import gov.tak.api.annotation.DontObfuscate;
import gov.tak.api.commons.graphics.Font;
import gov.tak.api.commons.graphics.FontMetrics;
import gov.tak.api.commons.graphics.TextFormat;
import gov.tak.api.marshal.IMarshal;
import gov.tak.platform.marshal.MarshalManager;

/**
 * Immutable text style and metrics details for map object.
 *
 */
@DontObfuscate
public final class MapTextFormat {

    static {
        MarshalManager.registerMarshal(new IMarshal() {
            @Override
            public <T, V> T marshal(V in) {
                if(in == null)  return null;
                return (T)((MapTextFormat)in).impl.format;
            }
        }, MapTextFormat.class, TextFormat.class);
        MarshalManager.registerMarshal(new IMarshal() {
            @Override
            public <T, V> T marshal(V in) {
                if(in == null)  return null;
                Font.Style style;
                switch(((Typeface)in).getStyle()) {
                    case Typeface.BOLD :
                        return (T)Font.Style.Bold;
                    case Typeface.BOLD_ITALIC :
                        return (T)Font.Style.BoldItalic;
                    case Typeface.ITALIC:
                        return (T)Font.Style.Italic;
                    case Typeface.NORMAL:
                    default :
                        return (T)Font.Style.Normal;
                }
            }
        }, Typeface.class, Font.Style.class);
        MarshalManager.registerMarshal(new IMarshal() {
            @Override
            public <T, V> T marshal(V in) {
                if(in == null)  return null;
                int style;
                switch(((Font)in).getStyle()) {
                    case Bold:
                        style = Typeface.BOLD;
                        break;
                    case BoldItalic:
                        style = Typeface.BOLD_ITALIC;
                        break;
                    case Italic:
                        style = Typeface.ITALIC;
                        break;
                    case Normal:
                    default :
                        style = Typeface.NORMAL;
                        break;
                }
                return (T)Typeface.create(((Font)in).getFamilyName(), style);
            }
        }, Font.class, Typeface.class);
    }
    private final static int COMMON_CHAR_START = 32; // first character (ASCII Code)
    private final static int COMMON_CHAR_END = 126; // last character (ASCII Code)

    private final static Map<TextFormat, Impl> SHARED_IMPL = new HashMap<>();

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
        this(new TextFormat(
                new Font(typefaceFamilyNameMap.get(typeface),
                        MarshalManager.marshal(typeface, Typeface.class, Font.Style.class),
                        textSize),
                outlined ? TextFormat.STYLE_OUTLINED : 0));

    }

    public MapTextFormat(TextFormat impl) {
        this(impl, MarshalManager.marshal(impl.getFont(), Font.class, Typeface.class));
    }

    private MapTextFormat(TextFormat fmt, Typeface face) {
        Impl i = null;
        synchronized (MapTextFormat.class) {
            final TextFormat key = fmt;
            i = SHARED_IMPL.get(key);
            if (i == null) {
                SHARED_IMPL.put(key, i = new Impl(fmt, face));
            }
        }

        this.impl = i;
    }
    
    public boolean isOutlined() {
        return this.impl.format.isOutlined();
    }

    public int getFontSize() {
        return (int)this.impl.format.getFont().getSize();
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
        return (int)impl.metrics.measureTextWidth(text);
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
        return (int)impl.metrics.measureTextHeight(text);
    }

    /**
     * @return the height of the largest font glyph
     */
    public int getTallestGlyphHeight() {
        return this.impl.fontHeight;
    }

    public int getBaselineOffsetFromBottom() {
        return this.impl.metrics.getBaselineOffsetFromBottom();
    }

    /**
     * The positive Y offset to between baselines for spacing
     * 
     * @return the y offset between baselines.
     */
    public int getBaselineSpacing() {
        return this.impl.metrics.getBaselineSpacing();
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
        if(this.impl.format.isOutlined())
            canvas.drawText(s, off, off + 1, x,
                    y - this.impl._fontMetricsInt.top, this.impl._outlinePaint);
    }

    public String getTypefaceFamilyName()
    {
        return impl.format.getFont().getFamilyName();
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
        final TextFormat format;
        final FontMetrics metrics;
        final Typeface face;
        float densityAdjustedSize;

        float[] commonCharWidths;

        int fontHeight;

        Paint _textPaint;
        Paint _outlinePaint;
        Paint.FontMetricsInt _fontMetricsInt;


        private Impl(TextFormat fmt, Typeface typeface) {
            this.face = typeface;
            format = fmt;

            metrics = FontMetrics.intern(format.getFont());

            this.commonCharWidths = new float[COMMON_CHAR_END - COMMON_CHAR_START + 1];
            this.densityAdjustedSize = 0.0f;
            this.init();
        }
        
        void init() {
            final int fontSize = (int)this.format.getFont().getSize();
            final float relativeScale = GLRenderGlobals.getRelativeScaling();
            if(Float.compare(this.densityAdjustedSize,fontSize*relativeScale) == 0)
                return;

            this.densityAdjustedSize = fontSize * relativeScale;
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
            _outlinePaint.setStrokeWidth(fontSize * 0.08f);

            _fontMetricsInt = _textPaint.getFontMetricsInt();
            this.fontHeight = (-_fontMetricsInt.top + _fontMetricsInt.bottom);

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
