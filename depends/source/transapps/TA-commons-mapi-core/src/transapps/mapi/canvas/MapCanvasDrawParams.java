package transapps.mapi.canvas;

import transapps.geom.BoundingBoxE6;
import transapps.geom.GeoPoint;
import transapps.mapi.MapView;
import android.content.Context;
import android.graphics.PointF;


/**
 * This object provides access to parameters and some helpful utilities which can be used while
 * drawing onto a {@link MapCanvas}.
 * 
 * @author SRA
 * @since NW SDK 1.0.34
 */
public abstract class MapCanvasDrawParams
{
    /**
     * The canvas to draw onto
     */
    public final MapCanvas canvas;

    /**
     * The projection that can be used to convert from GeoPoints to screen points
     */
    public final MapProjection projection;

    /**
     * This object can be used in the projection to convert between Geo Coordinates and screen
     * pixels. A reference to this point should never be used outside of the draw method that this
     * object was provided inside.
     */
    public final PointF screenPoint = new PointF( );

    /**
     * The density of the device.
     */
    public final float deviceDensity;

    /**
     * This object can be used to measure text. A reference to this object should never be used
     * outside of the draw method that this object was provided inside.
     * 
     * @since NW SDK 1.0.45
     */
    public final TextMeasurement textMeasurement = new TextMeasurement( );

    /**
     * This is the default constructor for the MapCanvasDrawParams
     * 
     * @param canvas
     *            The canvas to draw onto
     * @param projection
     *            The projection that can be used to convert from GeoPoints to screen points
     * @param deviceDensity
     *            The density of the device
     */
    protected MapCanvasDrawParams( MapCanvas canvas, MapProjection projection, float deviceDensity )
    {
        this.canvas = canvas;
        this.projection = projection;
        this.deviceDensity = deviceDensity;
    }

    /**
     * This returns the context which is connected to the activity
     * 
     * @return The activity context
     */
    public abstract Context getActivityContext( );

    /**
     * This returns the map view which the drawing will take place on
     * 
     * @return The map view connected to the drawing operations
     */
    public abstract MapView getMapView( );

    /**
     * This will get the current rotation of the map in degrees
     *
     * @return The current rotation of the map in degrees
     * @since NW SDK 1.1.15.4
     */
    public abstract float getMapRotation( );

    /**
     * This will get the current zoom level of the map which
     *
     * @return The current zoom level of the map
     * @since NW SDK 1.1.15.4
     */
    public abstract double getMapZoomLevel( );

    /**
     * This checks to see if the geo point location which is passed in is inside the screen. This
     * method can be used to help optimize drawing by quickly checking if an item is on the screen
     * or not so drawing can be skipped if it wouldn't be visible on the screen.
     * 
     * @param location
     *            The location to check to see if it is on the screen
     * @return true if the location is on the screen, false if it is not
     */
    public abstract boolean screenContainsPoint( GeoPoint location );

    /**
     * This checks to see if the geo box which is passed in is inside, intercepts or overlaps the
     * screen. This method can be used to help optimize drawing by quickly checking if an object is
     * on the screen or not so drawing can be skipped if it wouldn't be visible on the screen.
     * 
     * @param box
     *            The box to check to see if it would be at least partially visible on the screen
     * @return true if the box is at least partially on the screen, false if it is not
     */
    public abstract boolean screenContainsArea( BoundingBoxE6 box );

    /**
     * This will return true if the current render call this object was passed into is for the
     * special magnification pass
     * 
     * @return true if the current draw path is for magnification, false for the normal render pass
     */
    public abstract boolean isMagnificationPass( );

    /**
     * This will return the bounds for the map
     *
     * @param out
     *             The reusable location where the bounds should be stored
     * @return The bounds for the map which will be out if it is not null
     * @since NW SDK 1.1.15.4
     */
    public abstract BoundingBoxE6 getMapBounds( BoundingBoxE6 out );

    /**
     * This provides a simple value defining the current state of canvas for drawing
     * text. If this value doesn't change then it means that the properties that define
     * how text is drawn has not changed
     *
     * @return An integer value defining the current text state
     * @since NW SDK 1.1.15.4
     */
    public abstract int getCanvasTextState( );
}
