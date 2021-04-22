package com.atakmap.spatial;


import java.util.Collection;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.geometry.GeometryCollection;
import com.atakmap.map.layer.feature.geometry.LineString;
import com.atakmap.map.layer.feature.geometry.Point;
import com.atakmap.map.layer.feature.geometry.Polygon;
import com.atakmap.map.projection.Projection;
import com.atakmap.map.projection.ProjectionFactory;
import com.atakmap.math.PointD;

public final class GeometryTransformer {
    private GeometryTransformer() {}
    
    public static Geometry transform(Geometry src, int srcSrid, int dstSrid) {
        if(srcSrid == dstSrid)
            return src;

        final Projection srcProj = ProjectionFactory.getProjection(srcSrid);
        final Projection dstProj = ProjectionFactory.getProjection(dstSrid);
        
        if(src.getDimension() == 2 && dstSrid == 4326)
            return transformTo4326_2D(src, srcProj);
        if(src.getDimension() == 2 && srcSrid == 4326)
            return transformFrom4326_2D(src, dstProj);
        else if(src.getDimension() == 2) 
            return transformGeneric2DImpl(src, srcProj, dstProj);
        else if(src.getDimension() == 3)
            return transformGeneric3DImpl(src, srcProj, dstProj);
        else
            throw new IllegalStateException();
    }
    
    public static Geometry transform(Geometry src, Projection srcProj, Projection dstProj) {
        if(srcProj.getSpatialReferenceID() == dstProj.getSpatialReferenceID())
            return src;
        
        if(src.getDimension() == 2)
            return transformGeneric2DImpl(src, srcProj, dstProj);
        else if(src.getDimension() == 3)
            return transformGeneric3DImpl(src, srcProj, dstProj);
        else
            throw new IllegalStateException();
    }

    public static Envelope transform(Envelope src, int srcSrid, int dstSrid) {
        if(srcSrid == dstSrid)
            return src;

        final Projection srcProj = ProjectionFactory.getProjection(srcSrid);
        final Projection dstProj = ProjectionFactory.getProjection(dstSrid);

        return transform(src, srcProj, dstProj);
    }

