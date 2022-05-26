package com.atakmap.map.formats.c3dt;

import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;

import com.atakmap.coremap.log.Log;
import com.atakmap.map.RenderContext;
import com.atakmap.map.RenderSurface;

final class ChildRenderContext implements RenderContext {
    MainRenderContext main;
    EGLContext textureContext;
    EGLDisplay display;
    EGLConfig eglConfig;
    EGLSurface surfaceForTextureLoad;

    Thread thread;

    void destroy() {
        EGL14.eglDestroyContext(display, textureContext);
        EGL14.eglDestroySurface(display, surfaceForTextureLoad);
    }

    @Override
    public boolean isRenderThread() {
        return (thread == Thread.currentThread());
    }

    @Override
    public void queueEvent(Runnable r) {
        main.queueEvent(r);
    }

    @Override
    public void requestRefresh() {
        main.requestRefresh();
    }

    @Override
    public void setFrameRate(float rate) {
        main.setFrameRate(rate);
    }

    @Override
    public float getFrameRate() {
        return main.getFrameRate();
    }

    @Override
    public void setContinuousRenderEnabled(boolean enabled) {
        main.setContinuousRenderEnabled(enabled);
    }

    @Override
    public boolean isContinuousRenderEnabled() {
        return main.isContinuousRenderEnabled();
    }

    @Override
    public boolean supportsChildContext() {
        return true;
    }

    @Override
    public RenderContext createChildContext() {
        return create(main);
    }

    @Override
    public void destroyChildContext(gov.tak.api.engine.map.RenderContext child) {
        main.destroyChildContext(child);
    }

    @Override
    public boolean isAttached() {
        return (thread != null);
    }

    @Override
    public boolean attach() {
        if(EGL14.eglMakeCurrent(display, surfaceForTextureLoad, surfaceForTextureLoad, textureContext))
            thread = Thread.currentThread();
        return (thread == Thread.currentThread());
    }

    @Override
    public boolean detach() {
        thread = null;
        return true;
    }

    @Override
    public boolean isMainContext() {
        return false;
    }

    @Override
    public RenderSurface getRenderSurface() {
        return null;
    }

    /*************************************************************************/


    public static RenderContext create(MainRenderContext main) {
        if(main.ctx == null)
            return null;

        final int EGL_OPENGL_ES3_BIT = 0x00000040;

        try {
            ChildRenderContext child = new ChildRenderContext();
            child.main = main;

            child.display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
            if(child.display == EGL14.EGL_NO_DISPLAY)
                return null;

            int[] configAttribs =
            {
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
                EGL14.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT,
                EGL14.EGL_NONE,
            };

    // Step 5 - Find a config that matches all requirements.
            int[] numConfigs = new int[1];
            EGLConfig[] configs = new EGLConfig[1];
            if(!EGL14.eglChooseConfig(child.display, configAttribs, 0, configs, 0, 1, numConfigs, 0))
                return null;

            child.eglConfig = configs[0];
            if(child.eglConfig == null)
                return null;

            // la versione usata è la 2!
            int[] attrib_list =
            {
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 3,
                EGL14.EGL_NONE
            };

            child.textureContext = EGL14.eglCreateContext(child.display, child.eglConfig, main.ctx, attrib_list, 0);

            if (child.textureContext == EGL14.EGL_NO_CONTEXT)
                return null;

            int pbufferAttribs[] =
            {
                EGL14.EGL_WIDTH, 1,
                EGL14.EGL_HEIGHT, 1,
                EGL14.EGL_TEXTURE_TARGET, EGL14.EGL_NO_TEXTURE,
                EGL14.EGL_TEXTURE_FORMAT, EGL14.EGL_NO_TEXTURE,
                EGL14.EGL_NONE
            };

            child.surfaceForTextureLoad = EGL14.eglCreatePbufferSurface(child.display, child.eglConfig, pbufferAttribs, 0);
            return child;
        } catch(Throwable t) {
            Log.w("ChildRenderContext", "Failed to create child context", t);
            return null;
        }
    }
}
