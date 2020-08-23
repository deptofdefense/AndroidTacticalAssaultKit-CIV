package com.atakmap.map;

/**
 * <P>All methods defined by this interface are thread-safe unless otherwise
 * noted.
 *
 * @deprecated use {@link MapRenderer2} and {@link RenderContext}
 * @author Developer
 */
@Deprecated
public interface MapRenderer extends MapRendererBase {
    /**
     * @deprecated use {@link RenderContext#isRenderThread()}
     */
    public boolean isRenderThread();
    /**
     * @deprecated use {@link RenderContext#queueEvent(Runnable)}
     */
    public void queueEvent(Runnable r);
    /**
     * @deprecated use {@link RenderContext#requestRefresh()}
     */
    public void requestRefresh();
    /**
     * @deprecated use {@link RenderContext#setFrameRate(float)}
     */
    public void setFrameRate(float rate);

    /**
     * @deprecated use {@link RenderContext#getFrameRate()}
     */
    public float getFrameRate();

    /**
     * @deprecated use {@link RenderContext#setContinuousRenderEnabled(boolean)}
     */
    public void setContinuousRenderEnabled(boolean enabled);

    /**
     * @deprecated use {@link RenderContext#isContinuousRenderEnabled()}
     */
    public boolean isContinuousRenderEnabled();
}
