
package com.atakmap.android.navigation.views.buttons;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.renderscript.Type;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.atakmap.android.gui.drawable.ShadowDrawable;

/**
 * Drawable icon used for {@link NavButton} and other nav-related icons
 * Supports dynamic shadow below the icon
 */
public class NavButtonDrawable extends Drawable implements Drawable.Callback {

    // The scratch bitmap size for the shadow
    // This is used to ensure we get a shadow blur that is consistent between
    // different drawable sizes
    private static final int SHADOW_BMP_SIZE = 128;

    // Badge scaling
    private static final float BADGE_SCALE = 1f / 3f;

    // Badge text size
    private static final float BADGE_TEXT_SIZE = 14f;

    private final Context _context;
    private final Drawable _baseDrawable;

    // Set by user
    private float _shadowRadius = 16;
    private int _alpha = 255;
    private int _fillColor, _shadowColor;
    private ColorFilter _colorFilter, _shadowFilter;
    private boolean _shadowPadding = true;

    // Shadow parameters
    private final RenderScript _rs;
    private final ScriptIntrinsicBlur _blur;
    private Allocation _inBuffer, _outBuffer;
    private Bitmap _scratchBmp, _shadowBmp;
    private Canvas _scratchCanvas, _shadowCanvas;
    private final Paint _basePaint, _shadowPaint;
    private final Rect _shadowRect = new Rect();
    private final Rect _srcRect = new Rect();
    private final Rect _dstRect = new Rect();

    // Badge parameters
    private int _badgeCount;
    private final Paint _badgePaint;
    private float _calcDiameter = -1;
    private int _calcCount;
    private final Paint _textPaint;
    private final RectF _textRect = new RectF();
    private final Path _textPath = new Path();
    private float _textSize, _textY;
    private Drawable _badgeImage;

