package com.atakmap.map.layer.feature;

import com.atakmap.coremap.log.Log;
import com.atakmap.interop.Pointer;
import com.atakmap.map.layer.feature.AttributeSet;
import com.atakmap.map.layer.feature.Feature;
import com.atakmap.map.layer.feature.FeatureCursor;
import com.atakmap.map.layer.feature.FeatureDefinition2;
import com.atakmap.util.ReadWriteLock;

public class NativeFeatureCursor implements FeatureCursor, FeatureDefinition2 {
    private final static String TAG = "NativeFeatureCursor";

    final ReadWriteLock rwlock = new ReadWriteLock();
    Pointer pointer;
    Object owner;

    Exception allocStack;

    NativeFeatureCursor(Pointer pointer, Object owner) {
        this.pointer = pointer;

        try {
            throw new Exception();
        } catch(Exception e) {
            allocStack = e;
        }
    }

    @Override
    public Object getRawGeometry() {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            return getRawGeometry(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public int getGeomCoding() {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            final int cgeomCoding = getGeomCoding(this.pointer.raw);
            if (cgeomCoding == NativeFeatureDataSource.getFeatureDefinition_GeometryEncoding_GeomBlob())
                return FeatureDefinition.GEOM_SPATIALITE_BLOB;
            else if (cgeomCoding == NativeFeatureDataSource.getFeatureDefinition_GeometryEncoding_GeomGeom())
                return FeatureDefinition.GEOM_ATAK_GEOMETRY;
            else if (cgeomCoding == NativeFeatureDataSource.getFeatureDefinition_GeometryEncoding_GeomWkt())
                return FeatureDefinition.GEOM_WKT;
            else if (cgeomCoding == NativeFeatureDataSource.getFeatureDefinition_GeometryEncoding_GeomWkb())
                return FeatureDefinition.GEOM_WKB;
            else
                throw new IllegalStateException();
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
    public int getStyleCoding() {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            return getStyleCoding(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public Object getRawStyle() {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            return getRawStyle(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public AttributeSet getAttributes() {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            final Pointer cattrs = getAttributes(this.pointer.raw);
            if(cattrs == null)
                return null;
            return new AttributeSet(cattrs, this);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public Feature get() {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();

            return new Feature(getFsid(this.pointer.raw),
                    getId(this.pointer.raw),
                    getName(this.pointer.raw),
                    Feature.getGeometry(this),
                    Feature.getStyle(this),
                    this.getAttributes(),
                    getTimestamp(this.pointer.raw),
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
    public long getVersion() {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            return getVersion(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public long getFsid() {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            return getFsid(this.pointer.raw);
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
    public long getTimestamp() {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            return getTimestamp(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    protected void finalize() throws Throwable {
        if(this.pointer.raw != 0L)
            Log.w(TAG, "Native FeatureCursor leaked", allocStack);
    }

    static native void destruct(Pointer pointer);
    static native Object getRawGeometry(long ptr);
    static native int getGeomCoding(long ptr);
    static native String getName(long ptr);
    static native int getStyleCoding(long ptr);
    static native Object getRawStyle(long ptr);
    static native Pointer getAttributes(long ptr);
    static native long getId(long ptr);
    static native long getVersion(long ptr);
    static native long getFsid(long ptr);
    static native boolean moveToNext(long ptr);
    static native long getTimestamp(long ptr);
}
