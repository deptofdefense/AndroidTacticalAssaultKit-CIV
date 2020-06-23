
package com.atakmap.map;

import java.util.Stack;
import java.util.concurrent.ConcurrentLinkedQueue;

import android.graphics.Point;
import android.graphics.PointF;

import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoCalculations;

import com.atakmap.map.projection.Projection;
import com.atakmap.math.MathUtils;
import com.atakmap.math.PointD;

/**
 * Control object for moving the ortho-graphic map around the MapView
 */
public class AtakMapController {

    public static final String TAG = "MapController";




    /**************************************************************************/

    private AtakMapView _mapView;
    private ConcurrentLinkedQueue<OnFocusPointChangedListener> _focusListeners = new ConcurrentLinkedQueue<OnFocusPointChangedListener>();
    private ConcurrentLinkedQueue<OnPanRequestedListener> _panListeners = new ConcurrentLinkedQueue<OnPanRequestedListener>();
    private Stack<Point> _focusPointQueue = new Stack<Point>();
    private Point _defaultFocusPoint = new Point(0, 0);

    /**
     * Listener for map control implementations
     */
    public static interface OnFocusPointChangedListener {

        public void onFocusPointChanged(float x, float y);
    }

    /**
     * Listener for map pan implementations
     */
    public static interface OnPanRequestedListener {

        public void onPanRequested();
    }


    AtakMapController(AtakMapView view) {
        _mapView = view;
    }

    /**
     * Add listener for map control implementations
     *
     * @param l the listener
     */
    public void addOnFocusPointChangedListener(OnFocusPointChangedListener l) {
        _focusListeners.add(l);

        Point focusPoint = getFocusPointInternal ();

        l.onFocusPointChanged (focusPoint.x, focusPoint.y);
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

        Point focusPoint = getFocusPointInternal ();

        panTo (point, animate);
        panBy (focusPoint.x - viewx, viewy - focusPoint.y, animate);
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
        panByScaleRotate(x, y, _mapView.getMapScale(),
                _mapView.getMapRotation(), animate, notify);
    }

    private void panByScaleRotate(float x, float y, double scale,
                                  double rotation, boolean animate) {
        panByScaleRotate(x, y, scale, rotation, animate, true);
    }

    private void panByScaleRotate(float x, float y, double scale,
                                  double rotation, boolean animate, boolean notify) {
        Point focusPoint = getFocusPointInternal ();

        MapSceneModel sm = new MapSceneModel(_mapView,
                _mapView.getProjection(), _mapView.getPoint().get(),
                focusPoint.x, focusPoint.y,
                _mapView.getMapRotation(), _mapView.getMapTilt(),
                _mapView.getMapScale(),
                _mapView.isContinuousScrollEnabled());
        GeoPoint tgt = GeoPoint.createMutable();
        sm.inverse (new PointF(focusPoint.x + x, focusPoint.y + y), tgt);

        if(_mapView.isContinuousScrollEnabled()) {
            if(tgt.getLongitude() < -180d)
                tgt.set(tgt.getLatitude(), tgt.getLongitude()+360d, tgt.getAltitude(), tgt.getAltitudeReference(), tgt.getCE(), tgt.getLE());
            else if(tgt.getLongitude() > 180d)
                tgt.set(tgt.getLatitude(), tgt.getLongitude()-360d, tgt.getAltitude(), tgt.getAltitudeReference(), tgt.getCE(), tgt.getLE());
        }

        if (!tgt.isValid())
            return;

        if(notify)
            dispatchOnPanRequested();

        _mapView.updateView (tgt.getLatitude (),
                tgt.getLongitude (),
                scale, rotation,
                _mapView.getMapTilt(),
                animate);
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

        _mapView.updateView (_mapView.getLatitude (),
                             _mapView.getLongitude (),
                             scale,
                             _mapView.getMapRotation (),
                             _mapView.getMapTilt(),
                             animate);
    }

    void setDefaultFocusPoint(int offx, int offy) {
        final boolean needToDispatchFocus;
        synchronized (_focusPointQueue) {
            if(_defaultFocusPoint.x != offx || _defaultFocusPoint.y != offy) {
                _defaultFocusPoint.x = offx;
                _defaultFocusPoint.y = offy;
                Log.d(TAG, "*** set default focus: " + offx + "," + offy);
                needToDispatchFocus = (_focusPointQueue.size() < 1);
            } else {
                needToDispatchFocus = false;
            }
        }
        if (needToDispatchFocus)
            dispatchOnFocusPointChanged(offx, offy);
    }

