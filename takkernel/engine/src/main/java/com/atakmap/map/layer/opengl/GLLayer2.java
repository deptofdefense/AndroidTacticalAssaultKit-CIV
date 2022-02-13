package com.atakmap.map.layer.opengl;

import gov.tak.api.annotation.DontObfuscate;

@DontObfuscate
public interface GLLayer2 extends GLLayer {
    /**
     * Transitions the layer renderer into the <I>Started</I> state. The
     * renderer may only access its subject while in the <I>Started</I> state.
     * 
     * <P>This method may be invoked from any thread, however, it may never be
     * called concurrently with {@link #stop()}.
     */
    public void start();
    
    /**
     * Transitions the layer renderer into the <I>Stopped</I> state. The
     * renderer may not access its subject until returning to the <I>Started</I>
     * state, however, any renderable content previously derived may continue to
     * be rendered.
     * 
     * <P>This method may be invoked from any thread, however, it may never be
     * called concurrently with {@link #start()}.
     */
    public void stop();
}
