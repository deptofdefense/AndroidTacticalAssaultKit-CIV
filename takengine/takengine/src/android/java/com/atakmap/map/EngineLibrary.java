
package com.atakmap.map;

import com.atakmap.coremap.log.Log;

import java.util.concurrent.atomic.AtomicBoolean;

public final class EngineLibrary {
    private final static AtomicBoolean initialized = new AtomicBoolean(false);

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
            com.atakmap.coremap.loader.NativeLoader.loadLibrary("takengine");
            com.atakmap.coremap.loader.NativeLoader.loadLibrary("atakjni");
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
