
package com.atakmap.map.layer.feature.geometry.opengl;

import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.geometry.GeometryCollection;
import com.atakmap.map.layer.feature.geometry.LineString;
import com.atakmap.map.layer.feature.geometry.Point;
import com.atakmap.map.layer.feature.geometry.Polygon;

public abstract class GLGeometry {
    public final static int VERTICES_PROJECTED = 0;
    public final static int VERTICES_PIXEL = 1;

    protected final Geometry geometry;

    protected int verticesSrid;
    protected int pixelCoordsVersion;

    GLGeometry(Geometry geom) {
        this.geometry = geom;
    }
    
    public Geometry getSubject() {
        return this.geometry;
    }
    
    public static GLGeometry createRenderer(Geometry geom) {
        if(geom instanceof Point)
            return new GLPoint((Point)geom);
        else if(geom instanceof Polygon)
            return new GLPolygon((Polygon)geom);
        else if(geom instanceof LineString)
            return new GLLineString((LineString)geom);
        else if(geom instanceof GeometryCollection)
            return new GLGeometryCollection((GeometryCollection)geom);
        else
            throw new IllegalStateException();
    }
}
