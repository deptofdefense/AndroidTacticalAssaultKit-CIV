package com.atakmap.map.layer.feature.opengl;

import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;

import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.geometry.GeometryCollection;
import com.atakmap.map.layer.feature.geometry.LineString;
import com.atakmap.map.layer.feature.geometry.Point;
import com.atakmap.map.layer.feature.geometry.Polygon;
import com.atakmap.math.Rectangle;

/** @deprecated use the batch feature renderering framework */
@Deprecated
@DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.4")
public final class GLFeatureLayer {
    private static final String TAG = "GLFeatureLayer";

    public static boolean hitTest(Geometry g, GeoPoint point, double radius) {
        return hitTest(g, point, radius, null);
    }

    public static boolean hitTest(Geometry g, GeoPoint point, double radius, GeoPoint touchPoint) {
        if(g instanceof Point) {
            Point p = (Point)g;
            if(touchPoint != null) touchPoint.set(p.getY(), p.getX());
            return (GeoCalculations.distanceTo(point, new GeoPoint(p.getY(), p.getX())) <= radius);
        } else if(g instanceof LineString){
            if(!mbrIntersects(g.getEnvelope(), point, radius))
                return false;
            return testOrthoHit((LineString)g, point, radius, touchPoint);
        } else if(g instanceof Polygon) {
            if(!mbrIntersects(g.getEnvelope(), point, radius))
                return false;
            
            Polygon p = (Polygon)g;
            if(testOrthoHit(p.getExteriorRing(), point, radius, touchPoint))
                return true;
            for(LineString inner : p.getInteriorRings())
                if(testOrthoHit(inner, point, radius, touchPoint))
                    return true;
            return false;
        } else if(g instanceof GeometryCollection) {
            if(!mbrIntersects(g.getEnvelope(), point, radius))
                return false;

            for(Geometry child : ((GeometryCollection)g).getGeometries())
                if(hitTest(child, point, radius, touchPoint))
                    return true;
            return false;
        } else {
            throw new IllegalStateException();
        }
    }

    // XXX - next 3 modified from EditablePolyline, review for optimization
    
    private static boolean mbrIntersects(Envelope mbb, GeoPoint point, double radiusMeters) {
        final double x = point.getLongitude();
        final double y = point.getLatitude();

        if(Rectangle.contains(mbb.minX, mbb.minY, mbb.maxX, mbb.maxY, x, y))
            return true;
        
        // XXX - check distance from minimum bounding box is with the radius
        final double fromX;
        if(x < mbb.minX) {
            fromX = mbb.minX;
        } else if(x > mbb.maxX){
            fromX = mbb.maxX;
        } else {
            fromX = x;
        }
        
        final double fromY;
        if(y < mbb.minY) {
            fromY = mbb.minY;
        } else if(y > mbb.maxY){
            fromY = mbb.maxY;
        } else {
            fromY = y;
        }

        return (GeoCalculations.distanceTo(new GeoPoint(fromY, fromX), new GeoPoint(y, x)) < radiusMeters);
    }

    private static boolean testOrthoHit(LineString linestring, GeoPoint point, double radius, GeoPoint touchPoint) {

        boolean res = mbrIntersects(linestring.getEnvelope(), point, radius);
        if (!res) {
            //Log.d(TAG, "hit not contained in any geobounds");
            return false;
        }

        final int numPoints = linestring.getNumPoints();
        
        final double px = point.getLongitude();
        final double py = point.getLatitude();

        int detected_partition = -1;
        Envelope minibounds = new Envelope(Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN);
        double x0;
        double y0;
        double x1;
        double y1;
        for (int i = 0; i < numPoints-1; ++i) {
            x0 = linestring.getX(i);
            y0 = linestring.getY(i);
            x1 = linestring.getX(i+1);
            y1 = linestring.getY(i+1);
            
            // construct the minimum bounding box for the segment
            minibounds.minX = Math.min(x0, x1);
            minibounds.minY = Math.min(y0, y1);
            minibounds.maxX = Math.max(x0, x1);
            minibounds.maxY = Math.max(y0, y1);
            
            if (mbrIntersects(minibounds, point, radius)) {
                Log.d(TAG, "hit maybe contained in geobounds: " + i);
                Point isect = (touchPoint!=null) ? new Point(0, 0) : null;
                if(dist(x0, y0, x1, y1, px, py, isect) < radius) {
                    if(touchPoint != null && isect != null) 
                         touchPoint.set(isect.getY(), isect.getX());
                    return true;
                }
            }
        }
        //Log.d(TAG, "hit not contained in any sub geobounds");
        return false;
    }

    private static double dist(double x1, double y1, double x2, double y2, double x3,double y3, Point linePt) { // x3,y3 is the point
        double px = x2-x1;
        double py = y2-y1;
    
        double something = px*px + py*py;
    
        double u =  ((x3 - x1) * px + (y3 - y1) * py) / something;
    
        if(u > 1)
            u = 1;
        else if(u < 0)
            u = 0;
    
        double x = x1 + u * px;
        double y = y1 + u * py;
        
        if(linePt != null) {
            linePt.set(x, y);
        }

        return GeoCalculations.distanceTo(new GeoPoint(y, x), new GeoPoint(y3, x3));
    }

} // GLFeatureLayer
