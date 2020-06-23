package com.atakmap.map.layer.feature.geometry;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;

import com.atakmap.map.projection.Projection;
import com.atakmap.map.projection.ProjectionFactory;
import com.atakmap.math.PointD;
import com.atakmap.nio.Buffers;

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
        return parseWkb(ByteBuffer.wrap(arr));
    }

    public static Geometry parseWkb(ByteBuffer wkb) {
        return parseWkbImpl(wkb, 0);
    }

    public static Geometry parseWkbImpl(ByteBuffer wkb, int typeOverride) {
        final int byteOrder = wkb.get()&0xFF;
        switch(byteOrder) {
            case 0x00 :
                wkb.order(ByteOrder.BIG_ENDIAN);
                break;
            case 0x01 :
                wkb.order(ByteOrder.LITTLE_ENDIAN);
                break;
            default :
                throw new IllegalArgumentException("Invalid byte order");
        }
        final int codedType = wkb.getInt();
        final int type = (typeOverride != 0) ? typeOverride : codedType;
        final int dimension = 2 + ((type/1000)%2);
        final boolean hasMeasure = (type/2000)>0;
        
        if(typeOverride != 0 && codedType != typeOverride)
            Log.w("GeometryFactory", "WKB expected type does not match coded type. WKB may be malformed. coded=" + codedType + " expected=" + typeOverride);

        switch(type%1000) {
            case 1 : // wkbPoint
            {
                return parseWkbPoint(wkb, hasMeasure, dimension);
            }
            case 2 : // wkbLineString
            {
                return parseWkbLineString(wkb, hasMeasure, dimension);
            }
            case 17 : // wkbTriangle
            case 3 : // wkbPolygon
            {
                Polygon retval = new Polygon(dimension);
                final int numRings = wkb.getInt();
                for(int i = 0; i < numRings; i++) {
                    retval.addRing(parseWkbLineString(wkb, hasMeasure, dimension));
                }
                return retval;
            }
            case 4 : // wkbMultiPoint
            {
                GeometryCollection retval = new GeometryCollection(dimension);
                final int numPoints = wkb.getInt();
                for(int i = 0; i < numPoints; i++)
                    retval.addGeometry(parseWkbImpl(wkb, ((type/1000)*1000) + 1));
                return retval;
            }
            case 5 : // wkbMultiLineString
            {
                GeometryCollection retval = new GeometryCollection(dimension);
                final int numLines = wkb.getInt();
                for(int i = 0; i < numLines; i++)
                    retval.addGeometry(parseWkbImpl(wkb, ((type/1000)*1000) + 2));
                return retval;
            }
            case 15 : // wkbPolyhedralSurface
            case 16 : // wkbTIN
            case 6 : // wkbMultiPolygon
            {
                GeometryCollection retval = new GeometryCollection(dimension);
                final int numPolygons = wkb.getInt();
                for(int i = 0; i < numPolygons; i++)
                    retval.addGeometry(parseWkbImpl(wkb, ((type/1000)*1000) + 3));
                return retval;
            }
            case 7 : // wkbGeometryCollection
            {
                GeometryCollection retval = new GeometryCollection(dimension);
                final int numGeometries = wkb.getInt();
                for(int i = 0; i < numGeometries; i++)
                    retval.addGeometry(parseWkbImpl(wkb, 0));
                return retval;
            }
            default :
                throw new IllegalArgumentException("Invalid geometry type: " + type);
        }
    }
    
    private static Point parseWkbPoint(ByteBuffer wkb, boolean hasMeasure, int dimension) {
        Point retval = new Point(wkb.getDouble(), wkb.getDouble());
        if(dimension == 3) {
            retval.setDimension(3);
            retval.set(retval.getX(), retval.getY(), wkb.getDouble());
        }
        if(hasMeasure) {
            wkb.position(wkb.position()+8);
        }
        return retval;
    }

    private static LineString parseWkbLineString(ByteBuffer wkb, boolean hasMeasure, int dimension) {
        LineString retval = new LineString(dimension);
        final int numPoints = wkb.getInt();
        if(!hasMeasure) {
            if(dimension == 2) {
                for (int i = 0; i < numPoints; i++) {
                    retval.addPoint(wkb.getDouble(), wkb.getDouble());
                }
            } else if(dimension == 3) {
                for (int i = 0; i < numPoints; i++) {
                    retval.addPoint(wkb.getDouble(), wkb.getDouble(), wkb.getDouble());
                }
            }
        } else {
            if(dimension == 2) {
                for (int i = 0; i < numPoints; i++) {
                    retval.addPoint(wkb.getDouble(), wkb.getDouble());

                    wkb.position(wkb.position()+8);
                }
            } else if(dimension == 3) {
                for (int i = 0; i < numPoints; i++) {
                    retval.addPoint(wkb.getDouble(), wkb.getDouble(), wkb.getDouble());

                    wkb.position(wkb.position()+8);
                }
            }
        }
        return retval;
    }

    private static Polygon parseWkbPolygon(ByteBuffer wkb, boolean hasMeasure, int dimension) {
        Polygon retval = new Polygon(dimension);
        final int numRings = wkb.getInt();
        for(int i = 0; i < numRings; i++) {
            retval.addRing(parseWkbLineString(wkb, hasMeasure, dimension));
        }
        return retval;
    }

    public static Geometry parseSpatiaLiteBlob(byte[] arr) {
        if(arr == null)
            return null;
        return parseSpatiaLiteBlob(ByteBuffer.wrap(arr));
    }

    public static Geometry parseSpatiaLiteBlob(ByteBuffer blob) {
        if(blob.get() != 0x00)
            throw new IllegalArgumentException("Bad START byte");
        switch(blob.get()&0xFF) {
            case 0x00 :
                blob.order(ByteOrder.BIG_ENDIAN);
                break;
            case 0x01 :
                blob.order(ByteOrder.LITTLE_ENDIAN);
                break;
            default :
                throw new IllegalArgumentException("Invalid ENDIAN");
        }
//        Buffers.skip(blob, 36); // SRID + MBR
        final int srid = blob.getInt ();
        Buffers.skip(blob, 32); // MBR
        if((blob.get()&0xFF) != 0x7C)
            throw new IllegalArgumentException("Bad MBR_END byte");
        
        final Projection projection = srid != 4326
                                    ? ProjectionFactory.getProjection (srid)
                                    : null;

        final int classType = blob.getInt();
        final boolean hasZ = ((classType/1000)%2) == 1;
        final boolean hasM = (((classType/1000)%1000)>>1) == 1;
        final boolean isCompressed = ((classType/1000000) == 1);
        
        int dimension = 2;
        if(hasZ)
            dimension++;

        Geometry retval;
        switch(classType%1000) {
            case 1 : // point
                retval = parseSpatiaLitePointClass(blob, projection, dimension, hasM, isCompressed);
                break;
            case 2 : // linestring
                retval = parseSpatiaLiteLineStringClass(blob, projection, dimension, hasM, isCompressed);
                break;
            case 3 : // polygon
                retval = parseSpatiaLitePolygonClass(blob, projection, dimension, hasM, isCompressed);
                break;
            case 4 : // multipoint
                retval = parseSpatiaLiteGeometryCollection(blob, projection, dimension, hasM, isCompressed, Point.class);
                break;
            case 5 : // multilinestring
                retval = parseSpatiaLiteGeometryCollection(blob, projection, dimension, hasM, isCompressed, LineString.class);
                break;
            case 6 : // multipolygon
                retval = parseSpatiaLiteGeometryCollection(blob, projection, dimension, hasM, isCompressed, Polygon.class);
                break;
            case 7 : // geometry collection
                retval = parseSpatiaLiteGeometryCollection(blob, projection, dimension, hasM, isCompressed, Geometry.class);
                break;
            default :
                throw new IllegalArgumentException("Bad CLASS TYPE");
        }
        
        if((blob.get()&0xFF) != 0xFE)
            throw new IllegalArgumentException("Bad END byte");

        return retval;
    }
    
    private static Point parseSpatiaLitePointClass(ByteBuffer blob, Projection projection, int dimension, boolean hasM, boolean compressed) {
        Point retval = new Point(blob.getDouble(), blob.getDouble());
        if (projection != null)
          {
            GeoPoint geoPt = projection.inverse (new PointD (retval.getX(), retval.getY()),
                                                 null);

            retval.set (geoPt.getLongitude (),
                        geoPt.getLatitude ());
          }
        if(dimension == 3)
            retval.set(retval.getX(), retval.getY(), blob.getDouble());
        if(hasM)
            Buffers.skip(blob, 8);
        return retval;
    }
    
    private static LineString parseSpatiaLiteLineStringClass(ByteBuffer blob, Projection projection, int dimension, boolean hasM, boolean compressed) {
        LineString retval;
        if(!compressed) {
            // coding for uncompressed blob is same as WKB
            retval = parseWkbLineString(blob, hasM, dimension);
        } else if(dimension == 2) {
            final int numPoints = blob.getInt();

            final double firstX = blob.getDouble();
            final double firstY = blob.getDouble();
            if(hasM)
                Buffers.skip(blob, 8);

            retval = new LineString(dimension);
            retval.addPoint(firstX, firstY);

            for(int i = 1; i < numPoints; i++) {
                final float x = blob.getFloat();
                final float y = blob.getFloat();
                if(hasM)
                    Buffers.skip(blob, 4);
                retval.addPoint(firstX+x, firstY+y);
            }
        } else if(dimension == 3) {
            final int numPoints = blob.getInt();

            final double firstX = blob.getDouble();
            final double firstY = blob.getDouble();
            final double firstZ = blob.getDouble();
            if(hasM)
                Buffers.skip(blob, 8);

            retval = new LineString(dimension);
            retval.addPoint(firstX, firstY, firstZ);

            for(int i = 1; i < numPoints; i++) {
                final float x = blob.getFloat();
                final float y = blob.getFloat();
                final float z = blob.getFloat();
                if(hasM)
                    Buffers.skip(blob, 4);
                retval.addPoint(firstX+x, firstY+y, firstZ+z);
            }
        } else {
            throw new IllegalArgumentException();
        }
        if (projection != null)
          {
            GeoPoint geoPt = GeoPoint.createMutable();
            PointD pt = new PointD(0d, 0d, 0d);
            final int numPoints = retval.getNumPoints();
            for (int i = 0; i < numPoints; ++i)
              {
                pt.x = retval.getX(i);
                pt.y = retval.getY(i);
                projection.inverse (pt,
                                    geoPt);
                retval.setX(i, geoPt.getLongitude());
                retval.setY(i, geoPt.getLatitude());
              }
          }
        return retval;
    }
    
    private static Polygon parseSpatiaLitePolygonClass(ByteBuffer blob, Projection projection, int dimension, boolean hasM, boolean compressed) {
        Polygon retval = new Polygon(dimension);
        final int numRings = blob.getInt();
        for(int i = 0; i < numRings; i++) {
            retval.addRing(parseSpatiaLiteLineStringClass(blob, projection, dimension, hasM, compressed));
        }
        return retval;
    }
    
    private static Geometry parseSpatiaLiteCollectionEntity(ByteBuffer blob, Projection projection, int dimension, boolean hasM, boolean compressed) {
        if((blob.get()&0xFF) != 0x69)
            throw new IllegalArgumentException("Bad ENTITY byte");
        int classType = blob.getInt();
        switch(classType%1000) {
            case 1 : // point
                return parseSpatiaLitePointClass(blob, projection, dimension, hasM, compressed);
            case 2 : // linestring
                return parseSpatiaLiteLineStringClass(blob, projection, dimension, hasM, compressed);
            case 3 : // polygon
                return parseSpatiaLitePolygonClass(blob, projection, dimension, hasM, compressed);
            default :
                throw new IllegalArgumentException("Invalid collection entity CLASS TYPE");
        }
    }
    
    private static Geometry parseSpatiaLiteGeometryCollection(ByteBuffer blob, Projection projection, int dimension, boolean hasM, boolean compressed, Class<? extends Geometry> classRestriction) {
        GeometryCollection retval = new GeometryCollection(dimension);
        final int numGeometries = blob.getInt();
        Geometry child;
        for(int i = 0; i < numGeometries; i++) {
            child = parseSpatiaLiteCollectionEntity(blob, projection, dimension, hasM, compressed);
            if(!classRestriction.isAssignableFrom(child.getClass()))
                throw new IllegalArgumentException();
            retval.addGeometry(child);
        }
        return retval;
    }

    public static Geometry parseWkt(CharSequence wkt) {
        throw new UnsupportedOperationException();
    }
}
