
package com.atakmap.map;

import java.util.concurrent.ConcurrentLinkedQueue;

import android.graphics.Point;
import android.graphics.PointF;

import com.atakmap.coremap.maps.coords.GeoPoint;

/**
 * Control object for moving the ortho-graphic map around the MapView
 *
 */
public class AtakMapController {

    public static final String TAG = "MapController";

    /**************************************************************************/

    private AtakMapView _mapView;
    private ConcurrentLinkedQueue<OnFocusPointChangedListener> _focusListeners = new ConcurrentLinkedQueue<>();
    private ConcurrentLinkedQueue<OnPanRequestedListener> _panListeners = new ConcurrentLinkedQueue<OnPanRequestedListener>();


    /**
     * Listener for map control implementations
     *
     */
    public static interface OnFocusPointChangedListener {

        public void onFocusPointChanged(float x, float y);
    }

    /**
     * Listener for map pan implementations
     *
     */
    public static interface OnPanRequestedListener {

        public void onPanRequested();
    }


    AtakMapController(AtakMapView view) {
        _mapView = view;

        _mapView.globe.addOnFocusPointChangedListener(new Globe.OnFocusPointChangedListener() {
            @Override
            public void onFocusPointChanged(Globe view, float focusx, float focusy) {
                _mapView.tMapSceneModel = _mapView.renderer.getMapSceneModel(false, MapRenderer2.DisplayOrigin.UpperLeft);
                for(OnFocusPointChangedListener l : _focusListeners)
                    l.onFocusPointChanged(focusx, focusy);
            }
        });

    }

    /**
     * Add listener for map control implementations
     *
     * @param l the listener
     */
    public void addOnFocusPointChangedListener(OnFocusPointChangedListener l) {
        _focusListeners.add(l);
        l.onFocusPointChanged (getFocusX(), getFocusY());
    }

    /**
     * Removes the specified listener
     *
     * @param l The listener to remove
     */
    public void removeOnFocusPointChangedListener(OnFocusPointChangedListener l) {
        _focusListeners.remove(l);
    }

    /**
     * Add listener for map pan implementations
     * 
     * @param l the listener
     */
    public void addOnPanRequestedListener(OnPanRequestedListener l) {
        _panListeners.add(l);
    }

    /**
     * Removes the specified listener
     * 
     * @param l The listener to remove
     */
    public void removeOnPanRequestedListener(OnPanRequestedListener l) {
        _panListeners.remove(l);
    }

    /**
     * Pan the map to a specific GeoPoint centered in the MapView.  The map
     * will not pan past the extremities defined by the AtakMapView.
     * 
     * @see AtakMapView
     * @param point     The geographic coordinate to center on
     * @param animate   Pan smoothly if true; immediately if false
     */
    public void panTo (GeoPoint point,
                       boolean animate) {
        panTo(point, animate, true);
    }

    /**
     * Pan the map to a specific GeoPoint centered in the MapView.  The map
     * will not pan past the extremities defined by the AtakMapView.
     *
     * @see AtakMapView
     * @param point     The geographic coordinate to center on
     * @param animate   Pan smoothly if true; immediately if false
     * @param notify    If true, notify pan listeners
     */
    public void panTo(GeoPoint point, boolean animate, boolean notify) {
        panZoomRotateTo (point,
                _mapView.getMapScale (),
                _mapView.getMapRotation (),
                animate, notify);
    }


    /**
     * Pan the map to a specific GeoPoint centered in the MapView at the
     * specified scale.  The map will not pan past the extremities defined by
     * the AtakMapView.
     * 
     * @see AtakMapView
     * @param point     The geographic coordinate to center on
     * @param scale     The target scale of the map
     * @param animate   Pan smoothly if true; immediately if false
     */
    public void panZoomTo (GeoPoint point,
                           double scale,
                           boolean animate) {
        panZoomTo (point, scale, animate, true);
    }


    /**
     * Pan the map to a specific GeoPoint centered in the MapView at the
     * specified scale.  The map will not pan past the extremities defined by
     * the AtakMapView.
     *
     * @see AtakMapView
     * @param panTo     The geographic coordinate to center on
     * @param scale     The target scale of the map
     * @param animate   Pan smoothly if true; immediately if false
     * @param notify    If true, notify pan listeners
     */
    public void panZoomTo(GeoPoint panTo, double scale, boolean animate, boolean notify) {
        panZoomRotateTo (panTo, scale, _mapView.getMapRotation (), animate, notify);
    }


