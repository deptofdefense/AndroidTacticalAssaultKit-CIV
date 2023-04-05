
package com.atakmap.android.navigation.views.loadout;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.GridView;

import com.atakmap.app.R;

/**
 * A {@link GridView} used to display available tools
 */
public class LoadoutToolsGridView extends GridView {

    private boolean showDeleteOverlay;
    private int scrollHint;

    public LoadoutToolsGridView(Context context) {
        super(context);
    }

    public LoadoutToolsGridView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public LoadoutToolsGridView(Context context, AttributeSet attrs,
            int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    /**
     * Set whether to display the delete overlay (used while dragging)
     * @param show True to show the delete overlay
     */
    public void showDeleteOverlay(boolean show) {
        if (showDeleteOverlay != show) {
            showDeleteOverlay = show;
            invalidate();
        }
    }

    /**
     * Set whether to display the scroll hint overlay
     * @param scrollHint 1 to display bottom scroll hint
     *                   -1 to display top scroll hint
     *                   0 to display neither
     */
    public void setScrollHint(int scrollHint) {
        if (this.scrollHint != scrollHint) {
            this.scrollHint = scrollHint;
            invalidate();
        }
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);

        Resources r = getResources();
        int width = getWidth();
        int height = getHeight();
        int drSize = r.getDimensionPixelSize(R.dimen.nav_button_size);

        // Draw the delete overlay
        if (showDeleteOverlay) {
            int bgColor = r.getColor(R.color.black_overlay_transparent);
            Drawable icon = r.getDrawable(R.drawable.nav_delete);
            int x = (width - drSize) / 2;
            int y = (height - drSize) / 2;
            canvas.drawColor(bgColor, PorterDuff.Mode.MULTIPLY);
            drawAtRect(canvas, icon, x, y, x + drSize, y + drSize);
        }

        // Draw the scroll hint
        if (scrollHint != 0) {
            Drawable grad = r.getDrawable(R.drawable.black_overlay_gradient);
            if (scrollHint < 0) {
                // Top scroll hint
                drawAtRect(canvas, grad, 0, 0, width, drSize);
            } else {
                // Bottom scroll hint
                int save = canvas.save();
                canvas.rotate(180);
                canvas.translate(-width, -height);
                drawAtRect(canvas, grad, 0, 0, width, drSize);
                canvas.restoreToCount(save);
            }
        }
    }

    /**
     * Draw a {@link Drawable} using the given rectangle coordinates
     * @param canvas Canvas to draw on
     * @param dr Drawable to draw
     * @param left Left side of the rectangle
     * @param top Top of the rectangle
     * @param right Right side of the rectangle
     * @param bottom Bottom of the rectangle
     */
    private void drawAtRect(Canvas canvas, Drawable dr,
            int left, int top, int right, int bottom) {
        Rect oldBounds = new Rect(dr.getBounds());
        dr.setBounds(left, top, right, bottom);
        dr.draw(canvas);
        dr.setBounds(oldBounds);
    }
}
