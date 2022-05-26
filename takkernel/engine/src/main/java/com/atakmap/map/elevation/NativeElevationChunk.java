package com.atakmap.map.elevation;

import com.atakmap.interop.InteropCleaner;
import com.atakmap.interop.NativePeerManager;
import com.atakmap.interop.Pointer;
import com.atakmap.lang.ref.Cleaner;
import com.atakmap.map.Interop;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.geometry.Polygon;
import com.atakmap.map.layer.model.Mesh;
import com.atakmap.map.layer.model.ModelBuilder;
import com.atakmap.math.Matrix;
import com.atakmap.util.ReadWriteLock;

import gov.tak.api.annotation.DontObfuscate;

@DontObfuscate
final class NativeElevationChunk implements ElevationChunk {
    final static NativePeerManager.Cleaner CLEANER = new InteropCleaner(ElevationChunk.class);

    final static Interop<Geometry> Geometry_interop = Interop.findInterop(Geometry.class);
    final static Interop<Mesh> Mesh_interop = Interop.findInterop(Mesh.class);
    final static Interop<Matrix> Matrix_interop = Interop.findInterop(Matrix.class);

    final ReadWriteLock rwlock = new ReadWriteLock();
    private final Cleaner cleaner;
    Pointer pointer;
    Object owner;

    private NativeElevationChunk(Pointer pointer, Object owner) {
        this.cleaner = NativePeerManager.register(this, pointer, rwlock, null, CLEANER);

        this.pointer = pointer;
        this.owner = owner;
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
    public Polygon getBounds() {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            return (Polygon) Geometry_interop.create(Geometry_interop.clone(getBounds(this.pointer.raw)));
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public Data createData() {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();

            // XXX - cache data?
            Pointer cdata = null;
            try {
                cdata = createData(pointer.raw);
                if(cdata == null)
                    return null;

                Data data = new Data();
                data.localFrame = Matrix_interop.create(Matrix_interop.clone(Data_getLocalFrame(cdata.raw)));
                data.interpolated = Data_isInterpolated(cdata.raw);
                data.srid = Data_getSrid(cdata.raw);
                Pointer cdatavalue = Data_getValue(cdata.raw);
                if(cdatavalue != null)
                    data.value = ModelBuilder.build(Mesh_interop.create(cdatavalue));
                return data;
            } finally {
                if(cdata != null)
                    Data_destruct(cdata);
            }
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public double sample(double latitude, double longitude) {
        return 0;
    }

    @Override
    public boolean sample(double[] lla, int off, int len) {
        return false;
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
    public void dispose() {
        if(this.cleaner != null)
            this.cleaner.clean();
    }
    
    static ElevationChunk create(Pointer pointer, Object owner) {
        return new NativeElevationChunk(pointer, owner);
    }

    static native void destruct(Pointer pointer);

    static native double getResolution(long pointer);
    static native boolean isAuthoritative(long pointer);
    static native double getCE(long pointer);
    static native double getLE(long pointer);
    static native String getUri(long pointer);
    static native String getType(long pointer);
    static native long getBounds(long pointer);
    static native int getFlags(long pointer);

    static native Pointer createData(long pointer);

    static native void Data_destruct(Pointer pointer);
    static native long Data_getLocalFrame(long pointer);
    static native boolean Data_isInterpolated(long pointer);
    static native int Data_getSrid(long pointer);
    static native Pointer Data_getValue(long pointer);
}
