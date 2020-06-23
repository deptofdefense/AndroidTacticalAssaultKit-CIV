package transapps.mapi.canvas;

/**
 * This is an interface which makes it easy to create a path of lines which can be drawn all at once
 * using the {@link MapCanvas} while reducing the need to allocate a tone of new objects during the
 * render frame.
 * 
 * @author SRA
 * @since NW SDK 1.0.34
 */
public interface LinePath
{
    /**
     * This will clear the state of the path so that a new path or updated path can be created
     */
    public void clear( );

    /**
     * This will setup the start of a new line moving it to the given x, y coordinate. This can be
     * though of picking up your pen when drawing a line.
     * 
     * @param x
     *            The x coordinate to start the next line at
     * @param y
     *            The x coordinate to start the next line at
     */
    public void moveTo( float x, float y );

    /**
     * This will draw a line from the previous location to the new location where the previous
     * location was defined either by {@link #moveTo(float, float)} or {@link #lineTo(float, float)}
     * . This can be though of placing your pen down (if it wasn't already down) and drawing a line
     * to the specified location.
     * 
     * @param x
     *            The x coordinate to draw the line to
     * @param y
     *            The y coordinate to draw the line to
     */
    public void lineTo( float x, float y );

    /**
     * This will free any extra memory the path is holding onto
     */
    public void cleanUp( );

    /**
     * This will check to see if the path is empty, meaning no lines are part of the path
     * 
     * @return true for empty and false for not being empty
     */
    public boolean isEmpty( );

    /**
     * This will create a deep copy of the line path
     * 
     * @return A deep copy of this object
     */
    public LinePath createCopy( );
}
