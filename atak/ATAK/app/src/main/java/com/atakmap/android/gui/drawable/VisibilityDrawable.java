
package com.atakmap.android.gui.drawable;

import com.atakmap.android.hierarchy.action.Visibility2;
import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;

/**
 * 3 state visibility icon used by Overlay Manager
 *
 * Plugins should use this class when implementing their own custom row layouts
 * in Overlay Manager
 */
public class VisibilityDrawable extends ThreeStateDrawable
        implements Visibility2 {

    public VisibilityDrawable() {
        super(MapView.getMapView().getContext(),
                R.drawable.overlay_visible,
                R.drawable.overlay_semi_visible,
                R.drawable.overlay_not_visible);
    }

    /**
     * Set the visibility state of the icon
     * @param visibility Visibility state - {@link #VISIBLE},
     * {@link #INVISIBLE}, or {@link #SEMI_VISIBLE}
     */
    public void setVisibility(int visibility) {
        setCurrentState(visibility);
    }

    @Override
    public boolean setVisible(boolean visible) {
        int oldState = getVisibility();
        int newState = visible ? ON : OFF;
        if (oldState != newState) {
            setVisibility(newState);
            return true;
        }
        return false;
    }

    @Override
    public int getVisibility() {
        return _state;
    }
}
