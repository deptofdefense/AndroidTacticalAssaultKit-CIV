package com.atakmap.map.layer.opengl;

import com.atakmap.interop.InteropCleaner;
import com.atakmap.interop.NativePeerManager;
import com.atakmap.interop.Pointer;
import com.atakmap.lang.ref.Cleaner;
import com.atakmap.map.layer.Layer;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.util.Disposable;
import com.atakmap.util.ReadWriteLock;

import gov.tak.api.annotation.DontObfuscate;

@DontObfuscate
final class NativeGLLayer3 implements GLLayer3, Disposable {
    final static NativePeerManager.Cleaner CLEANER = new InteropCleaner(GLLayer3.class);

    final ReadWriteLock rwlock = new ReadWriteLock();
    private final Cleaner cleaner;
    Pointer pointer;
    Object owner;

    NativeGLLayer3(Pointer pointer, Object owner) {
        cleaner = NativePeerManager.register(this, pointer, rwlock, null, CLEANER);

        this.pointer = pointer;
        this.owner = owner;
    }

    @Override
    public void start() {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            start(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public void stop() {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            stop(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public Layer getSubject() {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            return getSubject(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public void draw(GLMapView view) {
        this.draw(view, GLMapView.RENDER_PASS_SPRITES|GLMapView.RENDER_PASS_SURFACE);
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
        if(cleaner != null)
            cleaner.clean();
    }

    /*************************************************************************/
    // Interop implementation

    static GLLayer3 create(Pointer pointer, Object owner) {
        return new NativeGLLayer3(pointer, owner);
    }

    static long getPointer(GLLayer3 managed) {
        if(managed instanceof NativeGLLayer3)
            return ((NativeGLLayer3)managed).pointer.raw;
        else
            return 0L;
    }

    // JNI implententation

    static native void start(long pointer);
    static native void stop(long pointer);
    static native Layer getSubject(long pointer);
    static native void draw(long pointer, GLMapView view, int renderPass);
    static native void release(long pointer);
    static native int getRenderPass(long pointer);
    static native void destruct(Pointer pointer);
}
