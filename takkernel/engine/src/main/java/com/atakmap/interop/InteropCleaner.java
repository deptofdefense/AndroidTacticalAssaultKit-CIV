package com.atakmap.interop;

import com.atakmap.coremap.log.Log;

import gov.tak.api.annotation.DeprecatedApi;
import gov.tak.platform.marshal.MarshalManager;

public final class InteropCleaner extends NativePeerManager.Cleaner {
    final Interop interop;
    final Class<?> interopClass;

    public InteropCleaner(Class<?> clazz) {
        this(Interop.findInterop(clazz), clazz);
    }

    /** @deprecated use {@link #InteropCleaner(Interop)} */
    @Deprecated
    @DeprecatedApi(since = "4.4", forRemoval = true, removeAt = "4.7")
    public InteropCleaner(com.atakmap.map.Interop interop) {
        this(MarshalManager.marshal(interop, com.atakmap.map.Interop.class, Interop.class), interop.getInteropClass());
    }

    public InteropCleaner(Interop interop) {
        this(interop, null);
    }

    private InteropCleaner(Interop interop, Class<?> interopClass) {
        if(interop == null)
            throw new IllegalArgumentException();
        this.interop = interop;
        this.interopClass = interopClass;
    }

    @Override
    protected void run(Pointer pointer, Object opaque) {
        if(opaque != null)
            Log.w("InteropCleaner", "Cleaning " + interopClass + ", non-null opaque provided");
        this.interop.destruct(pointer);
    }
}
