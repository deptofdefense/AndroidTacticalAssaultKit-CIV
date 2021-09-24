
package com.atakmap.android.imagecapture;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Parcel;
import com.atakmap.coremap.log.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Float rectangle made for canvas drawing with text
 */
public class TextRect extends RectF {

    private static final String TAG = "TextRect";

    public static final int ALIGN_TOP = 1;

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        if (!super.equals(o))
            return false;
        TextRect textRect = (TextRect) o;
        return Float.compare(textRect._padding, _padding) == 0 &&
                Float.compare(textRect._lineHeight, _lineHeight) == 0 &&
                Objects.equals(_paint, textRect._paint) &&
                Arrays.equals(_lines, textRect._lines);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(super.hashCode(), _paint, _padding,
                _lineHeight);
        result = 31 * result + Arrays.hashCode(_lines);
        return result;
    }

    public static final int ALIGN_Y_CENTER = 2;
    public static final int ALIGN_BOTTOM = 4;
    public static final int ALIGN_LEFT = 8;
    public static final int ALIGN_X_CENTER = 16;
    public static final int ALIGN_RIGHT = 32;

    private final Paint _paint;
    private final TextLine[] _lines;
    private final float _padding;
    private float _lineHeight;

    public TextRect(Paint paint, float padding, String... lines) {
        _paint = paint;
        List<String> lineList = new ArrayList<>();
        for (String l : lines) {
            if (l == null)
                lineList.add("");
            else if (l.contains("\n"))
                lineList.addAll(Arrays.asList(l.split("\n")));
            else
                lineList.add(l);
        }
        _lines = new TextLine[lineList.size()];
        for (int i = 0; i < _lines.length; i++)
            _lines[i] = new TextLine(lineList.get(i));
        _padding = padding;
        init();
    }

    // Calculate rectangle size
    private void init() {
        right = left = top = bottom = 0;
        for (TextLine line : _lines) {
            if (line == null)
                continue;
            _paint.setTypeface(line.getTypeface());
            RectF lineRect = getTextBounds(_paint, line.toString());
            line.setBounds(lineRect);
            this.right = Math.max(this.right, lineRect.width());
            _lineHeight = Math.max(_lineHeight, lineRect.height());
        }
        this.right += _padding * 2;
        this.bottom = _lineHeight * _lines.length + (_lines.length + 1)
                * _padding;
    }

    public int getNumLines() {
        return _lines.length;
    }

    /**
     * Set the typeface of a line (default, bold, italic, etc.)
     * This will affect the rectangle size
     * @param lineNum Line number
     * @param typeface Typeface enum
     */
    public void setTypeface(int lineNum, Typeface typeface) {
        if (lineNum >= 0 && lineNum < _lines.length) {
            _lines[lineNum].setTypeface(typeface);
            init();
        }
    }

    public void setPos(PointF pos, int align) {
        float xOffset, yOffset;
        switch (align & (ALIGN_TOP | ALIGN_Y_CENTER | ALIGN_BOTTOM)) {
            case ALIGN_TOP:
                yOffset = 0;
                break;
            case ALIGN_BOTTOM:
                yOffset = -height();
                break;
            default:
            case ALIGN_Y_CENTER:
                yOffset = -height() / 2;
                break;
        }
        switch (align & (ALIGN_LEFT | ALIGN_X_CENTER | ALIGN_RIGHT)) {
            case ALIGN_LEFT:
                xOffset = 0;
                break;
            case ALIGN_RIGHT:
                xOffset = -width();
                break;
            default:
            case ALIGN_X_CENTER:
                xOffset = -width() / 2;
                break;
        }
        offsetTo(pos.x + xOffset, pos.y + yOffset);
    }

    public void setPos(PointF pos) {
        setPos(pos, ALIGN_X_CENTER | ALIGN_Y_CENTER);
    }

    public void alignTo(int align) {
        setPos(new PointF(0, 0), align);
    }

    // Draw lines w/ weight
    public void draw(Canvas can, int startLine, int endLine,
            float weight, int borderColor) {
        Paint.Align tAlign = _paint.getTextAlign();
        float x = tAlign == Paint.Align.LEFT ? this.left + _padding
                : (tAlign == Paint.Align.RIGHT ? this.right - _padding
                        : this.centerX());
        float y = this.top;
        int i = 0;
        for (TextLine line : _lines) {
            y += _lineHeight + _padding;
            if (i >= startLine && i <= endLine) {
                line.draw(can, _paint, x, y, weight, borderColor);
            }
            i++;
        }
    }

    // Draw all lines beginning at startLine w/out weight
    public void draw(Canvas can, int startLine) {
        draw(can, startLine, _lines.length - 1);
    }

    // Draw lines w/out weight
    public void draw(Canvas can, int startLine, int endLine) {
        draw(can, startLine, endLine, 0, 0);
    }

    // Draw single line w/ weight
    public void draw(Canvas can, float weight, int borderColor) {
        draw(can, 0, _lines.length, weight, borderColor);
    }

    public void draw(Canvas can, float weight) {
        draw(can, 0, _lines.length, weight, Color.BLACK);
    }

    // Draw single line w/out weight
    public void draw(Canvas can) {
        draw(can, 0);
    }

    public static RectF getTextBounds(Paint p, String text) {
        Path textPath = new Path();
        p.getTextPath(text, 0, text.length(), 0, 0, textPath);
        RectF textBounds = new RectF();
        textPath.computeBounds(textBounds, true);
        return textBounds;
    }

    public static final Creator<TextRect> CREATOR = new Creator<TextRect>() {
        /**
         * Return a new rectangle from the data in the specified parcel.
         */
        @Override
        public TextRect createFromParcel(Parcel in) {
            TextRect r = new TextRect(new Paint(), 0);
            r.readFromParcel(in);
            return r;
        }

        /**
         * Return an array of rectangles of the specified size.
         */
        @Override
        public TextRect[] newArray(int size) {
            return new TextRect[size];
        }
    };

    public static class TextLine {

        private final String _line;
        private final List<TextSeg> _segs;
        private Typeface _typeface;
        private RectF _bounds;

        public TextLine(String line) {
            _segs = new ArrayList<>();
            _typeface = Typeface.DEFAULT;
            int last = 0, startTag;
            while ((startTag = line.indexOf("<#", last)) > -1) {
                int endTag = line.indexOf(">", startTag);
                if (endTag == -1)
                    endTag = line.length();
                if (startTag > last) {
                    // Add start/intermediate segment
                    _segs.add(
                            new TextSeg(line.substring(last, startTag), null));
                }

                // Parse color
                String colStr = line.substring(startTag + 1, endTag);
                Integer color;
                try {
                    color = Color.parseColor(colStr);
                } catch (Exception e) {
                    color = null;
                    Log.e(TAG, "Failed to parse color: " + colStr, e);
                }

                // Parse segment
                int endSeg = line.indexOf("</>", endTag);
                if (endSeg == -1)
                    endSeg = line.length();
                _segs.add(
                        new TextSeg(line.substring(endTag + 1, endSeg), color));
                last = endSeg + 3;
            }
            if (last < line.length()) {
                // Add trailing segment
                _segs.add(new TextSeg(line.substring(last), null));
            }

            StringBuilder sb = new StringBuilder();
            for (TextSeg seg : _segs)
                sb.append(seg.toString());
            _line = sb.toString();
        }

        public void setTypeface(Typeface tf) {
            _typeface = tf;
        }

        public Typeface getTypeface() {
            return _typeface;
        }

        public void setBounds(RectF bounds) {
            _bounds = bounds;
        }

        public RectF getBounds() {
            return _bounds;
        }

        public void draw(Canvas can, Paint paint, float x, float y,
                float weight, int borderColor) {
            int defColor = paint.getColor();
            paint.setTypeface(_typeface);
            Paint.Align align = paint.getTextAlign();

            // Offset start position of X based on width
            float startX = x;
            if (align == Paint.Align.CENTER)
                startX = x - paint.measureText(_line) / 2;
            else if (align == Paint.Align.RIGHT)
                startX = x - paint.measureText(_line);

            // Account for non-zero baseline
            if (_bounds != null)
                y -= _bounds.bottom;

            paint.setTextAlign(Paint.Align.LEFT);
            for (TextSeg seg : _segs) {
                String str = seg.toString();
                if (seg.getColor() != null)
                    paint.setColor(seg.getColor());
                if (weight > 0)
                    CanvasHelper.drawTextBorder(can, str, startX, y, weight,
                            paint, borderColor);
                else
                    can.drawText(str, startX, y, paint);
                startX += paint.measureText(seg.toString());
                paint.setColor(defColor);
            }
            paint.setTextAlign(align);
        }

        @Override
        public String toString() {
            return _line;
        }
    }

    public static class TextSeg {

        private String _str;
        private Integer _color;

        public TextSeg(String str, Integer color) {
            _str = str;
            _color = color;
        }

        public Integer getColor() {
            return _color;
        }

        @Override
        public String toString() {
            return _str;
        }
    }
}
