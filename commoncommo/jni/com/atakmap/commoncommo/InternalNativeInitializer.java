package com.atakmap.commoncommo;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Internal use only class that handles automatic native initialization
 * during any native-using public class loading.
 * This is one of two possible current implementations, with only one built
 * in to the library.  Which is chosen depends on a build-time property.
 * See also ExternalNativeInitializer.java
 */
final class NativeInitializer {
    final static AtomicBoolean initialized = new AtomicBoolean(false);

    private NativeInitializer() {}

    static void initialize() {
        if (initialized.getAndSet(true))
            return;

        try {
            System.loadLibrary("commoncommojni");
        } catch (Throwable t) {
            System.err.println("Failed to load libcommoncommojni");
        }

        try {
            Commo.initThirdpartyNativeLibraries();
        } catch (CommoException e) {
            System.err.println("Failed to initialize native libraries");
        }
    }
}
