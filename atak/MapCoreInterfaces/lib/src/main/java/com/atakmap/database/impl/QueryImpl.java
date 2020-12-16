
package com.atakmap.database.impl;

import com.atakmap.coremap.log.Log;
import com.atakmap.database.QueryIface;
import com.atakmap.interop.Pointer;
import com.atakmap.util.ReadWriteLock;

final class QueryImpl implements QueryIface {
    final ReadWriteLock rwlock = new ReadWriteLock();
    Pointer pointer;
    Object owner;

    QueryImpl(Pointer pointer, Object owner) {
        this.pointer = pointer;
        this.owner = owner;
    }

    @Override
    public void reset() {
        // XXX -
        if (true) {
            clearBindings();
            return;
        }

        this.rwlock.acquireRead();
        try {
            if (this.pointer.raw == 0L)
                throw new IllegalStateException();
            reset(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
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
    public int getColumnIndex(String columnName) {
        this.rwlock.acquireRead();
        try {
            if (this.pointer.raw == 0L)
                throw new IllegalStateException();
            return getColumnIndex(this.pointer.raw, columnName);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public String getColumnName(int columnIndex) {
        this.rwlock.acquireRead();
        try {
            if (this.pointer.raw == 0L)
                throw new IllegalStateException();
            return getColumnName(this.pointer.raw, columnIndex);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public String[] getColumnNames() {
        this.rwlock.acquireRead();
        try {
            if (this.pointer.raw == 0L)
                throw new IllegalStateException();
            return getColumnNames(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public int getColumnCount() {
        this.rwlock.acquireRead();
        try {
            if (this.pointer.raw == 0L)
                throw new IllegalStateException();
            return getColumnCount(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public byte[] getBlob(int columnIndex) {
        this.rwlock.acquireRead();
        try {
            if (this.pointer.raw == 0L)
                throw new IllegalStateException();
            return getBlob(this.pointer.raw, columnIndex);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public String getString(int columnIndex) {
        this.rwlock.acquireRead();
        try {
            if (this.pointer.raw == 0L)
                throw new IllegalStateException();
            return getString(this.pointer.raw, columnIndex);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public int getInt(int columnIndex) {
        this.rwlock.acquireRead();
        try {
            if (this.pointer.raw == 0L)
                throw new IllegalStateException();
            return getInt(this.pointer.raw, columnIndex);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public long getLong(int columnIndex) {
        this.rwlock.acquireRead();
        try {
            if (this.pointer.raw == 0L)
                throw new IllegalStateException();
            return getLong(this.pointer.raw, columnIndex);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public double getDouble(int columnIndex) {
        this.rwlock.acquireRead();
        try {
            if (this.pointer.raw == 0L)
                throw new IllegalStateException();
            return getDouble(this.pointer.raw, columnIndex);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public int getType(int columnIndex) {
        this.rwlock.acquireRead();
        try {
            if (this.pointer.raw == 0L)
                throw new IllegalStateException();
            final int ctype = getType(this.pointer.raw, columnIndex);
            if (ctype == getFieldType_TEFTBlob())
                return FIELD_TYPE_BLOB;
            else if (ctype == getFieldType_TEFTFloat())
                return FIELD_TYPE_FLOAT;
            else if (ctype == getFieldType_TEFTInteger())
                return FIELD_TYPE_INTEGER;
            else if (ctype == getFieldType_TEFTNull())
                return FIELD_TYPE_NULL;
            else if (ctype == getFieldType_TEFTString())
                return FIELD_TYPE_STRING;
            else
                throw new IllegalStateException();
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public boolean isNull(int columnIndex) {
        this.rwlock.acquireRead();
        try {
            if (this.pointer.raw == 0L)
                throw new IllegalStateException();
            return isNull(this.pointer.raw, columnIndex);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public boolean moveToNext() {
        this.rwlock.acquireRead();
        try {
            if (this.pointer.raw == 0L)
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
            if (this.pointer.raw != 0L)
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
    protected final void finalize() {
        if (this.pointer.raw != 0L)
            Log.w("QueryImpl", "Cursor leaked");
    }

    static native void destruct(Pointer pointer);

    static native void reset(long ptr);

    static native void bindBinary(long ptr, int idx, byte[] value);

    static native void bindInt(long ptr, int idx, int value);

    static native void bindLong(long ptr, int idx, long value);

    static native void bindDouble(long ptr, int idx, double value);

    static native void bindString(long ptr, int idx, String value);

    static native void bindNull(long ptr, int idx);

    static native void clearBindings(long ptr);

    static native int getColumnIndex(long ptr, String columnName);

    static native String getColumnName(long ptr, int columnIndex);

    static native String[] getColumnNames(long ptr);

    static native int getColumnCount(long ptr);

    static native byte[] getBlob(long ptr, int columnIndex);

    static native String getString(long ptr, int columnIndex);

    static native int getInt(long ptr, int columnIndex);

    static native long getLong(long ptr, int columnIndex);

    static native double getDouble(long ptr, int columnIndex);

    static native int getType(long ptr, int columnIndex);

    static native boolean isNull(long ptr, int columnIndex);

    static native boolean moveToNext(long ptr);

    static native int getFieldType_TEFTBlob();

    static native int getFieldType_TEFTNull();

    static native int getFieldType_TEFTString();

    static native int getFieldType_TEFTInteger();

    static native int getFieldType_TEFTFloat();
}
