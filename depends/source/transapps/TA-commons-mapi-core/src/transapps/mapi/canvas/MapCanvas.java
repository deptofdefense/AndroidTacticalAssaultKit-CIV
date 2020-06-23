package transapps.mapi.canvas;

import java.util.List;

import transapps.mapi.MapView;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;


/**
 * This interface provides a simplified drawing interface for objects on an OpenGL
 * surface. The interface is similar to that provided by the android.graphics.Canvas
 * class or the java.awt.Graphics2D class.
 * 
 * <br />
 * <br />
 * 
 * Example Usage:
 * 
 * <pre>
 * {
 *     &#064;code
 *     // Set some state. These changes will apply to all
 *     // draw and fill calls made after this. The &quot;fill color&quot;
 *     // applies to methods beginning with &quot;fill&quot; and the &quot;line color&quot;
 *     // applies to methods beginning with &quot;draw&quot;.
 *     canvas.setLineColor( Color.RED );
 *     canvas.setFillColor( Color.BLUE );
 * 
 *     // Draw a line
 *     canvas.drawLine( startX, startY, endX, endY );
 * 
 *     // Draw a rectangle
 *     canvas.drawRect( upperLeftX, upperLeftY, lowerRightX, lowerRightY );
 * 
 *     // Create a polygon
 *     CanvasPolygon polygon = canvas.createCanvasPolygon( );
 *     polygon.moveTo( vertex1.x, vertex1.y );
 *     polygon.lineTo( vertex2.x, vertex2.y );
 *     polygon.lineTo( vertex3.x, vertex3.y );
 * 
 *     // Draw the polygon's outline
 *     canvas.drawPolygon( polygon );
 * 
 *     // Fill in the polygon's area
 *     canvas.fillPolygon( polygon );
 * 
 *     // Change the typeface and typesize for text that will be drawn
 *     canvas.setType( Typeface.MONOSPACE, 30 );
 * 
 *     // Draw some text. The point provided is the lower left corner
 *     // of the first letter of the text.
 *     canvas.drawText( &quot;Hello, World!&quot;, lowerLeftX, lowerLeftY );
 * 
 * }
 * </pre>
 * 
 * @author SRA
 * @since NW SDK 1.0.34
 */
public interface MapCanvas
{
    /**
     * This is the enumeration used for how the caps on the lines should be drawn
     * 
     * @author SRA
     * @since NW SDK 1.0.34
     */
    public enum LineCapStyle
    {
        /**
         * This is for the cap/tip of the line to be rounded
         */
        ROUND,

        /**
         * This is for the cap/tip of the line to be flat. This will be the default value if null is
         * ever used.
         */
        BUTT;
    }

    /**
     * This is the enumeration used for how the joints on the line should be drawn
     * 
     * @author SRA
     * @since NW SDK 1.0.34
     */
    public enum LineJoinStyle
    {
        /**
         * This is for the joint to be flat. This will be the default when null is provided.
         */
        BEVEL,

        /**
         * This is for the joint to be a sharp point/edge
         */
        MITER,

        /**
         * This is for the joint to be curved/smooth
         */
        ROUND;
    }

    /**
     * This is the enumeration used for how lines should be stroked
     * 
     * @author SRA
     * @since NW SDK 1.0.34
     */
    public static enum LinePatternStyle
    {
        /**
         * This should be used when lines should be stroked as a solid line
         */
        SOLID,

        /**
         * This should be used when lines should be stroked as a dashed line
         */
        DASHED;
    }

    /**
     * Sets a color to use for all lines drawn by this canvas. This color will be applied to
     * all objects drawn by a method starting with "draw" such as
     * {@link #drawLine(float, float, float, float)} or {@link #drawPolygon(CanvasPolygon)}.
     * 
     * @param color
     *            Color to use when drawing lines. This is an integer of the format used
     *            by the {@link android.graphics.Color} class.
     */
    public void setLineColor( int color );

