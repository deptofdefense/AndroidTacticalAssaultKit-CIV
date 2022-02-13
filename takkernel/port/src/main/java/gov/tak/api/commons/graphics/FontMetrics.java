package gov.tak.api.commons.graphics;

import android.graphics.Paint;
import android.graphics.Typeface;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

// XXX -    Legacy `MapTextFormat` was not thread-safe, with no previously documented misbehavior.
//          This initial implementation will not introduce synchronization and is recognized as
//          not being thread-safe. If issues are observed, thread-safety will need to be
//          introduced. Concurrent modification may occur during changes to relative scaling.

public final class FontMetrics {
    private final static int COMMON_CHAR_START = 32; // first character (ASCII Code)
    private final static int COMMON_CHAR_END = 126; // last character (ASCII Code)

    final static Map<Font, WeakReference<FontMetrics>> _cache = new WeakHashMap<>();

    private final Font _font;
    private Paint _textPaint;

    float[] commonCharWidths;
    Paint.FontMetricsInt _fontMetricsInt;
    int baselineSpacing;
    int fontHeight;
    int baselineOffset;
    private float _scale;

    public FontMetrics(Font font) {
        _font = font;
        _scale = 0f; // force reset
        commonCharWidths = new float[COMMON_CHAR_END - COMMON_CHAR_START + 1];
        validateNoSync();
    }

    public Font getFont() {
        return _font;
    }

    private void validateNoSync() {
        final float scale = DisplaySettings.getRelativeScaling();
        if(_scale == scale)
            return;
        _scale = scale;

        int style;
        switch(_font.getStyle()) {
            case Bold:
                style = Typeface.BOLD;
                break;
            case Italic:
                style = Typeface.ITALIC;
                break;
            case BoldItalic:
                style = Typeface.BOLD_ITALIC;
                break;
            case Normal:
            default :
                style = Typeface.NORMAL;
                break;
        }
        final Paint textPaint = new Paint();
        textPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setTypeface(Typeface.create(_font.getFamilyName(), style));
        textPaint.setTextSize(_font.getSize()*scale);
        textPaint.setAntiAlias(true);
        textPaint.setARGB(0xff, 0xff, 0xff, 0xff);

        _textPaint = textPaint;

        _fontMetricsInt = _textPaint.getFontMetricsInt();
        this.baselineSpacing = (-_fontMetricsInt.ascent + _fontMetricsInt.descent);
        this.fontHeight = (-_fontMetricsInt.top + _fontMetricsInt.bottom);
        this.baselineOffset = _fontMetricsInt.bottom;

        // determine the width of each character and the maximum character width
        char[] text = new char[1]; // character
        float[] widths = new float[1];
        int cnt = 0;
        for (char ch = COMMON_CHAR_START; ch <= COMMON_CHAR_END; ch++) {
            text[0] = ch;
            _textPaint.getTextWidths(text, 0, 1, widths);
            this.commonCharWidths[cnt++] = widths[0];
        }
    }

    public float measureTextWidth(String text) {
        validateNoSync();

        double maxWidth = 0.0;
        double currWidth = 0.0;
        final char[] carr = new char[1];

        int numLines = 1;
        final int len = text.length();

        for (int i = 0; i < len; ++i) {
            char c = text.charAt(i);
            if (c != '\n') {
                if (c >= COMMON_CHAR_START && c <= COMMON_CHAR_END) {
                    currWidth += this.commonCharWidths[c - COMMON_CHAR_START];
                } else {
                    carr[0] = c;
                    currWidth += this._textPaint.measureText(carr, 0, 1);
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

    public float measureTextHeight(String text) {
        validateNoSync();

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
        return (fontHeight + getBaselineSpacing() * (numLines - 1));
    }

    public int getBaselineOffsetFromBottom() {
        validateNoSync();
        return baselineOffset;
    }

    /**
     * The positive Y offset to between baselines for spacing
     *
     * @return the y offset between baselines.
     */
    public int getBaselineSpacing() {
        validateNoSync();
        return baselineSpacing;
    }

    public static FontMetrics intern(Font font) {
        synchronized(_cache) {
            do {
                final WeakReference<FontMetrics> ref = _cache.get(font);
                if(ref == null) break;
                final FontMetrics fm = ref.get();
                if(fm == null)  break;
                return fm;
            } while(false);

            final FontMetrics fm = new FontMetrics(font);
            _cache.put(font, new WeakReference<>(fm));
            return fm;
        }
    }
}
