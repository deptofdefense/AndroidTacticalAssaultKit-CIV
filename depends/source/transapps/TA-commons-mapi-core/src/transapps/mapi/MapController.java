package transapps.mapi;

import transapps.geom.BoundingBoxE6;
import transapps.geom.Box;
import transapps.geom.Coordinate;
import transapps.geom.GeoPoint;

/**
 * An interface that resembles the Google Maps API MapController.  Use
 * this to move the map around and stuff.  Generally, you'll want to use
 * {@link GeoPoint} and {@link BoundingBoxE6}.  These methods are left
 * more abstract to make adapting a little simpler.
 *
 * @author Neil Boyd
 */
public interface MapController {
    /**
     * Make the bounds of the map the bounds of the box 
     * @param box
     */
    void zoomToBoundingBox(Box box);
    /**
     * Animate to the specified point
     * @param geoPoint
     */
    void animateTo(Coordinate geoPoint);
    /**
     * Move to the specified point
     * @param point
     */
    void setCenter(Coordinate point);
    /**
     * Zoom to the specified zoom level
     * @param zoomLevel
     * @return
     */
    int setZoom(int zoomLevel);
    /**
     * Zoom in once
     * @return
     */
    boolean zoomIn();
    /**
     * Zoom in and fix the center on the specified pixel
     * @param xPixel
     * @param yPixel
     * @return
     */
    boolean zoomInFixing(int xPixel, int yPixel);
    /**
     * Zoom out once
     * @return
     */
    boolean zoomOut();
    /**
     * Zoom out and fix the center on the specified pixel
     * @param xPixel
     * @param yPixel
     * @return
     */
    boolean zoomOutFixing(int xPixel, int yPixel);
    /**
     * 
     * @param yspan
     * @param xspan
     */
    void zoomToSpan(int yspan, int xspan);

    /**
     * Gets the rotation of the map in degrees where 0 means no rotation
     * 
     * @return The current rotation of the map in degrees
     */
    double getRotation( );

    /**
     * Sets the rotation of the map in degrees where 0 means no rotation
     * 
     * @param degrees
     *            The new rotation of the map
     */
    void setRotation( double degrees );
}
