package gov.tak.platform.engine;

import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLEventListener;
import javax.media.opengl.GLRunnable;

import com.atakmap.interop.Interop;
import com.atakmap.interop.InteropCleaner;
import com.atakmap.interop.NativePeerManager;
import com.atakmap.interop.Pointer;
import gov.tak.api.engine.map.IRenderContextSpi;
import gov.tak.api.engine.map.RenderContext;
import gov.tak.api.engine.map.RenderContextFactory;
import gov.tak.api.engine.map.RenderSurface;
import gov.tak.platform.commons.opengl.JOGLGLES;

/**
 * JOGL implementation of the takkernel RenderContext
 *
 * @since 3.0
 */
public final class JOGLRenderContext implements RenderContext
{
    final static Interop<RenderContext> RenderContext_interop = Interop.findInterop(RenderContext.class);
    final static NativePeerManager.Cleaner WRAPPER_CLEANER = new InteropCleaner(RenderContext.class);

    GLAutoDrawable impl;
    RenderSurface surface;
    Thread glThread;

    /**
     * Construct with a GLAutoDrawable
     *
     * @param impl GLAutoDrawable to construct with
     */
    public JOGLRenderContext(GLAutoDrawable impl)
    {
        final Pointer wrapped = RenderContext_interop.wrap(this);
        if(wrapped != null)
            NativePeerManager.register(this, wrapped, null, null, WRAPPER_CLEANER);

        JOGLGLES.init(impl);
        impl.addGLEventListener(0, new GLEventListener()
        {
            @Override
            public void init(GLAutoDrawable drawable) {}

            @Override
            public void dispose(GLAutoDrawable drawable) {}

            @Override
            public void display(GLAutoDrawable drawable) {
                JOGLGLES.init(impl);
            }

            @Override
            public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {}
        });
        this.impl = impl;
        this.surface = JOGLRenderSurface.get(impl);
    }

    /**
     * @return is this the render thread
     */
    @Override
    public synchronized boolean isRenderThread()
    {
        // XXX - added JOGL 2.2
        //return impl.isThreadGLCapable();
        return (Thread.currentThread() == glThread);
    }

    private synchronized void updateGLThread() {
        if (glThread == null)
            glThread = Thread.currentThread();
    }

    /**
     * queue event on the render thread
     *
     * @param r runnable to queue
     */
    @Override
    public void queueEvent(Runnable r)
    {
        // XXX-- this call can block the thread for an amount of time that can impact
        //       performance. A faster queue may be desired in the future.
        impl.invoke(false, new GLRunnable() {
            @Override
            public boolean run(GLAutoDrawable drawable) {
                updateGLThread();
                r.run();
                return true;
            }
        });
    }

    /**
     * unsupported
     */
    @Override
    public void requestRefresh()
    {
        // XXX - it looks like JOGL either provides continuous render or
        //       delegates completely to client app for GL thread management
    }

    /**
     * unsupported
     */
    @Override
    public void setFrameRate(float rate)
    {
        // XXX - no control on framerate
    }

    /**
     * unsupported
     *
     * @return
     */
    @Override
    public float getFrameRate()
    {
        return 0;
    }

    /**
     * unsupported
     */
    @Override
    public void setContinuousRenderEnabled(boolean enabled)
    {
        // XXX - it looks like JOGL either provides continuous render or
        //       delegates completely to client app for GL thread management
    }

    /**
     * unsupported
     */
    @Override
    public boolean isContinuousRenderEnabled()
    {
        // XXX - it looks like JOGL either provides continuous render or
        //       delegates completely to client app for GL thread management
        return true;
    }

    /**
     * unsupported
     *
     * @return false
     */
    @Override
    public boolean supportsChildContext()
    {
        // XXX - not yet implemented
        return false;
    }

    /**
     * unsupported
     *
     * @return null
     */
    @Override
    public RenderContext createChildContext()
    {
        // XXX - not yet implemented
        return null;
    }

    /**
     * unsupported
     */
    @Override
    public void destroyChildContext(RenderContext child)
    {
        // XXX - not yet implemented
    }

    /**
     * is this RenderContext associated with current thread / GLContext
     *
     * @return
     */
    @Override
    public boolean isAttached()
    {
        return isRenderThread() && impl.getContext().isCurrent();
    }

    /**
     * If this is the render thread, make this context current
     *
     * @return successful or not
     */
    @Override
    public boolean attach()
    {
        if (!isRenderThread())
        {
            return false;
        }
        impl.getContext().makeCurrent();
        return impl.getContext().isCurrent();
    }

    /**
     * release the GLContext if current
     *
     * @return true if successful
     */
    @Override
    public boolean detach()
    {
        if (!isRenderThread())
        {
            return false;
        }
        if (impl.getContext().isCurrent())
        {
            impl.getContext().release();
        }
        ;
        return isAttached();
    }

    /**
     * unsupported
     */
    @Override
    public boolean isMainContext()
    {
        return true;
    }

    /**
     * get associated RenderSurface
     *
     * @return RenderSurface
     */
    @Override
    public RenderSurface getRenderSurface()
    {
        return this.surface;
    }
}
