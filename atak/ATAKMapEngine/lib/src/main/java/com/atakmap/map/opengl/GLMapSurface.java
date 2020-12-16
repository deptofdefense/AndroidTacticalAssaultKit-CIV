
package com.atakmap.map.opengl;

import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import com.atakmap.android.maps.MapTextFormat;
import com.atakmap.coremap.concurrent.NamedThreadFactory;
import com.atakmap.map.AtakMapView;
import com.atakmap.map.AtakMapView.OnDisplayFlagsChangedListener;
import com.atakmap.map.RenderContext;
import com.atakmap.map.RenderSurface;
import com.atakmap.opengl.GLES20FixedPipeline;
import com.atakmap.android.maps.graphics.GLImageCache;
import com.atakmap.opengl.GLNinePatch;
import com.atakmap.opengl.GLText;

import com.atakmap.coremap.log.Log;
import com.atakmap.util.Collections2;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLDisplay;

public class GLMapSurface extends GLSurfaceView implements RenderContext, RenderSurface, OnDisplayFlagsChangedListener {

    public static String LOOKUP_TAG = "__GLMapSurfaceTag__";

    public static String TAG = "GLSurfaceView";

    private static Thread glThread;

    private static float lastDensity = AtakMapView.DENSITY;

    private int _getConfigAttr(EGL10 egl, EGLDisplay display, EGLConfig config, int attr) {
        int[] attr_value = {
                0
        };
        egl.eglGetConfigAttrib(display, config, attr, attr_value);
        return attr_value[0];
    }

    private void setConfigChooser() {
        this.setEGLConfigChooser(new EGLConfigChooser() {
            @Override
            public EGLConfig chooseConfig(EGL10 egl, EGLDisplay display) {
                int[] out_count = {
                        0
                };
                egl.eglGetConfigs(display, null, 0, out_count);

                int[] attrib_list = {
                        EGL10.EGL_BUFFER_SIZE, 16,
                        EGL10.EGL_DEPTH_SIZE, 8,
                        EGL10.EGL_STENCIL_SIZE, 1,
                        EGL10.EGL_NONE
                };

                egl.eglChooseConfig(display, attrib_list, null, 0, out_count);

                int count = out_count[0];
                EGLConfig[] configs = new EGLConfig[count];
                egl.eglChooseConfig(display, attrib_list, configs, configs.length, null);
                EGLConfig choice = null;

                for (int i = 0; i < configs.length; ++i) {
                    int bufferSize = _getConfigAttr(egl, display, configs[i], EGL10.EGL_BUFFER_SIZE);
                    int depthSize = _getConfigAttr(egl, display, configs[i], EGL10.EGL_DEPTH_SIZE);
                    int stencilSize = _getConfigAttr(egl, display, configs[i],
                            EGL10.EGL_STENCIL_SIZE);

                    Log.i(TAG, "OPENGL_CONFIG---------------------------");
                    Log.i(TAG, "bufferSize  =" + bufferSize);
                    Log.i(TAG, "depthSize   =" + depthSize);
                    Log.i(TAG, "stencilSize =" + stencilSize);

                    if (bufferSize >= 16 && depthSize >= 8 && stencilSize >= 1) {
                        choice = configs[0];
                    }
                }

                return choice;
            }
        });
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.v(TAG, "onPause()");

        tryToFreeUnusedMemory( );
    }

    /**
     * When called this will try to free any memory that is not being used. The goal of this
     * method is to reduce the memory footprint of the class, but it is not guaranteed to
     * free anything when the method is called.
     */
    public void tryToFreeUnusedMemory( )
    {
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.v(TAG, "onResume()");

    }

