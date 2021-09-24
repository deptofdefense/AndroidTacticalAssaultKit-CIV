
package com.atakmap.opengl;

public interface GLResolvable {

    public enum State {
        /** resolution has not yet started */
        UNRESOLVED,
        /** resolution has started but not yet finished */
        RESOLVING,
        /** successfully resolved */
        RESOLVED,
        /** could not be resolved */
        UNRESOLVABLE,
        /** resolution suspended */
        SUSPENDED,
    };

    /**************************************************************************/

    /**
     * Returns the current state of resolution.
     * 
     * @return
     */
    public State getState();

    /**
     * Resolution is suspended. If {@link #draw(GLMapView)} is invoked, any data that has already
     * been loaded should be rendered, however, no new data should be loaded. The rest of the data
     * may continue loading if {@link #resume()} is invoked.
     * <P>
     * This method does nothing if the current state is not {@link State#RESOLVING}.
     */
    public void suspend();

    /**
     * Resolution is resumed. This method may be invoked following a call to {@link #suspend()} to
     * resume loading data.
     * <P>
     * This method does nothing if the current state is not {@link State#SUSPENDED}.
     */
    public void resume();
}
