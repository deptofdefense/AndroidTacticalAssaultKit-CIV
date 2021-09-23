
package com.atakmap.map.layer.feature.geometry.opengl;

import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.map.layer.feature.geometry.Point;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.math.PointD;

/** @deprecated use the batch feature renderering framework */
@Deprecated
@DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.4")
public class GLPoint extends GLGeometry {

    private final PointD point;
    private PointD vertex;
    private PointD pixel;

    public GLPoint(Point point) {
        super(point);
        
        this.point = new PointD(point.getX(), point.getY(), point.getZ());
        this.vertex = new PointD(0, 0, 0);
        this.pixel = new PointD(0, 0, 0);
    }
    
    public void getVertex(GLMapView view, int vertexType, PointD vertex) {
        if(this.verticesSrid != view.drawSrid) {
            view.scratch.geo.set(this.point.y, this.point.x);
            view.scene.mapProjection.forward(view.scratch.geo, this.vertex);
            this.verticesSrid = view.drawSrid;
        }
        switch(vertexType) {
            case GLGeometry.VERTICES_PIXEL :
                if(this.pixelCoordsVersion != view.drawVersion) {
                    view.scene.forward.transform(this.vertex, this.pixel);
                    this.pixelCoordsVersion = view.drawVersion;
                }
                vertex.x = pixel.x;
                vertex.y = pixel.y;
                vertex.z = pixel.z;
                break;
            case GLGeometry.VERTICES_PROJECTED :
                vertex.x = this.vertex.x;
                vertex.y = this.vertex.y;
                vertex.z = this.vertex.z;
                break;
            default :
                throw new IllegalArgumentException();
        }
    }
}