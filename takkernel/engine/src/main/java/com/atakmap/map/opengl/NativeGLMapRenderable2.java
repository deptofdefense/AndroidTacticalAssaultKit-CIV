package com.atakmap.map.opengl;

import com.atakmap.interop.InteropCleaner;
import com.atakmap.interop.NativePeerManager;
import com.atakmap.interop.Pointer;
import com.atakmap.lang.ref.Cleaner;
import com.atakmap.util.Disposable;
import com.atakmap.util.ReadWriteLock;

import gov.tak.api.annotation.DontObfuscate;

@DontObfuscate
public class NativeGLMapRenderable2 implements GLMapRenderable2, Disposable {
    final static NativePeerManager.Cleaner CLEANER = new InteropCleaner(GLMapRenderable2.class);

    final ReadWriteLock rwlock = new ReadWriteLock();
    private final Cleaner cleaner;
    Pointer pointer;
    Object owner;

    NativeGLMapRenderable2(Pointer pointer, Object owner) {
        cleaner = NativePeerManager.register(this, pointer, rwlock, null, CLEANER);

        this.pointer = pointer;
        this.owner = owner;
    }

    @Override
    public void draw(GLMapView view, int renderPass) {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            draw(this.pointer.raw, view, renderPass);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public void release() {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            release(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public int getRenderPass() {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            return getRenderPass(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public final void dispose() {
        if(this.cleaner != null)
            this.cleaner.clean();
    }

    /*************************************************************************/
    // Interop implementation

    static GLMapRenderable2 create(Pointer pointer, Object owner) {
        return new NativeGLMapRenderable2(pointer, owner);
    }

    static long getPointer(GLMapRenderable2 managed) {
        if(managed instanceof NativeGLMapRenderable2)
            return ((NativeGLMapRenderable2)managed).pointer.raw;
        else
            return 0L;
    }

    // JNI implententation

    static native void draw(long pointer, GLMapView view, int renderPass);
    static native void release(long pointer);
    static native int getRenderPass(long pointer);
    static native void destruct(Pointer pointer);
}
