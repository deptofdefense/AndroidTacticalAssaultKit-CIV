package com.atakmap.math;

import com.atakmap.interop.InteropCleaner;
import com.atakmap.interop.NativePeerManager;
import com.atakmap.interop.Pointer;
import com.atakmap.util.ReadWriteLock;

import gov.tak.api.annotation.DontObfuscate;

@DontObfuscate
public class Frustum {

    final static NativePeerManager.Cleaner CLEANER = new InteropCleaner(Frustum.class);

    final Pointer pointer;
    final Object ownerRef;
    final ReadWriteLock rwlock = new ReadWriteLock();

    public Frustum(Matrix matrix) {
        this(create(matrix.pointer.raw), null);
    }

    Frustum(Pointer pointer, Object ownerRef) {
        this.pointer = pointer;
        this.ownerRef = ownerRef;
        NativePeerManager.register(this, pointer, this.rwlock, null, CLEANER);
    }

    public boolean intersects(Sphere s) {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                return false;
            s.rwlock.acquireRead();
            try {
                if(s.pointer.raw == 0L)
                    return false;
                return intersectsSphere(this.pointer.raw, s.pointer.raw);
            } finally {
                s.rwlock.releaseRead();
            }
        } finally {
            this.rwlock.releaseRead();
        }
    }

    public boolean intersects(AABB aabb) {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                return false;
            aabb.rwlock.acquireRead();
            try {
                if(aabb.pointer.raw == 0L)
                    return false;
                return intersectsSphere(this.pointer.raw, aabb.pointer.raw);
            } finally {
                aabb.rwlock.releaseRead();
            }
        } finally {
            this.rwlock.releaseRead();
        }
    }

    static native Pointer create(long matrix);
    static native boolean intersectsSphere(long ptr, long spherePtr);
    static native boolean intersectsAABB(long ptr, long aabbPtr);

    // Interop implementation
    static long getPointer(Frustum object) {
        return object.pointer.raw;
    }
    static boolean hasPointer(Frustum object) {
        return true;
    }
    static Frustum create(Pointer pointer, Object ownerReference) {
        return new Frustum(pointer, ownerReference);
    }

    static native void destruct(Pointer pointer);
}
