
package com.atakmap.map;

import java.util.concurrent.ConcurrentLinkedQueue;

import android.graphics.Point;
import android.graphics.PointF;

import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.elevation.ElevationManager;
import com.atakmap.map.layer.control.SurfaceRendererControl;
import com.atakmap.math.PointD;

import gov.tak.api.annotation.DontObfuscate;

/**
 * Control object for moving the ortho-graphic map around the MapView
 *
 * @deprecated  use {@link CameraController}
 */
@Deprecated
@DeprecatedApi(since = "4.2", forRemoval = true, removeAt = "4.5")
@DontObfuscate
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
        if(notify)
            dispatchOnPanRequested();
        CameraController.Programmatic.panTo(_mapView.getRenderer3(), point, animate);
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

        if(notify)
           dispatchOnPanRequested();

        double gsd = Globe.getMapResolution(_mapView.getDisplayDpi(), scale);

        // apply some minimum camera range
        final double alt = point.getAltitude();
        final double localElevation = ElevationManager.getElevation(point.getLatitude(),
                        point.getLongitude(), null);

        if(!Double.isNaN(alt) && (Double.isNaN(localElevation) || alt > localElevation)) {
            double minOffset = 25d;
            SurfaceRendererControl ctrl = _mapView.getRenderer3().getControl(SurfaceRendererControl.class);
            if(ctrl != null) {
                double r = ctrl.getCameraCollisionRadius();
                if(r > 0d && r < minOffset)
                    minOffset = r;
            }

            final double maxGsd = MapSceneModel.gsd(alt + minOffset, _mapView.tMapSceneModel.camera.fov, _mapView.getHeight());
            if(gsd < maxGsd)
                gsd = maxGsd;
        }

        _mapView.getRenderer3().lookAt(point, gsd, rotation, _mapView.getMapTilt(), animate);
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

        CameraController.Interactive.panBy(_mapView.getRenderer3(), x, y, MapRenderer3.CameraCollision.AdjustFocus, animate);
    }

    private void panByScaleRotate(float x, float y, double scale,
                                  double rotation, boolean animate, boolean notify) {

        if(notify)
            dispatchOnPanRequested();

        // XXX - attempt to keep pixel at location xpos,ypos in current scene
        //       at same location in new scene, post-zoom
        final MapRenderer3 renderer = _mapView.getRenderer3();
        final MapSceneModel sm = renderer.getMapSceneModel(false, MapRenderer3.DisplayOrigin.UpperLeft);
        // if globe is not tilted, we can ignore the more espensive hit tests
        final int inverseHints = (sm.camera.elevation > -90d) ? MapRenderer3.HINT_RAYCAST_IGNORE_SURFACE_MESH|MapRenderer3.HINT_RAYCAST_IGNORE_TERRAIN_MESH : 0;
        GeoPoint result = GeoPoint.createMutable();
        if(renderer.inverse(new PointD(x, y), result, MapRenderer3.InverseMode.RayCast, inverseHints, MapRenderer3.DisplayOrigin.UpperLeft) != MapRenderer3.InverseResult.None) {
            // we got a hit, zoom/rotate about point
            final MapRenderer3.CameraCollision collide = MapRenderer3.CameraCollision.AdjustFocus;
            CameraController.Interactive.rotateTo(renderer, rotation, result, sm.focusx+x, sm.focusy+y, collide, animate);
            CameraController.Interactive.zoomTo(renderer, Globe.getMapResolution(renderer.getRenderContext().getRenderSurface().getDpi(), scale), result, sm.focusx+x, sm.focusy+y, collide, animate);
        } else {
            // no hit, zoom/rotate on current position
            CameraController.Programmatic.rotateTo(renderer, rotation, animate);
            CameraController.Programmatic.zoomTo(renderer, Globe.getMapResolution(renderer.getRenderContext().getRenderSurface().getDpi(), scale), animate);
        }
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

        CameraController.Programmatic.zoomTo(_mapView.getRenderer3(), Globe.getMapResolution(_mapView.getDisplayDpi(), scale), animate);
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

        // XXX - attempt to keep pixel at location xpos,ypos in current scene
        //       at same location in new scene, post-zoom
        final MapRenderer3 renderer = _mapView.getRenderer3();
        final MapSceneModel sm = renderer.getMapSceneModel(false, MapRenderer3.DisplayOrigin.UpperLeft);
        // if globe is not tilted, we can ignore the more expensive hit tests
        final int inverseHints = (sm.camera.elevation > -90d) ? MapRenderer3.HINT_RAYCAST_IGNORE_SURFACE_MESH|MapRenderer3.HINT_RAYCAST_IGNORE_TERRAIN_MESH : 0;
        GeoPoint result = GeoPoint.createMutable();
        MapRenderer3.CameraCollision collide = (scaleFactor > 1d) ? MapRenderer3.CameraCollision.Abort : MapRenderer3.CameraCollision.AdjustFocus;
        if(renderer.inverse(new PointD(xpos, ypos), result, MapRenderer3.InverseMode.RayCast, inverseHints, MapRenderer3.DisplayOrigin.UpperLeft) != MapRenderer3.InverseResult.None) {
            // we got a hit, rotate about point
            CameraController.Interactive.zoomBy(renderer, scaleFactor, result, xpos, ypos, collide, animate);
        } else {
            // no hit, apply rotation on current position
            CameraController.Interactive.zoomBy(renderer, scaleFactor, collide, animate);
        }
    }
    
    

    /**
     * Sets the map to the specified rotation (animate)
     * 
     * @param rotation  The new rotation of the map
     * @param animate   Rotate smoothly if true; immediately if false
     */
    public void rotateTo (double rotation,
                          boolean animate) {

        CameraController.Programmatic.rotateTo(_mapView.getRenderer3(), rotation, animate);
    }
    
    public void rotateBy (double theta,
                          float xpos,
                          float ypos,
                          boolean animate) {

        // XXX - attempt to keep pixel at location xpos,ypos in current scene
        //       at same location in new scene, post-rotate
        final MapRenderer3 renderer = _mapView.getRenderer3();
        final MapSceneModel sm = renderer.getMapSceneModel(false, MapRenderer3.DisplayOrigin.UpperLeft);
        // if globe is not tilted, we can ignore the more expensive hit tests
        final int inverseHints = (sm.camera.elevation > -90d) ? MapRenderer3.HINT_RAYCAST_IGNORE_SURFACE_MESH|MapRenderer3.HINT_RAYCAST_IGNORE_TERRAIN_MESH : 0;
        GeoPoint result = GeoPoint.createMutable();
        if(renderer.inverse(new PointD(xpos, ypos), result, MapRenderer3.InverseMode.RayCast, inverseHints, MapRenderer3.DisplayOrigin.UpperLeft) != MapRenderer3.InverseResult.None) {
            // we got a hit, rotate about point
            CameraController.Interactive.rotateTo(renderer, theta+sm.camera.azimuth, result, xpos, ypos, MapRenderer3.CameraCollision.AdjustFocus, animate);
        } else {
            // no hit, apply rotation on current position
            CameraController.Programmatic.rotateTo(renderer, theta+sm.camera.azimuth, animate);
        }
    }

    public void tiltTo(double tilt,
                       boolean animate) {

        CameraController.Programmatic.tiltTo(_mapView.getRenderer3(), tilt, animate);
    }

    public void tiltTo(double tilt,
                       double rotation,
                       boolean animate) {

        final MapRenderer3 renderer = _mapView.getRenderer3();
        final MapSceneModel sm = renderer.getMapSceneModel(false, MapRenderer3.DisplayOrigin.UpperLeft);
        renderer.lookAt(sm.mapProjection.inverse(sm.camera.target, null),
                        sm.gsd,
                        rotation,
                        tilt,
                        animate);
    }
    
    public void tiltBy(double tilt,
                       float xpos,
                       float ypos,
                       boolean animate) {

        // XXX - attempt to keep pixel at location xpos,ypos in current scene
        //       at same location in new scene, post-tilt
        final MapRenderer3 renderer = _mapView.getRenderer3();
        final MapSceneModel sm = renderer.getMapSceneModel(false, MapRenderer3.DisplayOrigin.UpperLeft);
        GeoPoint result = GeoPoint.createMutable();
        if(renderer.inverse(new PointD(xpos, ypos), result, MapRenderer3.InverseMode.RayCast, 0, MapRenderer3.DisplayOrigin.UpperLeft) != MapRenderer3.InverseResult.None) {
            // we got a hit, tilt about point
            CameraController.Interactive.tiltBy(renderer, tilt, result, xpos, ypos, MapRenderer3.CameraCollision.AdjustFocus, animate);
        } else {
            // no hit, apply tilt on current position
            CameraController.Programmatic.tiltTo(renderer, tilt+(90d+sm.camera.elevation), animate);
        }
    }

    public void tiltBy(double tilt,
                       GeoPoint focus,
                       boolean animate) {

        MapRenderer3.CameraCollision collide = (tilt > 0d) ? MapRenderer3.CameraCollision.Abort : MapRenderer3.CameraCollision.AdjustFocus;
        CameraController.Interactive.tiltBy(_mapView.getRenderer3(), tilt, focus, collide, animate);
    }

    public void rotateBy(double theta,
                       GeoPoint focus,
                       boolean animate) {

        CameraController.Interactive.rotateBy(_mapView.getRenderer3(), theta, focus, MapRenderer3.CameraCollision.AdjustFocus, animate);
    }

    public void updateBy(double scale,
                         double rotation,
                         double tilt,
                         float xpos,
                         float ypos,
                         boolean animate) {

        // XXX - attempt to keep pixel at location xpos,ypos in current scene
        //       at same location in new scene, post-tilt
        final MapRenderer3 renderer = _mapView.getRenderer3();
        final MapSceneModel sm = renderer.getMapSceneModel(false, MapRenderer3.DisplayOrigin.UpperLeft);
        GeoPoint result = GeoPoint.createMutable();
        if(renderer.inverse(new PointD(xpos, ypos), result, MapRenderer3.InverseMode.RayCast, 0, MapRenderer3.DisplayOrigin.UpperLeft) != MapRenderer3.InverseResult.None) {
            // we got a hit, tilt about point
            CameraController.Interactive.tiltTo(renderer, tilt, result, xpos, ypos, MapRenderer3.CameraCollision.AdjustFocus, animate);
            CameraController.Interactive.rotateTo(renderer, rotation, result, xpos, ypos, MapRenderer3.CameraCollision.AdjustFocus, animate);
            CameraController.Interactive.zoomTo(renderer, Globe.getMapResolution(renderer.getRenderContext().getRenderSurface().getDpi(), scale), result, xpos, ypos, MapRenderer3.CameraCollision.AdjustFocus, animate);
        } else {
            // no hit, apply tilt on current position
            CameraController.Programmatic.tiltTo(renderer, tilt, animate);
            CameraController.Programmatic.rotateTo(renderer, rotation, animate);
            CameraController.Programmatic.zoomTo(renderer, Globe.getMapResolution(renderer.getRenderContext().getRenderSurface().getDpi(), scale), animate);
        }
    }

    public void updateBy(double scale,
                         double rotation,
                         double tilt,
                         GeoPoint pos,
                         boolean animate) {

        if (pos == null) 
            return;

        // XXX - attempt to keep pixel at location xpos,ypos in current scene
        //       at same location in new scene, post-update
        final MapRenderer3 renderer = _mapView.getRenderer3();
        final MapSceneModel sm = renderer.getMapSceneModel(false, MapRenderer3.DisplayOrigin.UpperLeft);
        final PointF relativeFocusXY = sm.forward(pos, (PointF)null);

        CameraController.Interactive.tiltTo(renderer, tilt, pos, relativeFocusXY.x, relativeFocusXY.y, MapRenderer3.CameraCollision.AdjustFocus, animate);
        CameraController.Interactive.rotateTo(renderer, rotation, pos, relativeFocusXY.x, relativeFocusXY.y, MapRenderer3.CameraCollision.AdjustFocus, animate);
        CameraController.Interactive.zoomTo(renderer, Globe.getMapResolution(renderer.getRenderContext().getRenderSurface().getDpi(), scale ), pos, relativeFocusXY.x, relativeFocusXY.y, MapRenderer3.CameraCollision.AdjustFocus, animate);
    }

    /**************************************************************************/


    public void dispatchOnPanRequested() {
        for (OnPanRequestedListener l : _panListeners) {
            l.onPanRequested();
        }
    }
}
