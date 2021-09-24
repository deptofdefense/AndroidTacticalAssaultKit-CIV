package com.atakmap.interop;

import com.atakmap.coremap.log.Log;

public final class InteropCleaner extends NativePeerManager.Cleaner {
    final com.atakmap.map.Interop interop;

    public InteropCleaner(Class<?> clazz) {
        this(com.atakmap.map.Interop.findInterop(clazz));
    }

    public InteropCleaner(com.atakmap.map.Interop interop) {
        if(interop == null)
            throw new IllegalArgumentException();
        this.interop = interop;
    }

    @Override
    protected void run(Pointer pointer, Object opaque) {
        if(opaque != null)
            Log.w("InteropCleaner", "Cleaning " + interop.getInteropClass() + ", non-null opaque provided");
        this.interop.destruct(pointer);
    }
}
