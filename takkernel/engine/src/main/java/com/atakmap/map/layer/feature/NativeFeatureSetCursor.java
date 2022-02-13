package com.atakmap.map.layer.feature;

import com.atakmap.coremap.log.Log;
import com.atakmap.interop.Pointer;
import com.atakmap.map.layer.feature.FeatureSet;
import com.atakmap.map.layer.feature.FeatureSetCursor;
import com.atakmap.util.ReadWriteLock;

import gov.tak.api.annotation.DontObfuscate;

@DontObfuscate
public class NativeFeatureSetCursor implements FeatureSetCursor {
    private final static String TAG = "NativeFeatureSetCursor";

    final ReadWriteLock rwlock = new ReadWriteLock();
    Pointer pointer;
    Object owner;

    NativeFeatureSetCursor(Pointer pointer, Object owner) {
        this.pointer = pointer;
        this.owner = owner;
    }

    @Override
    public FeatureSet get() {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            return new FeatureSet(getId(this.pointer.raw),
                                  getProvider(this.pointer.raw),
                                  getType(this.pointer.raw),
                                  getName(this.pointer.raw),
                                  getMinResolution(this.pointer.raw),
                                  getMaxResolution(this.pointer.raw),
                                  getVersion(this.pointer.raw));
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public long getId() {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            return getId(this.pointer.raw);
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
    public String getProvider() {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            return getProvider(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public String getName() {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            return getName(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public double getMinResolution() {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            return getMinResolution(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public double getMaxResolution() {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            return getMaxResolution(this.pointer.raw);
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
            Log.w(TAG, "Native FeatureSetCursor leaked");
    }

    static native void destruct(Pointer pointer);
    static native long getId(long ptr);
    static native String getType(long ptr);
    static native String getProvider(long ptr);
    static native String getName(long ptr);
    static native double getMinResolution(long ptr);
    static native double getMaxResolution(long ptr);
    static native long getVersion(long ptr);
    static native boolean moveToNext(long ptr);
}