    public NavButtonDrawable(Context appContext, Drawable baseDrawable) {
        _context = appContext;

        _baseDrawable = baseDrawable;
        _baseDrawable.setCallback(this);
        _basePaint = new Paint(
                Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

        RenderScript rs = null;
        ScriptIntrinsicBlur blur = null;
        if (ShadowDrawable.isSupported()) {
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

        _textSize = BADGE_TEXT_SIZE;

        _shadowPaint = new Paint(_basePaint);
        _shadowFilter = new PorterDuffColorFilter(Color.BLACK,
                PorterDuff.Mode.SRC_ATOP);

        _badgePaint = new Paint();
        _badgePaint.setColor(Color.RED);
        _badgePaint.setAntiAlias(true);
        _badgePaint.setStyle(Paint.Style.FILL);

        _textPaint = new Paint();
        _textPaint.setColor(Color.WHITE);
        _textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        _textPaint.setTextSize(_textSize);
        _textPaint.setAntiAlias(true);
        _textPaint.setTextAlign(Paint.Align.CENTER);
    }

    public NavButtonDrawable(Context appContext, int baseDrawableId) {
        this(appContext, appContext.getDrawable(baseDrawableId));
    }

    public NavButtonDrawable(ImageView view) {
        this(view.getContext(), view.getDrawable());
    }

    /**
     * Get the base drawable
     * @return Base drawable
     */
    public Drawable getBaseDrawable() {
        return _baseDrawable;
    }

    /**
     * Set the primary color of this drawable
     * @param color Color integer
     */
    public void setColor(int color) {
        if (_fillColor != color) {
            _fillColor = color;
            setColorFilter(new PorterDuffColorFilter(color,
                    PorterDuff.Mode.SRC_ATOP));
        }
    }

    /**
     * Set the radius of the shadow
     * @param radius Radius in pixels or zero to remove shadow
     */
    public void setShadowRadius(float radius) {
        if (Float.compare(_shadowRadius, radius) != 0) {
            _shadowRadius = radius;
            if (radius > 0 && _blur != null)
                _blur.setRadius(radius);
            _scratchBmp = null;
            invalidateSelf();
        }
    }

    /**
     * Set the color filter for the button shadow
     * @param filter Color filter
     */
    public void setShadowColorFilter(ColorFilter filter) {
        _shadowFilter = filter;
        invalidateSelf();
    }

    /**
     * Set the shadow color
     * @param color Color integer
     */
    public void setShadowColor(int color) {
        color = (_blur != null ? 0xFF000000 : 0x80000000) | (color & 0xFFFFFF);
        if (_shadowColor != color) {
            _shadowColor = color;
            setShadowColorFilter(new PorterDuffColorFilter(color,
                    PorterDuff.Mode.SRC_ATOP));
        }
    }

    /**
     * Set whether automatic shaddow padding is enabled
     * @param padding True if enabled
     */
    public void setShadowPadding(boolean padding) {
        if (_shadowPadding != padding) {
            _shadowPadding = padding;
            invalidateSelf();
        }
    }

    /**
     * Set the count to display in the top-right badge
     * @param count Count (zero to hide badge)
     */
    public void setBadgeCount(int count) {
        if (_badgeCount != count) {
            _badgeCount = count;
            invalidateSelf();
        }
    }

    /**
     * Set an image drawable to display in the bottom-right
     * @param image Image drawable
     */
    public void setBadgeImage(Drawable image) {
        if (_badgeImage != image) {
            _badgeImage = image;
            invalidateSelf();
        }
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        // Must setup scratch canvas first
        setupScratchCanvas();

        _basePaint.setColorFilter(_colorFilter);
        _basePaint.setAlpha(_alpha);

        // Draw shadow underlay
        drawShadow(canvas);

        // Draw the base drawable on top of the shadow
        canvas.drawBitmap(_scratchBmp, _srcRect, _dstRect, _basePaint);

        // Draw the top-right and bottom-right badges
        drawBadges(canvas);


        // restore original behavior with the layer drawable used for icons
        // for simple counting one should make use of the badging capability
        // within the following example -
        //NavButtonModel mdl = NavButtonManager.getInstance()
        //        .getModelByPlugin(HelloWorldTool.this);
        //if (mdl != null) {
        //    // Increment the badge count and refresh
        //    mdl.setBadgeCount(++count);
        //    NavButtonManager.getInstance().notifyModelChanged(mdl);
        //    Log.d(TAG, "increment visual count to: " + count);
        //}

        if (_baseDrawable instanceof LayerDrawable) {
            LayerDrawable layer = (LayerDrawable) _baseDrawable;
            for (int i = 1; i < layer.getNumberOfLayers();++i) {
                Drawable d = layer.getDrawable(i);
                d.draw(canvas);
            }
        }

    }

    private void setupScratchCanvas() {
        Rect bounds = getBounds();
        int width = bounds.width();
        int height = bounds.height();
        if (boundsChanged(_scratchBmp, width, height)) {
            _scratchBmp = Bitmap.createBitmap(width, height,
                    Bitmap.Config.ARGB_8888);
            _scratchCanvas = new Canvas(_scratchBmp);
        }
    }

    private void drawToScratch(Drawable dr) {
        _scratchCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        dr.draw(_scratchCanvas);
    }

    private void drawShadow(Canvas canvas) {
        Rect bounds = getBounds();
        int width = bounds.width();
        int height = bounds.height();

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

        _srcRect.set(0, 0, width, height);
        _dstRect.set(0, 0, width, height);

        // Shadow scratch needs to be updated
        if (boundsChanged(_shadowBmp, sWidth, sHeight)) {
            _shadowBmp = Bitmap.createBitmap(sWidth, sHeight,
                    Bitmap.Config.ARGB_8888);
            _shadowCanvas = new Canvas(_shadowBmp);
            if (_rs != null) {
                Type type = new Type.Builder(_rs, Element.RGBA_8888(_rs))
                        .setX(sWidth).setY(sHeight).setMipmaps(false).create();
                _inBuffer = Allocation.createTyped(_rs, type);
                _outBuffer = Allocation.createTyped(_rs, type);
            }
        }

        // XXX - No idea how the color filter keeps leaking into the base
        // drawable. If we don't continuously set this to null then the scratch
        // bitmap has the color filter already applied (which we don't want).
        // ALSO calling this causes the drawable to invalidate itself infinitely
        // I'm out of ideas at this point...
        //_baseDrawable.setColorFilter(null);

        // Draw the base drawable to the scratch bitmap
        if (_baseDrawable instanceof LayerDrawable)
            drawToScratch(((LayerDrawable) _baseDrawable).getDrawable(0));
        else
            drawToScratch(_baseDrawable);

        // Generate and render shadow
        if (_shadowRadius > 0) {

            // Apply padding so the shadow doesn't get cut off
            if (_shadowPadding) {
                float sRad = _shadowRadius * scale;
                float drop = _blur != null ? sRad / 2 : 0;
                float padding = sRad * 2;
                float pWidth = width - padding;
                float pHeight = height - padding;
                float padScale = Math.min(pWidth / width, pHeight / height);
                if (padScale > 0) {
                    pWidth = width * padScale;
                    pHeight = height * padScale;
                    int offsetX = (int) ((width - pWidth) / 2);
                    int offsetY = (int) (((height - pHeight) / 2) - drop);
                    _dstRect.set(0, 0, (int) pWidth, (int) pHeight);
                    _dstRect.offset(offsetX, offsetY);
                }
                _shadowRect.set(0, 0, (int) (sWidth * padScale),
                        (int) (sHeight * padScale));
                _shadowRect.offset((sWidth - _shadowRect.width()) / 2,
                        (sHeight - _shadowRect.height()) / 2);
            } else
                _shadowRect.set(0, 0, sWidth, sHeight);

            // Scale down to the shadow bitmap so we get a consistent blur
            _shadowPaint.setColorFilter(_shadowFilter);
            _shadowCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            _shadowCanvas.drawBitmap(_scratchBmp, _srcRect, _shadowRect,
                    _shadowPaint);
            _shadowRect.set(0, 0, sWidth, sHeight);

            if (_blur != null) {
                // Copy to render script buffer
                _inBuffer.copyFrom(_shadowBmp);

                // Blur the bitmap
                _blur.setInput(_inBuffer);
                _blur.forEach(_outBuffer);

                // Copy out to the shadow bitmap
                _outBuffer.copyTo(_shadowBmp);

                // Draw the shadow
                for (int i = 0; i < 2; i++)
                    canvas.drawBitmap(_shadowBmp, _shadowRect, _srcRect,
                            _shadowPaint);
            } else {
                // Shadow not supported - Draw a stroke instead
                float strokeWidth = 2 * scale;
                for (int x = -1; x <= 1; x++) {
                    for (int y = -1; y <= 1; y++) {
                        int save = canvas.save();
                        canvas.translate(x * strokeWidth, y * strokeWidth);
                        canvas.drawBitmap(_shadowBmp, _shadowRect, _srcRect,
                                _shadowPaint);
                        canvas.restoreToCount(save);
                    }
                }
            }
        }
    }

    private void drawBadges(Canvas canvas) {
        Rect bounds = getBounds();
        int width = bounds.width();
        int height = bounds.height();

        // Position the badge in the top-right quadrant of the icon.
        int minSize = Math.min(width, height);
        float diameter = BADGE_SCALE * minSize;
        float radius = diameter / 2f;
        float sRadius = radius * 1.2f;
        float sMargin = sRadius - radius;
        float centerX = width - sRadius - 1;
        float centerY = sRadius + 1;
        float scale = diameter / minSize;

        // Bottom-right customizable badge
        Drawable badge = _badgeImage;
        if (badge != null) {
            int restore = canvas.save();
            canvas.scale(scale, scale);
            float borderSize = sRadius - radius;
            float x = width - diameter - borderSize;
            float y = height - diameter - borderSize;
            canvas.translate(x / scale, y / scale);
            Rect oldBounds = badge.getBounds();
            badge.setBounds(bounds.left, bounds.top, bounds.right,
                    bounds.bottom);
            badge.draw(canvas);
            badge.setBounds(oldBounds);
            canvas.restoreToCount(restore);
        }

        // Top-right red badge
        if (_badgeCount > 0) {

            // Draw badge circle.
            _badgePaint.setColor(Color.BLACK);
            canvas.drawCircle(centerX, centerY, sRadius, _badgePaint);
            _badgePaint.setColor(0xFFBB0000);
            canvas.drawCircle(centerX, centerY, radius, _badgePaint);

            // Draw badge count text inside the circle
            String countString = String.valueOf(_badgeCount);
            if (_badgeCount != _calcCount
                    || Float.compare(diameter, _calcDiameter) != 0) {
                // Compute appropriate size to fit text in circle
                _textPaint.setTextSize(BADGE_TEXT_SIZE);
                _textPaint.getTextPath(countString, 0, countString.length(),
                        0, 0, _textPath);
                _textPath.computeBounds(_textRect, true);
                _textSize = Math.min(diameter * (_textRect.height()
                        / _textRect.width()), diameter);
                _textPaint.setTextSize(_textSize);
                _textPaint.getTextPath(countString, 0, countString.length(),
                        0, 0, _textPath);
                _textPath.computeBounds(_textRect, true);
                float textHeight = _textRect.height();
                _textY = ((diameter - textHeight) / 2f) + textHeight
                        + (12f / textHeight) + sMargin;
                _calcCount = _badgeCount;
                _calcDiameter = diameter;
            }

            _textPaint.setTextSize(_textSize);
            canvas.drawText(countString, centerX, _textY, _textPaint);
        }
    }

    private static boolean boundsChanged(Bitmap bmp, int width, int height) {
        return bmp == null || bmp.getWidth() != width
                || bmp.getHeight() != height;
    }

    @Override
    public void setAlpha(int alpha) {
        _alpha = alpha;
        invalidateSelf();
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        _colorFilter = colorFilter;
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.UNKNOWN;
    }

    @Override
    public void setBounds(int left, int top, int right, int bottom) {
        super.setBounds(left, top, right, bottom);
        _baseDrawable.setBounds(left, top, right, bottom);
    }

    @Override
    public int getIntrinsicWidth() {
        return _baseDrawable.getIntrinsicWidth();
    }

    @Override
    public int getIntrinsicHeight() {
        return _baseDrawable.getIntrinsicHeight();
    }

    @Nullable
    @Override
    public ConstantState getConstantState() {
        return _cState;
    }

    private final ConstantState _cState = new ConstantState() {
        @NonNull
        @Override
        public Drawable newDrawable() {
            NavButtonDrawable copy = new NavButtonDrawable(_context,
                    _baseDrawable);
            copy.setAlpha(_alpha);
            copy.setColorFilter(_colorFilter);
            copy.setShadowRadius(_shadowRadius);
            copy.setShadowColorFilter(_shadowFilter);
            copy.setBadgeCount(_badgeCount);
            return copy;
        }

        @Override
        public int getChangingConfigurations() {
            return 0;
        }
    };

    @Override
    public void invalidateDrawable(@NonNull Drawable who) {
        invalidateSelf();
    }

    @Override
    public void scheduleDrawable(@NonNull Drawable who, @NonNull Runnable what,
            long when) {
        scheduleSelf(what, when);
    }

    @Override
    public void unscheduleDrawable(@NonNull Drawable who,
            @NonNull Runnable what) {
        unscheduleSelf(what);
    }
}
