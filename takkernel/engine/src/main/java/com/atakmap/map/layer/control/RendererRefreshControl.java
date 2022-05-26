package com.atakmap.map.layer.control;

import com.atakmap.map.MapControl;

/**
 * Control allowing client code to signal to renderer to refresh.
 *
 * <P>Most renderers should support transparent mechanisms for automatic
 * refresh, however, there may be some situations in which the renderer may not
 * know that a refresh is required. This control enables client code to
 * explicitly request that refresh based on knowledge that the client may have
 * not normally available to the renderer.
 *
 * <P>The actual handling is implementation specific; renderers should know
 * what content they should be refreshing when this method is invoked.
 */
public interface RendererRefreshControl extends MapControl {
    void requestRefresh();
}
