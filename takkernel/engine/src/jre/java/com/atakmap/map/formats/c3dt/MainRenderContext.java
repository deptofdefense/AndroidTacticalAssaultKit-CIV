package com.atakmap.map.formats.c3dt;

import gov.tak.api.engine.map.RenderContext;
import gov.tak.api.engine.map.RenderSurface;
import com.atakmap.map.opengl.GLMapSurface;

final class MainRenderContext implements RenderContext {
    RenderContext impl;
    Boolean childContextSupported = null;

    MainRenderContext(GLMapSurface impl) {
        this.impl = impl;
    }
    public boolean isRenderThread() {
        return impl.isRenderThread();
    }
    public void queueEvent(Runnable r) {
        impl.queueEvent(r);
    }
    public void requestRefresh() {
        impl.requestRefresh();
    }
    public void setFrameRate(float rate) {
        impl.setFrameRate(rate);
    }
    public float getFrameRate() {
        return impl.getFrameRate();
    }
    public void setContinuousRenderEnabled(boolean enabled) {
        impl.setContinuousRenderEnabled(enabled);
    }
    public boolean isContinuousRenderEnabled() {
        return isContinuousRenderEnabled();
    }
    public synchronized boolean supportsChildContext() {
        return false;
    }
    public RenderContext createChildContext() {
        return null;
    }
    public void destroyChildContext(RenderContext child) {}
    public boolean isAttached() {
        return true;
    }
    public boolean attach() {
        return isRenderThread();
    }
    public boolean detach() {
        return false;
    }
    public boolean isMainContext() {
        return true;
    }

    @Override
    public RenderSurface getRenderSurface() {
        return impl.getRenderSurface();
    }
}
