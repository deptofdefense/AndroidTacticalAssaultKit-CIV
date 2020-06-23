package transapps.mapi.canvas;



/**
 * Interface for a container class used to specify polygons that can be drawn to a layer using
 * the {@link MapCanvas} class.
 * 
 * @author SRA and PAR
 * @since NW SDK 1.0.34
 */
public interface CanvasPolygon
{
    /**
     * Creates a new segment using the specified coordinate. If there is already an open segment,
     * this method will first close it before beginning a new one. No segment is actually created
     * when this method is called and a call to {@link #lineTo(float, float)} or one of the arc
     * methods is needed to actually add drawable information to the segment.
     * 
     * @param x
     *            X coordinate of the first vertex in a new segment.
     * @param y
     *            Y coordinate of the first vertex in a new segment.
     * @since NW SDK 1.0.45
     */
    public void moveTo( float x, float y );

    /**
     * Connects the last point in the current segment to a new point using a straight line.
     * This method should not be called until after the {@link #lineTo(float, float)} methods have
     * been called.
     * 
     * @param x
     *            X coordinate of a new vertex in the current segment.
     * @param y
     *            Y coordinate of a new vertex in the current segment.
     * @since NW SDK 1.0.45
     */
    public void lineTo( float x, float y );

    /**
     * Connects the last point in the current segment to a new point using an arc.
     * This method should not be called until after the {@link #lineTo(float, float)} methods have
     * been called.
     * 
     * @param x
     *            X coordinate of the end point of an arc connected to the previous point in this
     *            segment.
     * @param y
     *            Y coordinate of the end point of an arc connected to the previous point in this
     *            segment.
     * @param scale
     *            Defines the radius of the drawn arc relative to 1/2 the distance between the
     *            previous point in this segment and the provided point. For example, providing a
     *            scale of 2 will draw an arc that has radius equal to the distance between the
     *            previous point and the provided one, a scale of 3 will draw an arc with a radius
     *            equal to 1.5 times the distance between the previous point and the provided one,
     *            etc. This value must be greater than or equal to 1.0.
     * @param moveClockwise
     *            Whether to move in the clockwise or counterclockwise direction from the start
     *            point to the end point when drawing the arc.
     * @since NW SDK 1.0.45
     */
    public void arcWithScaleToPoint( float x, float y, float scale, boolean moveClockwise );

    /**
     * Connects the last point in the current segment to a new point using an arc.
     * This method should not be called until after the {@link #lineTo(float, float)} methods have
     * been called.
     * 
     * @param x
     *            X coordinate of the end point of an arc connected to the previous point in this
     *            segment.
     * @param y
     *            Y coordinate of the end point of an arc connected to the previous point in this
     *            segment.
     * @param radius
     *            The desired radius, in pixels, of the arc that will be drawn between the previous
     *            point in this segment and the provided point. This radius must be greater than or
     *            equal to half the distance between the previous point and the provided point.
     * @param moveClockwise
     *            Whether to move in the clockwise or counterclockwise direction from the start
     *            point to the end point when drawing the arc.
     * @since NW SDK 1.0.45
     */
    public void arcWithRadiusToPoint( float x, float y, float radius, boolean moveClockwise );

    /**
     * This checks to see if there are any segments in the polygon
     * 
     * @return false if there are no segments, true if there is at least one
     * @since NW SDK 1.0.45
     */
    public boolean isEmpty( );

    /**
     * This will clear the state of the polygon
     * 
     * @since NW SDK 1.0.45
     */
    public void clear( );

    /**
     * This checks to see if the polygon is finished or not. The polygon will not be finished until
     * it is passed into a draw call. After it has been drawn any new changes to the polygon will
     * clear the polygon.
     * 
     * @return true if the polygon is finished and new polygon modification calls will clear the
     *         current polygon data
     * @since NW SDK 1.0.45
     */
    public boolean finished( );
}
