package transapps.mapi.canvas;


import transapps.geom.GeoPoint;
import android.graphics.PointF;


/**
 * This is the specific type of projection which should be used when converting between GeoPoints
 * and pixel coordinates for use with the {@link MapCanvas}. It is also important to note that the
 * coordinate system of the pixels has the origin (0,0) as the lower left corner of the screen
 * instead of the typical Android upper left corner. In the MapProjection coordinate space the y
 * value will increase while moving up the screen and the x value will increase when moving to the
 * right.
 * 
 * @author SRA
 * @since NW SDK 1.0.34
 */
public interface MapProjection
{
    /**
     * Converts the {@link GeoPoint} to a screen pixel suitable for drawing on on the
     * {@link MapCanvas}.
     * 
     * @param in
     *            The geo point to convert into pixels coordinates
     * @param out
     *            The optional reusable point which if not null will be updated with the screen
     *            coordinates and then returned
     * @return The out value if out is not null (else a new {@link PointF} instance) set to the
     *         screen coordinates of the geo point.
     */
    public PointF toPixels( GeoPoint in, PointF out );

    /**
     * Converts the native projection's screen point into a {@link GeoPoint}.
     * 
     * @param in
     *            The screen point in pixels (which will be in the same coordinate system as the
     *            result from {@link #toPixels(GeoPoint, PointF)}) to convert into geo coordinates
     * @param out
     *            The optional reusable point which if not null will be updated with the geo
     *            coordinates and then returned
     * @return The out value if out is not null (else a new {@link GeoPoint} instance) set to the
     *         geo location of the screen coordinates.
     */
    public GeoPoint fromPixels( PointF in, GeoPoint out );

    /**
     * Converts the passed in native projection's screen point into the view based coordinate
     * system.
     * 
     * @param toConvert
     *            The screen point in pixels to convert into view space coordinates
     */
    public void convertPixelsToViewCoordinates( PointF toConvert );

    /**
     * Converts the passed in view based coordinate system into the native projection's screen
     * point.
     * 
     * @param toConvert
     *            The screen point in pixels to convert into native projection's screen point
     */
    public void convertViewCoordinatesToPixels( PointF toConvert );

    /**
     * This creates a new object which defines the rendering state of the projection. This state
     * will continue to hold the state at the time this method was called until
     * {@link #updateRenderState(MapRenderState)} is called. If you have a render state from the
     * previous frame it can be compared to the current frame to see if the render state has
     * changed. If the state has not changed any point transformations or other calculations from
     * the previous frame are still valid and can be reused if desired.
     * 
     * @return A new instance containing the current rendering state
     */
    public MapRenderState createRenderState( );

    /**
     * This will update the provided render state object with the current render state if and only
     * if the provided object was returned from the {@link #createRenderState()} method.
     * 
     * @param stateToUpdate
     *            The render state which came from {@link #createRenderState()} and should be
     *            updated.
     */
    public void updateRenderState( MapRenderState stateToUpdate );
}
