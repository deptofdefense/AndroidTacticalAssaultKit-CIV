package com.atakmap.map.projection;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.interop.InteropCleaner;
import com.atakmap.interop.NativePeerManager;
import com.atakmap.interop.Pointer;
import com.atakmap.math.PointD;
import com.atakmap.util.ReadWriteLock;

import gov.tak.api.annotation.DontObfuscate;

@DontObfuscate
final class NativeProjection extends AbstractProjection {
    final static NativePeerManager.Cleaner CLEANER = new InteropCleaner(Projection.class);

    final ReadWriteLock rwlock = new ReadWriteLock();
    Pointer pointer;
    Object owner;

    private NativeProjection(Pointer pointer) {
        this(pointer, null);
    }

    NativeProjection(Pointer pointer, Object owner) {
        super(getSrid(pointer.raw), is3D(pointer.raw));

        NativePeerManager.register(this, pointer, this.rwlock, null, CLEANER);

        this.pointer = pointer;
        this.owner = owner;
    }

    @Override
    protected void forwardImpl(GeoPoint g, PointD p) {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            final boolean hae = (g.getAltitudeReference() == GeoPoint.AltitudeReference.HAE);
            forward(this.pointer.raw, g.getLatitude(), g.getLongitude(), hae ? g.getAltitude() : Double.NaN, p);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    protected void inverseImpl(PointD p, GeoPoint g) {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            final boolean hae = (g.getAltitudeReference() == GeoPoint.AltitudeReference.HAE);
            inverse(this.pointer.raw, p.x, p.y, p.z, g);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public double getMinLatitude() {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            return getMinLatitude(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public double getMinLongitude() {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            return getMinLongitude(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public double getMaxLatitude() {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            return getMaxLatitude(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public double getMaxLongitude() {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            return getMaxLongitude(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    // Interop<Projection>
    static long getPointer(Projection obj) {
        if(obj instanceof NativeProjection) {
            NativeProjection impl = (NativeProjection)obj;
            impl.rwlock.acquireRead();
            try {
                return impl.pointer.raw;
            } finally {
                impl.rwlock.releaseRead();
            }
        } else {
            return 0L;
        }
    }
    static Projection create(Pointer pointer, Object owner) {
        if(isWrapper(pointer.raw))
            return unwrap(pointer.raw);
        else
            return new NativeProjection(pointer, owner);
    }
    static native void destruct(Pointer pointer);
    static native Pointer wrap(Projection managed);

    static native boolean isWrapper(long pointer);
    static native Projection unwrap(long pointer);

    static native int getSrid(long pointer);
    static native boolean is3D(long pointer);
    static native void forward(long pointer, double lat, double lng, double hae, PointD result);
    static native void inverse(long pointer, double x, double y, double z, GeoPoint result);
    static native double getMinLatitude(long pointer);
    static native double getMinLongitude(long pointer);
    static native double getMaxLatitude(long pointer);
    static native double getMaxLongitude(long pointer);

}
