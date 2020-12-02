
package com.atakmap.map.layer.feature.geometry.opengl;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;

import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.map.layer.feature.geometry.LineString;

import com.atakmap.map.opengl.GLMapView;

/** @deprecated use the batch feature renderering framework */
@Deprecated
@DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.4")
public final class GLLineString extends GLGeometry {

    private final DoubleBuffer points;
    private final FloatBuffer vertices;    
    private final FloatBuffer pixels;

    private final int numPoints;
    
    public GLLineString(LineString lineString) {
        super(lineString);

        this.numPoints = lineString.getNumPoints();
        
        ByteBuffer buf;
        
        buf = com.atakmap.lang.Unsafe.allocateDirect(this.numPoints*2*8);
        buf.order(ByteOrder.nativeOrder());
        this.points = buf.asDoubleBuffer();
        
        buf = com.atakmap.lang.Unsafe.allocateDirect(this.numPoints*3*4);
        buf.order(ByteOrder.nativeOrder());
        this.vertices = buf.asFloatBuffer();
        
        buf = com.atakmap.lang.Unsafe.allocateDirect(this.numPoints*2*4);
        buf.order(ByteOrder.nativeOrder());
        this.pixels = buf.asFloatBuffer();
        
        for(int i = 0; i < numPoints; i++) {
            this.points.put(i*2, lineString.getX(i));
            this.points.put(i*2+1, lineString.getY(i));
        }
    }

    public FloatBuffer getVertices(GLMapView view, int vertexType) {
        switch(vertexType) {
            case GLGeometry.VERTICES_PROJECTED :
                if(this.verticesSrid != view.drawSrid) {
                    for(int i = 0; i < this.numPoints; i++) {
                        view.scratch.geo.set(this.points.get(i*2+1), this.points.get(i*2));
                        view.scene.mapProjection.forward(view.scratch.geo, view.scratch.pointD);
                        this.vertices.put(i*3, (float)view.scratch.pointD.x);
                        this.vertices.put(i*3+1, (float)view.scratch.pointD.y);
                    }
                }
                return this.vertices;
            case GLGeometry.VERTICES_PIXEL :
                // XXX - if a native bulk matrix multiplication were available
                //       it would probably be much faster to use the projected
                //       vertices but I believe we will be better off using the
                //       source points and going from geodetic straight to pixel
                //       using the bulk forward
                if(this.pixelCoordsVersion != view.drawVersion) {
                    view.forward(this.points, this.pixels);
                    this.pixelCoordsVersion = view.drawVersion;
                }
                return this.pixels;
            default :
                throw new IllegalArgumentException();
        }
    }
    
    public int getNumVertices() {
        return this.numPoints;
    }

}
