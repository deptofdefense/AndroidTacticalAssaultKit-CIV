
package com.atakmap.android.gui.drawable;

import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;

/**
 * 3 state checkbox icon used by Overlay Manager
 *
 * Plugins should use this class when implementing their own custom row layouts
 * in Overlay Manager
 */
public class CheckBoxDrawable extends ThreeStateDrawable {

    public static final int CHECKED = ON;
    public static final int SEMI_CHECKED = SEMI;
    public static final int UNCHECKED = OFF;

    public CheckBoxDrawable() {
        super(MapView.getMapView().getContext(),
                R.drawable.btn_check_on,
                R.drawable.btn_check_semi,
                R.drawable.btn_check_off);
    }

    /**
     * Set the checked state of the icon
     * @param checked Check state - {@link #CHECKED}, {@link #UNCHECKED},
     *               or {@link #SEMI_CHECKED}
     */
    public void setChecked(int checked) {
        setCurrentState(checked);
    }

    /**
     * Set the checked state of the icon
     * @param checked True if checked, false if unchecked
     */
    public void setChecked(boolean checked) {
        setChecked(checked ? CHECKED : UNCHECKED);
    }

    /**
     * Get the check state of the icon
     * @return {@link #CHECKED}, {@link #UNCHECKED}, or {@link #SEMI_CHECKED}
     */
    public int getChecked() {
        return _state;
    }
}