    public static Envelope transform(Envelope src, Projection srcProj, Projection dstProj) {
        if(srcProj.getSpatialReferenceID() == dstProj.getSpatialReferenceID())
            return src;


        GeoPoint lla = GeoPoint.createMutable();
        PointD xyz = new PointD(0, 0, 0);

        xyz.x = src.minX;
        xyz.y = src.minY;
        xyz.z = src.minZ;
        srcProj.inverse(xyz, lla);
        dstProj.forward(lla, xyz);

        Envelope dst = new Envelope(xyz.x, xyz.y, xyz.z, xyz.x, xyz.y, xyz.z);

        xyz.x = src.maxX;
        xyz.y = src.minY;
        xyz.z = src.minZ;
        srcProj.inverse(xyz, lla);
        dstProj.forward(lla, xyz);
        if(xyz.x < dst.minX)        dst.minX = xyz.x;
        else if(xyz.x > dst.maxX)   dst.maxX = xyz.x;
        if(xyz.y < dst.minY)        dst.minY = xyz.y;
        else if(xyz.y > dst.maxY)   dst.maxY = xyz.y;
        if(xyz.z < dst.minZ)        dst.minZ = xyz.z;
        else if(xyz.z > dst.maxZ)   dst.maxZ = xyz.z;

        xyz.x = src.maxX;
        xyz.y = src.maxY;
        xyz.z = src.minZ;
        srcProj.inverse(xyz, lla);
        dstProj.forward(lla, xyz);
        if(xyz.x < dst.minX)        dst.minX = xyz.x;
        else if(xyz.x > dst.maxX)   dst.maxX = xyz.x;
        if(xyz.y < dst.minY)        dst.minY = xyz.y;
        else if(xyz.y > dst.maxY)   dst.maxY = xyz.y;
        if(xyz.z < dst.minZ)        dst.minZ = xyz.z;
        else if(xyz.z > dst.maxZ)   dst.maxZ = xyz.z;

        xyz.x = src.minX;
        xyz.y = src.maxY;
        xyz.z = src.minZ;
        srcProj.inverse(xyz, lla);
        dstProj.forward(lla, xyz);
        if(xyz.x < dst.minX)        dst.minX = xyz.x;
        else if(xyz.x > dst.maxX)   dst.maxX = xyz.x;
        if(xyz.y < dst.minY)        dst.minY = xyz.y;
        else if(xyz.y > dst.maxY)   dst.maxY = xyz.y;
        if(xyz.z < dst.minZ)        dst.minZ = xyz.z;
        else if(xyz.z > dst.maxZ)   dst.maxZ = xyz.z;

        // short-circuit on 2D
        if(Double.isNaN(src.maxX) || (src.minX == src.maxX))
            return dst;

        xyz.x = src.minX;
        xyz.y = src.minY;
        xyz.z = src.maxZ;
        srcProj.inverse(xyz, lla);
        dstProj.forward(lla, xyz);
        if(xyz.x < dst.minX)        dst.minX = xyz.x;
        else if(xyz.x > dst.maxX)   dst.maxX = xyz.x;
        if(xyz.y < dst.minY)        dst.minY = xyz.y;
        else if(xyz.y > dst.maxY)   dst.maxY = xyz.y;
        if(xyz.z < dst.minZ)        dst.minZ = xyz.z;
        else if(xyz.z > dst.maxZ)   dst.maxZ = xyz.z;

        xyz.x = src.maxX;
        xyz.y = src.minY;
        xyz.z = src.maxZ;
        srcProj.inverse(xyz, lla);
        dstProj.forward(lla, xyz);
        if(xyz.x < dst.minX)        dst.minX = xyz.x;
        else if(xyz.x > dst.maxX)   dst.maxX = xyz.x;
        if(xyz.y < dst.minY)        dst.minY = xyz.y;
        else if(xyz.y > dst.maxY)   dst.maxY = xyz.y;
        if(xyz.z < dst.minZ)        dst.minZ = xyz.z;
        else if(xyz.z > dst.maxZ)   dst.maxZ = xyz.z;

        xyz.x = src.maxX;
        xyz.y = src.maxY;
        xyz.z = src.maxZ;
        srcProj.inverse(xyz, lla);
        dstProj.forward(lla, xyz);
        if(xyz.x < dst.minX)        dst.minX = xyz.x;
        else if(xyz.x > dst.maxX)   dst.maxX = xyz.x;
        if(xyz.y < dst.minY)        dst.minY = xyz.y;
        else if(xyz.y > dst.maxY)   dst.maxY = xyz.y;
        if(xyz.z < dst.minZ)        dst.minZ = xyz.z;
        else if(xyz.z > dst.maxZ)   dst.maxZ = xyz.z;

        xyz.x = src.minX;
        xyz.y = src.maxY;
        xyz.z = src.maxZ;
        srcProj.inverse(xyz, lla);
        dstProj.forward(lla, xyz);
        if(xyz.x < dst.minX)        dst.minX = xyz.x;
        else if(xyz.x > dst.maxX)   dst.maxX = xyz.x;
        if(xyz.y < dst.minY)        dst.minY = xyz.y;
        else if(xyz.y > dst.maxY)   dst.maxY = xyz.y;
        if(xyz.z < dst.minZ)        dst.minZ = xyz.z;
        else if(xyz.z > dst.maxZ)   dst.maxZ = xyz.z;

        return dst;
    }

    private static Geometry transformGeneric2DImpl(Geometry src, Projection srcProj, Projection dstProj) {
        if(src instanceof Point) {
            Point point = (Point)src;
            PointD proj = new PointD(point.getX(), point.getY());
            GeoPoint geo = GeoPoint.createMutable();
            srcProj.inverse(proj, geo);
            dstProj.forward(geo, proj);
            return new Point(proj.x, proj.y);
        } else if(src instanceof LineString) {
            LineString linestring = (LineString)src;
            PointD proj = new PointD(0d, 0d);
            GeoPoint geo = GeoPoint.createMutable();
            
            LineString dst = new LineString(src.getDimension());
            for(int i = 0; i < linestring.getNumPoints(); i++) {
                proj.x = linestring.getX(i);
                proj.y = linestring.getY(i);
                srcProj.inverse(proj, geo);
                dstProj.forward(geo, proj);
                dst.addPoint(proj.x, proj.y);
            }
            return dst;            
        } else if(src instanceof Polygon) {
            Polygon polygon = (Polygon)src;
            
            Polygon dst = new Polygon(src.getDimension());
            dst.addRing((LineString)transformGeneric2DImpl(polygon.getExteriorRing(), srcProj, dstProj));
            
            Collection<LineString> innerRings = polygon.getInteriorRings();
            for(LineString ring : innerRings)
                dst.addRing((LineString)transformGeneric2DImpl(ring, srcProj, dstProj));
            return dst;
        } else if(src instanceof GeometryCollection) {
            GeometryCollection dst = new GeometryCollection(src.getDimension());
            Collection<Geometry> srcChildren = ((GeometryCollection) src).getGeometries();
            for(Geometry child : srcChildren)
                dst.addGeometry(transformGeneric2DImpl(child, srcProj, dstProj));
            return dst;
        } else {
            throw new IllegalStateException();
        }
    }
    
