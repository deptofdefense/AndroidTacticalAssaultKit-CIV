package com.atakmap.map.layer.feature;

import com.atakmap.coremap.log.Log;
import com.atakmap.interop.NativePeerManager;
import com.atakmap.interop.Pointer;
import com.atakmap.lang.ref.Cleaner;
import com.atakmap.util.Disposable;
import com.atakmap.util.ReadWriteLock;

import java.io.File;
import java.io.IOException;

import gov.tak.api.annotation.DontObfuscate;

@DontObfuscate
public class NativeFeatureDataSource implements FeatureDataSource, Disposable {

    final static NativePeerManager.Cleaner CLEANER = new NativePeerManager.Cleaner() {
        @Override
        protected void run(Pointer pointer, Object opaque) {
            FeatureDataSource_destruct(pointer);
        }
    };

    final ReadWriteLock rwlock = new ReadWriteLock();
    private final Cleaner cleaner;
    Pointer pointer;

    protected NativeFeatureDataSource(Pointer pointer) {
        cleaner = NativePeerManager.register(this, pointer, rwlock, null, CLEANER);
        this.pointer = pointer;
    }

    @Override
    public final Content parse(File file) throws IOException {
        if(file == null)
            return null;
        final Pointer retval;
        this.rwlock.acquireRead();
        try {
            retval = FeatureDataSource_parse(this.pointer.raw, file.getAbsolutePath());
        } finally {
            this.rwlock.releaseRead();
        }

        if(retval == null)
            return null;

        return new NativeContent(retval);
    }