    /**
     * Sets a color for this canvas to use when filling areas. This color will be applied to
     * all objects created by a method starting with "fill" such as
     * {@link #fillPolygon(CanvasPolygon)}.
     * 
     * @param color
     *            Color to use when filling areas. This is an integer of the format used
     *            by the {@link android.graphics.Color} class.
     */
    public void setFillColor( int color );

    /**
     * Sets the width to use when drawing lines. This width will be applied to
     * all objects drawn by a method starting with "draw" such as
     * {@link #drawLine(float, float, float, float)} or {@link #drawPolygon(CanvasPolygon)}, with
     * the exception of the "drawText" methods, which
     * use a font size as specified by {@link #setType(Typeface, int)} or {@link #setTypesize(int)}
     * instead.
     * 
     * @param width
     *            Width to use when drawing lines. This value is measured in pixels.
     */
    public void setLineWidth( float width );

    /**
     * Sets the style to use when joining two continuous lines together. This style
     * will be applied to all objects drawn with a method starting in "draw" that is made
     * up of lines that share vertex points, such as {@link #drawLines(LinePath)},
     * or {@link #drawPolygon(CanvasPolygon)}.
     * 
     * @param style
     *            Style to use when joining lines.
     */
    public void setLineJoinStyle( LineJoinStyle style );

    /**
     * Sets the style to use when starting or terminating lines. This style will be applied
     * to all objects drawn with a method starting in "draw" that are non-continuous, such as
     * {@link #drawLines(List)} or {@link #drawLine(float, float, float, float)}.
     * 
     * @param style
     *            Style to use when drawing line caps.
     */
    public void setLineCapStyle( LineCapStyle style );

    /**
     * Sets a style to use when drawing pattern textures on lines. This style will be applied to
     * all objects drawn with a method starting in "draw".
     * 
     * @param style
     *            Style to apply to drawn line textures.
     */
    public void setLinePattern( LinePatternStyle style );

    /**
     * Sets a style to use when drawing pattern textures on lines. This style will be applied to all
     * objects drawn with a method starting in "draw".
     * 
     * @param mask
     *            Bit mask representing the pattern to be drawn on the line. This mask should be
     *            made
     *            up of "pairs" that consist of either on or off (1 or 0) and how many times that
     *            should be repeated.
     *            E.X. for a pattern that consists of 5 on pixels, 10 off pixels, and then 5 on
     *            pixels, you should pass
     *            {1,5,0,10,1,5} as the mask. If the length of this mask is not a power of 2, it
     *            will likely be truncated when
     *            drawn to lines.
     */
    public void setLinePattern( int [] mask );

    /**
     * Sets the typeface that will be used when drawing text.
     * 
     * @param typeface
     *            Typeface to use when drawing text.
     */
    public void setTypeface( Typeface typeface );

    /**
     * Sets the size of text that will be drawn to the canvas.
     * 
     * @param size
     *            Font size to use when drawing text.
     */
    public void setTypesize( int size );

    /**
     * Helper method to set both the typeface and typesize at the same time. Calling
     * this method is the same as calling both {@link #setTypeface(Typeface)} and
     * {@link #setTypesize(int)}.
     * 
     * @param typeface
     *            Typeface to use when drawing text.
     * @param size
     *            Typesize to use when drawing text.
     */
    public void setType( Typeface typeface, int size );

    /**
     * Helper method to set whether or not textures drawn onto lines should be scaled.
     * Scaled textures will be streched or squeezed so that their beginning is always
     * at the beginning of the line and their end is always at the end of the line. Unscaled
     * textures will always remain the same length regardless of the zoom level.
     * 
     * @param scaleLineTextures
     *            Whether or not to scale line textures.
     */
    public void setScaleLineTextures( boolean scaleLineTextures );

