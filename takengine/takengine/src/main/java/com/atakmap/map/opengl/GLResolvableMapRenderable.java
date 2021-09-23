
package com.atakmap.map.opengl;

import com.atakmap.opengl.GLResolvable;

public interface GLResolvableMapRenderable extends GLMapRenderable, GLResolvable {

    /**************************************************************************/
    // GL Map Renderable

    /**
     * {@inheritDoc}
     * <P>
     * The following behavior is expected for the various states during execution of this method:
     * <UL>
     * <LI><code>UNRESOLVED</code> - resolution is started and the current state transitions to
     * <code>RESOLVING</code></LI>
     * <LI><code>RESOLVING</code> - the rendered graphics are updated as new data is loaded. The
     * state will change to <code>RESOLVED</code> once all data has finished loading or
     * <code>UNRESOLVABLE</code> if resolution fails.</LI>
     * <LI><code>RESOLVED</code> - all data has been loaded and should be displayed. The state is
     * unchanged.</LI>
     * <LI><code>SUSPENDED</code> - any loaded data is displayed, however no new data should be
     * loaded. The state is unchanged.</LI>
     * <LI><code>UNRESOLVABLE</code> - the data failed to load. The state is unchanged.</LI>
     * </UL>
     * 
     * @param view {@inheritDoc}
     */
    @Override
    public void draw(GLMapView view);

    /**
     * Any resources and all loaded data is released. When this method returns, the object should be
     * in the <code>UNRESOLVED</code> state.
     */
    @Override
    public void release();
}