    @Override
    public final String getName() {
        this.rwlock.acquireRead();
        try {
            return FeatureDataSource_getName(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public final int parseVersion() {
        this.rwlock.acquireRead();
        try {
            return FeatureDataSource_parseVersion(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public final void dispose() {
        if(cleaner != null)
            cleaner.clean();
    }

    @DontObfuscate
    public final static class NativeContent implements FeatureDataSource.Content {
        final static String TAG = "NativeContent";

        final static NativePeerManager.Cleaner CLEANER = new NativePeerManager.Cleaner() {
            @Override
            protected void run(Pointer pointer, Object opaque) {
                Content_destruct(pointer);
            }
        };

        final ReadWriteLock rwlock = new ReadWriteLock();
        Pointer pointer;
        Cleaner cleaner;

        public NativeContent(Pointer pointer) {
            cleaner = NativePeerManager.register(this, pointer, rwlock, null, CLEANER);

            this.pointer = pointer;
        }

        @Override
        public String getType() {
            this.rwlock.acquireRead();
            try {
                return Content_getType(this.pointer.raw);
            } finally {
                this.rwlock.releaseRead();
            }
        }

        @Override
        public String getProvider() {
            this.rwlock.acquireRead();
            try {
                return Content_getProvider(this.pointer.raw);
            } finally {
                this.rwlock.releaseRead();
            }
        }

        @Override
        public boolean moveToNext(ContentPointer pointer) {
            switch(pointer) {
                case FEATURE:
                    this.rwlock.acquireRead();
                    try {
                        return Content_moveToNextFeature(this.pointer.raw);
                    } finally {
                        this.rwlock.releaseRead();
                    }
                case FEATURE_SET:
                    this.rwlock.acquireRead();
                    try {
                        return Content_moveToNextFeatureSet(this.pointer.raw);
                    } finally {
                        this.rwlock.releaseRead();
                    }
                default :
                    return false;
            }
        }

        @Override
        public FeatureDefinition get() {
            this.rwlock.acquireRead();
            try {
                final long cretval = Content_get(this.pointer.raw);
                if (cretval == 0L)
                    return null;
                FeatureDefinition retval = new FeatureDefinition();
                retval.name = FeatureDefinition_getName(cretval);
                Pointer cattr = FeatureDefinition_getAttributes(cretval);
                if (cattr != null)
                    retval.attributes = new AttributeSet(cattr, null);
                final int cgeomCoding = FeatureDefinition_getGeomCoding(cretval);
                if (cgeomCoding == getFeatureDefinition_GeometryEncoding_GeomBlob())
                    retval.geomCoding = FeatureDefinition.GEOM_SPATIALITE_BLOB;
                else if (cgeomCoding == getFeatureDefinition_GeometryEncoding_GeomGeom())
                    retval.geomCoding = FeatureDefinition.GEOM_ATAK_GEOMETRY;
                else if (cgeomCoding == getFeatureDefinition_GeometryEncoding_GeomWkt())
                    retval.geomCoding = FeatureDefinition.GEOM_WKT;
                else if (cgeomCoding == getFeatureDefinition_GeometryEncoding_GeomWkb())
                    retval.geomCoding = FeatureDefinition.GEOM_WKB;
                else
                    throw new IllegalStateException();
                retval.rawGeom = FeatureDefinition_getRawGeometry(cretval);
                if (retval.rawGeom instanceof Pointer)
                    retval.rawGeom = Interop.createGeometry((Pointer) retval.rawGeom, null);
                final int cstyleCoding = FeatureDefinition_getStyleCoding(cretval);
                if (cstyleCoding == getFeatureDefinition_StyleEncoding_StyleOgr())
                    retval.styleCoding = FeatureDefinition.STYLE_OGR;
                else if (cstyleCoding == getFeatureDefinition_StyleEncoding_StyleStyle())
                    retval.styleCoding = FeatureDefinition.STYLE_ATAK_STYLE;
                else
                    throw new IllegalStateException();
                retval.rawStyle = FeatureDefinition_getRawStyle(cretval);
                if (retval.rawStyle instanceof Pointer)
                    retval.rawStyle = Interop.createStyle((Pointer) retval.rawStyle, null);

                return retval;
            } finally {
                this.rwlock.releaseRead();
            }
        }

        @Override
        public String getFeatureSetName() {
            this.rwlock.acquireRead();
            try {
                return Content_getFeatureSetName(this.pointer.raw);
            } finally {
                this.rwlock.releaseRead();
            }
        }

        @Override
        public double getMinResolution() {
            this.rwlock.acquireRead();
            try {
                return Content_getMinResolution(this.pointer.raw);
            } finally {
                this.rwlock.releaseRead();
            }
        }

        @Override
        public double getMaxResolution() {
            this.rwlock.acquireRead();
            try {
                return Content_getMaxResolution(this.pointer.raw);
            } finally {
                this.rwlock.releaseRead();
            }
        }

        @Override
        public void close() {
            if(cleaner != null)
                cleaner.clean();
        }

        @Override
        public void finalize() {
            if(this.pointer.raw != 0L)
                Log.w(TAG, "Native FeatureDataSource.Content leaked");
        }
    }

    /*************************************************************************/

    /**
     * Wraps a Java <code>FeatureDataSource</code> with an SDK implementation.
     *
     * @param managed
     * @return
     */
    static native Pointer wrap(FeatureDataSource managed);

    static native void FeatureDataSource_destruct(Pointer pointer);
    static native Pointer FeatureDataSource_parse(long ptr, String path);
    static native String FeatureDataSource_getName(long ptr);
    static native int FeatureDataSource_parseVersion(long ptr);

    static native void Content_destruct(Pointer pointer);
    static native String Content_getType(long ptr);
    static native String Content_getProvider(long ptr);
    static native boolean Content_moveToNextFeature(long ptr);
    static native boolean Content_moveToNextFeatureSet(long ptr);
    static native long Content_get(long ptr);
    static native String Content_getFeatureSetName(long ptr);
    static native double Content_getMinResolution(long ptr);
    static native double Content_getMaxResolution(long ptr);

    static native Object FeatureDefinition_getRawGeometry(long ptr);
    static native int FeatureDefinition_getGeomCoding(long ptr);
    static native String FeatureDefinition_getName(long ptr);
    static native int FeatureDefinition_getStyleCoding(long ptr);
    static native Object FeatureDefinition_getRawStyle(long ptr);
    static native Pointer FeatureDefinition_getAttributes(long ptr);

    static native void FeatureDataSourceContentFactory_register(Pointer pointer, int priority);
    static native void FeatureDataSourceContentFactory_unregister(Pointer pointer);
    static native Pointer FeatureDataSourceContentFactory_getProvider(String name);
    static native Pointer FeatureDataSourceContentFactory_parse(String path, String hint);

    static native int getFeatureDefinition_GeometryEncoding_GeomWkt();
    static native int getFeatureDefinition_GeometryEncoding_GeomWkb();
    static native int getFeatureDefinition_GeometryEncoding_GeomBlob();
    static native int getFeatureDefinition_GeometryEncoding_GeomGeom();

    static native int getFeatureDefinition_StyleEncoding_StyleOgr();
    static native int getFeatureDefinition_StyleEncoding_StyleStyle();
}
