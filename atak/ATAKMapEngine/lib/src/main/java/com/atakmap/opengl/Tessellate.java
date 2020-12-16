package com.atakmap.opengl;

import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.interop.DataType;
import com.atakmap.lang.Unsafe;
import com.atakmap.math.MathUtils;
import com.atakmap.math.PointD;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;

public final class Tessellate {
    /**
     * The tessellation mode
     */
    public static enum Mode {
        XYZ,
        WGS84,
    };

    public static native double[] linestring(double[] src, int size, int count, double threshold, boolean wgs84);
    public static native float[] linestring(float[] src, int size, int count, double threshold, boolean wgs84);
    public static <T> Buffer linestring(Class<T> srcVertexType, Buffer src, int stride, int size, int count, double threshold, boolean wgs84) {
        if(!src.isDirect())
            throw new IllegalArgumentException();
        if(src.position() != 0)
            throw new IllegalArgumentException();
        Buffer retval =
                linestring(DataType.convert(srcVertexType, true),
                           src,
                           stride,
                           size,
                           count,
                           threshold,
                           wgs84);
        if(retval == src)
            return src;
        ((ByteBuffer)retval).order(ByteOrder.nativeOrder());
        return retval;
    }
    static native Buffer linestring(int tedt, Buffer src, int stride, int size, int count, double threshold, boolean wgs84);

    /**
     *
     * @param src
     * @param stride
     * @param size
     * @param count     The number of vertices in the polygon.
     *                  First-point-as-last should not be included in the input
     * @param threshold
     * @param wgs84
     * @return
     */
    public static DoubleBuffer polygon(DoubleBuffer src, int stride, int size, int count, double threshold, boolean wgs84) {
        final Buffer retval = polygonImpl(src, stride, size, count, threshold, wgs84);
        if (retval == null)
            return null;
        if (retval instanceof DoubleBuffer) {
            return (DoubleBuffer) retval;
        } else if(retval instanceof ByteBuffer){
            ((ByteBuffer)retval).order(ByteOrder.nativeOrder());
            return ((ByteBuffer)retval).asDoubleBuffer();
        } else {
            throw new IllegalStateException();
        }
    }
    static native Buffer polygonImpl(DoubleBuffer src, int stride, int size, int count, double threshold, boolean wgs84);
}