    /**
     * Draws a line between two points. The line will be drawn using the color set with
     * the {@link #setLineColor(int)} method. This line will have a width as specified
     * by the {@link #setLineWidth(float)} method.
     * 
     * @param x0
     *            X coordinate of the beginning point of the line.
     * @param y0
     *            Y coordinate of the beginning point of the line.
     * @param x1
     *            X coordinate of the end point of the line.
     * @param y1
     *            Y coordinate of the end point of the line.
     */
    public void drawLine( float x0, float y0, float x1, float y1 );

    /**
     * Draws a set of lines according to the endPoints provided. This
     * method will draw a set of independent lines, and will not connect
     * the segments to one another. The line
     * will be drawn using the color set with the {@link #setLineColor(int)} method. This line will
     * have a width as specified by the {@link #setLineWidth(float)} method.
     * 
     * @param endPoints
     *            Set of points to draw as lines. The size of this
     *            list must be an even number. The list will be divided into pairs,
     *            starting with the first two endPoints, and each pair will be drawn
     *            as an independent line on the canvas.
     */
    public void drawLines( List<PointF> endPoints );

    /**
     * Draws a line according to the stored in a polyline. The line will be drawn using the color
     * set with the {@link #setLineColor(int)} method. This line will have a width as specified by
     * the {@link #setLineWidth(float)} method.
     * 
     * @param polyline
     *            A list of points representing the vertices to use when
     *            drawing this line. This method does NOT connect the end of
     *            this line to it's beginning.
     * @since NW SDK 1.0.45
     */
    public void drawLine( List<PointF> polyline );

    /**
     * Draws a rectangle given upper left and lower right corners. The rectangle will be drawn
     * using the color set with the {@link #setLineColor(int)} method. The lines making
     * up this rectangle will have a width as specified by the {@link #setLineWidth(float)} method.
     * 
     * @param left
     *            The X coordinate of the upper left corner of the rectangle.
     * @param top
     *            The Y coordinate of the upper left corner of the rectangle.
     * @param right
     *            The X coordinate of the lower right corner of the rectangle.
     * @param bottom
     *            The Y coordinate of the lower right corner of the rectangle.
     */
    public void drawRect( float left, float top, float right, float bottom );

    /**
     * Draws a polygon to this canvas. This will just be the outline of the polygon,
     * and it will be drawn in the color specified by the {@link #setLineColor(int)}. The
     * lines drawn to make this polygon will have a width as specified by the
     * {@link #setLineWidth(float)} method.
     * 
     * @param polygon
     *            Polygon to draw to the canvas.
     */
    public void drawPolygon( CanvasPolygon polygon );

    /**
     * Draws a polygon to this canvas filling in the entire area covered by the polygon with
     * the color specified by the {@link #setFillColor(int)} method.
     * 
     * @param polygon
     *            Polygon to draw to the canvas.
     */
    public void fillPolygon( CanvasPolygon polygon );

    /**
     * Draws a string to the canvas. This string will use the Typeface and
     * font size as specified by the {@link #setType(Typeface, int)} method.
     * 
     * @param text
     *            String to draw to the canvas.
     * @param x
     *            X coordinate on the canvas of the lower left corner of the
     *            first letter of the string.
     * @param y
     *            Y coordinate on the canvas of the lower left corner of the
     *            first letter of the string.
     */
    public void drawText( String text, float x, float y );

    /**
     * Draws a string to the canvas. This string will use the Typeface and
     * font size as specified by the {@link #setType(Typeface, int)} method.
     * 
     * @param text
     *            String to draw to the canvas.
     * @param start
     *            Index of the first character to draw in the string. Can be used
     *            to draw substrings from a large piece of text.
     * @param end
     *            Index of the last character to draw in the string. Can be used
     *            to draw substrings from a large piece of text.
     * @param x
     *            X coordinate on the canvas of the lower left corner of the
     *            first letter of the string.
     * @param y
     *            Y coordinate on the canvas of the lower left corner of the
     *            first letter of the string.
     */
    public void drawText( String text, int start, int end, float x, float y );

