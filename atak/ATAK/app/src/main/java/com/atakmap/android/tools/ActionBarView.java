
package com.atakmap.android.tools;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.widget.LinearLayout;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.navigation.views.NavView;
import com.atakmap.annotations.DeprecatedApi;

/**
 * Simple View wrapper to account for ActionBarReceiver.SCALE_FACTOR in onMeasure
 * Tools should extend this in their layout XML for use in <code>ActionBarReceiver.setToolView</code>
 */
public class ActionBarView extends LinearLayout {

    private static final String TAG = "ActionBarView";

    // Toolbar positions
    public static final int TOP_LEFT = 0;
    public static final int TOP_RIGHT = 1;

    private boolean closable = true;
    private boolean closeButton = true;
    private int position = TOP_RIGHT;

    public ActionBarView(Context context) {
        super(context);
    }

    public ActionBarView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ActionBarView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        Context appContext = isInEditMode() ? getContext()
                : (MapView.getMapView() != null
                        ? MapView.getMapView().getContext()
                        : null);
        SharedPreferences prefs = appContext != null ? PreferenceManager
                .getDefaultSharedPreferences(appContext) : null;
        if (prefs == null || !prefs.getBoolean("largeActionBar", false)) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        } else {
            // Only need to do this if the view context != app context
            // and large icons is enabled
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            int dWidth = Math.round(((float) getMeasuredWidth())
                    * NavView.DEFAULT_SCALE_FACTOR);
            int dHeight = Math.round(((float) getMeasuredHeight())
                    * NavView.DEFAULT_SCALE_FACTOR);
            setMeasuredDimension(dWidth, dHeight);
        }
    }

    /**
     * If an action bar view can be closed as part of its normally supplied actons.
     * @return if the actionbar can be minimized when this tool is active  (true) or not (false)
     */
    public boolean isClosable() {
        return closable;
    }

    /**
     * If an action bar view can be closed as part of its normally supplied actons.
     * @param state if the actionbar can be minimized when this tool is active  (true) or not (false)
     */
    public void setClosable(final boolean state) {
        closable = state;
    }

    /**
     * If a close button should be supplied for this action bar
     * @return True if we should add the default close button
     */
    public boolean showCloseButton() {
        return closeButton;
    }

    /**
     * If a close button should be supplied for this action bar
     * @param state True to show the default close button, false to hide
     */
    public void showCloseButton(final boolean state) {
        closeButton = state;
    }

    /**
     * Set the desired position for this toolbar
     * @param position Toolbar position ({@link #TOP_LEFT} or {@link #TOP_RIGHT}
     */
    public void setPosition(int position) {
        this.position = position;
    }

    public int getPosition() {
        return position;
    }

    /* Deprecated embed state */

    @Deprecated
    @DeprecatedApi(since = "4.5", forRemoval = true, removeAt = "4.8")
    public static final int EMBEDDED = 0;

    @Deprecated
    @DeprecatedApi(since = "4.5", forRemoval = true, removeAt = "4.8")
    public static final int FULLSIZE = 1;

    @Deprecated
    @DeprecatedApi(since = "4.5", forRemoval = true, removeAt = "4.8")
    public static final int FLOATING = 2;

    @Deprecated
    @DeprecatedApi(since = "4.5", forRemoval = true, removeAt = "4.8")
    private int embedState = EMBEDDED;

    /**
     * @deprecated Support for this will be removed
     * For getting desired position see {@link #getPosition()}
     */
    @Deprecated
    @DeprecatedApi(since = "4.5", forRemoval = true, removeAt = "4.8")
    public boolean isEmbedded() {
        return embedState == EMBEDDED;
    }

    /**
     * @deprecated Support for this will be removed
     * For setting desired position see {@link #setPosition(int)}
     */
    @Deprecated
    @DeprecatedApi(since = "4.5", forRemoval = true, removeAt = "4.8")
    public void setEmbedded(final boolean state) {
        embedState = state ? EMBEDDED : FULLSIZE;
    }

    /**
     * @deprecated Support for this will be removed
     * For setting desired position see {@link #setPosition(int)}
     */
    @Deprecated
    @DeprecatedApi(since = "4.5", forRemoval = true, removeAt = "4.8")
    public void setEmbedState(final int state) {
        embedState = state;
    }

    /**
     * @deprecated Support for this will be removed
     * For getting desired position see {@link #getPosition()}
     */
    @Deprecated
    @DeprecatedApi(since = "4.5", forRemoval = true, removeAt = "4.8")
    public int getEmbedState() {
        return embedState;
    }

}
