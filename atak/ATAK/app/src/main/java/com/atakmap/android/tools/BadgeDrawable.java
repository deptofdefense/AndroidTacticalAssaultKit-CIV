
package com.atakmap.android.tools;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import androidx.annotation.NonNull;

/**
 *
 * Thanks to http://stackoverflow.com/questions/18156477/how-to-make-an-icon-in-the-action-bar-with-the-number-of-notification
 *
 * TODO: Merge in the rest of the Data Sync BadgeDrawable changes here for 3.13
 *  which allows you to add a badge to any drawable without needing to create
 *  a "*_layers.xml" file
 */
public class BadgeDrawable extends Drawable {

    private static final float DEFAULT_TEXT_SIZE = 14f;
    private static final float HEIGHT_TO_TEXT_SCALE = 3.43f;

    private float _textSize, _textY;

    /**
     * Ability to draw unread count (string in a red circle, top right corner)
     */
    private final Paint _badgePaint;
    private final Paint _textPaint;
    private final RectF _textRect = new RectF();
    private final Path _textPath = new Path();

    /**
     * Ability to draw a presence overlay (colored circle in bottom right corner)
     */
    private Boolean _connectState;
    private Paint _connectPaint;

    private int _badgeCount;

    // Used for calculating badge text size
    private float _calcDiameter = -1;
    private int _calcCount;

    public BadgeDrawable(Context context) {
        _connectState = null;

        //_textSize = context.getResources().getDimension(R.dimen.badge_text_size);
        _textSize = DEFAULT_TEXT_SIZE;

        _badgePaint = new Paint();
        _badgePaint.setColor(Color.RED);
        _badgePaint.setAntiAlias(true);
        _badgePaint.setStyle(Paint.Style.FILL);

        _connectPaint = new Paint();
        _connectPaint.setColor(Color.GREEN);
        _connectPaint.setAntiAlias(true);
        _connectPaint.setStyle(Paint.Style.FILL);

        _textPaint = new Paint();
        _textPaint.setColor(Color.WHITE);
        _textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        _textPaint.setTextSize(_textSize);
        _textPaint.setAntiAlias(true);
        _textPaint.setTextAlign(Paint.Align.CENTER);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (_badgeCount <= 0 && _connectState == null) {
            return;
        }

        Rect bounds = getBounds();
        float width = bounds.width();
        float height = bounds.height();

        // Position the badge in the top-right quadrant of the icon.
        float radius = ((Math.min(width, height) / 2) - 1) / 2;
        float centerX = width - radius - 1;
        float centerY = radius + 1;

        if (_badgeCount > 0) {
            // Draw badge circle.
            canvas.drawCircle(centerX, centerY, radius, _badgePaint);

            // Draw badge count text inside the circle
            String countString = String.valueOf(_badgeCount);
            float diameter = radius * 2;
            if (_badgeCount != _calcCount
                    || Float.compare(diameter, _calcDiameter) != 0) {
                // Compute appropriate size to fit text in circle
                _textPaint.setTextSize(DEFAULT_TEXT_SIZE);
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
                        + (12f / textHeight);
                _calcCount = _badgeCount;
                _calcDiameter = diameter;
            }

            _textPaint.setTextSize(_textSize);
            canvas.drawText(countString, centerX, _textY, _textPaint);
        }

        //optionally draw presence indicator
        if (_connectState != null && _connectState) {
            centerX = width - radius - 1;
            centerY = height - radius + 1;

            //radius a bit smaller
            float rad2 = radius * 0.75f;
            canvas.drawCircle(centerX, centerY, rad2, _connectPaint);
        }
    }

    /*
     * Sets the count (i.e notifications) to display.
     */
    public void setCount(int count) {
        _badgeCount = count;
        invalidateSelf();
    }

    /**
     * Sets the count on a Badge given a count and a presence color.
     * @param presenceColor the presence color as an integer representation of Color.
     */
    public void setColor(Integer presenceColor) {
        if (presenceColor == null) {
            _connectState = false;
        } else {
            _connectState = true;

            _connectPaint = new Paint();
            _connectPaint.setColor(presenceColor);
            _connectPaint.setAntiAlias(true);
            _connectPaint.setStyle(Paint.Style.FILL);
        }

    }

    @Override
    public void setAlpha(int alpha) {
        // do nothing
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        // do nothing
    }

    @Override
    public int getOpacity() {
        return PixelFormat.UNKNOWN;
    }
}