    /**
     * Pan the map to a specific GeoPoint centered in the MapView at the
     * specified scale and rotation.  The map will not pan past the extremities
     * defined by the AtakMapView.
     * 
     * @see AtakMapView
     * @param point     The geographic coordinate to center on
     * @param scale     The target scale of the map
     * @param rotation  The target rotation of the map
     * @param animate   Pan smoothly if true; immediately if false
     */
    public void panZoomRotateTo (GeoPoint point,
                                 double scale,
                                 double rotation,
                                 boolean animate) {
        panZoomRotateTo(point, scale, rotation, animate, true);
    }


    /**
     * Pan the map to a specific GeoPoint centered in the MapView at the
     * specified scale and rotation.  The map will not pan past the extremities
     * defined by the AtakMapView.
     *
     * @see AtakMapView
     * @param point     The geographic coordinate to center on
     * @param scale     The target scale of the map
     * @param rotation  The target rotation of the map
     * @param animate   Pan smoothly if true; immediately if false
     * @param notify    If true, notify pan listeners
     */
    public void panZoomRotateTo(GeoPoint point,
                                 double scale,
                                 double rotation,
                                 boolean animate,
                                 boolean notify) {
        if (point == null || !point.isValid () || Double.isNaN (scale))
            return;

        if (_mapView.getMapTilt() != 0d) {
            // XXX - Hack to fix issue with panning to the wrong spot
            // when the map is tilted - look into lower-level cause
            PointF p = _mapView.forward(point);
            panByScaleRotate(p.x - getFocusX(), p.y - getFocusY(),
                    scale, rotation, animate, notify);
            return;
        }

        if(notify)
           dispatchOnPanRequested();

        _mapView.updateView (point.getLatitude (),
                             point.getLongitude (),
                             scale,
                             rotation,
                             _mapView.getMapTilt(),
                             animate);
    }

    /**
     * Pans the map to the specified coordinate, then translates the view by the
     * number of pixels specified.  The map will not pan past the extremities
     * defined by the AtakMapView.
     * 
     * @param point     The geographic coordinate to center on
     * @param viewx     The x translation
     * @param viewy     The y translation
     * @param animate   Pan smoothly if true; immediately if false
     */
    public void panTo (GeoPoint point,
                       float viewx,
                       float viewy,
                       boolean animate) {
        if (!point.isValid ())
            return;

        panTo (point, animate);
        panBy (getFocusX() - viewx, viewy - getFocusY(), animate);
    }

    /**
     * Pan the map a given number of <I>scaled</I> pixels. The map will not pan
     * past the extremities defined by the AtakMapView.
     * 
     * @param x         Unscaled horizontal pixels to pan
     * @param y         Unscaled vertical pixels to pan
     * @param scale     The factor to scale the translation by
     * @param animate   Pan smoothly if true; immediately if false
     */
    public void panByAtScale (float x,
                              float y,
                              double scale,
                              boolean animate) {
        scale = _mapView.getMapScale () / scale;
        x *= scale;
        y *= scale;

        panBy (x, y, animate);
    }

    /**
     * Pan the map a given number of pixels.  The map will not pan past the
     * extremities defined by AtakMapView.
     * 
     * @param x         Horizontal pixels to pan
     * @param y         Vertical pixels to pan
     * @param animate   Pan smoothly if true; immediately if false
     */
    public void panBy(float x, float y, boolean animate) {
        panBy(x, y, animate, true);
    }

    /**
     * Pan the map a given number of pixels.  The map will not pan past the
     * extremities defined by AtakMapView.
     *
     * @param x         Horizontal pixels to pan
     * @param y         Vertical pixels to pan
     * @param animate   Pan smoothly if true; immediately if false
     * @param notify    If true, notify pan listeners
     */
    public void panBy(float x, float y, boolean animate, boolean notify) {
        if(notify)
            dispatchOnPanRequested();

        Globe.panBy(_mapView.globe, x, y, animate);
    }

    private void panByScaleRotate(float x, float y, double scale,
                                  double rotation, boolean animate, boolean notify) {

        if(notify)
            dispatchOnPanRequested();

        Globe.panByScaleRotate(_mapView.globe, x, y, scale, rotation, animate);
    }

    /**
     * Set the map scale (instant)
     * 
     * @param scale     The new map scale
     * @param animate   Zoom smoothly if true; immediately if false
     */
    public void zoomTo (double scale,
                        boolean animate) {
        // Don't zoom to NaN
        if (Double.isNaN(scale))
            return;

        Globe.zoomTo(_mapView.globe, scale, animate);
    }

