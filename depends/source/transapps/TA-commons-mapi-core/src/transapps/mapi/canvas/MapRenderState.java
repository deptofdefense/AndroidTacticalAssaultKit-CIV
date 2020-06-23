package transapps.mapi.canvas;

/**
 * This interface stores the current state of the map view/projection so it can be checked to see if
 * the map has changed since the previous frame. This object can be used to help determine when
 * previous frame computations can be reused during the current frame.
 * 
 * @author SRA
 * @since NW SDK 1.0.34
 */
public interface MapRenderState
{
    /**
     * This will check to see if the render state has changed in some way since the previous state
     * was recorded
     * 
     * @param previousState
     *            The previous recorded state
     * @return true if the state has changed false if it is the same
     */
    public boolean hasStateChanged( MapRenderState previousState );
}
