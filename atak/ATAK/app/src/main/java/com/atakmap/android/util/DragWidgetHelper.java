
package com.atakmap.android.util;

import android.content.SharedPreferences;
import android.graphics.PointF;
import android.preference.PreferenceManager;
import android.view.MotionEvent;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.widgets.MapWidget;

public class DragWidgetHelper implements
        MapWidget.OnClickListener, MapWidget.OnMoveListener,
        MapWidget.OnPressListener, MapWidget.OnUnpressListener {

    private PointF pointDown = null;
    private boolean dragging;
    private final SharedPreferences prefs;
    private final String preferenceKey;
    private final float DENSITY = MapView.DENSITY;
    private final MapView mapView;
    private final MapWidget mw;

    private boolean touchEnabled = true;
    private final static String TAG = "DragWidgetHelper";

    /**
     * Helper class to allow for easier implementation of draggable widgets.   This widget should not
     * be expected to honor click events.
     * @param mapView the mapView
     * @param preferenceKey the key used to store the x and y values as part of the dragging.
     */
    public DragWidgetHelper(final MapView mapView, final String preferenceKey,
            final MapWidget mw) {
        prefs = PreferenceManager
                .getDefaultSharedPreferences(mapView.getContext());
        this.preferenceKey = preferenceKey;
        this.mapView = mapView;
        this.mw = mw;
        this.mw.addOnClickListener(this);
        this.mw.addOnPressListener(this);
        this.mw.addOnUnpressListener(this);
        this.mw.addOnMoveListener(this);

    }

    /**
     * Get the upper left of the mapWidget based on the position of the mapWidget at the current
     * orientation.  Will return the preference for the widget as it was saved in the system.
     * @return the upper left point for the widget
     */
    public PointF getUpperLeft() {
        float ulxp = prefs.getFloat(preferenceKey + ".upperLeftX.portrait",
                mw.getPointX());
        float ulyp = prefs.getFloat(preferenceKey + ".upperLeftY.portrait",
                mw.getPointY());

        float ulxl = prefs.getFloat(preferenceKey + ".upperLeftX.landscape",
                mw.getPointX());
        float ulyl = prefs.getFloat(preferenceKey + ".upperLeftY.landscape",
                mw.getPointY());

        if (mapView.isPortrait())
            return new PointF(ulxp, ulyp);
        else
            return new PointF(ulxl, ulyl);
    }

    @Override
    public void onMapWidgetClick(MapWidget widget, MotionEvent event) {
        if (dragging) {
            return;
        }

    }

    @Override
    public boolean onMapWidgetMove(MapWidget widget, MotionEvent event) {
        if (dragging || (pointDown != null &&
                Math.abs(pointDown.x - event.getX()) < 2 &&
                Math.abs(pointDown.y - event.getY()) < 2)) {
            dragging = true;

        }

        if (dragging) {
            float x = event.getX();
            float y = event.getY();

            if (mapView.isPortrait()) {
                prefs.edit().putFloat(preferenceKey + ".upperLeftX.portrait", x)
                        .apply();
                prefs.edit().putFloat(preferenceKey + ".upperLeftY.portrait", y)
                        .apply();
            } else {
                prefs.edit()
                        .putFloat(preferenceKey + ".upperLeftX.landscape", x)
                        .apply();
                prefs.edit()
                        .putFloat(preferenceKey + ".upperLeftY.landscape", y)
                        .apply();
            }

            mw.orientationChanged();
        }
        return true;
    }

    /**
     * Enable or disable the ability to touch / drag the widget.
     * @param enabled
     */
    public void setTouchEnabled(boolean enabled) {
        touchEnabled = enabled;
    }

    @Override
    public void onMapWidgetPress(MapWidget widget, MotionEvent event) {
        if (!touchEnabled)
            return;
        dragging = false;
        pointDown = new PointF(event.getX(), event.getY());
    }

    @Override
    public void onMapWidgetUnpress(MapWidget widget, MotionEvent event) {

    }

}