    public GLMapSurface(AtakMapView mapView, GLMapRenderer renderer) {
        super(mapView.getContext());

        _mapView = mapView;

        DisplayMetrics displayMetrics = new DisplayMetrics();
        WindowManager windowManager = (WindowManager) _mapView.getContext()
                .getSystemService(Context.WINDOW_SERVICE);
        windowManager.getDefaultDisplay().getMetrics(displayMetrics);

        _displayDpi = displayMetrics.densityDpi;

        setEGLContextClientVersion(3);

        // The EGLContext is not preserved on pause. In order to fix this, the rendering engine
        // was destroyed and recreated. If this proves reliable, we should be able to follow
        // the normal lifecylce onPause/onResume.
        // - reliable on the S3 and Note 1 International
        // - unreliable on the Note 1 Domestic
        // setPreserveEGLContextOnPause(preserveContext);

        // SHB: need to revisit this with actual imagery loaded on the device.
        try {

            if (System.getProperty("USE_GENERIC_EGL_CONFIG", "false").equals("true")) { 

                Log.d(TAG, "application has been informed that OPEN GL is a bit busted. falling back to generic EGL Configuration 8,8,8,16,0");
                setEGLConfigChooser(8, 8, 8, 8, 16, 0);

            } else { 

                Log.d(TAG, "application has been informed that OPEN GL is good. using EGL Configuration 5,6,5,0,8,8");
                setEGLConfigChooser(5, 6, 5, 0, 8, 8);
            }
        } catch (IllegalArgumentException e) {
            Log.e(TAG, e.getMessage() + "; trying another configSpec");
            // Required for EGL compatibility with the emulator, currently only supported on 2.3.3
            // ARM
            setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        }

        setTag(LOOKUP_TAG);

        this.glMapView = new GLMapView(this, this.getMapView().getGlobe(), 0, 0, GLMapView.MATCH_SURFACE, GLMapView.MATCH_SURFACE);
        renderer.setView(glMapView);

        setRenderer(_renderer = renderer);

        this.queueEvent(new Runnable() {
            @Override
            public void run() {
                GLMapSurface.glThread = Thread.currentThread();
            }
        });
        this.glMapView.setBaseMap(new GLBaseMap());
    }

    /*************************************************************************/
    // Render Context

    @Override
    public final boolean isRenderThread() {
        return isGLThread();
    }

    @Override
    public final void queueEvent(Runnable r) {
        super.queueEvent(r);
        // an event is queued, kick off a refresh
        this.requestRefresh();
    }

    @Override
    public final void requestRefresh() {
        // if we're not continuous rendering and we have the renderable,
        // request a render
        if(!isContinuousRenderEnabled() && this.glMapView != null)
            this.requestRender();
    }

    @Override
    public final void setFrameRate(float rate) {
        _renderer.setFrameRate(rate);
    }

    @Override
    public final float getFrameRate() {
        return (float)_renderer.getFramerate();
    }

    @Override
    public final void setContinuousRenderEnabled(boolean enabled) {
        this.continuousRenderEnabled = enabled;
        this.setRenderMode(this.continuousRenderEnabled ? GLMapSurface.RENDERMODE_CONTINUOUSLY : GLMapSurface.RENDERMODE_WHEN_DIRTY);
    }

    @Override
    public final boolean isContinuousRenderEnabled() {
        return this.continuousRenderEnabled;
    }

    @Override
    public final boolean supportsChildContext() {
        // XXX - not currently implemented
        return false;
    }

    @Override
    public final RenderContext createChildContext() {
        // XXX - not currently implemented
        return null;
    }

    @Override
    public final void destroyChildContext(RenderContext child) {
        // XXX - not currently implemented
    }

    @Override
    public final boolean isAttached() {
        // main context is always attached
        return true;
    }

    @Override
    public final boolean attach() {
        // main context may only be attached to main render thread
        return isRenderThread();
    }

    @Override
    public final boolean detach() {
        // main context may not be detached
        return false;
    }

    @Override
    public final boolean isMainContext() {
        // this is the main context
        return true;
    }

    @Override
    public RenderSurface getRenderSurface() {
        return this;
    }

    @Override
    public double getDpi() {
        return _displayDpi;
    }

    @Override
    public void addOnSizeChangedListener(OnSizeChangedListener l) {
        synchronized(_sizeChangedListeners) {
            _sizeChangedListeners.add(l);
        }
    }