    private static Geometry transformTo4326_2D(Geometry src, Projection srcProj) {
        if(src instanceof Point) {
            Point point = (Point)src;
            PointD proj = new PointD(point.getX(), point.getY());
            GeoPoint geo = GeoPoint.createMutable();
            srcProj.inverse(proj, geo);
            return new Point(geo.getLongitude(), geo.getLatitude());
        } else if(src instanceof LineString) {
            LineString linestring = (LineString)src;
            PointD proj = new PointD(0d, 0d);
            GeoPoint geo = GeoPoint.createMutable();
            
            LineString dst = new LineString(src.getDimension());
            for(int i = 0; i < linestring.getNumPoints(); i++) {
                proj.x = linestring.getX(i);
                proj.y = linestring.getY(i);
                srcProj.inverse(proj, geo);
                dst.addPoint(geo.getLongitude(), geo.getLatitude());
            }
            return dst;            
        } else if(src instanceof Polygon) {
            Polygon polygon = (Polygon)src;
            
            Polygon dst = new Polygon(src.getDimension());
            dst.addRing((LineString)transformTo4326_2D(polygon.getExteriorRing(), srcProj));
            
            Collection<LineString> innerRings = polygon.getInteriorRings();
            for(LineString ring : innerRings)
                dst.addRing((LineString)transformTo4326_2D(ring, srcProj));
            return dst;
        } else if(src instanceof GeometryCollection) {
            GeometryCollection dst = new GeometryCollection(src.getDimension());
            Collection<Geometry> srcChildren = ((GeometryCollection) src).getGeometries();
            for(Geometry child : srcChildren)
                dst.addGeometry(transformTo4326_2D(child, srcProj));
            return dst;
        } else {
            throw new IllegalStateException();
        }
    }
    
    private static Geometry transformFrom4326_2D(Geometry src, Projection dstProj) {
        if(src instanceof Point) {
            Point point = (Point)src;
            PointD proj = new PointD(0d, 0d);
            GeoPoint geo = GeoPoint.createMutable().set(point.getY(), point.getX());
            dstProj.forward(geo, proj);
            return new Point(proj.x, proj.y);
        } else if(src instanceof LineString) {
            LineString linestring = (LineString)src;
            PointD proj = new PointD(0d, 0d);
            GeoPoint geo = GeoPoint.createMutable();
            
            LineString dst = new LineString(src.getDimension());
            for(int i = 0; i < linestring.getNumPoints(); i++) {
                geo.set(linestring.getY(i), linestring.getX(i));
                dstProj.forward(geo, proj);
                dst.addPoint(proj.x, proj.y);
            }
            return dst;            
        } else if(src instanceof Polygon) {
            Polygon polygon = (Polygon)src;
            
            Polygon dst = new Polygon(src.getDimension());
            dst.addRing((LineString)transformFrom4326_2D(polygon.getExteriorRing(), dstProj));
            
            Collection<LineString> innerRings = polygon.getInteriorRings();
            for(LineString ring : innerRings)
                dst.addRing((LineString)transformFrom4326_2D(ring, dstProj));
            return dst;
        } else if(src instanceof GeometryCollection) {
            GeometryCollection dst = new GeometryCollection(src.getDimension());
            Collection<Geometry> srcChildren = ((GeometryCollection) src).getGeometries();
            for(Geometry child : srcChildren)
                dst.addGeometry(transformFrom4326_2D(child, dstProj));
            return dst;
        } else {
            throw new IllegalStateException();
        }
    }
    
    private static Geometry transformGeneric3DImpl(Geometry src, Projection srcProj, Projection dstProj) {
        if(src instanceof Point) {
            Point point = (Point)src;
            PointD proj = new PointD(point.getX(), point.getY(), point.getZ());
            GeoPoint geo = GeoPoint.createMutable();
            srcProj.inverse(proj, geo);
            dstProj.forward(geo, proj);
            return new Point(proj.x, proj.y, proj.z);
        } else if(src instanceof LineString) {
            LineString linestring = (LineString)src;
            PointD proj = new PointD(0d, 0d);
            GeoPoint geo = GeoPoint.createMutable();
            
            LineString dst = new LineString(src.getDimension());
            for(int i = 0; i < linestring.getNumPoints(); i++) {
                proj.x = linestring.getX(i);
                proj.y = linestring.getY(i);
                proj.z = linestring.getZ(i);
                srcProj.inverse(proj, geo);
                dstProj.forward(geo, proj);
                dst.addPoint(proj.x, proj.y, proj.z);
            }
            return dst;            
        } else if(src instanceof Polygon) {
            Polygon polygon = (Polygon)src;
            
            Polygon dst = new Polygon(src.getDimension());
            dst.addRing((LineString)transformGeneric3DImpl(polygon.getExteriorRing(), srcProj, dstProj));
            
            Collection<LineString> innerRings = polygon.getInteriorRings();
            for(LineString ring : innerRings)
                dst.addRing((LineString)transformGeneric3DImpl(ring, srcProj, dstProj));
            return dst;
        } else if(src instanceof GeometryCollection) {
            GeometryCollection dst = new GeometryCollection(src.getDimension());
            Collection<Geometry> srcChildren = ((GeometryCollection) src).getGeometries();
            for(Geometry child : srcChildren)
                dst.addGeometry(transformGeneric3DImpl(child, srcProj, dstProj));
            return dst;
        } else {
            throw new IllegalStateException();
        }
    }
    
