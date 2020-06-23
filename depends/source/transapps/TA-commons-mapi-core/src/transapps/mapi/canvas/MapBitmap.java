package transapps.mapi.canvas;

/**
 * This interface is for working with bitmaps that can be drawn on the map using the
 * {@link MapCanvas} API.<br/>
 * <br/>
 * Bitmaps can become invalid in rare cases after the activity resumes, but they will also become
 * invalid if release is called on them. So if a MapBitmap is retained between drawing frames the
 * {@link #isValid()} method should be checked before it is used to make sure it is still valid.
 * 
 * @author SRA
 * @since NW SDK 1.0.34
 */
public interface MapBitmap
{
    /**
     * The width of the of the bitmap
     * 
     * @return The width of the bitmap
     */
    public int getWidth( );

    /**
     * The height of the of the bitmap
     * 
     * @return The height of the bitmap
     */
    public int getHeight( );

    /**
     * This will check to see if the bitmap is valid or not. If the {@link MapBitmap#release()} was
     * called then this will return false.
     * 
     * @return true if the bitmap is valid and false if it should no longer be used for drawing
     */
    public boolean isValid( );

    /**
     * This will release the bitmap making it invalid, which could cause strange images to be drawn
     * if it is used. This method should only be called on the GLThread, which is the thread that
     * the drawing methods for the {@link MapCanvas} are normally called on.
     */
    public void release( );
}
