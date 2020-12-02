package android.graphics;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;

public final class Canvas {
    private Bitmap bitmap;
    private Graphics2D impl;

    public Canvas(Bitmap bitmap) {
        this.bitmap = bitmap;
        this.impl = bitmap.image.createGraphics();
    }

    public void drawColor(int argb) {
        final java.awt.Color c = this.impl.getColor();
        this.impl.setColor(new java.awt.Color(Color.red(argb), Color.green(argb), Color.blue(argb), Color.alpha(argb)));
        this.impl.fillRect(0, 0, this.bitmap.getWidth(), this.bitmap.getHeight());
        this.impl.setColor(c);
    }

    public void drawBitmap(Bitmap bitmap, Rect src, RectF dst, Paint paint) {
        this.impl.drawImage(bitmap.image,
                            (int)dst.left,
                            (int)dst.top,
                            (int)Math.ceil(dst.right),
                            (int)Math.ceil(dst.bottom),
                            (src != null) ? (int)src.left : 0,
                            (src != null) ? (int)src.top : 0,
                            (src != null) ? (int)Math.ceil(src.right) : bitmap.getWidth(),
                            (src != null) ? (int)Math.ceil(src.bottom) : bitmap.getHeight(),
                            null);
    }

    public void drawBitmap(Bitmap bitmap, Rect src, Rect dst, Paint paint) {
        drawBitmap(bitmap, src, new RectF(dst.left, dst.top, dst.right, dst.bottom), paint);
    }

    public void drawBitmap(Bitmap bitmap, int x, int y, Paint paint) {
        drawBitmap(bitmap, null, new Rect(x, y, bitmap.getWidth(), bitmap.getHeight()), paint);
    }

    public void drawRect(Rect rect, Paint paint) {
        throw new UnsupportedOperationException();
    }

    public void drawText(String text, float x, float y, Paint paint) {
        if (paint != null)
            paint.apply(this.impl);
        if (paint.style == Paint.Style.STROKE) {
            FontRenderContext frc = this.impl.getFontRenderContext();
            TextLayout textLayout = new TextLayout(text, this.impl.getFont(), frc);
            Shape outline = textLayout.getOutline(AffineTransform.getTranslateInstance(x, y));
            this.impl.draw(outline);
        } else{
            this.impl.drawString(text, x, y);
        }
    }

    public void drawText(String text, int start, int end, float x, float y, Paint paint) {
        this.drawText(text.substring(start, end), x, y, paint);
    }
}