    /**
     * Returns the current focus point of the map; defaults to the center.
     * 
     * @return The current focus point of the map.
     */
    public Point getFocusPoint() {
        final float focusX;
        final float focusY;
        _mapView.globe.rwlock.acquireRead();
        try {
            if(_mapView.globe.pointer.raw == 0L)
                return new Point(0, 0);
            focusX = Globe.getFocusPointX(_mapView.globe.pointer.raw);
            focusY = Globe.getFocusPointY(_mapView.globe.pointer.raw);
        } finally {
            _mapView.globe.rwlock.releaseRead();
        }
        return new Point ((int)focusX, (int)focusY);
    }

    /**
     * Returns the x-coordinate of the current focus.
     * 
     * @return The x-coordinate of the current focus.
     */
    public float getFocusX() {
        _mapView.globe.rwlock.acquireRead();
        try {
            if(_mapView.globe.pointer.raw == 0L)
                return 0f;
            return Globe.getFocusPointX(_mapView.globe.pointer.raw);
        } finally {
            _mapView.globe.rwlock.releaseRead();
        }
    }

    /**
     * Returns the y-coordinate of the current focus.
     * 
     * @return The y-coordinate of the current focus.
     */
    public float getFocusY() {
        _mapView.globe.rwlock.acquireRead();
        try {
            if(_mapView.globe.pointer.raw == 0L)
                return 0f;
            return Globe.getFocusPointY(_mapView.globe.pointer.raw);
        } finally {
            _mapView.globe.rwlock.releaseRead();
        }
    }

    /**
     * Modifies the scale of the map and translates by the specified number of
     * pixels.  The translation offsets are specified at the <I>current</I> map
     * scale.
     * 
     * @param scaleFactor       The ratio to apply to the map scale.
     * @param xpos              The x translation (at the current scale)
     * @param ypos              The y translation (at the current scale)
     * @param animate           Pan smoothly if true; immediately if false
     */
    public void zoomBy (double scaleFactor,
                        float xpos,
                        float ypos,
                        boolean animate) {

        Globe.zoomBy(_mapView.globe, scaleFactor, xpos, ypos, animate);
    }
    
    

    /**
     * Sets the map to the specified rotation (animate)
     * 
     * @param rotation  The new rotation of the map
     * @param animate   Rotate smoothly if true; immediately if false
     */
    public void rotateTo (double rotation,
                          boolean animate) {

        Globe.rotateTo(_mapView.globe, rotation, animate);
    }
    
    public void rotateBy (double theta,
                          float xpos,
                          float ypos,
                          boolean animate) {

        Globe.rotateBy(_mapView.globe, theta, xpos, ypos, animate);
    }

    public void tiltTo(double tilt,
                       boolean animate) {

        Globe.tiltTo(_mapView.globe, tilt, animate);
    }

    public void tiltTo(double tilt,
                       double rotation,
                       boolean animate) {

        _mapView.globe.rwlock.acquireRead();
        try {
            if(_mapView.globe.pointer.raw == 0L)
                return;
            Globe.tiltTo(_mapView.globe.pointer.raw, tilt, rotation, animate);
        } finally {
            _mapView.globe.rwlock.releaseRead();
        }
    }
    
    public void tiltBy(double tilt,
                       float xpos,
                       float ypos,
                       boolean animate) {

        Globe.tiltBy(_mapView.globe, tilt, xpos, ypos, animate);
    }

    public void tiltBy(double tilt,
                       GeoPoint focus,
                       boolean animate) {

        Globe.tiltBy(_mapView.globe, tilt, focus, animate);
    }

    public void rotateBy(double theta,
                       GeoPoint focus,
                       boolean animate) {

        Globe.rotateBy(_mapView.globe, theta, focus, animate);
    }

    public void updateBy(double scale,
                         double rotation,
                         double tilt,
                         float xpos,
                         float ypos,
                         boolean animate) {

        _mapView.globe.rwlock.acquireRead();
        try {
            if(_mapView.globe.pointer.raw == 0L)
                return;
            Globe.updateBy(_mapView.globe.pointer.raw, scale, rotation, tilt, xpos, ypos, animate);
        } finally {
            _mapView.globe.rwlock.releaseRead();
        }
    }

    public void updateBy(double scale,
                         double rotation,
                         double tilt,
                         GeoPoint pos,
                         boolean animate) {

        if (pos == null) 
            return;

        _mapView.globe.rwlock.acquireRead();
        try {
            if(_mapView.globe.pointer.raw == 0L)
                return;
            Globe.updateBy(_mapView.globe.pointer.raw, scale, rotation, tilt, pos.getLatitude(), pos.getLongitude(), pos.getAltitude(), Globe.isHae(pos), animate);
        } finally {
            _mapView.globe.rwlock.releaseRead();
        }
    }

    /**************************************************************************/


    protected void dispatchOnPanRequested() {
        for (OnPanRequestedListener l : _panListeners) {
            l.onPanRequested();
        }
    }
}