    @Override
    public void removeOnSizeChangedListener(OnSizeChangedListener l) {
        synchronized(_sizeChangedListeners) {
            _sizeChangedListeners.remove(l);
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        synchronized(_sizeChangedListeners) {
            for(OnSizeChangedListener l : _sizeChangedListeners)
                l.onSizeChanged(this, (right-left), (bottom-top));
        }
    }

    /*************************************************************************/

    public static int getMaxTextureUnits() {
        return GLRenderGlobals.getMaxTextureUnits();
    }

    public static boolean isGLThread() {
        return (Thread.currentThread() == glThread);
    }

    private void orderlyRelease() {
        final GLMapView toRelease = glMapView;
        this.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        final boolean[] released = new boolean[] {(toRelease==null)};
        this.queueEvent(new Runnable() {
            @Override
            public void run() {
                if(toRelease != null)
                    toRelease.release();
                
                synchronized(released) {
                    released[0] = true;
                    released.notify();
                }
            }
        });
        
        synchronized(released) {
            while(!released[0]) {
                this.requestRender();
                try {
                    released.wait(100);
                } catch(InterruptedException ignored) {}
            }
        }
    }

    public void dispose() {
        Log.d(TAG, "dispose()");

        if (glMapView != null) {
            orderlyRelease();

            glMapView.dispose();
            glMapView = null;
        }

        GLRenderGlobals.dispose(this);

        if (_backgroundExecutor != null)
            _backgroundExecutor.shutdownNow();

        if (_backgroundMathExecutor != null)
            _backgroundMathExecutor.shutdownNow();

        setTag(LOOKUP_TAG + "disposed");

        _smallNinePatch = null;
        _mediumNinePatch = null;

        _renderer = null;
        _mapView = null;

    }

    public AtakMapView getMapView()
    {
        return _mapView;
    }

    public GLMapView getGLMapView() {
        return this.glMapView;
    }

    public GLMapRenderer getRenderer() {
        return _renderer;
    }

    synchronized public ExecutorService getBackgroundExecutor() {
        if (_backgroundExecutor == null) {
            _backgroundExecutor = Executors.newFixedThreadPool(1,
                    new NamedThreadFactory("BackgroundExecutor"));
        }
        return _backgroundExecutor;
    }

    synchronized public ExecutorService getBackgroundMathExecutor() {
        if (_backgroundMathExecutor == null) {
            _backgroundMathExecutor = Executors.newFixedThreadPool(1,
                    new NamedThreadFactory("BackgroundMathExecutor"));
        }
        return _backgroundMathExecutor;
    }

    @Override
    public void onDisplayFlagsChanged(AtakMapView view) {
        final int flags = view.getDisplayFlags();
        queueEvent(new Runnable() {
            @Override
            public void run() {
                SETTING_displayLabels = (flags & AtakMapView.DISPLAY_NO_LABELS) == 0;
                SETTING_shortenLabels = (flags & AtakMapView.DISPLAY_IGNORE_SHORT_LABELS) == 0;
                SETTING_limitTextureUnits = (flags & AtakMapView.DISPLAY_LIMIT_TEXTURE_UNITS) == AtakMapView.DISPLAY_LIMIT_TEXTURE_UNITS;
                SETTING_enableTextureTargetFBO = (flags & AtakMapView.DISABLE_TEXTURE_FBO) == 0;

                GLRenderGlobals.setLimitTextureUnits(SETTING_limitTextureUnits);
            }
        });
    }
    
    public void updateDisplayDensity() {
        queueEvent(new Runnable() {
            @Override
            public void run() {
                if(AtakMapView.DENSITY != lastDensity && glMapView != null) {
                    glMapView.release();
                    
                    // XXX - would be REALLY nice to keep this wholly on the
                    //       rendering side without any model invalidation
                    MapTextFormat.invalidate();

                    GLText.invalidate();
                    final GLRenderGlobals glbls = GLRenderGlobals.peek(GLMapSurface.this);
                    if(glbls != null)
                        glbls.getImageCache().release();
                    lastDensity = AtakMapView.DENSITY;
                }
            }
        });
    }

    /**
     * <B>READ-ONLY</B>
     * <P>
     * global setting for whether labels should be drawn
     */
    public static boolean SETTING_displayLabels = true;
    /**
     * <B>READ-ONLY</B>
     * <P>
     * global setting for whether labels should marquee
     */
    public static boolean SETTING_shortenLabels = true;
    /**
     * <B>READ-ONLY</B>
     * <P>
     * global setting for whether the number of texture units used should be limited to 2.
     */
    public static boolean SETTING_limitTextureUnits = false;
    
    /**
     * <B>READ-ONLY</B>
     * 
     * <P>global setting for whether FBOs with texture targets are enabled.
     */
    public static boolean SETTING_enableTextureTargetFBO = true;
    
    private ExecutorService _backgroundExecutor;
    private ExecutorService _backgroundMathExecutor;
    private GLMapRenderer _renderer;
    private GLImageCache.Entry _alertImageEntry;
    private AtakMapView _mapView;
    private GLMapView glMapView;
    private GLRenderGlobals orenderGlobals;
    private final double _displayDpi;
    private final Set<OnSizeChangedListener> _sizeChangedListeners = Collections2.newIdentityHashSet();

    private GLNinePatch _mediumNinePatch;
    private GLNinePatch _smallNinePatch;

    private boolean continuousRenderEnabled = true;
}
