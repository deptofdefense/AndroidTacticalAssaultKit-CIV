package gov.tak.platform.engine.map;

import com.atakmap.interop.Interop;
import com.atakmap.interop.NativePeerManager;
import com.atakmap.interop.Pointer;
import com.atakmap.util.ReadWriteLock;

import gov.tak.api.annotation.DontObfuscate;
import gov.tak.api.engine.map.RenderSurface;

@DontObfuscate
final class NativeRenderSurface implements RenderSurface {
    final static NativePeerManager.Cleaner CLEANER = new NativePeerManager.Cleaner() {
        @Override
        protected void run(Pointer pointer, Object opaque) {
            destruct(pointer);
        }
    };

    static {
        Interop.registerInterop(RenderSurface.class, new Interop<RenderSurface>() {
            @Override
            public long getPointer(RenderSurface obj) {
                return (obj instanceof NativeRenderSurface) ? ((NativeRenderSurface)obj).pointer.raw : null;
            }

            @Override
            public RenderSurface create(Pointer pointer, Object owner) {
                return new NativeRenderSurface(pointer, owner);
            }

            @Override
            public Pointer clone(long pointer) {
                return null;
            }

            @Override
            public Pointer wrap(RenderSurface object) {
                return null;
            }

            @Override
            public void destruct(Pointer pointer) {
                NativeRenderSurface.destruct(pointer);
            }

            @Override
            public boolean hasObject(long pointer) {
                return false;
            }

            @Override
            public RenderSurface getObject(long pointer) {
                return null;
            }

            @Override
            public boolean hasPointer(RenderSurface object) {
                return (object instanceof NativeRenderSurface);
            }

            @Override
            public boolean supportsWrap() {
                return false;
            }

            @Override
            public boolean supportsClone() {
                return false;
            }

            @Override
            public boolean supportsCreate() {
                return true;
            }
        });
    }

    final ReadWriteLock rwlock = new ReadWriteLock();
    final Pointer pointer;
    final Object owner;

    NativeRenderSurface(Pointer pointer, Object owner) {
        NativePeerManager.register(this, pointer, rwlock, null, CLEANER);
        this.pointer = pointer;
        this.owner = owner;
    }

    @Override
    public double getDpi() {
        this.rwlock.acquireRead();
        try {
            if(pointer.raw == 0L)   throw new IllegalStateException();
            return getDpi(pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public int getWidth() {
        this.rwlock.acquireRead();
        try {
            if(pointer.raw == 0L)   throw new IllegalStateException();
            return getWidth(pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public int getHeight() {
        this.rwlock.acquireRead();
        try {
            if(pointer.raw == 0L)   throw new IllegalStateException();
            return getHeight(pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public void addOnSizeChangedListener(OnSizeChangedListener l) {
        this.rwlock.acquireRead();
        try {
            if(pointer.raw == 0L)   throw new IllegalStateException();
            addOnSizeChangedListener(pointer.raw, l);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public void removeOnSizeChangedListener(OnSizeChangedListener l) {
        this.rwlock.acquireRead();
        try {
            if(pointer.raw == 0L)   throw new IllegalStateException();
            removeOnSizeChangedListener(pointer.raw, l);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    static native void destruct(Pointer pointer);
    static native double getDpi(long ptr);
    static native int getWidth(long ptr);
    static native int getHeight(long ptr);
    static native long addOnSizeChangedListener(long ptr, OnSizeChangedListener l);
    static native void removeOnSizeChangedListener(long ptr, OnSizeChangedListener l);
}