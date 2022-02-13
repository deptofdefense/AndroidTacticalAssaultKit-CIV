package com.atakmap.map.formats.c3dt;

import android.opengl.EGL14;
import android.opengl.EGLContext;

import gov.tak.api.engine.map.RenderSurface;
import gov.tak.api.engine.map.RenderContext;
import com.atakmap.map.opengl.GLMapSurface;

final class MainRenderContext implements RenderContext {
    RenderContext impl;
    Boolean childContextSupported = null;
    EGLContext ctx;

    MainRenderContext(GLMapSurface impl) {
        this.impl = impl;
        if(isRenderThread())
            ctx = EGL14.eglGetCurrentContext();
        else
            queueEvent(new Runnable() {
                public void run() {
                    ctx = EGL14.eglGetCurrentContext();
                }
            });
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
        if(childContextSupported == null) {
            RenderContext child = null;
            try {
                child = ChildRenderContext.create(this);
                childContextSupported = Boolean.valueOf(child != null);
            } finally {
                if(child != null)
                    destroyChildContext(child);
            }
        }
        return childContextSupported.booleanValue();
    }
    public RenderContext createChildContext() {
        return ChildRenderContext.create(this);
    }
    public void destroyChildContext(RenderContext child) {
        if(child instanceof ChildRenderContext)
            ((ChildRenderContext)child).destroy();
    }
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
