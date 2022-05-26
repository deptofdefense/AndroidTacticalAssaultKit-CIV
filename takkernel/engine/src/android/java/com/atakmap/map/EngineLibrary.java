
package com.atakmap.map;

import com.atakmap.coremap.log.Log;
import com.atakmap.map.opengl.GLMapRenderer;
import com.atakmap.map.opengl.GLMapSurface;
import com.atakmap.map.opengl.GLMapView;

import java.util.concurrent.atomic.AtomicBoolean;

import gov.tak.api.annotation.DontObfuscate;
import gov.tak.api.engine.map.IGlobe;
import gov.tak.api.engine.map.IMapRendererSpi;
import gov.tak.api.engine.map.IRenderContextSpi;
import gov.tak.api.engine.map.MapRenderer;
import gov.tak.api.engine.map.MapRendererFactory;
import gov.tak.api.engine.map.RenderContextFactory;

@DontObfuscate
public final class EngineLibrary {
    private final static AtomicBoolean initialized = new AtomicBoolean(false);

    static {
        RenderContextFactory.registerSpi(new IRenderContextSpi() {
            @Override
            public gov.tak.api.engine.map.RenderContext create(Object parent) {
                if(parent instanceof AtakMapView)
                    return ((AtakMapView)parent).getGLSurface();
                return null;
            }
        });
        MapRendererFactory.registerSpi(new IMapRendererSpi() {
            @Override
            public MapRenderer create(IGlobe globe, Object parent, Class<?> surfaceType) {
                // check if `surfaceType` is same or superclass of `GLMapSurface`
                if(!surfaceType.isAssignableFrom(GLMapSurface.class)) return null;

                // construct `GLMapSurface` as context/render surface and `GLMapView` as renderer
                if(parent instanceof AtakMapView && globe instanceof Globe) {
                    final GLMapSurface context = new GLMapSurface((AtakMapView)parent, new GLMapRenderer());
                    GLMapView mapView = new GLMapView(context, (Globe)globe, 0,0, GLMapView.MATCH_SURFACE, GLMapView.MATCH_SURFACE);
                    return LegacyAdapters.adapt(mapView);
                }
                return null;
            }
        });
    }

    private EngineLibrary() {
    }

    /**
     * This method must be invoked prior to calling any other methods in the
     * engine library.
     */
    public static void initialize() {
        if (initialized.getAndSet(true))
            return;

        try {
            // explicitly load libgdal as gdal.AllRegister only loads the
            // JNI libraries
            try {
                com.atakmap.coremap.loader.NativeLoader
                        .loadLibrary("gnustl_shared");
            } catch (UnsatisfiedLinkError ignored) {
            }

            try {
                com.atakmap.coremap.loader.NativeLoader.loadLibrary("ltidsdk");
            } catch (UnsatisfiedLinkError ignored) {
            }

            // force load/init of sqlite/spatialite
            //jsqlite.Database.julian_from_long(0L);

            com.atakmap.coremap.loader.NativeLoader.loadLibrary("charset");
            com.atakmap.coremap.loader.NativeLoader.loadLibrary("iconv");
            com.atakmap.coremap.loader.NativeLoader.loadLibrary("spatialite");
            com.atakmap.coremap.loader.NativeLoader.loadLibrary("proj");
            com.atakmap.coremap.loader.NativeLoader.loadLibrary("gdal");
            com.atakmap.coremap.loader.NativeLoader.loadLibrary("las");
            com.atakmap.coremap.loader.NativeLoader.loadLibrary("las_c");
            com.atakmap.coremap.loader.NativeLoader.loadLibrary("takengine");
            com.atakmap.coremap.loader.NativeLoader.loadLibrary("takenginejni");
        } catch (Throwable t) {
            Log.w("EngineLibrary", "Failed to load native libraries", t);
        }
    }

    /**
     * This method should be invoked when the application is exiting. No
     * methods in the engine library may be called after this method is
     * invoked.
     *
     * <P>Note that invocation of this method is not strictly required for
     * Android runtimes, but should always be called for JRE runtimes.
     */
    public static void shutdown() {
        // Note: delegating to separate method to afford some flexibility if
        // additional managed shutdown hooks are found to be necessary without
        // requiring modification to JNI code
        shutdownImpl();
    }

    private static native void shutdownImpl();
}
