package gov.tak.examples.examplewindow;

import com.atakmap.map.RenderContext;
import com.atakmap.map.RenderSurface;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLRunnable;
import jogamp.opengl.GLAutoDrawableBase;

public class JOGLRenderContext implements RenderContext {
    GLAutoDrawable impl;
    RenderSurface surface;

    public JOGLRenderContext(GLAutoDrawable impl) {
        this.impl = impl;
        this.surface = JOGLRenderSurface.get(impl);
    }

    @Override
    public boolean isRenderThread() {
        return impl.isThreadGLCapable();
    }

    @Override
    public void queueEvent(Runnable r) {
        impl.invoke(false, new GLRunnable() {
            @Override
            public boolean run(GLAutoDrawable drawable) {
                r.run();
                return true;
            }
        });
    }

    @Override
    public void requestRefresh() {
        // XXX - it looks like JOGL either provides continuous render or
        //       delegates completely to client app for GL thread management
    }

    @Override
    public void setFrameRate(float rate) {
        // XXX - no control on framerate
    }

    @Override
    public float getFrameRate() {
        return 0;
    }

    @Override
    public void setContinuousRenderEnabled(boolean enabled) {
        // XXX - it looks like JOGL either provides continuous render or
        //       delegates completely to client app for GL thread management
    }

    @Override
    public boolean isContinuousRenderEnabled() {
        // XXX - it looks like JOGL either provides continuous render or
        //       delegates completely to client app for GL thread management
        return true;
    }

    @Override
    public boolean supportsChildContext() {
        // XXX - not yet implemented
        return false;
    }

    @Override
    public RenderContext createChildContext() {
        // XXX - not yet implemented
        return null;
    }

    @Override
    public void destroyChildContext(RenderContext child) {
        // XXX - not yet implemented
    }

    @Override
    public boolean isAttached() {
        return isRenderThread() && impl.getContext().isCurrent();
    }

    @Override
    public boolean attach() {
        if(!isRenderThread())
            return false;
        impl.getContext().makeCurrent();
        return impl.getContext().isCurrent();
    }

    @Override
    public boolean detach() {
        if(!isRenderThread())
            return false;
        if(impl.getContext().isCurrent())
            impl.getContext().release();;
        return isAttached();
    }

    @Override
    public boolean isMainContext() {
        return true;
    }

    @Override
    public RenderSurface getRenderSurface() {
        return this.surface;
    }
}
