package com.atakmap.commoncommo;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Internal use only class that handles automatic native initialization
 * during any native-using public class loading.
 * This is one of two possible current implementations, with only one built
 * in to the library.  Which is chosen depends on a build-time property.
 * See also InternalNativeInitializer.java
 */
final class NativeInitializer {
    private NativeInitializer() {}

    static void initialize() {
        // no-op; initialization is reserved to client application
    }
}
