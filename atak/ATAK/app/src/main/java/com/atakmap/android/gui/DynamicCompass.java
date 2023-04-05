
package com.atakmap.android.gui;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.atakmap.android.gui.drawable.ShadowDrawable;
import com.atakmap.android.navigation.views.NavView;
import com.atakmap.android.navigation.views.buttons.NavButtonDrawable;
import com.atakmap.app.R;
import com.atakmap.coremap.conversions.Angle;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

/**
 * A compass with several options for moving the map camera
 * Show in the top-left corner of the main screen
 */
public class DynamicCompass extends RelativeLayout {

    private ImageView circle, background;
    private ImageView compassArrow;
    private ImageView rotateArrow;
    private ImageView tiltTicks, tiltArrow;
    private HeadingText headingTxt;
    private float tilt;
    private float heading;
    private int headingInt;
    private boolean headingVisible;
    private boolean tiltVisible;
    private boolean rotateVisible;
    private boolean gpsLock;

    // Shadow background
    private View shadow;
    private ShadowDrawable shadowDr;
    private ViewGroup compassLines;
    private float iconScale;
    private boolean updateShadow;

    public DynamicCompass(Context context) {
        super(context);
    }

    public DynamicCompass(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public DynamicCompass(Context context, AttributeSet attrs,
            int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public DynamicCompass(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        init();
    }

    private void init() {
        if (isInEditMode())
            return;

        shadow = findViewById(R.id.compass_shadow);
        compassLines = findViewById(R.id.compass_lines);
        circle = findViewById(R.id.compass_circle);
        background = findViewById(R.id.compass_background);
        compassArrow = findViewById(R.id.compass_arrow);
        rotateArrow = findViewById(R.id.rotate_arrow);
        tiltTicks = findViewById(R.id.tilt_ticks);
        tiltArrow = findViewById(R.id.tilt_arrow);
        headingTxt = findViewById(R.id.heading_value);

        shadowDr = new ShadowDrawable(getContext(), compassLines);
        shadow.setBackground(shadowDr);

        NavButtonDrawable dr = new NavButtonDrawable(circle);
        dr.setShadowRadius(0);
        circle.setImageDrawable(dr);

        dr = new NavButtonDrawable(tiltTicks);
        dr.setShadowRadius(0);
        tiltTicks.setImageDrawable(dr);

        dr = new NavButtonDrawable(compassArrow);
        dr.setShadowRadius(0);
        compassArrow.setImageDrawable(dr);

        update();
    }

    /**
     * Set the tilt value of the compass
     * @param tiltValue Value to set tilt indicator to
     */
    public void setTilt(double tiltValue) {
        this.tilt = (float) tiltValue;
    }

    /**
     * Set whether the tilt widget should be visible
     * @param visible True if visible
     */
    public void setTiltVisible(boolean visible) {
        if (this.tiltVisible != visible) {
            this.tiltVisible = visible;
            this.updateShadow = true;
        }
    }

    /**
     * Set the heading value of the compass
     * @param heading Value to set heading indicator to - the final heading
     *                is scaled between (0,360]
     */
    public void setHeading(float heading) {
        if (Double.isNaN(heading))
            return;

        heading = heading % 360;
        if (heading <= 0)
            heading+=360;

        this.heading = heading;
    }

    /**
     * Set whether the heading value is visible
     * @param visible True if visible
     */
    public void setHeadingVisible(boolean visible) {
        this.headingVisible = visible;
    }

    /**
     * Set whether the left-side free rotate arrow is visible
     * @param visible True if visible
     */
    public void setRotateVisible(boolean visible) {
        if (this.rotateVisible != visible) {
            this.rotateVisible = visible;
            this.updateShadow = true;
        }
    }

    /**
     * Set whether a GPS lock is active
     * @param gpsLock True if GPS lock active
     */
    public void setGPSLock(boolean gpsLock) {
        this.gpsLock = gpsLock;
    }

    /**
     * Update compass UI
     */
    public void update() {
        Resources res = getResources();
        if (compassArrow == null)
            init();

        rotateArrow.setVisibility(rotateVisible ? VISIBLE : GONE);

        if (tiltVisible) {
            tiltArrow.setRotation(tilt);
            tiltArrow.setVisibility(VISIBLE);
            tiltTicks.setVisibility(VISIBLE);
        } else {
            tiltArrow.setVisibility(GONE);
            tiltTicks.setVisibility(GONE);
        }

        if (gpsLock) {
            headingTxt.setText(GeoPointMetaData.GPS);
            headingTxt.setUnits(null);
        } else {
            int heading = (int) Math.round(this.heading);
            if (heading == 0)
                heading = 360;
            if (headingInt != heading) {
                headingInt = heading;
                headingTxt.setHeading(heading);
                headingTxt.setUnits(Angle.DEGREE);
                compassArrow.setRotation(heading);
            }
        }
        headingTxt.setVisibility(headingVisible ? VISIBLE : GONE);

        int color = NavView.getInstance().getUserIconColor();
        int shadowColor = NavView.getInstance().getUserIconShadowColor();

        // Update the icon colors per user preference
        circle.setColorFilter(color, PorterDuff.Mode.MULTIPLY);
        rotateArrow.setColorFilter(color, PorterDuff.Mode.MULTIPLY);
        tiltTicks.setColorFilter(color, PorterDuff.Mode.MULTIPLY);

        // Update the scaling per user preference
        float scale = NavView.getInstance().getUserIconScale();
        if (iconScale != scale) {
            iconScale = scale;
            updateShadow = true;
        }
        int size = (int) (res.getDimension(R.dimen.nav_button_size) * scale);
        int arrowPad = (int) (res.getDimension(
                R.dimen.dynamic_compass_arrow_padding) * scale);

        compassArrow.setPadding(arrowPad, arrowPad, arrowPad, arrowPad);
        setSize(circle, size);
        setSize(background, size);
        setSize(tiltTicks, size);
        setSize(tiltArrow, size);
        setSize(compassArrow, size);
        setSize(rotateArrow, size);
        headingTxt.setStrokeWidth(res.getDimension(R.dimen.auto_space));

        // Finally update the background shadow if needed
        if (updateShadow) {
            shadow.invalidate();
            updateShadow = false;
        }
        shadowDr.setColor(shadowColor);
    }

    /**
     * Set the size of a given view
     * @param view View
     * @param size Size (width/height) in pixels
     */
    private static void setSize(View view, int size) {
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp.width != size || lp.height != size) {
            lp.width = size;
            lp.height = size;
            view.setLayoutParams(lp);
        }
    }

    /**
     * A text view that displays the compass heading
     * This is different from {@link TextView} in that it supports outlined text
     */
    public static class HeadingText extends View {

        private Paint textPaint;
        private int headingDeg;
        private String headingStr = "0";
        private final Rect textBounds = new Rect();
        private float strokeWidth;
        private @Nullable Angle units;

        public HeadingText(Context context) {
            super(context);
        }

        public HeadingText(Context context, @Nullable AttributeSet attrs) {
            super(context, attrs);
        }

        public HeadingText(Context context, @Nullable AttributeSet attrs,
                int defStyleAttr) {
            super(context, attrs, defStyleAttr);
        }

        /**
         * Set the heading value
         * @param headingDeg Heading in degrees
         */
        void setHeading(int headingDeg) {
            if (this.headingDeg != headingDeg) {
                this.headingDeg = headingDeg;
                setText(String.valueOf(headingDeg));
            }
        }

        /**
         * Set the text displayed in the view
         * @param text Text string
         */
        void setText(String text) {
            if (!FileSystemUtils.isEquals(this.headingStr, text)) {
                this.headingStr = text;
                invalidate();
            }
        }

        /**
         * Set the text stroke width
         * @param width Width in pixels
         */
        void setStrokeWidth(float width) {
            if (Float.compare(strokeWidth, width) != 0) {
                this.strokeWidth = width;
                invalidate();
            }
        }

        /**
         * Set the heading units
         * @param units Angle units
         */
        void setUnits(@Nullable Angle units) {
            if (this.units != units) {
                this.units = units;
                invalidate();
            }
        }

        @Override
        protected void onFinishInflate() {
            super.onFinishInflate();
            textPaint = new Paint();
            textPaint.setAntiAlias(true);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            float scale = getScale();
            int color, shadow;
            if (isInEditMode()) {
                color = Color.WHITE;
                shadow = Color.BLACK;
            } else {
                color = NavView.getInstance().getUserIconColor();
                shadow = NavView.getInstance().getUserIconShadowColor();
            }

            // Make the stroke shadow a bit transparent
            shadow = shadow & 0xAAFFFFFF;

            // Update the text bounds
            textPaint.setTextSize(getTextSize());
            textPaint.getTextBounds(headingStr, 0, headingStr.length(),
                    textBounds);

            float strokeWidth = this.strokeWidth * scale;
            float x = (getWidth() - textBounds.width()) / 2f;
            float y = getHeight() - strokeWidth - 1;
            String text = String.valueOf(headingDeg);

            if (units != null) {
                // Add units after text has been centered
                text += units.getAbbrev();
            }

            // Draw the stroke
            textPaint.setColor(shadow);
            textPaint.setStrokeWidth(strokeWidth);
            textPaint.setStyle(Paint.Style.STROKE);
            canvas.drawText(text, x, y, textPaint);

            // Draw the text
            textPaint.setColor(color);
            textPaint.setStyle(Paint.Style.FILL);
            canvas.drawText(text, x, y, textPaint);
        }

        /**
         * Get the current scale for the text
         * @return Scale factor
         */
        private float getScale() {
            return isInEditMode() ? 1
                    : NavView.getInstance().getUserIconScale();
        }

        /**
         * Get the current scaled font size
         * @return Font size in pixels
         */
        private float getTextSize() {
            float sizePx = getResources().getDimension(
                    R.dimen.dynamic_compass_text_size);
            return sizePx * getScale();
        }
    }
}
