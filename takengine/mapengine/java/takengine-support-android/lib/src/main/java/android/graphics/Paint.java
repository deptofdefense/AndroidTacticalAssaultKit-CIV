package android.graphics;

import java.awt.*;
import java.awt.image.BufferedImage;

public final class Paint {
    public enum Style {
        STROKE,
        FILL,
        FILL_AND_STROKE,
    }

    public enum Align {
        LEFT,
    }

    public final static class FontMetricsInt {
        public int ascent;
        public int bottom;
        public int descent;
        public int leading;
        public int top;
    }

    final static BufferedImage pixel = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);

    Typeface typeface;
    Style style;
    float strokeWidth;
    Align align;
    int color;
    boolean antiAlias;
    float textSize;

    public Paint() {
        this.typeface = Typeface.DEFAULT;
        this.style = Style.FILL_AND_STROKE;
        this.strokeWidth = 1f;
        this.align = Align.LEFT;
        this.color = -1;
        this.antiAlias = true;
        this.textSize = 16f;
    }

    public Paint(Paint other) {
        this.typeface = other.typeface;
        this.style = other.style;
        this.strokeWidth = other.strokeWidth;
        this.align = other.align;
        this.color = other.color;
        this.antiAlias = other.antiAlias;
        this.textSize = other.textSize;
    }

    public void setColor(int color) {
        this.color = color;
    }

    public void setStyle(Style style) {
        this.style = style;
    }

    public void setStrokeWidth(float width) {
        this.strokeWidth = width;
    }

   public void setTextAlign(Paint.Align align) {
        this.align = align;
   }

    public void setTypeface(Typeface typeface) {
        this.typeface = typeface;
    }

    public void setTextSize(float size) {
        this.textSize = size;
    }
   public void setAntiAlias(boolean val) {
        this.antiAlias = val;
   }

   public void setARGB(int a, int r, int g, int b) {
           setColor(Color.argb(a, r, g, b));
       }

   public void setAlpha(int alpha) {
        this.color = (alpha<<24)|(this.color&0x00FFFFFF);
   }

    public float measureText(char[] text, int index, int count) {
        Graphics2D g2d = apply(pixel.createGraphics());
        final FontMetrics awtFontMetrics = g2d.getFontMetrics();
        return (float)awtFontMetrics.getStringBounds(text, index, count, g2d).getWidth();
    }

   public float measureText(String text, int start, int end) {
       Graphics2D g2d = apply(pixel.createGraphics());
       final FontMetrics awtFontMetrics = g2d.getFontMetrics();
       return (float)awtFontMetrics.getStringBounds(text, start, end, g2d).getWidth();
   }

   public int getTextWidths(char[] text, int index, int length, float[] widths) {
        for(int i = 0; i < length; i++)
            widths[i] = measureText(text, i+index, 1);
        return length;
   }

    public FontMetricsInt getFontMetricsInt() {
        Graphics2D g2d = apply(pixel.createGraphics());

        final FontMetrics awtFontMetrics = g2d.getFontMetrics();
        final FontMetricsInt retval = new FontMetricsInt();
        retval.ascent = -awtFontMetrics.getAscent();
        retval.descent = awtFontMetrics.getDescent();
        retval.leading = awtFontMetrics.getLeading();
        retval.top = -awtFontMetrics.getMaxAscent();
        retval.bottom = awtFontMetrics.getMaxDescent();

        return  retval;
    }

    Graphics2D apply(Graphics2D g2d) {
        int fontStyle;
        if (this.typeface.isBold() && this.typeface.isItalic()) {
            fontStyle = Font.BOLD | Font.ITALIC;
        } else if (this.typeface.isBold()) {
            fontStyle = Font.BOLD;
        } else if (this.typeface.isItalic()) {
            fontStyle = Font.ITALIC;
        } else {
            fontStyle = Font.PLAIN;
        }
        g2d.setFont(new Font("Arial", fontStyle, (int)Math.ceil(textSize)));
        g2d.setColor(new java.awt.Color(color));
        switch(this.style) {
            case STROKE:
            case FILL_AND_STROKE:
                g2d.setStroke(new BasicStroke(strokeWidth));
                break;
        }
        return g2d;
    }
}
