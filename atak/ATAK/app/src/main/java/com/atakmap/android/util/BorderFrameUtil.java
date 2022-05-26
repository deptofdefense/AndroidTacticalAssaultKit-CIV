
package com.atakmap.android.util;

import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.atakmap.android.maps.MapView;
import com.atakmap.app.ATAKActivity;
import com.atakmap.app.R;

/**
 * Helper methods for adding/removing content from the bordering view frames
 * This is typically used to show color classification bars on the top and
 * bottom of the app screen
 */
public class BorderFrameUtil {

    // Each side of the screen that a border frame is available
    public static final int TOP = 0;
    public static final int BOTTOM = 1;
    public static final int LEFT = 2;
    public static final int RIGHT = 3;

    /**
     * Given a side of the screen, get the corresponding border frame
     * @param side Frame side - either {@link #TOP}, {@link #BOTTOM},
     *              {@link #LEFT} or {@link #RIGHT}
     * @return Frame layout or null if N/A
     */
    public static FrameLayout getFrame(int side) {
        MapView mv = MapView.getMapView();
        if (mv == null)
            return null;

        ATAKActivity act = (ATAKActivity) mv.getContext();
        switch (side) {
            case TOP:
                return act.findViewById(R.id.top_side_panel_container);
            case BOTTOM:
                return act.findViewById(R.id.bottom_side_panel_container);
            case LEFT:
                return act.findViewById(R.id.left_side_panel_container);
            case RIGHT:
                return act.findViewById(R.id.right_side_panel_container);
        }
        return null;
    }

    /**
     * Add a view to one of the border frames
     * @param side Frame side
     * @param v View to add
     * @param gravity Gravity position
     */
    public static void addView(int side, View v, int gravity) {
        FrameLayout frame = getFrame(side);
        if (frame != null) {
            FrameLayout.LayoutParams fp;
            ViewGroup.LayoutParams lp = v.getLayoutParams();
            if (lp instanceof FrameLayout.LayoutParams)
                fp = (FrameLayout.LayoutParams) lp;
            else {
                if (lp != null)
                    fp = new FrameLayout.LayoutParams(lp);
                else
                    fp = new FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                            FrameLayout.LayoutParams.WRAP_CONTENT);
            }
            fp.gravity = gravity;
            frame.addView(v, fp);
        }
    }

    /**
     * Add a view to one of the border frames
     * @param side Frame side
     * @param v View to add
     */
    public static void addView(int side, View v) {
        FrameLayout frame = getFrame(side);
        if (frame != null)
            frame.addView(v);
    }

    /**
     * Remove a view from one of the border frames
     * @param side Frame side
     * @param v View to remove
     */
    public static void removeView(int side, View v) {
        FrameLayout frame = getFrame(side);
        if (frame != null)
            frame.removeView(v);
    }

    /**
     * Set the background color for one of the border frames
     * @param side Frame side
     * @param color Background color
     */
    public static void setBackgroundColor(int side, int color) {
        FrameLayout frame = getFrame(side);
        if (frame != null)
            frame.setBackgroundColor(color);
    }
}