    /**
     * This will measure how large the text will be rendered given the current state of the canvas.
     * The resulting size will be returned in screen coordinates.
     * 
     * @param text
     *            The text to measure
     * @param out
     *            An optional parameter which if not null will be where the resulting measurement
     *            will be stored
     * @return A {@link TextMeasurement} object containing the measurement in screen coordinates of
     *         the text. If out is not null out will be returned, else a new instance of a
     *         TextMeasurement will be returned
     * @since NW SDK 1.0.45
     */
    public TextMeasurement measureText( String text, TextMeasurement out );

    /**
     * This will create and return a new {@link LinePath} instance which be used with the
     * {@link #drawLines(LinePath)} method.
     * 
     * @return The new line path instance to use for drawing lines
     */
    public LinePath createLinePath( );

    /**
     * This will create and return a new {@link GeoLinePath} instance which be used with the
     * {@link #drawLines(LinePath)} method.
     *
     * @param mapView
     *             The map view from the drawing parameters
     * @return The new geo line path instance to use for drawing lines, or null if the map view
     * passed in is not valid
     * @see MapCanvasDrawParams#getMapView()
     * @since NW SDK 1.1.15.4
     */
    public GeoLinePath createGeoLinePath( MapView mapView );

    /**
     * This will create an empty polygon
     * 
     * @return The polygon object which the polygon can be created with
     */
    public CanvasPolygon createCanvasPolygon( );

    /**
     * Draws a set of lines according to the path provided. This method will draw a set of
     * independent lines, and will not connect the segments to one another. The line will be drawn
     * using the color set with the {@link #setLineColor(int)} method. This line will have a width
     * as specified by the {@link #setLineWidth(float)} method.
     * 
     * @param path
     *            The path that describes the lines to be drawn. This value must be an instance
     *            returned from the {@link #createLinePath()} and will not draw anything if that is
     *            not the case or null is passed in.
     */
    public void drawLines( LinePath path );

    /**
     * Draws the specified {@link MapBitmap} at the specified location.
     * 
     * @param image
     *            The bitmap
     * @param x
     *            The x-coordinate
     * @param y
     *            The y-coordinate
     */
    public void drawBitmap( MapBitmap image, float x, float y );

    /**
     * Fills the specified region with the specified {@link MapBitmap}.
     * 
     * @param image
     *            The bitmap to draw
     * @param x
     *            The x-coordinate
     * @param y
     *            The y-coordinate
     * @param width
     *            The width of the region
     * @param height
     *            The height of the region
     */
    public void drawBitmap( MapBitmap image, float x, float y, float width, float height );

    /**
     * Fills the specified region with the specified {@link MapBitmap}.
     * 
     * @param image
     *            The bitmap to draw
     * @param region
     *            The region where the bitmap should be drawn
     */
    public void drawBitmap( MapBitmap image, RectF region );

    /**
     * Fills the specified quadrilateral (Quad) with the specified {@link MapBitmap}.
     * 
     * @param image
     *            The bitmap to draw
     * @param ulX
     *            The x-coordinate of the upper-left point
     * @param ulY
     *            The y-coordinate of the upper-left point
     * @param urX
     *            The x-coordinate of the upper-right point
     * @param urY
     *            The y-coordinate of the upper-right point
     * @param lrX
     *            The x-coordinate of the lower-right point
     * @param lrY
     *            The y-coordinate of the lower-right point
     * @param llX
     *            The x-coordinate of the lower-left point
     * @param llY
     *            The y-coordinate of the lower-left point
     */
    public void drawBitmap( MapBitmap image, float ulX, float ulY, float urX, float urY, float lrX,
                            float lrY, float llX, float llY );