    /**
     * Returns the current focus point of the map; defaults to the center.
     * 
     * @return The current focus point of the map.
     */
    public Point getFocusPoint() {
        return new Point (getFocusPointInternal ());
    }

    /**
     * Returns the x-coordinate of the current focus.
     * 
     * @return The x-coordinate of the current focus.
     */
    public float getFocusX() {
        return getFocusPointInternal ().x;
    }

    /**
     * Returns the y-coordinate of the current focus.
     * 
     * @return The y-coordinate of the current focus.
     */
    public float getFocusY() {
        return getFocusPointInternal ().y;
    }

    private
    Point
    getFocusPointInternal ()
      {
        synchronized (_focusPointQueue)
          {
            return _focusPointQueue.isEmpty ()
                ? _defaultFocusPoint
                : _focusPointQueue.peek ();
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
        
        double newScale = _mapView.getMapScale () * scaleFactor;
        
        // Don't zoom to NaN
        if (Double.isNaN (newScale))
            return;

        newScale = MathUtils.clamp(newScale,
                                   _mapView.getMinMapScale (),
                                   _mapView.getMaxMapScale ());

        this.updateBy(newScale,
                      _mapView.getMapRotation(),
                      _mapView.getMapTilt(),
                      xpos, ypos,
                      animate);
    }
    
    

    /**
     * Sets the map to the specified rotation (animate)
     * 
     * @param rotation  The new rotation of the map
     * @param animate   Rotate smoothly if true; immediately if false
     */
    public void rotateTo (double rotation,
                          boolean animate) {
        _mapView.updateView (_mapView.getLatitude (),
                             _mapView.getLongitude (),
                             _mapView.getMapScale (),
                             rotation,
                             _mapView.getMapTilt(),
                             animate);
    }
    
    public void rotateBy (double theta,
                          float xpos,
                          float ypos,
                          boolean animate) {

        double newRotation = _mapView.getMapRotation () + theta;
        
        // Don't zoom to NaN
        if (Double.isNaN (newRotation))
            return;
        
        this.updateBy(_mapView.getMapScale(),
                      newRotation,
                      _mapView.getMapTilt(),
                      xpos, ypos,
                      animate);
    }

    public void tiltTo(double tilt,
                       boolean animate) {
        _mapView.updateView (_mapView.getLatitude (),
                _mapView.getLongitude (),
                _mapView.getMapScale (),
                _mapView.getMapRotation(),
                tilt,
                animate);
    }

    public void tiltTo(double tilt,
                       double rotation,
                       boolean animate) {
        _mapView.updateView (_mapView.getLatitude (),
                             _mapView.getLongitude (),
                             _mapView.getMapScale (),
                             rotation,
                             tilt,
                             animate);
    }
    
    public void tiltBy(double tilt,
                       float xpos,
                       float ypos,
                       boolean animate) {

        double newTilt = _mapView.getMapTilt () + tilt;
        
        // Don't zoom to NaN
        if (Double.isNaN (newTilt))
            return;
        
        this.updateBy(_mapView.getMapScale(),
                      _mapView.getMapRotation(),
                      newTilt,
                      xpos, ypos,
                      animate);

    }

    public void tiltBy(double tilt,
                       GeoPoint focus,
                       boolean animate) {

        double newTilt = _mapView.getMapTilt () + tilt;

        // Don't zoom to NaN
        if (Double.isNaN (newTilt))
            return;

        Point focusPoint = getFocusPointInternal();

        MapSceneModel nadirSurface = new MapSceneModel(AtakMapView.DENSITY*240f,
                                                       _mapView.getWidth(), _mapView.getHeight(),
                                                       _mapView.getProjection(),
                                                       new GeoPoint(focus.getLatitude(), focus.getLongitude()),
                                                       focusPoint.x, focusPoint.y,
                                                       0d,
                                                       0d,
                                                       _mapView.getMapResolution(),
                                                       _mapView.isContinuousScrollEnabled());

        // compute cam->tgt range
        double rangeSurface = MathUtils.distance(
                nadirSurface.camera.location.x, nadirSurface.camera.location.y, nadirSurface.camera.location.z,
                nadirSurface.camera.target.x, nadirSurface.camera.target.y, nadirSurface.camera.target.z
        );


        PointD point2Proj = nadirSurface.mapProjection.forward(focus, null);

        double rangeElevated = MathUtils.distance(
                nadirSurface.camera.location.x, nadirSurface.camera.location.y, nadirSurface.camera.location.z,
                point2Proj.x, point2Proj.y, point2Proj.z
        );

        // scale resolution by cam->'point' distance
        final double resolutionAtElevated = _mapView.getMapResolution() * (rangeSurface / rangeElevated);

        // construct model to 'point' at altitude with scaled resolution with rotate/tilt
        MapSceneModel scene = new MapSceneModel(AtakMapView.DENSITY*240,
                _mapView.getWidth(),
                _mapView.getHeight(),
                _mapView.getProjection(),
                focus,
                focusPoint.x,
                focusPoint.y,
                _mapView.getMapRotation(),
                newTilt,
                resolutionAtElevated,
                _mapView.isContinuousScrollEnabled()
        );

        // obtain new center
        GeoPoint focusGeo2 =  scene.inverse(new PointF(focusPoint.x, focusPoint.y), null);
        if(focusGeo2 == null) {
            Log.w(TAG, "Unable to compute new tilt center: focus=" + focus + " new tilt=" + newTilt);
            return;
        }

        // obtain new tilt
        //double mapTilt = scene.camera.elevation + 90;
        double mapTilt = newTilt;

        // obtain new rotation
        //double mapRotation = scene.camera.azimuth;
        double mapRotation = _mapView.getMapRotation();

        PointD focusGeo2Proj = scene.mapProjection.forward(focusGeo2, null);



        //double terminalSlant = MathUtils.distance(
        //        scene.camera.target.x, scene.camera.target.y, scene.camera.target.z,
        //        focusGeo2Proj.x, focusGeo2Proj.y, focusGeo2Proj.z);
      
        _mapView.updateView(
                focusGeo2.getLatitude(),
                focusGeo2.getLongitude(),
                _mapView.getMapScale(),
                mapRotation,
                mapTilt,
                animate);

    }

    public void rotateBy(double tilt,
                       GeoPoint focus,
                       boolean animate) {

        double newRot = _mapView.getMapRotation () + tilt;

        // Don't zoom to NaN
        if (Double.isNaN (newRot))
            return;

        Point focusPoint = getFocusPointInternal();

        MapSceneModel nadirSurface = new MapSceneModel(AtakMapView.DENSITY*240f,
                _mapView.getWidth(), _mapView.getHeight(),
                _mapView.getProjection(),
                new GeoPoint(focus.getLatitude(), focus.getLongitude()),
                focusPoint.x, focusPoint.y,
                0d,
                0d,
                _mapView.getMapResolution(),
                _mapView.isContinuousScrollEnabled());

        // compute cam->tgt range
        double rangeSurface = MathUtils.distance(
                nadirSurface.camera.location.x, nadirSurface.camera.location.y, nadirSurface.camera.location.z,
                nadirSurface.camera.target.x, nadirSurface.camera.target.y, nadirSurface.camera.target.z
        );


        PointD point2Proj = nadirSurface.mapProjection.forward(focus, null);

        double rangeElevated = MathUtils.distance(
                nadirSurface.camera.location.x, nadirSurface.camera.location.y, nadirSurface.camera.location.z,
                point2Proj.x, point2Proj.y, point2Proj.z
        );

        // scale resolution by cam->'point' distance
        final double resolutionAtElevated = _mapView.getMapResolution() * (rangeSurface / rangeElevated);

        PointF focusBy = _mapView.forward(focus);

        // construct model to 'point' at altitude with scaled resolution with rotate/tilt
        MapSceneModel scene = new MapSceneModel(AtakMapView.DENSITY*240,
                _mapView.getWidth(),
                _mapView.getHeight(),
                _mapView.getProjection(),
                focus,
                focusPoint.x,
                focusPoint.y,
                newRot,
                _mapView.getMapTilt(),
                resolutionAtElevated,
                _mapView.isContinuousScrollEnabled()
        );


        // obtain new center
        GeoPoint focusGeo2 =  scene.inverse(new PointF(focusPoint.x, focusPoint.y), null);
        if(focusGeo2 == null) {
            Log.w(TAG, "Unable to compute new rotation center: focus=" + focus + " new rotation=" + newRot);
            return;
        }
/*
        updateBy(_mapView.getMapScale(),
                newRot,
                _mapView.getMapTilt(),
                focusGeo2,
                animate);
*/
//        /*
        // obtain new tilt
        //double mapTilt = scene.camera.elevation + 90;
        double mapTilt = _mapView.getMapTilt();

        // obtain new rotation
        //double mapRotation = scene.camera.azimuth;
        double mapRotation = newRot;

        PointD focusGeo2Proj = scene.mapProjection.forward(focusGeo2, null);

        //double terminalSlant = MathUtils.distance(
        //        scene.camera.target.x, scene.camera.target.y, scene.camera.target.z,
        //        focusGeo2Proj.x, focusGeo2Proj.y, focusGeo2Proj.z);

        _mapView.updateView(
                focusGeo2.getLatitude(),
                focusGeo2.getLongitude(),
                _mapView.getMapScale(),
                mapRotation,
                mapTilt,
                animate);
// */
    }

    public void updateBy(double scale,
                         double rotation,
                         double tilt,
                         float xpos,
                         float ypos,
                         boolean animate) {
        
        GeoPoint mapCenter = _mapView.getPoint().get();
        Point focusPoint = getFocusPointInternal ();
        
        final Projection proj = _mapView.getProjection();
        
        MapSceneModel sm = new MapSceneModel(_mapView,
                proj, mapCenter,
                focusPoint.x, focusPoint.y,
                rotation, tilt,
                scale, _mapView.isContinuousScrollEnabled());
        
        GeoPoint focusLatLng = _mapView.inverse (xpos, ypos,
                AtakMapView.InverseMode.Model).get();
        GeoPoint focusLatLng2 = sm.inverse(new PointF(xpos, ypos), null, true);

        if (focusLatLng != null && focusLatLng2 != null) {
            double focusLng2 = focusLatLng2.getLongitude();
            if (_mapView.isContinuousScrollEnabled())
                focusLng2 = GeoCalculations.wrapLongitude(focusLng2);
            double latDiff = focusLatLng2.getLatitude() - focusLatLng.getLatitude();
            double lonDiff = focusLng2 - focusLatLng.getLongitude();
            double lng = mapCenter.getLongitude() - lonDiff;
            if (_mapView.isContinuousScrollEnabled())
                lng = GeoCalculations.wrapLongitude(lng);

            _mapView.updateView (mapCenter.getLatitude() - latDiff,
                    lng, scale, rotation, tilt, animate);
        }
    }

    public void updateBy(double scale,
                         double rotation,
                         double tilt,
                         GeoPoint pos,
                         boolean animate) {

        if (pos == null) 
            return;

        GeoPoint mapCenter = _mapView.getPoint().get();
        Point focusPoint = getFocusPointInternal();

        final Projection proj = _mapView.getProjection();

        MapSceneModel sm = new MapSceneModel(_mapView,
                proj, mapCenter,
                focusPoint.x, focusPoint.y,
                rotation, tilt,
                scale, _mapView.isContinuousScrollEnabled());

        GeoPoint focusLatLng = pos;
        PointF xypos = _mapView.forward(focusLatLng);
        GeoPoint focusLatLng2 = sm.inverse(xypos, null, true);

        if (focusLatLng != null && focusLatLng2 != null) {
            double focusLng2 = focusLatLng2.getLongitude();
            if (_mapView.isContinuousScrollEnabled())
                focusLng2 = GeoCalculations.wrapLongitude(focusLng2);
            double latDiff = focusLatLng2.getLatitude() - focusLatLng.getLatitude();
            double lonDiff = focusLng2 - focusLatLng.getLongitude();
            double lng = mapCenter.getLongitude() - lonDiff;
            if (_mapView.isContinuousScrollEnabled())
                lng = GeoCalculations.wrapLongitude(lng);

            _mapView.updateView (mapCenter.getLatitude() - latDiff,
                    lng, scale, rotation, tilt, animate);
        }
    }

    /**************************************************************************/

    protected void dispatchOnFocusPointChanged(float x, float y) {
        for (OnFocusPointChangedListener l : _focusListeners) {
            l.onFocusPointChanged(x, y);
        }
    }

    protected void dispatchOnPanRequested() {
        for (OnPanRequestedListener l : _panListeners) {
            l.onPanRequested();
        }
    }


}
