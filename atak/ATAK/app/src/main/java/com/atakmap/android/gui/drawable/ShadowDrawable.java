
package com.atakmap.android.gui.drawable;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.renderscript.Type;
import android.view.View;

import com.atakmap.android.navigation.views.buttons.NavButtonDrawable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A drawable which renders a shadow under a given {@link View}
 *
 * This works similar to {@link NavButtonDrawable} but does not auto-pad the
 * original drawable. It simply creates a shadow that is rendered under a given
 * view. Padding may be necessary to prevent the shadow from being cut off.
 */
public class ShadowDrawable extends Drawable {

    // Set of device models that do not properly support or perform well with
    // the RenderScript API
    private static final Set<String> NOT_SUPPORTED = new HashSet<>(
            Collections.singletonList("SM-T330NU")); // Samsung Tab 4

    /**
     * Check if RenderScript is supported on this device
     * @return True if supported
     */
    public static boolean isSupported() {
        return !NOT_SUPPORTED.contains(Build.MODEL);
    }

    // The scratch bitmap size for the shadow
    // This is used to ensure we get a shadow blur that is consistent between
    // different drawable sizes
    private static final int SHADOW_BMP_SIZE = 128;

    private final View _view;
    private final RenderScript _rs;
    private final ScriptIntrinsicBlur _blur;
    private final Paint _paint;
    private final Rect _viewRect = new Rect();
    private final Rect _shadowRect = new Rect();

    private Allocation _inBuffer;
    private Allocation _outBuffer;
    private Bitmap _viewBmp, _shadowBmp;
    private Canvas _viewCanvas, _shadowCanvas;
    private ColorFilter _shadowFilter;
    private int _shadowRadius = 16;
    private int _color;
    private int _alpha;

    public ShadowDrawable(Context appContext, @NonNull View view) {
        _view = view;
        _paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        _shadowFilter = new PorterDuffColorFilter(Color.BLACK,
                PorterDuff.Mode.SRC_ATOP);

        RenderScript rs = null;
        ScriptIntrinsicBlur blur = null;
        if (isSupported()) {
            try {
                rs = RenderScript.create(appContext);
                blur = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));
                blur.setRadius(_shadowRadius);
            } catch (Exception ignored) {
                // Render script not supported
            }
        }
        _rs = rs;
        _blur = blur;
    }

    /**
     * Set the color of the shadow
     * @param color Shadow color
     */
    public void setColor(int color) {
        if (_color != color) {
            _color = color;
            setColorFilter(new PorterDuffColorFilter(_color,
                    PorterDuff.Mode.SRC_ATOP));
        }
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect bounds = getBounds();
        int width = bounds.width();
        int height = bounds.height();

        int vWidth = _view.getWidth();
        int vHeight = _view.getHeight();

        float scale;
        double ar = (double) width / height;
        int sWidth = SHADOW_BMP_SIZE, sHeight = SHADOW_BMP_SIZE;
        if (ar > 1) {
            sWidth *= ar;
            scale = (float) width / sWidth;
        } else {
            sHeight /= ar;
            scale = (float) height / sHeight;
        }

        if (boundsChanged(_viewBmp, vWidth, vHeight)) {
            _viewBmp = Bitmap.createBitmap(vWidth, vHeight, Config.ARGB_8888);
            _viewCanvas = new Canvas(_viewBmp);
            _viewRect.set(0, 0, vWidth, vHeight);
        }

        if (boundsChanged(_shadowBmp, sWidth, sHeight)) {
            _shadowBmp = Bitmap.createBitmap(sWidth, sHeight, Config.ARGB_8888);
            _shadowCanvas = new Canvas(_shadowBmp);
            _shadowRect.set(0, 0, sWidth, sHeight);
            if (_rs != null) {
                Type type = new Type.Builder(_rs, Element.RGBA_8888(_rs))
                        .setX(sWidth).setY(sHeight).setMipmaps(false).create();
                _inBuffer = Allocation.createTyped(_rs, type);
                _outBuffer = Allocation.createTyped(_rs, type);
            }
        }

        int sRad = _blur != null ? _shadowRadius / 2 : 0;
        _shadowRect.offset(0, sRad);

        // Draw the view to the view canvas
        _viewCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        _view.draw(_viewCanvas);

        // Then draw to the shadow canvas enlarged using the color filter
        _paint.setColorFilter(_shadowFilter);
        _shadowCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        _shadowCanvas.drawBitmap(_viewBmp, _viewRect, _shadowRect, _paint);
        _shadowRect.offset(0, -sRad);

        if (_blur != null) {
            // Copy pixels from enlarged view bitmap
            _inBuffer.copyFrom(_shadowBmp);

            // Blur the bitmap
            _blur.setInput(_inBuffer);
            _blur.forEach(_outBuffer);

            // Copy out to the shadow bitmap
            _outBuffer.copyTo(_shadowBmp);

            // Draw to the output canvas
            for (int i = 0; i < 2; i++)
                canvas.drawBitmap(_shadowBmp, _shadowRect, bounds, _paint);
        } else {
            // Shadow not supported - Draw a stroke instead
            float strokeWidth = 2 * scale;
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    int save = canvas.save();
                    canvas.translate(x * strokeWidth, y * strokeWidth);
                    canvas.drawBitmap(_shadowBmp, _shadowRect, bounds, _paint);
                    canvas.restoreToCount(save);
                }
            }
        }
    }

    @Override
    public void setAlpha(int alpha) {
        _alpha = alpha;
        invalidateSelf();
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        _shadowFilter = colorFilter;
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    private static boolean boundsChanged(Bitmap bmp, int width, int height) {
        return bmp == null || bmp.getWidth() != width
                || bmp.getHeight() != height;
    }
}
