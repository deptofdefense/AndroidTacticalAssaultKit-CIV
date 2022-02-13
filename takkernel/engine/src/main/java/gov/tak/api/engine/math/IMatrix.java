package gov.tak.api.engine.math;

import com.atakmap.math.NoninvertibleTransformException;

import gov.tak.api.util.Disposable;

public interface IMatrix {
    enum MatrixOrder {
        ROW_MAJOR,
        COLUMN_MAJOR,
    };

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
    PointD transform(PointD src, PointD dst);

    /**
     * Inverts this matrix. If the determinant of the matrix is <code>0.0</code>, the matrix cannot
     * be inverted and <code>false</code> will be returned.
     * 
     * In the case where <code>invert()</code> is not successful, no change will be made to the
     * matrix.
     *
     * @return  <code>true</code> if the matrix was successfully inverted, <code>false</code> if it
     * could not be inverted.
     */
    boolean inverse();

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
    void concatenate(IMatrix t);
    
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
    void preConcatenate(IMatrix t);

    /**
     * Sets the coefficients of this matrix to the coefficients of the specified
     * matrix.
     *
     * @param t A matrix
     */
    void set(IMatrix t);

    /**
     * Obtains the coefficients for this matrix, in row major order.
     *
     * @param matrix    The coefficients for this matrix, in row major order.
     */
    void get(double[] matrix);

    /**
     * Obtains the coefficient at the specified row/column.
     *
     * @param row       The row
     * @param column    The column
     *
     * @return  The coefficient at the specified row/column.
     */
    double get(int row, int column);

    /**
     * Sets the coefficient at the specified row/column.
     *
     * @param row       The row
     * @param column    The column
     *
     * @param v The new coefficient for the specified row/column.
     */
    void set(int row, int column, double v);

    /**
     * Retrieves the coefficients for this matrix in the specified order.
     *
     * @param matrix    The coefficients for the matrix
     * @param order     The desired order for the coefficients
     */
    void get(double[] matrix, MatrixOrder order);
    
    void translate(double tx, double ty);

    void translate(double tx, double ty, double tz);

    void rotate(double theta);
    
    void rotate(double theta, double axisX, double axisY, double axisZ);

    void rotate(double theta, double anchorx, double anchory);

    void rotate(double theta, double anchorx, double anchory, double anchorz, double xAxis, double yAxis, double zAxis);

    void scale(double scale);
    
    void scale(double scaleX, double scaleY);

    void scale(double scaleX, double scaleY, double scaleZ);

    void setToIdentity();

    void setToTranslation(double tx, double ty);

    void setToTranslation(double tx, double ty, double tz);

    void setToRotation(double theta);

    void setToRotation(double theta, double anchorx, double anchory);

    void setToScale(double scale);

    void setToScale(double scaleX, double scaleY);

    void setToScale(double scaleX, double scaleY, double scaleZ);
}
