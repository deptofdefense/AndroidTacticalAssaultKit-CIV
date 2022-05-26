package com.atakmap.map.layer.opengl;

import android.util.Pair;

import com.atakmap.interop.InteropCleaner;
import com.atakmap.interop.NativePeerManager;
import com.atakmap.interop.Pointer;
import com.atakmap.lang.ref.Cleaner;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.layer.Layer;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.util.Disposable;
import com.atakmap.util.ReadWriteLock;

import gov.tak.api.annotation.DontObfuscate;

@DontObfuscate
final class NativeGLLayerSpi2 implements GLLayerSpi2, Disposable {
    final static NativePeerManager.Cleaner CLEANER = new InteropCleaner(GLLayerSpi2.class);

    final ReadWriteLock rwlock = new ReadWriteLock();
    private final Cleaner cleaner;
    Pointer pointer;
    Object object;

    NativeGLLayerSpi2(Pointer pointer, Object owner) {
        cleaner = NativePeerManager.register(this, pointer, rwlock, null, CLEANER);

        this.pointer = pointer;
        this.object = owner;
    }

    @Override
    public int getPriority() {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            return getPriority(this.pointer.raw);
        } finally {
            this.rwlock.acquireRead();
        }
    }

    @Override
    public GLLayer2 create(Pair<MapRenderer, Layer> object) {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            return create(this.pointer.raw, (GLMapView)object.first, object.second);
        } finally {
            this.rwlock.acquireRead();
        }
    }

    @Override
    public final void dispose() {
        if(this.cleaner != null)
            this.cleaner.clean();
    }

    /*************************************************************************/
    // Interop

    static native long getPointer(GLLayerSpi2 object);
    static native Pointer wrap(GLLayerSpi2 object);
    static native boolean hasPointer(GLLayerSpi2 object);
    static native GLLayerSpi2 create(Pointer pointer, Object ownerReference);
    static native boolean hasObject(long pointer);
    static native GLLayerSpi2 getObject(long pointer);
    //static Pointer clone(long otherRawPointer);
    static native void destruct(Pointer pointer);

    // JNI implementation

    static native int getPriority(long pointer);
    static native GLLayer2 create(long pointer, GLMapView view, Layer layer);
}