    private static Geometry transformTo4326_3D(Geometry src, Projection srcProj) {
        if(src instanceof Point) {
            Point point = (Point)src;
            PointD proj = new PointD(point.getX(), point.getY(), point.getZ());
            GeoPoint geo = GeoPoint.createMutable();
            srcProj.inverse(proj, geo);
            return new Point(geo.getLongitude(), geo.getLatitude());
        } else if(src instanceof LineString) {
            LineString linestring = (LineString)src;
            PointD proj = new PointD(0d, 0d);
            GeoPoint geo = GeoPoint.createMutable();
            
            LineString dst = new LineString(src.getDimension());
            for(int i = 0; i < linestring.getNumPoints(); i++) {
                proj.x = linestring.getX(i);
                proj.y = linestring.getY(i);
                proj.z = linestring.getZ(i);
                srcProj.inverse(proj, geo);
                dst.addPoint(geo.getLongitude(), geo.getLatitude());
            }
            return dst;            
        } else if(src instanceof Polygon) {
            Polygon polygon = (Polygon)src;
            
            Polygon dst = new Polygon(src.getDimension());
            dst.addRing((LineString)transformTo4326_3D(polygon.getExteriorRing(), srcProj));
            
            Collection<LineString> innerRings = polygon.getInteriorRings();
            for(LineString ring : innerRings)
                dst.addRing((LineString)transformTo4326_3D(ring, srcProj));
            return dst;
        } else if(src instanceof GeometryCollection) {
            GeometryCollection dst = new GeometryCollection(src.getDimension());
            Collection<Geometry> srcChildren = ((GeometryCollection) src).getGeometries();
            for(Geometry child : srcChildren)
                dst.addGeometry(transformTo4326_3D(child, srcProj));
            return dst;
        } else {
            throw new IllegalStateException();
        }
    }
    
    private static Geometry transformFrom4326_3D(Geometry src, Projection dstProj) {
        if(src instanceof Point) {
            Point point = (Point)src;
            PointD proj = new PointD(0d, 0d);
            GeoPoint geo = GeoPoint.createMutable();
            geo.set(point.getY(), point.getX());

            if(point.getZ() != 0d)
                geo.set(point.getZ());
            dstProj.forward(geo, proj);
            return new Point(proj.x, proj.y);
        } else if(src instanceof LineString) {
            LineString linestring = (LineString)src;
            PointD proj = new PointD(0d, 0d);
            GeoPoint geo = GeoPoint.createMutable();
            
            LineString dst = new LineString(src.getDimension());
            for(int i = 0; i < linestring.getNumPoints(); i++) {
                geo.set(linestring.getY(i), linestring.getX(i));
                geo.set(linestring.getZ(i));
                dstProj.forward(geo, proj);
                dst.addPoint(proj.x, proj.y);
            }
            return dst;            
        } else if(src instanceof Polygon) {
            Polygon polygon = (Polygon)src;
            
            Polygon dst = new Polygon(src.getDimension());
            dst.addRing((LineString)transformFrom4326_3D(polygon.getExteriorRing(), dstProj));
            
            Collection<LineString> innerRings = polygon.getInteriorRings();
            for(LineString ring : innerRings)
                dst.addRing((LineString)transformFrom4326_3D(ring, dstProj));
            return dst;
        } else if(src instanceof GeometryCollection) {
            GeometryCollection dst = new GeometryCollection(src.getDimension());
            Collection<Geometry> srcChildren = ((GeometryCollection) src).getGeometries();
            for(Geometry child : srcChildren)
                dst.addGeometry(transformFrom4326_3D(child, dstProj));
            return dst;
        } else {
            throw new IllegalStateException();
        }
    }
}
