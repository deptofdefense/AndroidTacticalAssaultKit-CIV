
package com.atakmap.database.impl;

import com.atakmap.coremap.log.Log;
import com.atakmap.database.StatementIface;
import com.atakmap.interop.Pointer;
import com.atakmap.util.ReadWriteLock;

final class StatementImpl implements StatementIface {

    final ReadWriteLock rwlock = new ReadWriteLock();
    Pointer pointer;
    Object owner;

    StatementImpl(Pointer pointer, Object owner) {
        this.pointer = pointer;
        this.owner = owner;
    }

    @Override
    public void execute() {
        this.rwlock.acquireRead();
        try {
            if (this.pointer.raw == 0L)
                throw new IllegalStateException();
            execute(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public void close() {
        this.rwlock.acquireWrite();
        try {
            if (this.pointer.raw != 0L)
                destruct(this.pointer);
        } finally {
            this.rwlock.releaseWrite();
        }
    }

    @Override
    public void bind(int idx, byte[] value) {
        this.rwlock.acquireRead();
        try {
            if (this.pointer.raw == 0L)
                throw new IllegalStateException();
            bindBinary(this.pointer.raw, idx, value);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public void bind(int idx, int value) {
        this.rwlock.acquireRead();
        try {
            if (this.pointer.raw == 0L)
                throw new IllegalStateException();
            bindInt(this.pointer.raw, idx, value);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public void bind(int idx, long value) {
        this.rwlock.acquireRead();
        try {
            if (this.pointer.raw == 0L)
                throw new IllegalStateException();
            bindLong(this.pointer.raw, idx, value);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public void bind(int idx, double value) {
        this.rwlock.acquireRead();
        try {
            if (this.pointer.raw == 0L)
                throw new IllegalStateException();
            bindDouble(this.pointer.raw, idx, value);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public void bind(int idx, String value) {
        this.rwlock.acquireRead();
        try {
            if (this.pointer.raw == 0L)
                throw new IllegalStateException();
            bindString(this.pointer.raw, idx, value);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public void bindNull(int idx) {
        this.rwlock.acquireRead();
        try {
            if (this.pointer.raw == 0L)
                throw new IllegalStateException();
            bindNull(this.pointer.raw, idx);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public void clearBindings() {
        this.rwlock.acquireRead();
        try {
            if (this.pointer.raw == 0L)
                throw new IllegalStateException();
            clearBindings(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    protected final void finalize() {
        if (this.pointer.raw != 0L)
            Log.w("StatementImpl", "Statement leaked");
    }

    static native void destruct(Pointer pointer);

    static native void execute(long ptr);

    static native void bindBinary(long ptr, int idx, byte[] value);

    static native void bindInt(long ptr, int idx, int value);

    static native void bindLong(long ptr, int idx, long value);

    static native void bindDouble(long ptr, int idx, double value);

    static native void bindString(long ptr, int idx, String value);

    static native void bindNull(long ptr, int idx);

    static native void clearBindings(long ptr);
}