    /**
     * This will create a new MapBitmap instance of the image passed in. It is the responsibility of
     * the caller to call {@link MapBitmap#release()} on the return bitmap when they are done with
     * it. However, create and release should not be called every frame. Instead the same MapBitmap
     * should be reused across multiple frames and then released when it no longer needs to be
     * drawn.<br/>
     * <br/>
     * {@link MapBitmap}s that are returned from this method can be used between frames and create
     * should not be called each frame to create a MapBitmap for the same Bitmap image.
     * 
     * @param image
     *            The bitmap image to create a MapBitmap from
     * @return The MapBitmap or null if image is null or there was an issue creating the MapBitmap
     * @since NW SDK 1.0.45
     */
    public MapBitmap createMapBitmap( Bitmap image );

    /**
     * This will create a new MapBitmap instance of the image passed in. It is the responsibility of
     * the caller to call {@link MapBitmap#release()} on the return bitmap when they are done with
     * it. However, create and release should not be called every frame. Instead the same MapBitmap
     * should be reused across multiple frames and then released when it no longer needs to be
     * drawn.<br/>
     * <br/>
     * {@link MapBitmap}s that are returned from this method can be used between frames and create
     * should not be called each frame to create a MapBitmap for the same BitmapDrawable image.
     * Unless you are storing the Drawable object for other purposes or you did not load the
     * Drawable from the resources it is preferred that you use the
     * {@link #createMapBitmap(Resources, int)} method.
     * 
     * @param image
     *            The bitmap drawable to create a MapBitmap from
     * @return The MapBitmap or null if image is null or there was an issue creating the MapBitmap
     * @since NW SDK 1.0.45
     */
    public MapBitmap createMapBitmap( BitmapDrawable image );

    /**
     * This will create a new MapBitmap instance of the image passed in. It is the responsibility of
     * the caller to call {@link MapBitmap#release()} on the return bitmap when they are done with
     * it. However, create and release should not be called every frame. Instead the same MapBitmap
     * should be reused across multiple frames and then released when it no longer needs to be
     * drawn.<br/>
     * <br/>
     * {@link MapBitmap}s that are returned from this method can be used between frames and create
     * should not be called each frame to create a MapBitmap for the same drawable resource.
     * 
     * @param resources
     *            The resources to look up the bitmap drawable object
     * @param drawableId
     *            The resource identifier for the drawable image
     * @return The MapBitmap or null if resources is null, drawableId is invalid, the resulting
     *         drawable is not a {@link BitmapDrawable} or there was an issue creating the MapBitmap
     * @since NW SDK 1.0.45
     */
    public MapBitmap createMapBitmap( Resources resources, int drawableId );

    /**
     * This will save the current matrix state of the canvas so it can be restored later.
     * Each call to this method should be paired with a call to {@link #restore()}
     * 
     * @see {#link {@link #restore()}
     */
    public void save( );

    /**
     * This restores the matrix state of the canvas back to the previous save state. Each
     * call to {@link #save()} should be followed by a call of this method when the operation
     * needing the transformation has been finished
     */
    public void restore( );

    /**
     * This will update the current matrix by applying a translation
     * 
     * @param x
     *            The amount to translate in the x direction
     * @param y
     *            The amount to translate in the y direction
     */
    public void translate( float x, float y );

    /**
     * This will scale the current matrix by the provided values in the x and y direction
     * 
     * @param scaleX
     *            The x scale factor
     * @param scaleY
     *            The y scale factor
     */
    public void scale( float scaleX, float scaleY );

    /**
     * This will update the current matrix by applying a rotation around the origin
     * 
     * @param rotationDegrees
     *            The amount to rotate in degrees counter-clockwise
     */
    public void rotate( float rotationDegrees );

    /**
     * This will update the current matrix by applying a rotation around the given center
     * point
     * 
     * @param rotationDegrees
     *            The amount to rotate in degrees counter-clockwise
     * @param cx
     *            The center x coordinate of the rotation
     * @param cy
     *            The center y coordinate of the rotation
     */
    public void rotate( float rotationDegrees, float cx, float cy );
}
