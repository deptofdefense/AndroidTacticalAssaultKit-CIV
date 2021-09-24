package com.atakmap.spatial;

import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.geometry.GeometryCollection;
import com.atakmap.map.layer.feature.geometry.LineString;
import com.atakmap.map.layer.feature.geometry.Point;
import com.atakmap.map.layer.feature.geometry.Polygon;

/**
 * Helper class providing operations than can be used to interact with Geometry
 * objects.
 */
public class GeometryOperations {
    
    private GeometryOperations() {
        // Only static access to this class
    }
    
    // Tests where a point lies in relation to a line on a plane.
    private static double isLeft(double p0x, double p0y,
                            double p1x, double p1y,
                            double p2x, double p2y){
        return ((p1x - p0x) * (p2y - p0y) - (p2x - p0x) * (p1y - p0y));
    }
    
    // Calculates the Winding Number for a LineString object. The winding number
    // may be used to determine if a point lies inside of a polygon. If the WN
    // is 0, that means that it lies outside of the polygon. A WN of 1 indicates
    // that the point lies inside of the polygon. WNs above 1 indicate that the
    // polygon is non regular and that the point lies within areas of the polygon
    // where edges may cross over themselves creating pockets within the outer
    // ring of the polygon.
    
    // This code is adapted from http://geomalgorithms.com/a03-_inclusion.html and
    // and requests that the following notice be included.
    
    // Copyright 2000 softSurfer, 2012 Dan Sunday
    // This code may be freely used, distributed and modified for any purpose
    // providing that this copyright notice is included with it.
    // SoftSurfer makes no warranty for this code, and cannot be held
    // liable for any real or imagined damage resulting from its use.
    // Users of this code must verify correctness for their application.
    private static int winding(LineString ls, double px, double py){
        int result = 0;

        // loop through all edges of the polygon
        for (int i = 0; i < ls.getNumPoints(); i++) {
            double v0x = ls.getX(i);
            double v0y = ls.getY(i);
            double v1x, v1y;
            if(i == (ls.getNumPoints() -1)){
                v1x = ls.getX(0);
                v1y = ls.getY(0);
            }else{
                v1x = ls.getX(i + 1);
                v1y = ls.getY(i + 1);
            }
            
            if (v0y <= py) {
                if (v1y  > py){
                     if (isLeft(v0x, v0y, v1x, v1y, px, py) > 0){
                         result += 1;
                     }
                }
            }
            else {
                if (v1y  <= py){
                     if (isLeft(v0x, v0y, v1x, v1y, px, py) < 0){
                         result -= 1;
                     }
                }
            }
        }
        
        return result;
    }
    
    /**
     * Tests whether or not a geometry object contains a given point.
     * @param geometry Geometry object to test.
     * @param x X coordinate of the point to test for inclusion in the provided geometry.
     * @param y Y coordinate of the point to test for inclusion in the provided geometry.
     * @return True if the point is contained by the provided Geometry, false otherwise.
     */
    public static boolean contains(Geometry geometry, double x, double y){
        if(geometry.getDimension() != 2){
            throw new IllegalArgumentException("This method does not support 3D geometry objects");
        }
        
        if(geometry instanceof Point){
            if(((Point)geometry).getX() == x && ((Point)geometry).getY() == y){
                return true;
            }
        } else if(geometry instanceof LineString){
            if(((LineString)geometry).isClosed()){
                if(winding((LineString) geometry, x, y) != 0){
                    return true;
                }
            }
        } else if(geometry instanceof Polygon){
            if(winding(((Polygon) geometry).getExteriorRing(), x, y) != 0){
                boolean inPocket = false;
                for(LineString l : ((Polygon)geometry).getInteriorRings()){
                    if(winding(l, x, y) != 0){
                        inPocket = true;
                    }
                }
                
                if(!inPocket){
                    return true;
                }
            }
        } else if(geometry instanceof GeometryCollection){
            for(Geometry g : ((GeometryCollection)geometry).getGeometries()){
                if(contains(g, x, y)){
                    return true;
                }
            }
        }else{
            throw new IllegalArgumentException("Unsupported geometry type.");
        }
        
        return false;
    }

}
