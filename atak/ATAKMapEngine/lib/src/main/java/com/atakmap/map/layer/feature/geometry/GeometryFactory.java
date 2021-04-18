package com.atakmap.map.layer.feature.geometry;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.atakmap.lang.Unsafe;

public final class GeometryFactory {

    private GeometryFactory() {}
    
    public static Geometry fromEnvelope(Envelope mbb) {
        LineString retval = new LineString(2);
        retval.addPoint(mbb.minX, mbb.minY);
        retval.addPoint(mbb.minX, mbb.maxY);
        retval.addPoint(mbb.maxX, mbb.maxY);
        retval.addPoint(mbb.maxX, mbb.minY);
        retval.addPoint(mbb.minX, mbb.maxY);
        return new Polygon(retval);
    }

    public static Geometry polygonFromQuad(double ax, double ay, double bx, double by, double cx, double cy, double dx, double dy) {
        LineString ls = new LineString(2);
        ls.addPoint(ax, ay);
        ls.addPoint(bx, by);
        ls.addPoint(cx, cy);
        ls.addPoint(dx, dy);
        ls.addPoint(ax, ay);
        return new Polygon(ls);
    }
    public static Geometry parseWkb(byte[] arr) {
        if(arr == null)
            return null;
        return parseWkb(arr, 0, arr.length, new int[1]);
    }

    public static Geometry parseWkb(ByteBuffer buffer) {
        if(buffer == null)
            return null;
        final boolean be = (buffer.order() == ByteOrder.BIG_ENDIAN);
        int[] numRead = new int[1];
        final Geometry retval;
        if(buffer.hasArray()) {
            retval = parseWkb(buffer.array(), buffer.position(), buffer.remaining(), numRead);
        } else if(buffer.isDirect()) {
            retval = parseWkb(Unsafe.getBufferPointer(buffer) + buffer.position(), buffer.remaining(), numRead);
        } else {
            byte[] arr = new byte[buffer.remaining()];
            buffer.duplicate().get(arr);
            retval = parseWkb(arr, 0, arr.length, numRead);
        }
        buffer.position(buffer.position()+numRead[0]);
        return retval;
    }

    public static Geometry parseWkbImpl(ByteBuffer wkb, int typeOverride) {
        if(wkb == null)
            return null;
        if(typeOverride == 0)
            return parseWkb(wkb);

        // XXX -
        ByteBuffer wkb2 = wkb.duplicate();
        wkb2.mark();
        final int byteOrder = wkb2.get()&0xFF;
        switch(byteOrder) {
            case 0x00 :
                wkb2.order(ByteOrder.BIG_ENDIAN);
                break;
            case 0x01 :
                wkb2.order(ByteOrder.LITTLE_ENDIAN);
                break;
            default :
                throw new IllegalArgumentException("Invalid byte order");
        }
        final int codedType = wkb2.getInt();
        if(codedType == typeOverride)
            return parseWkb(wkb);

        byte[] arr = new byte[wkb.remaining()];
        wkb2.reset();
        wkb2.get(arr);
        wkb2 = ByteBuffer.wrap(arr);
        switch(byteOrder) {
            case 0x00 :
                wkb2.order(ByteOrder.BIG_ENDIAN);
                break;
            case 0x01 :
                wkb2.order(ByteOrder.LITTLE_ENDIAN);
                break;
            default :
                throw new IllegalArgumentException("Invalid byte order");
        }
        wkb2.putInt(1, typeOverride);
        return parseWkb(wkb2);
    }

    public static void toWkb(Geometry geom, ByteBuffer buffer) {
        if(geom == null)
            throw new NullPointerException();
        final boolean be = (buffer.order() == ByteOrder.BIG_ENDIAN);
        if(buffer.hasArray()) {
            final int written = toWkb(geom.pointer.raw, buffer.array(), buffer.position(), buffer.remaining(), be);
            buffer.position(buffer.position()+written);
        } else if(buffer.isDirect()) {
            final int written = toWkb(geom.pointer.raw, Unsafe.getBufferPointer(buffer) + buffer.position(), buffer.remaining(), be);
            buffer.position(buffer.position()+written);
        } else {
            byte[] arr = new byte[buffer.remaining()];
            buffer.duplicate().get(arr);
            final int written = toWkb(geom.pointer.raw, arr, 0, arr.length, be);
            buffer.put(arr, 0, written);
        }
    }

    public static Geometry parseSpatiaLiteBlob(byte[] arr) {
        if(arr == null)
            return null;
        return parseSpatiaLiteBlob(ByteBuffer.wrap(arr));
    }

    public static Geometry parseSpatiaLiteBlob(ByteBuffer buffer) {
        if(buffer == null)
            return null;
        final boolean be = (buffer.order() == ByteOrder.BIG_ENDIAN);
        int[] numRead = new int[1];
        int[] srid = new int[1];
        final Geometry retval;
        if(buffer.hasArray()) {
            retval = parseSpatiaLiteBlob(buffer.array(), buffer.position(), buffer.remaining(), srid, numRead);
        } else if(buffer.isDirect()) {
            retval = parseSpatiaLiteBlob(Unsafe.getBufferPointer(buffer) + buffer.position(), buffer.remaining(), srid, numRead);
        } else {
            byte[] arr = new byte[buffer.remaining()];
            buffer.duplicate().get(arr);
            retval = parseSpatiaLiteBlob(arr, 0, arr.length, srid, numRead);
        }
        buffer.position(buffer.position()+numRead[0]);
        return retval;
    }

