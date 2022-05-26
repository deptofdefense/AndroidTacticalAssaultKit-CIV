package com.atakmap.map.layer.feature;

import com.atakmap.coremap.log.Log;
import com.atakmap.interop.Pointer;
import com.atakmap.util.ReadWriteLock;

import java.io.File;

import gov.tak.api.annotation.DontObfuscate;

@DontObfuscate
public class NativeFileCursor implements DataSourceFeatureDataStore.FileCursor {
    final ReadWriteLock rwlock = new ReadWriteLock();
    Pointer pointer;
    Object owner;

    NativeFileCursor(Pointer pointer, Object owner) {
        this.pointer = pointer;
        this.owner = owner;
    }
    @Override
    public File getFile() {
        this.rwlock.acquireRead();
        try {
            final String path = getFile(this.pointer.raw);
            return (path != null) ? new File(path) : null;
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public boolean moveToNext() {
        this.rwlock.acquireRead();
        try {
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
            return this.pointer.raw == 0L;
        } finally {
            this.rwlock.releaseRead();
        }
    }

    protected void finalize() {
        if(this.pointer.raw != 0L)
            Log.w("NativeFileCursor", "Leaking native cursor");
    }

    static native void destruct(Pointer pointer);
    static native String getFile(long ptr);
    static native boolean moveToNext(long ptr);
}
