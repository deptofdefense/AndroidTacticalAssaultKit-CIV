
package com.atakmap.math;

import com.atakmap.interop.InteropCleaner;
import com.atakmap.interop.NativePeerManager;
import com.atakmap.interop.Pointer;
import com.atakmap.lang.ref.Cleaner;
import com.atakmap.util.Disposable;
import com.atakmap.util.ReadWriteLock;

import gov.tak.api.annotation.DontObfuscate;
import gov.tak.api.engine.math.IMatrix;

/**
 * Holds a 4x4 matrix for transforming points. The internal representation of
 * the matrix is <I>row major</I>.
 * 
 * @author Developer
 */
@DontObfuscate
public final class Matrix implements IMatrix, Disposable {

    final static NativePeerManager.Cleaner CLEANER = new InteropCleaner(Matrix.class);
    private final Cleaner cleaner;

    final ReadWriteLock rwlock = new ReadWriteLock();

    Pointer pointer;
    Object owner;

    Matrix(Pointer pointer, Object owner) {
        cleaner = NativePeerManager.register(this, pointer, rwlock, null, CLEANER);

        this.pointer = pointer;
        this.owner = owner;
    }

    /**
     * Creates a new matrix using the specified coefficients. The matrix
     * coefficients are ordered in row major order.
     * 
     * @param m00   The matrix coefficient at row 0, column 0
     * @param m01   The matrix coefficient at row 0, column 1
     * @param m02   The matrix coefficient at row 0, column 2
     * @param m03   The matrix coefficient at row 0, column 3
     * @param m10   The matrix coefficient at row 1, column 0
     * @param m11   The matrix coefficient at row 1, column 1
     * @param m12   The matrix coefficient at row 1, column 2
     * @param m13   The matrix coefficient at row 1, column 3
     * @param m20   The matrix coefficient at row 2, column 0
     * @param m21   The matrix coefficient at row 2, column 1
     * @param m22   The matrix coefficient at row 2, column 2
     * @param m23   The matrix coefficient at row 2, column 3
     * @param m30   The matrix coefficient at row 3, column 0
     * @param m31   The matrix coefficient at row 3, column 1
     * @param m32   The matrix coefficient at row 3, column 2
     * @param m33   The matrix coefficient at row 3, column 3
     */
    public Matrix(double m00, double m01, double m02, double m03,
                  double m10, double m11, double m12, double m13,
                  double m20, double m21, double m22, double m23,
                  double m30, double m31, double m32, double m33) {

        this(create(m00, m01, m02, m03,
                m10, m11, m12, m13,
                m20, m21, m22, m23,
                m30, m31, m32, m33),
                null);
    }

