package gov.tak.platform.engine.map;

import com.atakmap.interop.NativePeerManager;
import com.atakmap.interop.Pointer;
import com.atakmap.util.ReadWriteLock;

import gov.tak.api.annotation.DontObfuscate;
import gov.tak.api.engine.map.RenderSurface;

@DontObfuscate
final class NativeRenderSurfaceSizeChangedListener implements RenderSurface.OnSizeChangedListener {
    final static NativePeerManager.Cleaner CLEANER = new NativePeerManager.Cleaner() {
        @Override
        protected void run(Pointer pointer, Object opaque) {
            destruct(pointer);
        }
    };

    long surfacePtr;
    Pointer pointer;
    final ReadWriteLock rwlock = new ReadWriteLock();

    NativeRenderSurfaceSizeChangedListener(long surfacePtr, Pointer pointer) {
        NativePeerManager.register(this, this.pointer, rwlock, null, CLEANER);
        this.surfacePtr = surfacePtr;
        this.pointer = pointer;
    }

    @Override
    public void onSizeChanged(RenderSurface surface, int width, int height) {
        rwlock.acquireRead();
        try {
            if(pointer.raw != 0L)
                onSizeChanged(surfacePtr, pointer.raw, width, height);
        } finally {
            rwlock.releaseRead();
        }
    }

    static native void destruct(Pointer pointer);
    static native void onSizeChanged(long surfacePtr, long callbackPtr, int width, int height);
}
