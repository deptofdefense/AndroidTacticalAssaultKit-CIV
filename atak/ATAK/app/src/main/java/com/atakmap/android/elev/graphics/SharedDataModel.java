
package com.atakmap.android.elev.graphics;

import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;

public class SharedDataModel {

    public final static String HIDE = MapView.getMapView().getContext()
            .getString(R.string.hide_caps);
    public final static String RELATIVE = MapView.getMapView().getContext()
            .getString(R.string.relative);
    public final static String ABSOLUTE = MapView.getMapView().getContext()
            .getString(R.string.abs);
    public final static String VISIBLE = MapView.getMapView().getContext()
            .getString(R.string.visible);
    public final static String GRADIENT_ABSOLUTE = MapView.getMapView()
            .getContext()
            .getString(R.string.abs_gradient);
    public final static String GRADIENT_RELATIVE = MapView.getMapView()
            .getContext()
            .getString(R.string.rel_gradient);

    public static String next(String curr) {
        // yes, I know, this is super bad impl, but its better than what it replaces.
        if (curr.equals(HIDE))
            return RELATIVE;
        else
            return HIDE;
    }

    // ///////////////////////////////////////////////////
    // Display configuration
    //
    public String isoDisplayMode = RELATIVE;

    public final static boolean isoCalculating = false;

    // scale generation
    public final static int isoScaleMarks = 20;
    public final static int isoScaleStart = 5; // increments between altitude iso lines
    public final static int isoScaleIncrements = 25; // increments between altitude iso lines

    public double minHeat = isoScaleStart;
    public double maxHeat = (isoScaleMarks * isoScaleIncrements)
            + isoScaleStart;

    public final static int isoProgress = 0;

    private SharedDataModel() {
    }

    static private SharedDataModel instance;

    static public synchronized SharedDataModel getInstance() {
        if (instance == null) {
            instance = new SharedDataModel();
        }

        return instance;
    }
}