    /**
     * Transforms a point in the source coordinate space into the destination
     * coordinate space. It is safe to use the same <code>PointD</code> instance
     * for both <code>src</code> and <code>dst</code>.
     * 
     * @param src   The source point
     * @param dst   The destination point. May be the same as <code>src</code>
     *              or <code>null</code>.
     * 
     * @return  <code>dst</code> or a newly allocated <code>PointD</code>
     *          instance if <code>dst</code> is <code>null</code>.
     */
    public PointD transform(PointD src, PointD dst) {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            if(dst == null)
                dst = new PointD(0d, 0d, 0d);
            transform(this.pointer.raw, src.x, src.y, src.z, dst);
            return dst;
        } finally {
            this.rwlock.releaseRead();
        }
    }

    /**
     * Creates the inverse of this matrix.
     * 
     * @return  The inverse of this matrix
     * 
     * @throws NoninvertibleTransformException  If this matrix could not be
     *                                          inverted
     */
    public Matrix createInverse() throws NoninvertibleTransformException {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            final Pointer retval = createInverse(this.pointer.raw);
            if(retval == null)
                throw new NoninvertibleTransformException("Failed to invert matrix");
            return new Matrix(retval, null);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    /**
     * Concatenates this matrix with the specified matrix. Subsequent
     * transformations with this transform will be equivalent to transforming
     * first by <code>t</code>, then by this transform, prior to the
     * concatenation.
     * <P>
     * <code>this = this X t</code>
     * 
     * @param t A matrix
     */
    public void concatenate(Matrix t) {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            t.rwlock.acquireRead();
            try {
                if(t.pointer.raw == 0L)
                    throw new IllegalStateException();
                concatenate(this.pointer.raw, t.pointer.raw);
            } finally {
                t.rwlock.releaseRead();
            }
        } finally {
            this.rwlock.releaseRead();
        }
    }


    /**
     * Pre-concatenates this matrix with the specified matrix. Subsequent
     * transformations with this transform will be equivalent to transforming
     * first by this transform, prior to the pre-concatenation, then by
     * <code>t</code>.
     * <P>
     * <code>this = t X this</code>
     * 
     * @param t A matrix
     */
    public void preConcatenate(Matrix t) {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            t.rwlock.acquireRead();
            try {
                if(t.pointer.raw == 0L)
                    throw new IllegalStateException();
                preConcatenate(this.pointer.raw, t.pointer.raw);
            } finally {
                t.rwlock.releaseRead();
            }
        } finally {
            this.rwlock.releaseRead();
        }
    }

    /**
     * Sets the coefficients of this matrix to the coefficients of the specified
     * matrix.
     * 
     * @param t A matrix
     */
    public void set(Matrix t) {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            t.rwlock.acquireRead();
            try {
                if(t.pointer.raw == 0L)
                    throw new IllegalStateException();
                set(this.pointer.raw, t.pointer.raw);
            } finally {
                t.rwlock.releaseRead();
            }
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public gov.tak.api.engine.math.PointD transform(gov.tak.api.engine.math.PointD src, gov.tak.api.engine.math.PointD dst) {
        rwlock.acquireRead();
        try {
            if(pointer.raw == 0L)
                throw new IllegalStateException();
            PointD result = new PointD();
            transform(pointer.raw, src.x, src.y, src.z, result);
            if(dst == null) return new gov.tak.api.engine.math.PointD(result.x, result.y, result.z);
            dst.x = result.x;
            dst.y = result.y;
            dst.z = result.z;
            return dst;
        } finally {
            rwlock.releaseRead();
        }
    }

    @Override
    public boolean inverse() {
        rwlock.acquireRead();
        try {
            if(pointer.raw == 0L)
                throw new IllegalStateException();
            Pointer result = null;
            try {
                result = createInverse(pointer.raw);
                if (result == null) return false;
                set(pointer.raw, result.raw);
                return true;
            } finally {
                if(result != null)
                    destruct(result);
            }
        } finally {
            rwlock.releaseRead();
        }
    }

    @Override
    public void concatenate(IMatrix t) {
        if(t instanceof Matrix) {
            concatenate((Matrix)t);
        } else {
            Pointer other = null;
            try {
                double[] mx = new double[16];
                t.get(mx);
                other = create(mx);
                rwlock.acquireRead();
                try {
                    if(pointer.raw == 0L)
                        throw new IllegalStateException();
                    concatenate(pointer.raw, other.raw);
                } finally {
                    rwlock.releaseRead();
                }
            } finally {
                if(other != null)
                    destruct(other);
            }
        }
    }

    @Override
    public void preConcatenate(IMatrix t) {
        if(t instanceof Matrix) {
            preConcatenate((Matrix)t);
        } else {
            Pointer other = null;
            try {
                double[] mx = new double[16];
                t.get(mx);
                other = create(mx);
                rwlock.acquireRead();
                try {
                    if(pointer.raw == 0L)
                        throw new IllegalStateException();
                    preConcatenate(pointer.raw, other.raw);
                } finally {
                    rwlock.releaseRead();
                }
            } finally {
                if(other != null)
                    destruct(other);
            }
        }
    }

    @Override
    public void set(IMatrix t) {
        if(t instanceof Matrix) {
            set((Matrix)t);
        } else {
            Pointer other = null;
            try {
                double[] mx = new double[16];
                t.get(mx);
                other = create(mx);
                rwlock.acquireRead();
                try {
                    if(pointer.raw == 0L)
                        throw new IllegalStateException();
                    set(pointer.raw, other.raw);
                } finally {
                    rwlock.releaseRead();
                }
            } finally {
                if(other != null)
                    destruct(other);
            }
        }
    }

    /**
     * Obtains the coefficients for this matrix, in row major order.
     * 
     * @param matrix    The coefficients for this matrix, in row major order.
     */
    public void get(double[] matrix) {
        this.get(matrix, MatrixOrder.ROW_MAJOR);
    }
    
    /**
     * Obtains the coefficient at the specified row/column.
     * 
     * @param row       The row
     * @param column    The column
     * 
     * @return  The coefficient at the specified row/column.
     */
    public double get(int row, int column) {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            return get(this.pointer.raw, row, column);
        } finally {
            this.rwlock.releaseRead();
        }
    }
    
    /**
     * Sets the coefficient at the specified row/column.
     * 
     * @param row       The row
     * @param column    The column
     * 
     * @param v The new coefficient for the specified row/column.
     */
    public void set(int row, int column, double v) {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                 throw new IllegalStateException();
            set(this.pointer.raw, row, column, v);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    /**
     * Retrieves the coefficients for this matrix in the specified order.
     * 
     * @param matrix    The coefficients for the matrix
     * @param order     The desired order for the coefficients
     */
    public void get(double[] matrix, MatrixOrder order) {
        switch(order) {
            case ROW_MAJOR :
                this.rwlock.acquireRead();
                try {
                    if(this.pointer.raw == 0L)
                        throw new IllegalStateException();
                    getRowMajor(this.pointer.raw, matrix);
                } finally {
                    this.rwlock.releaseRead();
                }
                break;
            case COLUMN_MAJOR :
                this.rwlock.acquireRead();
                try {
                    if(this.pointer.raw == 0L)
                        throw new IllegalStateException();
                    getColumnMajor(this.pointer.raw, matrix);
                } finally {
                    this.rwlock.releaseRead();
                }
                break;
            default :
                throw new IllegalStateException();
        }
    }
    
    public void translate(double tx, double ty) {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            translate(this.pointer.raw, tx, ty);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    public void translate(double tx, double ty, double tz) {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            translate(this.pointer.raw, tx, ty, tz);
        } finally {
            this.rwlock.releaseRead();
        }
    }
    
    public void rotate(double theta) {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            rotate(this.pointer.raw, theta);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    public void rotate(double theta, double axisX, double axisY, double axisZ) {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            rotate(this.pointer.raw, theta, axisX, axisY, axisZ);
        } finally {
            this.rwlock.releaseRead();
        }
    }
    
    public void rotate(double theta, double anchorx, double anchory) {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            rotate(this.pointer.raw, theta, anchorx, anchory);
        } finally {
            this.rwlock.releaseRead();
        }
    }
    
    public void rotate(double theta, double anchorx, double anchory, double anchorz, double xAxis, double yAxis, double zAxis) {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            rotate(this.pointer.raw, theta, anchorx, anchory, anchorz, xAxis, yAxis, zAxis);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    public void scale(double scale) {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            scale(this.pointer.raw, scale);
        } finally {
            this.rwlock.releaseRead();
        }
    }
    
    public void scale(double scaleX, double scaleY) {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            scale(this.pointer.raw, scaleX, scaleY);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    public void scale(double scaleX, double scaleY, double scaleZ) {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            scale(this.pointer.raw, scaleX, scaleY, scaleZ);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    public void setToIdentity() {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            setToIdentity(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }
    
    public void setToTranslation(double tx, double ty) {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            setToTranslate(this.pointer.raw, tx, ty);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    public void setToTranslation(double tx, double ty, double tz) {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            setToTranslate(this.pointer.raw, tx, ty, tz);
        } finally {
            this.rwlock.releaseRead();
        }
    }
    
    public void setToRotation(double theta) {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            setToRotate(this.pointer.raw, theta);
        } finally {
            this.rwlock.releaseRead();
        }
    }
    
    public void setToRotation(double theta, double anchorx, double anchory) {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            setToRotate(this.pointer.raw, theta, anchorx, anchory);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    public void setToScale(double scale) {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            setToScale(this.pointer.raw, scale);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    public void setToScale(double scaleX, double scaleY) {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            setToScale(this.pointer.raw, scaleX, scaleY);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    public void setToScale(double scaleX, double scaleY, double scaleZ) {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            setToScale(this.pointer.raw, scaleX, scaleY, scaleZ);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    public Object clone() {
        final Pointer retval = create();
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            set(retval.raw, this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
        return new Matrix(retval, null);
    }

    @Override
    public void dispose() {
        if(this.cleaner != null)
            this.cleaner.clean();
    }
    /**************************************************************************/

    public static Matrix getScaleInstance(double scale) {
        return getScaleInstance(scale, scale);
    }

    public static Matrix getScaleInstance(double scaleX,
                                                       double scaleY) {

        return getScaleInstance(scaleX, scaleY, 1d);
    }
    public static Matrix getScaleInstance(double scaleX,
                                                       double scaleY,
                                                       double scaleZ) {

        return new Matrix(scaleX, 0, 0, 0,
                                       0, scaleY, 0, 0,
                                       0, 0, scaleZ, 0,
                                       0, 0, 0, 1);
    }

    public static Matrix getRotateInstance(final double theta) {
        final double cosTheta = Math.cos(theta);
        final double sinTheta = Math.sin(theta);

        return new Matrix(cosTheta, -sinTheta, 0, 0,
                                       sinTheta, cosTheta, 0, 0,
                                       0, 0, 1, 0,
                                       0, 0, 0, 1);
    }

    public static Matrix getRotateInstance(double theta, double x,
            double y) {
        
        Matrix retval = getTranslateInstance(x, y);
        retval.rotate(theta);
        retval.translate(-x, -y);

        return retval;
    }

    public static Matrix getTranslateInstance(double tx, double ty) {
        return getTranslateInstance(tx, ty, 0);
    }

    public static Matrix getTranslateInstance(double tx, double ty, double tz) {
        return new Matrix(1, 0, 0, tx,
                                       0, 1, 0, ty,
                                       0, 0, 1, tz, 
                                       0, 0, 0, 1);
    }

    public static Matrix getIdentity() {
        return getScaleInstance(1);
    }

    /**
     * Creates a ProjectiveTransform that maps between a source and destination coordinate space.
     * Coordinates should be specified in clockwise order, starting with the upper-left corner of
     * the quadrilateral.
     * 
     * @param src1
     * @param src2
     * @param src3
     * @param src4
     * @param dst1
     * @param dst2
     * @param dst3
     * @param dst4
     */
    
    public static Matrix mapQuads(PointD src1,
                                               PointD src2,
                                               PointD src3,
                                               PointD src4,
                                               PointD dst1,
                                               PointD dst2,
                                               PointD dst3,
                                               PointD dst4) {

        return mapQuads(src1.x, src1.y,
                        src2.x, src2.y,
                        src3.x, src3.y,
                        src4.x, src4.y,
                        dst1.x, dst1.y,
                        dst2.x, dst2.y,
                        dst3.x, dst3.y,
                        dst4.x, dst4.y);
    }
    
    /**
     * Creates a ProjectiveTransform that maps between a source and destination coordinate space.
     * Coordinates should be specified in clockwise order, starting with the upper-left corner of
     * the quadrilateral.
     * 
     * @param srcX1
     * @param srcY1
     * @param srcX2
     * @param srcY2
     * @param srcX3
     * @param srcY3
     * @param srcX4
     * @param srcY4
     * @param dstX1
     * @param dstY1
     * @param dstX2
     * @param dstY2
     * @param dstX3
     * @param dstY3
     * @param dstX4
     * @param dstY4
     */
    public static Matrix mapQuads(double srcX1, double srcY1,
                                   double srcX2, double srcY2,
                                   double srcX3, double srcY3,
                                   double srcX4, double srcY4,
                                   double dstX1, double dstY1,
                                   double dstX2, double dstY2,
                                   double dstX3, double dstY3,
                                   double dstX4, double dstY4) {

        Pointer retval =  mapQuadsNative(srcX1, srcY1, srcX2, srcY2, srcX3, srcY3, srcX4, srcY4, dstX1, dstY1, dstX2, dstY2, dstX3, dstY3, dstX4, dstY4);
        if(retval == null)
            return null;
        return new Matrix(retval, null);
    }

    static Pointer create(double[] mx) {
        return create(mx[0], mx[1], mx[2], mx[3],
                mx[4], mx[5], mx[6], mx[7],
                mx[8], mx[9], mx[10], mx[11],
                mx[12], mx[13], mx[14], mx[15]);
    }
    // Interop<MapSceneModel> interface
    static long getPointer(Matrix obj) {
        if(obj == null)
            return 0L;
        obj.rwlock.acquireRead();
        try {
            return obj.pointer.raw;
        } finally {
            obj.rwlock.releaseRead();
        }
    }
    static Matrix create(Pointer pointer, Object owner) {
        return new Matrix(pointer, owner);
    }
    static native Pointer clone(long pointer);
    static native void destruct(Pointer pointer);

    // JNI interface
    static native Pointer create();
    static native Pointer create(double m00, double m01, double m02, double m03,
                                 double m10, double m11, double m12, double m13,
                                 double m20, double m21, double m22, double m23,
                                 double m30, double m31, double m32, double m33);

    static native void transform(long ptr, double srcX, double srcY, double srcZ, PointD jdst);
    static native Pointer createInverse(long srcPt);
    static native void concatenate(long selfPtr, long otherPtr);
    static native void preConcatenate(long selfPtr, long otherPtr);
    static native void set(long selfPtr, long otherPtr);
    static native void set(long ptr, int row, int col, double v);
    static native void getRowMajor(long ptr, double[] jarr);
    static native void getColumnMajor(long ptr, double[] jarr);
    static native double get(long ptr, int row, int col);
    static native void translate(long ptr, double tx, double ty);
    static native void translate(long ptr, double tx, double ty, double tz);
    static native void rotate(long ptr, double theta);
    static native void rotate(long ptr, double theta, double anchorX, double anchorY);
    static native void rotate(long ptr, double theta, double axisX, double axisY, double axisZ);
    static native void rotate(long ptr, double theta, double anchorX, double anchorY, double anchorZ, double axisX, double axisY, double axisZ);
    static native void scale(long ptr, double s);
    static native void scale(long ptr, double sx, double sy);
    static native void scale(long ptr, double sx, double sy, double sz);
    static native void setToIdentity(long ptr);
    static native void setToTranslate(long ptr, double tx, double ty);
    static native void setToTranslate(long ptr, double tx, double ty, double tz);
    static native void setToRotate(long ptr, double theta);
    static native void setToRotate(long ptr, double theta, double anchorX, double anchorY);
    static native void setToScale(long ptr, double s);
    static native void setToScale(long ptr, double sx, double sy);
    static native void setToScale(long ptr, double sx, double sy, double sz);

    static native Pointer mapQuadsNative(double srcX1, double srcY1,
             double srcX2, double srcY2,
             double srcX3, double srcY3,
             double srcX4, double srcY4,
             double dstX1, double dstY1,
             double dstX2, double dstY2,
             double dstX3, double dstY3,
             double dstX4, double dstY4);
}
