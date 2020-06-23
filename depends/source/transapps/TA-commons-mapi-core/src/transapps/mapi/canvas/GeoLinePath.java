package transapps.mapi.canvas;

import transapps.geom.GeoPoint;


/**
 * This is a special version of the {@link LinePath} which takes geo coordinates instead of pixels.
 * Since geo coordinates are provided instead of pixels it is more optimized when the drawing the
 * line because the geo point to pixel transformation can be done in bulk. However it is up
 * to the owner of the path to call {@link #updateForCurrentProjection()} when the geo points should
 * be transformed to pixels again. Otherwise the previous pixel transformation will be used.
 * @author CSRA
 * @since NW SDK 1.1.15.4
 */
public interface GeoLinePath extends LinePath
{
    /**
     * This will setup the start of a new line moving it to the given geo coordinate. This can be
     * though of picking up your pen when drawing a line.
     *
     * @param latitude
     *             The latitude to start the next line at
     * @param longitude
     *             The longitude to start the next line at
     */
    @Override
    public void moveTo( float latitude, float longitude );

    /**
     * This will move the cursor/pen of the path to the geo point location without drawing any
     * lines from the previous location
     *
     * @param geoPoint
     *             The new starting location of the path
     */
    public void moveTo( GeoPoint geoPoint );

    /**
     * This will draw a line from the previous location to the new location where the previous
     * location was defined either by {@link #moveTo(float, float)} or {@link #lineTo(float, float)}
     * . This can be though of placing your pen down (if it wasn't already down) and drawing a line
     * to the specified location.
     *
     * @param latitude
     *             The latitude to draw the line to
     * @param longitude
     *             The longitude to draw the line to
     */
    @Override
    public void lineTo( float latitude, float longitude );

    /**
     * This will draw a line from the previous location to the new location where the previous
     * location was defined either by {@link #moveTo(float, float)} or {@link #lineTo(float, float)}
     * . This can be though of placing your pen down (if it wasn't already down) and drawing a line
     * to the specified location.
     *
     * @param geoPoint
     *             The location to start the next line at
     */
    public void lineTo( GeoPoint geoPoint );

    /**
     * This will update the geo points so the next time the path is drawn it will use the current
     * projection. Calling this method does not perform the update, it just notifies that an update
     * is needed the next time the path is drawn.
     */
    public void updateForCurrentProjection( );
}