    public static void toSpatiaLiteBlob(Geometry geom, int srid, ByteBuffer buffer) {
        if(geom == null)
            throw new NullPointerException();
        final boolean be = (buffer.order() == ByteOrder.BIG_ENDIAN);
        if(buffer.hasArray()) {
            final int written = toSpatiaLiteBlob(geom.pointer.raw, srid, buffer.array(), buffer.position(), buffer.remaining(), be);
            buffer.position(buffer.position()+written);
        } else if(buffer.isDirect()) {
            final int written = toSpatiaLiteBlob(geom.pointer.raw, srid, Unsafe.getBufferPointer(buffer) + buffer.position(), buffer.remaining(), be);
            buffer.position(buffer.position()+written);
        } else {
            byte[] arr = new byte[buffer.remaining()];
            buffer.duplicate().get(arr);
            final int written = toSpatiaLiteBlob(geom.pointer.raw, srid, arr, 0, arr.length, be);
            buffer.put(arr, 0, written);
        }
    }

    public static int computeSpatiaLiteBlobSize(Geometry geom) {
        if(geom == null)
            throw new NullPointerException();
        return computeSpatiaLiteBlobSize(geom.pointer.raw);
    }
    public static Geometry createRectangle(Geometry corner1, Geometry corner2, int algorithm) {
        if(corner1 == null || corner2 == null)
            throw new NullPointerException();
        return createRectangle(corner1.pointer.raw, corner2.pointer.raw, algorithm);
    }
    public static Geometry createRectangle(Geometry point1, Geometry point2, Geometry point3, int algorithm) {
        if(point1 == null || point2 == null || point3 == null)
            throw new NullPointerException();
        return createRectangle(point1.pointer.raw, point2.pointer.raw, point3.pointer.raw, algorithm);
    }
    public static Geometry createRectangle(Geometry location, double orientation, double length, double width, int algorithm) {
        if(location == null)
            throw new NullPointerException();
        return createRectangle(location.pointer.raw, orientation, length, width, algorithm);
    }

    public enum Algorithm {
        cartesian, wgs84
    }


    public static Geometry parseWkt(String wkt) {
        throw new UnsupportedOperationException();
    }

    public static Geometry createEllipse(Geometry location, double orientation, double major, double minor, int algorithm) {
        if(location == null)
            throw new NullPointerException();
        return createEllipse(location.pointer.raw, orientation, major, minor, algorithm);
    }

    public static Geometry createEllipse2(Envelope env, int algorithm) {
        if(env == null)
            throw new NullPointerException();
        return createEllipse(env.minX, env.minY, env.maxX, env.maxY, algorithm);
    }


    public static Geometry extrude(Geometry geom, double extrude, ExtrusionHints extrusionHints) {
        if(geom == null)
            throw new NullPointerException();
        return extrudeConstant(geom.pointer.raw, extrude, extrusionHints.getId());
    }

    public static Geometry extrude(Geometry geom, double[] extrude, ExtrusionHints extrusionHints) {
        if(geom == null)
            throw new NullPointerException();
        return extrudePerVertex(geom.pointer.raw, extrude, extrusionHints.getId());
    }

    public enum ExtrusionHints {
        TEEH_None(0),
        TEEH_IncludeBottomFace(1),
        TEEH_OmitTopFace(2),
        TEEH_GeneratePolygons(4);

        private int id; // Could be other data type besides int
        private ExtrusionHints(int id) {
            this.id = id;
        }

        public static ExtrusionHints fromId(int id) {
            for (ExtrusionHints type : values()) {
                if (type.getId() == id) {
                    return type;
                }
            }
            return null;
        }

        private int getId() {
            return id;
        }
    }
    static native int computeSpatiaLiteBlobSize(long pointer);
    static native int toSpatiaLiteBlob(long pointer, int srid, byte[] buf, int off, int len, boolean be);
    static native int toSpatiaLiteBlob(long pointer, int srid, long ptr, int len, boolean be);
    static native Geometry parseSpatiaLiteBlob(byte[] buf, int off, int len, int[] srid, int[] numRead);
    static native Geometry parseSpatiaLiteBlob(long ptr, int len, int[] srid, int[] numRead);

    static native int computeWkbSize(long pointer);
    static native int toWkb(long pointer, byte[] buf, int off, int len, boolean be);
    static native int toWkb(long pointer, long ptr, int len, boolean be);
    static native Geometry parseWkb(byte[] buf, int off, int len, int[] numRead);
    static native Geometry parseWkb(long ptr, int len, int[] numRead);
    static native Geometry createEllipse(long locationPtr, double orientation, double major, double minor, int algoPtr);
    static native Geometry createEllipse(double minX, double minY, double maxX, double maxY, int algoPtr);
    static native Geometry extrudeConstant(long pointer, double extrude, int hint);
    static native Geometry extrudePerVertex(long pointer, double[] extrude, int hint);
    static native Geometry createRectangle(long corner1Ptr, long corner2Ptr, int algoPtr);
    static native Geometry createRectangle(long point1Ptr, long point2Ptr, long point3Ptr, int algoPtr);
    static native Geometry createRectangle(long locationPtr, double orientation, double length, double width, int algoPtr);
}
