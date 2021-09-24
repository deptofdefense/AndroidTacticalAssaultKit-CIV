package com.atakmap.map;

import com.atakmap.annotations.DeprecatedApi;

/**
 * <P>All methods defined by this interface are thread-safe unless otherwise
 * noted.
 *
 * @deprecated use {@link MapRenderer2} and {@link RenderContext}
 * @author Developer
 */
@Deprecated
@DeprecatedApi(since="4.1", forRemoval = true, removeAt = "4.4")
public interface MapRenderer extends MapRendererBase {
    /**
     * @deprecated use {@link RenderContext#isRenderThread()}
     */
    @Deprecated
    @DeprecatedApi(since="4.1")
    public boolean isRenderThread();
    /**
     * @deprecated use {@link RenderContext#queueEvent(Runnable)}
     */
    @Deprecated
    @DeprecatedApi(since="4.1")
    public void queueEvent(Runnable r);
    /**
     * @deprecated use {@link RenderContext#requestRefresh()}
     */
    @Deprecated
    @DeprecatedApi(since="4.1")
    public void requestRefresh();
    /**
     * @deprecated use {@link RenderContext#setFrameRate(float)}
     */
    @Deprecated
    @DeprecatedApi(since="4.1")
    public void setFrameRate(float rate);

    /**
     * @deprecated use {@link RenderContext#getFrameRate()}
     */
    @Deprecated
    @DeprecatedApi(since="4.1")
    public float getFrameRate();

    /**
     * @deprecated use {@link RenderContext#setContinuousRenderEnabled(boolean)}
     */
    @Deprecated
    @DeprecatedApi(since="4.1")
    public void setContinuousRenderEnabled(boolean enabled);

    /**
     * @deprecated use {@link RenderContext#isContinuousRenderEnabled()}
     */
    @Deprecated
    @DeprecatedApi(since="4.1")
    public boolean isContinuousRenderEnabled();
}

