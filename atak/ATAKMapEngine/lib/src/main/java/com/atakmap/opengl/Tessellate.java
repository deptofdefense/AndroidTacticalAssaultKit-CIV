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
import java.util.LinkedList;

import gov.nasa.worldwind.util.glu.GLU;
import gov.nasa.worldwind.util.glu.GLUtessellator;
import gov.nasa.worldwind.util.glu.GLUtessellatorCallback;

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
        if(stride == 0)
            stride = size*8;

        // XXX - probably very broken
        stride /= 8;

        VertexIterator it = new BufferVertexIterator(src, stride, size, count);

        CountingTessCallback counter = new CountingTessCallback();
        it.reset();
        polygon(it, counter, threshold);

        if(counter.error)
            return null;

        AssemblingTessCallback assembler = new AssemblingTessCallback();
        assembler.vertices = Unsafe.allocateDirect(counter.vertices*size, DoubleBuffer.class);
        assembler.size = size;
        it.reset();
        polygon(it, assembler, threshold);

        if(assembler.error)
            return null;

        DoubleBuffer tessellated = assembler.vertices;
        tessellated.flip();

        if(threshold > 0d) {
            LinkedList<DoubleBuffer> finished = new LinkedList<>();
            int finishedCount = 0;

            boolean reprocess;
            do {
                reprocess = false;
                LinkedList<DoubleBuffer> outstanding = new LinkedList<>();
                int outstandingCount = 0;
                DoubleBuffer done = tessellated.duplicate();
                done.limit(done.position());
                while (tessellated.hasRemaining()) {
                    // examine each output triangle for further tessellation
                    DoubleBuffer t = tessellated.duplicate();
                    t.limit(t.position() + stride * 3);
                    tessellated.position(tessellated.position() + stride * 3);

                    DoubleBuffer processed;
                    boolean[] b = new boolean[1];
                    if(wgs84)
                        processed = tessellateTriangleWGS84(t, stride, size, threshold, b);
                    else
                        processed = tessellateTriangleXYZ(t, stride, size, threshold, b);
                    // XXX - not currently working (looks like some triangle
                    // duplication) but the intent with the disabled switch
                    // below is to have 'done' contain a running span of
                    // triangles that did not require further tessellation.
                    // that span would only be flushed when a triangle that did
                    // require tessellation was encountered
                    if(true) {
                        if(b[0]) {
                            outstanding.add(processed);
                            outstandingCount += processed.remaining()/stride;
                        } else {
                            finished.add(processed);
                            finishedCount += processed.remaining()/stride;
                        }
                    } else
                    // XXX -
                    if(processed == t) {
                        // no processing occurred, advance the pointer on 'done'
                        done.limit(tessellated.position());
                    } else {
                        // flush done
                        if(done.hasRemaining()) {
                            finished.add(done);
                            finishedCount += done.remaining() / stride;
                            done = tessellated.duplicate();
                            done.limit(done.position());
                        }

                        if(!b[0]) {
                            // tessellation occurred, but is done, move to finished
                            finished.add(processed);
                            finishedCount += processed.remaining() / stride;
                        } else {
                            outstanding.add(processed);
                            outstandingCount += processed.remaining()/stride;
                        }
                    }
                    reprocess |= b[0];
                }

                // flush done
                if(done.hasRemaining()) {
                    finished.add(done);
                    finishedCount += done.remaining() / stride;
                }

                // merge all outstanding triangle lists and reprocess
                if(!outstanding.isEmpty()) {
                    tessellated = Unsafe.allocateDirect(outstandingCount * stride, DoubleBuffer.class);
                    for (DoubleBuffer t : outstanding)
                        tessellated.put(t);
                    tessellated.flip();
                }
            } while (reprocess);

            if(finished.size() > 1) {
                tessellated = Unsafe.allocateDirect(finishedCount * stride, DoubleBuffer.class);
                for (DoubleBuffer t : finished)
                    tessellated.put(t);
                tessellated.flip();
            } else if(!finished.isEmpty()) {
                tessellated = finished.getFirst();
            }
        }

        return tessellated;

    }

    private static void polygon(VertexIterator verts, GLUtessellatorCallback cb, double threshold) {
        int windingRule = GLU.GLU_TESS_WINDING_ODD;
        double tolerance = 0d;
        GLUtessellator tess = GLU.gluNewTess();
        GLU.gluTessProperty(tess, GLU.GLU_TESS_WINDING_RULE, windingRule);
        GLU.gluTessProperty(tess, GLU.GLU_TESS_TOLERANCE, tolerance);
        GLU.gluTessCallback(tess, GLU.GLU_TESS_COMBINE, cb);
        GLU.gluTessCallback(tess, GLU.GLU_TESS_BEGIN, cb);
        GLU.gluTessCallback(tess, GLU.GLU_TESS_EDGE_FLAG, cb); // specify edge flag to force triangles
        GLU.gluTessCallback(tess, GLU.GLU_TESS_END, cb);
        GLU.gluTessCallback(tess, GLU.GLU_TESS_ERROR, cb);
        GLU.gluTessCallback(tess, GLU.GLU_TESS_VERTEX, cb);

        GLU.gluTessBeginPolygon(tess, null);
        GLU.gluTessBeginContour(tess);

        while(verts.hasNext()) {
            double[] xyz = new double[3];
            verts.next(xyz);
            GLU.gluTessVertex(tess, xyz, 0, xyz);
        }

        GLU.gluTessEndContour(tess);
        GLU.gluTessEndPolygon(tess);

        GLU.gluDeleteTess(tess);
    }

    private static DoubleBuffer tessellateTriangleWGS84(DoubleBuffer src, int stride, int size, double threshold, boolean[] reprocess) {
        final int pos = src.position();
        final double ax = src.get(pos);
        final double ay = src.get(pos+1);
        final double az = size == 3 ? src.get(pos+2) : 0d;
        final double bx = src.get(pos+stride);
        final double by = src.get(pos+stride+1);
        final double bz = size == 3 ? src.get(pos+stride+2) : 0d;
        final double cx = src.get(pos+(2*stride));
        final double cy = src.get(pos+(2*stride)+1);
        final double cz = size == 3 ? src.get(pos+(2*stride)+2) : 0d;

        final GeoPoint a = new GeoPoint(ay, ax, az);
        final GeoPoint b = new GeoPoint(by, bx, bz);
        final GeoPoint c = new GeoPoint(cy, cx, cz);

        final double dab = a.distanceTo(b);
        final double dbc = b.distanceTo(c);
        final double dca = c.distanceTo(a);

        reprocess[0] |= (dab/threshold) > 2d || (dbc/threshold) > 2d || (dca/threshold) > 2d;

        final int subAB = dab > threshold ? 1 : 0;
        final int subBC = dbc > threshold ? 1 : 0;
        final int subCA = dca > threshold ? 1 : 0;

        final int subs = subAB+subBC+subCA;

        // XXX - winding order!!!

        // no tessellation
        if(subs == 0) {
            return src;
        } else if(subs == 3) {
            // full tessellation
            final GeoPoint d = GeoCalculations.midPointWGS84(a, b);
            final GeoPoint e = GeoCalculations.midPointWGS84(b, c);
            final GeoPoint f = GeoCalculations.midPointWGS84(c, a);
            DoubleBuffer retval = Unsafe.allocateDirect(12*stride, DoubleBuffer.class);

            put(retval, a, d, f, size);
            put(retval, d, b, e, size);
            put(retval, d, e, f, size);
            put(retval, f, e, c, size);

            retval.flip();
            return retval;
        } else if(subs == 2) {
            DoubleBuffer retval = Unsafe.allocateDirect(9*stride, DoubleBuffer.class);

            if(subAB == 1 && subCA == 1) {
                final GeoPoint d = GeoCalculations.midPointWGS84(a, b);
                final GeoPoint f = GeoCalculations.midPointWGS84(c, a);

                put(retval, a, d, f, size);
                put(retval, f, d, b, size);
                put(retval, f, b, c, size);
            } else if(subAB == 1 && subBC == 1) {
                final GeoPoint d = GeoCalculations.midPointWGS84(a, b);
                final GeoPoint e = GeoCalculations.midPointWGS84(b, c);

                put(retval, a, d, e, size);
                put(retval, d, b, e, size);
                put(retval, a, e, c, size);
            } else if(subBC == 1 && subCA == 1) {
                final GeoPoint e = GeoCalculations.midPointWGS84(b, c);
                final GeoPoint f = GeoCalculations.midPointWGS84(c, a);

                put(retval, c, e, f, size);
                put(retval, f, e, a, size);
                put(retval, e, a, b, size);
            } else {
                throw new IllegalStateException();
            }

            retval.flip();
            return retval;
        } else if(subs == 1) {
            DoubleBuffer retval = Unsafe.allocateDirect(6*stride, DoubleBuffer.class);
            if(subAB != 0) {
                final GeoPoint d = GeoCalculations.midPointWGS84(a, b);

                put(retval, a, d, c, size);
                put(retval, c, b, d, size);
            } else if(subBC != 0) {
                final GeoPoint e = GeoCalculations.midPointWGS84(b, c);

                put(retval, b, e, a, size);
                put(retval, a, e, c, size);
            } else if(subCA != 0) {
                final GeoPoint f = GeoCalculations.midPointWGS84(c, a);

                put(retval, a, b, f, size);
                put(retval, f, b, c, size);
            } else {
                throw new IllegalStateException();
            }

            retval.flip();
            return retval;
        } else {
            throw new IllegalStateException();
        }
    }

    private static DoubleBuffer tessellateTriangleXYZ(DoubleBuffer src, int stride, int size, double threshold, boolean[] reprocess) {
        final int pos = src.position();
        final double ax = src.get(pos);
        final double ay = src.get(pos+1);
        final double az = size == 3 ? src.get(pos+2) : 0d;
        final double bx = src.get(pos+stride);
        final double by = src.get(pos+stride+1);
        final double bz = size == 3 ? src.get(pos+stride+2) : 0d;
        final double cx = src.get(pos+(2*stride));
        final double cy = src.get(pos+(2*stride)+1);
        final double cz = size == 3 ? src.get(pos+(2*stride)+2) : 0d;

        PointD a = new PointD(ax, ay, az);
        PointD b = new PointD(bx, by, bz);
        PointD c = new PointD(cx, cy, cz);

        final double dab = MathUtils.distance(ax, ay, az, bx, by, bz);
        final double dbc = MathUtils.distance(bx, by, bz, cx, cy, cz);
        final double dca = MathUtils.distance(cx, cy, cz, ax, ay, az);

        reprocess[0] |= (dab/threshold) > 2d || (dbc/threshold) > 2d || (dca/threshold) > 2d;

        final int subAB = dab > threshold ? 1 : 0;
        final int subBC = dbc > threshold ? 1 : 0;
        final int subCA = dca > threshold ? 1 : 0;

        final int subs = subAB+subBC+subCA;

        // XXX - winding order!!!

        // no tessellation
        if(subs == 0) {
            return src;
        } else if(subs == 3) {
            // full tessellation
            final PointD d = midPoint(a, b);
            final PointD e = midPoint(b, c);
            final PointD f = midPoint(c, a);
            DoubleBuffer retval = Unsafe.allocateDirect(12*stride, DoubleBuffer.class);

            put(retval, a, d, f, size);
            put(retval, d, b, e, size);
            put(retval, d, e, f, size);
            put(retval, f, e, c, size);

            retval.flip();
            return retval;
        } else if(subs == 2) {
            DoubleBuffer retval = Unsafe.allocateDirect(9*stride, DoubleBuffer.class);

            if(subAB == 1 && subCA == 1) {
                final PointD d = midPoint(a, b);
                final PointD f = midPoint(c, a);

                put(retval, a, d, f, size);
                put(retval, f, d, b, size);
                put(retval, f, b, c, size);
            } else if(subAB == 1 && subBC == 1) {
                final PointD d = midPoint(a, b);
                final PointD e = midPoint(b, c);

                put(retval, a, d, e, size);
                put(retval, d, b, e, size);
                put(retval, a, e, c, size);
            } else if(subBC == 1 && subCA == 1) {
                final PointD e = midPoint(b, c);
                final PointD f = midPoint(c, a);

                put(retval, c, e, f, size);
                put(retval, f, e, a, size);
                put(retval, e, a, b, size);
            } else {
                throw new IllegalStateException();
            }

            retval.flip();
            return retval;
        } else if(subs == 1) {
            DoubleBuffer retval = Unsafe.allocateDirect(6*stride, DoubleBuffer.class);
            if(subAB != 0) {
                final PointD d = midPoint(a, b);

                put(retval, a, d, c, size);
                put(retval, c, b, d, size);
            } else if(subBC != 0) {
                final PointD e = midPoint(b, c);

                put(retval, b, e, a, size);
                put(retval, a, e, c, size);
            } else if(subCA != 0) {
                final PointD f = midPoint(c, a);

                put(retval, a, b, f, size);
                put(retval, f, b, c, size);
            } else {
                throw new IllegalStateException();
            }

            retval.flip();
            return retval;
        } else {
            throw new IllegalStateException();
        }
    }

    private static PointD midPoint(PointD a, PointD b) {
        return new PointD((a.x+b.x)/2d, (a.y+b.y)/2d, (a.z+b.z)/2d);
    }

    private static void put(DoubleBuffer buf, GeoPoint a, GeoPoint b, GeoPoint c, int size) {
        put(buf, a, size);
        put(buf, b, size);
        put(buf, c, size);
    }

    private static void put(DoubleBuffer buf, GeoPoint g, int size) {
        buf.put(g.getLongitude());
        buf.put(g.getLatitude());
        if(size == 3)
            buf.put(Double.isNaN(g.getAltitude()) ? 0d : g.getAltitude());
    }

    private static void put(DoubleBuffer buf, PointD a, PointD b, PointD c, int size) {
        put(buf, a, size);
        put(buf, b, size);
        put(buf, c, size);
    }

    private static void put(DoubleBuffer buf, PointD p, int size) {
        buf.put(p.x);
        buf.put(p.y);
        if(size == 3)
            buf.put(p.z);
    }

    private static interface VertexIterator {
        public boolean hasNext();
        public void next(double[] v);
        public void reset();
    }

    private static class BufferVertexIterator implements VertexIterator {
        private int idx;
        private int stride;
        private int size;
        private int count;
        private DoubleBuffer data;
        int pos;

        public BufferVertexIterator(DoubleBuffer data, int stride, int size, int count) {
            this.data = data;
            this.stride = stride;
            this.size = size;
            this.count = count;
            this.idx = 0;
            pos = this.data.position();
        }

        public void reset() {
            this.idx = 0;
        }

        public boolean hasNext() {
            return idx < count;
        }

        public void next(double[] v) {
            v[0] = data.get(pos+idx*stride + 0);
            v[1] = data.get(pos+idx*stride + 1);
            if(size == 3)
                v[2] = data.get(pos+idx*stride + 2);
            // XXX -
            v[2] = 0d;
            idx++;
        }
    }

    private static class CountingTessCallback implements GLUtessellatorCallback {
        int type;
        int vertices = 0;
        boolean error = false;

        @Override
        public void begin(int type) {
            this.type = type;
        }

        @Override
        public void beginData(int type, Object polygonData) {}

        @Override
        public void edgeFlag(boolean boundaryEdge) {}

        @Override
        public void edgeFlagData(boolean boundaryEdge, Object polygonData) {}

        @Override
        public void vertex(Object o) {
            vertexData(o, null);
        }

        @Override
        public void vertexData(Object vertexData, Object polygonData) {
            vertices++;
        }

        @Override
        public void end() {}

        @Override
        public void endData(Object polygonData) {}

        @Override
        public void combine(double[] coords, Object[] data, float[] weight, Object[] outData) {
            combineData(coords, data, weight, outData, null);
        }

        @Override
        public void combineData(double[] coords, Object[] data, float[] weight, Object[] outData, Object polygonData) {
            outData[0] = new double[] { coords[0], coords[1], coords[2] };
        }

        @Override
        public void error(int errnum) {
            errorData(errnum, null);
        }

        @Override
        public void errorData(int errnum, Object polygonData) {
            error = true;
        }
    }

    private static class AssemblingTessCallback implements GLUtessellatorCallback {
        int type;
        DoubleBuffer vertices;
        int size;
        boolean error;

        @Override
        public void begin(int type) {
            this.type = type;
        }

        @Override
        public void beginData(int type, Object polygonData) {}

        @Override
        public void edgeFlag(boolean boundaryEdge) {}

        @Override
        public void edgeFlagData(boolean boundaryEdge, Object polygonData) {}

        @Override
        public void vertex(Object o) {
            vertexData(o, null);
        }

        @Override
        public void vertexData(Object vertexData, Object polygonData) {
            double[] xyz = (double[])vertexData;
            vertices.put(xyz, 0, size);
        }

        @Override
        public void end() {}

        @Override
        public void endData(Object polygonData) {}

        @Override
        public void combine(double[] coords, Object[] data, float[] weight, Object[] outData) {
            combineData(coords, data, weight, outData, null);
        }

        @Override
        public void combineData(double[] coords, Object[] data, float[] weight, Object[] outData, Object polygonData) {
            outData[0] = new double[] { coords[0], coords[1], coords[2] };
        }

        @Override
        public void error(int errnum) {
            errorData(errnum, null);
        }

        @Override
        public void errorData(int errnum, Object polygonData) {
            error = true;
        }
    }
}
