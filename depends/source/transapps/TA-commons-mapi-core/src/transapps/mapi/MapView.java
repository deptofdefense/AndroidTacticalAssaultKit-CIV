package transapps.mapi;

import com.atakmap.map.opengl.GLMapView;
import transapps.geom.BoundingBoxE6;
import transapps.geom.GeoPoint;
import transapps.geom.Box;
import transapps.geom.Coordinate;
import transapps.geom.Projection;
import transapps.mapi.events.MapListener;
import transapps.mapi.overlay.MapOverlay;
import android.graphics.Rect;
import android.view.View;

/**
 * An interface that resembles the Google Maps API MapView.  This is an adapter
 * for map views.  This does not really have to be a {@link View}, but it may be.
 *
 * @author Neil Boyd
 */
public interface MapView {
    
    // ===========================================================
    // View control
    // ===========================================================
    /**
     * Use this to move the map around and zoom and stuff
     * @return
     */
    MapController getController();
    
    
    // ===========================================================
    // Projection
    // ===========================================================
    
    /**
     * Use this to convert points to/from {@link Coordinate} from/to screen pixels
     * @return
     */
    Projection getProjection();
    
    
    // ===========================================================
    // view state
    // ===========================================================
    
    /**
     * @return The current zoom
     */
    int getZoomLevel();

    /**
     * @return The current zoom
     */
    double getZoomLevelAsDouble();

    /**
     * @return The max zoom
     */
    int getMaxZoomLevel();

    /**
     * This will return the minimum zoom level that is supported by the map view
     * 
     * @return The minimum zoom
     */
    int getMinZoomLevel( );

    /**
     * This will return the map resolution at a latitude using current zoom level
     *
     * @param lat latitude in degrees
     * @return map resolution in meters/pixel
     * @since NW SDK 1.0.49
     */
    double getMapResolution( double lat );

    /**
     * Get the bounds of the view in model coordinates optionally reusing the supplied box.
     * Generally, you'll want to use {@link BoundingBoxE6} here.  This is left more abstract
     * to support the transapps.geom library.
     * 
     * @param reuse
     * @return
     */
    <T extends Box> T getBoundingBox( T reuse );
    
    /**
     * Get the center of the view in model coordinates optionally reusing the supplied point.
     * Generally, you'll want to use {@link GeoPoint} here.  This is left more abstract
     * to support the transapps.geom library.
     * 
     * @param reuse
     * @return
     */
    <T extends  Coordinate> T getMapCenter( T reuse );
    
    // ===========================================================
    // Overlays
    // ===========================================================
    
    OverlayManager getOverlayManager();
    
    /**
     * Returns the overlay manager which manages {@link MapOverlay} objects.
     * 
     * @return The manager for {@link MapOverlay}s.
     * @since NW SDK 1.0.34
     */
    MapOverlayManager getMapOverlayManager( );

    // ===========================================================
    // Listeners
    // ===========================================================
    
    /**
     * Apply a listener to receive pan/zoom notifications
     * @param l
     */
    void setMapListener( MapListener l );

    
    // ===========================================================
    // view stuff
    // ===========================================================
    
    /**
     * @return The android view for this {@link MapView}.  This might return this, thats ok
     */
    View getView();
    
    /**
     * 
     * @return
     */
    boolean isAnimating();
    
    /**
     * Get the bounding box in screen coordinates
     * 
     * @param reuse
     * @return
     */
    Rect getScreenRect( Rect reuse );

    /**
     * Gets the rotation of the map in degrees where 0 means no rotation
     * 
     * @return The current rotation of the map in degrees
     */
    float getRotation( );

    /**
     * Register the callback for the map animation settlement event
     * @param c - animation settlement callback
     *
     * @since NW SDK 1.0.47
     */
    void registerOnAnimationSettledCallback(GLMapView.OnAnimationSettledCallback c);

    /**
     * Unregister the callback for the map animation settlement event
     * @param c - animation settlement callback
     *
     * @since NW SDK 1.0.47
     */
    void unregisterOnAnimationSettledCallback(GLMapView.OnAnimationSettledCallback c);
}
