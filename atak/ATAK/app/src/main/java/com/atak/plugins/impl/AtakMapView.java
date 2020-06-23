
package com.atak.plugins.impl;

import android.graphics.Rect;
import android.view.View;

import com.atakmap.android.maps.MapView;

import transapps.geom.Box;
import transapps.geom.Coordinate;
import transapps.geom.Projection;
import transapps.mapi.MapController;
import transapps.mapi.OverlayManager;
import transapps.mapi.events.MapListener;

/*
 * Wrapper class for the implementation that carries the real ATAK MapView through to to plugins using
 * the transapps MapView class.     The two are not related.
 */
final public class AtakMapView implements transapps.mapi.MapView {
    public static final String TAG = "AtakMapView";

    private final MapView impl;

    /**
     * @param impl the map view implementation from ATAK.
     */
    public AtakMapView(MapView impl) {
        this.impl = impl;
    }

    @Override
    public <T extends Box> T getBoundingBox(T reuse) {
        throw new UnsupportedOperationException();
    }

    @Override
    public MapController getController() {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T extends Coordinate> T getMapCenter(T reuse) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getMaxZoomLevel() {
        throw new UnsupportedOperationException();
    }

    @Override
    public OverlayManager getOverlayManager() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Projection getProjection() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Rect getScreenRect(Rect reuse) {
        throw new UnsupportedOperationException();
    }

    @Override
    public View getView() {
        return this.impl;
    }

    @Override
    public int getZoomLevel() {
        throw new UnsupportedOperationException();
    }

    @Override
    public double getZoomLevelAsDouble() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isAnimating() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setMapListener(MapListener arg0) {
        throw new UnsupportedOperationException();
    }

}
