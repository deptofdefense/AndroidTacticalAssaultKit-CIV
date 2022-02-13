package com.atakmap.map.layer.feature.geometry;

import com.atakmap.interop.InteropCleaner;
import com.atakmap.interop.NativePeerManager;
import com.atakmap.interop.Pointer;
import com.atakmap.lang.ref.Cleaner;
import com.atakmap.util.Disposable;
import com.atakmap.util.ReadWriteLock;

import java.nio.ByteBuffer;

import gov.tak.api.annotation.DontObfuscate;

@DontObfuscate
public abstract class Geometry implements Disposable {

    final static NativePeerManager.Cleaner CLEANER = new InteropCleaner(Geometry.class);

    final ReadWriteLock rwlock = new ReadWriteLock();
    Pointer pointer;
    Geometry owner;
    Cleaner cleaner;

    Geometry(Pointer pointer) {
        setPointer(pointer);
        this.owner = null;
    }

    private void setPointer(Pointer pointer) {
        if(cleaner != null)
            cleaner.clean();

        this.pointer = pointer;
        cleaner = NativePeerManager.register(this, pointer, rwlock, null, CLEANER);
    }

    void reset(Geometry owner, Pointer pointer, boolean hard) {
        if(pointer == null)
            throw new IllegalArgumentException();

        if(pointer == this.pointer)
            return;

        this.rwlock.acquireWrite();
        try {
            if(!hard && this.owner != null)
                throw new IllegalStateException();
            this.owner = owner;
            setPointer(pointer);
        } finally {
            this.rwlock.releaseWrite();
        }
    }

    /**
     * Orphans this <code>Geometry</code>. The <code>Geometry</code> will no
     * longer have an owner reference.
     */
    final void orphan() {
        this.rwlock.acquireWrite();
        try {
            this.owner = null;
        } finally {
            this.rwlock.releaseWrite();
        }
    }

    public final void setDimension(int dimension) {
        this.rwlock.acquireRead();
        try {
            setDimension(this.pointer, dimension);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    public final int getDimension() {
        this.rwlock.acquireRead();
        try {
            return getDimension(this.pointer);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    public final Envelope getEnvelope() {
        double[] mbb = new double[6];
        this.rwlock.acquireRead();
        try {
            getEnvelope(this.pointer, mbb);
        } finally {
            this.rwlock.releaseRead();
        }
        return new Envelope(mbb[0], mbb[1], mbb[2], mbb[3], mbb[4], mbb[5]);
    }

    public final int computeWkbSize() {
        this.rwlock.acquireRead();
        try {
            return GeometryFactory.computeWkbSize(pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    public final void toWkb(ByteBuffer buffer) {
        GeometryFactory.toWkb(this, buffer);
    }

    @Override
    public final void dispose() {
        if(cleaner != null)
            cleaner.clean();
    }

    @Override
    public final boolean equals(Object o) {
        if (!(o instanceof Geometry))
            return false;

        final Geometry other = (Geometry) o;
        this.rwlock.acquireRead();
        other.rwlock.acquireRead();
        try {
            return equals(this.pointer.raw, other.pointer.raw);
        } finally {
            other.rwlock.releaseRead();
            this.rwlock.releaseRead();
        }
    }

    @Override
    public final int hashCode() {
        return this.pointer.hashCode();
    }

    @Override
    public final Geometry clone() {
        this.rwlock.acquireRead();
        try {
            return create(clone(this.pointer.raw), null);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    /*************************************************************************/
    // Interop interface
    static Geometry create(Pointer ptr, Object owner) {
        if(ptr == null)
            return null;

        final int geomClass = getGeometryClass(ptr);
        if(geomClass == getTEGC_Point()) {
            return new Point(ptr);
        } else if(geomClass == getTEGC_LineString()) {
            return new LineString(ptr);
        } else if(geomClass == getTEGC_Polygon()) {
            Polygon retval = new Polygon(ptr);
            retval.exteriorRing = new LineString(Polygon_getExteriorRing(retval.pointer));
            retval.exteriorRing.owner = retval;
            return retval;
        } else if(geomClass == getTEGC_GeometryCollection()) {
            return new GeometryCollection(ptr);
        } else {
            throw new IllegalStateException();
        }
    }

    static long getPointer(Geometry object) {
        if(object != null)
            return object.pointer.raw;
        else
            return 0L;
    }
    static boolean hasPointer(Geometry object) {
        return (object != null);
    }
    static native Pointer clone(long otherRawPointer);
    static void destruct(Pointer pointer) {
        destroy(pointer);
    }

    // native-wraps-Java not supported
    //static Pointer wrap(T object)
    //static boolean hasObject(long pointer);
    //static T getObject(long pointer);

    /*************************************************************************/
    // JNI declarations

    static native int getTEGC_Point();
    static native int getTEGC_LineString();
    static native int getTEGC_Polygon();
    static native int getTEGC_GeometryCollection();

    static native boolean equals(long a, long b);
    static native void destroy(Pointer pointer);
    static native void getEnvelope(Pointer pointer, double[] envelope);
    static native int getDimension(Pointer pointer);
    static native void setDimension(Pointer pointer, int dimension);
    static native int getGeometryClass(Pointer pointer);

    static native Pointer Point_create(double x, double y);
    static native Pointer Point_create(double x, double y, double z);
    static native double Point_getX(Pointer pointer);
    static native double Point_getY(Pointer pointer);
    static native double Point_getZ(Pointer pointer);
    static native void Point_set(Pointer pointer, double x, double y);
    static native void Point_set(Pointer pointer, double x, double y, double z);

    static native Pointer Linestring_create(int dimension);
    static native int Linestring_getNumPoints(Pointer pointer);
    static native boolean Linestring_isClosed(Pointer pointer);
    static native double Linestring_getX(Pointer pointer, int index);
    static native double Linestring_getY(Pointer pointer, int index);
    static native double Linestring_getZ(Pointer pointer, int index);
    static native void Linestring_addPoint(Pointer pointer, double x, double y);
    static native void Linestring_addPoint(Pointer pointer, double x, double y, double z);
    static native void Linestring_addPoints(Pointer pointer, double[] pts, int off, int count, int ptsDim);
    static native void Linestring_setX(Pointer pointer, int idx, double x);
    static native void Linestring_setY(Pointer pointer, int idx, double y);
    static native void Linestring_setZ(Pointer pointer, int idx, double z);

    static native Pointer Polygon_create(int dimension);
    static native Pointer Polygon_setExteriorRing(Pointer polygon, Pointer ring);
    static native Pointer Polygon_addInteriorRing(Pointer polygon, Pointer ring);
    static native boolean Polygon_removeInteriorRing(Pointer polygon, Pointer ring);
    static native boolean Polygon_containsInteriorRing(Polygon polygon, Pointer ring);
    static native void Polygon_clear(Pointer pointer);
    static native void Polygon_clearInteriorRings(Pointer pointer);
    static native Pointer Polygon_getExteriorRing(Pointer pointer);
    static native int Polygon_getNumInteriorRings(Pointer pointer);
    static native Pointer Polygon_getInteriorRing(Pointer pointer, int index);

    static native Pointer GeometryCollection_create(int dimension);
    static native Pointer GeometryCollection_add(Pointer collection, Pointer child);
    static native boolean GeometryCollection_remove(Pointer collection, Pointer child);
    static native int GeometryCollection_getNumChildren(Pointer pointer);
    static native Pointer GeometryCollection_getChild(Pointer pointer, int index);
    static native void GeometryCollection_clear(Pointer pointer);
}
