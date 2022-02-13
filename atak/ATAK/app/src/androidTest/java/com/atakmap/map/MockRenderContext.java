
package com.atakmap.map;

public final class MockRenderContext implements RenderContext {
    float frameRate = 60f;
    boolean continuousRender;
    RenderSurface surface;

    public MockRenderContext(RenderSurface surface) {
        this.surface = surface;
    }

    @Override
    public boolean isRenderThread() {
        return true;
    }

    @Override
    public void queueEvent(Runnable r) {
        r.run();
    }

    @Override
    public void requestRefresh() {
        // no-op
    }

    @Override
    public void setFrameRate(float rate) {
        this.frameRate = rate;
    }

    @Override
    public float getFrameRate() {
        return this.frameRate;
    }

    @Override
    public void setContinuousRenderEnabled(boolean enabled) {
        this.continuousRender = enabled;
    }

    @Override
    public boolean isContinuousRenderEnabled() {
        return this.continuousRender;
    }

    @Override
    public boolean supportsChildContext() {
        return false;
    }

    @Override
    public RenderContext createChildContext() {
        return null;
    }

    @Override
    public boolean isAttached() {
        return true;
    }

    @Override
    public boolean attach() {
        return false;
    }

    @Override
    public boolean detach() {
        return false;
    }

    @Override
    public boolean isMainContext() {
        return true;
    }

    @Override
    public RenderSurface getRenderSurface() {
        return surface;
    }

    @Override
    public void destroyChildContext(
            gov.tak.api.engine.map.RenderContext child) {

    }
}
