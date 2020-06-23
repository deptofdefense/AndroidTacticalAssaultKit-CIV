
package com.atakmap.android.tools;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.widget.LinearLayout;

import com.atakmap.android.maps.MapView;

/**
 * Simple View wrapper to account for ActionBarReceiver.SCALE_FACTOR in onMeasure
 * Tools should extend this in their layout XML for use in <code>ActionBarReceiver.setToolView</code>
 */
public class ActionBarView extends LinearLayout {

    private static final String TAG = "ActionBarView";

    public static final int EMBEDDED = 0;
    public static final int FULLSIZE = 1;
    public static final int FLOATING = 2;

    private int embedState = EMBEDDED;
    private boolean closable = true;
    private boolean closeButton = true;

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
                    * ActionBarReceiver.SCALE_FACTOR);
            int dHeight = Math.round(((float) getMeasuredHeight())
                    * ActionBarReceiver.SCALE_FACTOR);
            setMeasuredDimension(dWidth, dHeight);
        }
    }

    /**
     * When displaying a custom action bar set of actions, the state of the actionbar can be either 
     * fullsize or embedded as part of all of the other actions.
     * @return if the actionview should be embedded (true) or fullsize (false)
     */
    public boolean isEmbedded() {
        return embedState == EMBEDDED;
    }

    /**
     * When displaying a custom action bar set of actions, the state of the actionbar can be either 
     * fullsize or embedded as part of all of the other actions.
     * @param state if the actionview should be embedded (true) or fullsize (false)
     */
    public void setEmbedded(final boolean state) {
        embedState = state ? EMBEDDED : FULLSIZE;
    }

    /**
     * Same as above but with a third option to display the view below the action bar
     * @param state Embed state (0 = embedded, 1 = full size, 2 = floating below)
     */
    public void setEmbedState(final int state) {
        embedState = state;
    }

    public int getEmbedState() {
        return embedState;
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

}
