package transapps.geom;

import android.graphics.Point;
import android.view.MotionEvent;

/**
 * An interface that resembles the Google Maps API Projection interface.  This guy is used
 * to translate between model coordinates, screen coordinates, view coordinates, and
 * intermediate coordinates.
 * <ul>
 * <li><b>model coordinates</b> are those coordinates used by the model, projected in
 * the model's projection and using the model's datum (i.e. cyl mercator wgs84 geo points)</li>
 * <li><b>screen coordinates</b> are pixels used to draw on the canvas, originating from the
 * canvas origin</li>
 * <li><b>view coordinates</b> are pixels obtained from {@link MotionEvent}s and the like
 * originating at the top left of the view. </li>
 * <li><b>intermediate coordinates</b> are pixels obtained from {@link #toPixelsProjected(Coordinate, Point)}.
 * These are model coordinates that are project to screen coordinates at the max scale of the view.  Until these
 * pixels are then translated using {@link #toPixelsTranslated(Point, Point)}, they have little meaning.</li>   
 * </ul>
 * <br/><b> Note that projection is only really good on the ui thread and the internal is only valid after the
 * first draw call.  Behavior is undefined if this is used outside the ui thread</b> 
 * <br/><br/>Uses:
 * <br/>Convert a model coordinate to a screen coordinate suitable for drawing
 * <pre>
 *   Point tmpPoint; // point I keep around for reuse
 *   IProjection projection = ...;
 *   Canvas canvas = ...;
 *   ICoordate circleLocation = ...;
 *   projection.toPixels( circleLocation, tmpPoint );
 *   canvas.drawCircle( tmpPoint.x, tmpPoint.y, 10.0f, null );
 * </pre>
 * <br/>Convert a view coordinate to a screen coordinate for comparison
 * <pre>
 *   MotionEvent e; // event received
 *   Point tmpPoint1; // point I keep around for reuse
 *   Point tmpPoint2; // point I keep around for reuse
 *   IProjection projection = ...;
 *   ICoordate circleLocation = ...;
 *   projection.toPixels( circleLocation, tmpPoint1 );
 *   projection.toPixels( (int) e.getX(), (int) e.getY(), tmpPoint2 );
 *   Rect touchSlopRect = new Rect from tmpPoint1
 *   touchSlopRect.contains( tmpPoint2 );
 * </pre>
 * <br/>Convert a view coordinate to a model coordinate
 * <pre>
 *   MotionEvent e; // event received
 *   Point tmpPoint1; // point I keep around for reuse
 *   IProjection projection = ...;
 *   projection.toPixels( (int) e.getX(), (int) e.getY(), tmpPoint1 );
 *   ICoordinate c = projection.fromPixels( tmpPoint1.x, tmpPoint1.y, new {@link Point}() );
 * </pre>
 * 
 *
 * @author Neil Boyd
 * @author mriley
 */
public interface Projection {

    /**
     * Converts the model {@link Coordinate} to a screen pixel suitable for drawing on on the
     * canvas.  <b>Note that screen pixels may not have the same origin as the default canvas origin.</b>
     * 
     * @return
     */
    Point toPixels(Coordinate in, Point out);
    
    /**
     * Converts a model {@link Coordinate} into an intermediate pixel projected at the maximum zoom level.  
     * This screen pixel may then be translated via {@link #toPixelsTranslated(Point, Point)} to convert
     * the screen pixel to a screen pixel appropriate for drawing at the current zoom level.  This allows you
     * to optimize your projection calls so that only the computationally light weight translation call need be
     * called each draw.
     *  
     * @param in
     * @param out
     * @return
     */
    Point toPixelsProjected( Coordinate in, Point out );
    
    /**
     * Converts a screen coordinate to an intermediate pixel projected at the maximum zoom level. 
     * 
     * @param in
     * @return
     * @see Projection#toPixelsProjected(Coordinate, Point)
     */
    Point fromPixelsProjected(Point in, Point out);
    
    /**
     * Converts an intermediate pixel projected using {@link #toPixelsProjected(Coordinate, Point)} to a screen
     * pixel suitable for drawing at the current zoom level.  This allows you to optimize your projection calls so 
     * that only this computationally light weight translation call need be called each draw.
     * 
     * @param in
     * @param out
     * @return
     */
    Point toPixelsTranslated( Point in, Point out );
    
    /**
     * Converts a view relative pixel to a screen pixel.  View relative pixels are those obtained from
     * {@link MotionEvent}s.  Screen pixels are points with the sam origin as the canvas.
     * 
     * @param viewRelativeX
     * @param viewRelativeY
     * @param out
     * @return
     */
    Point toPixels( int viewRelativeX, int viewRelativeY, Point out );
    
    /**
     * Converts a screen pixel to a model {@link Coordinate}.  Note that screen pixels must have
     * the same origin as the canvas.   If x and y represent view coordinates (coordinates who's origin
     * is relative to the view like those provided by {@link MotionEvent}s) they must first be converted
     * to screen coordinates before comparing to {@link Point}s returned by {@link #toPixels(Coordinate, Point)} 
     * <br/><br/>
     * @param x
     * @param y
     * @param out
     * @return
     */
    <T extends Coordinate> T fromPixels(int x, int y, T out);
    
    /**
     * Converts the distance in model units to the largest number of pixels in the view.  For cyl mercator wgs84 this
     *  will be meters to pixels at the equator.
     * 
     * @param modelDistance
     * @return
     */
    float distanceToMaxPixels( float modelDistance );
}