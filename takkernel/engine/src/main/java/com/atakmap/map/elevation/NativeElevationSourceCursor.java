package com.atakmap.map.elevation;

import com.atakmap.coremap.log.Log;
import com.atakmap.interop.Pointer;
import com.atakmap.map.Interop;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.util.ReadWriteLock;

import gov.tak.api.annotation.DontObfuscate;

@DontObfuscate
class NativeElevationSourceCursor implements ElevationSource.Cursor {

    final static Interop<Geometry> Geometry_interop = Interop.findInterop(Geometry.class);

    private final static String TAG = "NativeFeatureCursor";

    final ReadWriteLock rwlock = new ReadWriteLock();
    Pointer pointer;
    Object owner;

    NativeElevationSourceCursor(Pointer pointer, Object owner) {
        this.pointer = pointer;
        this.owner = owner;
    }

    @Override
    public ElevationChunk get() {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            return NativeElevationChunk.create(get(this.pointer.raw), null);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public double getResolution() {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            return getResolution(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public boolean isAuthoritative() {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            return isAuthoritative(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public double getCE() {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            return getCE(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public double getLE() {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            return getLE(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public String getUri() {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            return getUri(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public String getType() {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            return getType(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public Geometry getBounds() {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            return Geometry_interop.create(Geometry_interop.clone(getBounds(this.pointer.raw)));
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public int getFlags() {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            return getFlags(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public boolean moveToNext() {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            return moveToNext(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public void close() {
        this.rwlock.acquireWrite();
        try {
            if(this.pointer.raw != 0L)
                destruct(this.pointer);
        } finally {
            this.rwlock.releaseWrite();
        }
    }

    @Override
    public boolean isClosed() {
        this.rwlock.acquireRead();
        try {
            return (this.pointer.raw == 0L);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    protected void finalize() throws Throwable {
        if(this.pointer.raw != 0L)
            Log.w(TAG, "Native FeatureCursor leaked");
    }

    /*************************************************************************/

    static native void destruct(Pointer pointer);

    static native boolean moveToNext(long pointer);
    static native Pointer get(long pointer);
    static native double getResolution(long pointer);
    static native boolean isAuthoritative(long pointer);
    static native double getCE(long pointer);
    static native double getLE(long pointer);
    static native String getUri(long pointer);
    static native String getType(long pointer);
    static native long getBounds(long pointer);
    static native int getFlags(long pointer);
}
