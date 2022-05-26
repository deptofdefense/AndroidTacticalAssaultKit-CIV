
package com.atakmap.map.layer.feature.geometry.opengl;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;

import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.map.layer.feature.geometry.LineString;
import com.atakmap.map.layer.feature.geometry.Polygon;
import com.atakmap.map.opengl.GLMapView;

/** @deprecated use the batch feature renderering framework */
@Deprecated
@DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.4")
public final class GLPolygon extends GLGeometry {

    /** longitude,latitude pairs */
    private final DoubleBuffer points;
    /** projected coordinate space x,y,z */
    private final FloatBuffer vertices;    
    /** screen pixels x,y */
    private final FloatBuffer pixels;
    
    /** number of vertices per ring */
    private final int[] ringVerts;
    /** offset in vertices to start of ring */
    private final int[] ringOffset;
    
    private int totalPoints;

    public GLPolygon(Polygon polygon) {
        super(polygon);
        
        final int numRings = 1+polygon.getInteriorRings().size();
        this.ringVerts = new int[numRings];
        this.ringOffset = new int[numRings];
        
        this.totalPoints = 0;
        
        int idx = 0;
        this.ringVerts[idx] = polygon.getExteriorRing().getNumPoints();
        this.ringOffset[idx] = this.totalPoints;
        this.totalPoints += this.ringVerts[idx];
        idx++;
        
        for(LineString inner : polygon.getInteriorRings()) {
            this.ringVerts[idx] = inner.getNumPoints();
            this.ringOffset[idx] = this.totalPoints;
            this.totalPoints += this.ringVerts[idx];
            idx++;
        }
        
        ByteBuffer buf;
        
        buf = com.atakmap.lang.Unsafe.allocateDirect(this.totalPoints*2*8);
        buf.order(ByteOrder.nativeOrder());
        this.points = buf.asDoubleBuffer();
        
        buf = com.atakmap.lang.Unsafe.allocateDirect(this.totalPoints*3*4);
        buf.order(ByteOrder.nativeOrder());
        this.vertices = buf.asFloatBuffer();
        
        buf = com.atakmap.lang.Unsafe.allocateDirect(this.totalPoints*2*4);
        buf.order(ByteOrder.nativeOrder());
        this.pixels = buf.asFloatBuffer();
        
        idx = 0;
        final LineString exterior = polygon.getExteriorRing();
        for(int i = 0; i < exterior.getNumPoints(); i++) {
            this.points.put(idx++, exterior.getX(i));
            this.points.put(idx++, exterior.getY(i));
        }

        for(LineString inner : polygon.getInteriorRings()) {
            for(int i = 0; i < inner.getNumPoints(); i++) {
                this.points.put(idx++, inner.getX(i));
                this.points.put(idx++, inner.getY(i));
            }
        }
    }

    public FloatBuffer getVertices(GLMapView view, int vertexType, int ring) {
        final int vertSize;
        FloatBuffer retval;
        switch(vertexType) {
            case VERTICES_PROJECTED :
                if(this.verticesSrid != view.drawSrid) {
                    for(int i = 0; i < this.totalPoints; i++) {
                        view.scratch.geo.set(this.points.get(i*2+1), this.points.get(i*2));
                        view.scene.mapProjection.forward(view.scratch.geo, view.scratch.pointD);
                        this.vertices.put(i*3, (float)view.scratch.pointD.x);
                        this.vertices.put(i*3+1, (float)view.scratch.pointD.y);
                        this.vertices.put(i*3+2, (float)view.scratch.pointD.z);
                    }
                }
                retval = this.vertices;
                vertSize = 3;
                break;
            case VERTICES_PIXEL :
                // XXX - if a native bulk matrix multiplication were available
                //       it would probably be much faster to use the projected
                //       vertices but I believe we will be better off using the
                //       source points and going from geodetic straight to pixel
                //       using the bulk forward
                if(this.pixelCoordsVersion != view.drawVersion) {
                    view.forward(this.points, this.pixels);
                    this.pixelCoordsVersion = view.drawVersion;
                }
                retval = this.pixels;
                vertSize = 2;
                break;
            default :
                throw new IllegalArgumentException();
        }
        // XXX - note that we are not creating a slice as that would make the
        //       Buffer incompatible with the various pointer functions in
        //       Unsafe and it is also unsupported by the C++ SDK
        retval = retval.duplicate();
        retval.position(this.ringOffset[ring]*vertSize);
        retval.limit(retval.position()+this.ringVerts[ring]*vertSize);
        return retval;
    }    

    public int getNumVertices(int ring) {
        return this.ringVerts[ring];
    }

    public int getNumInteriorRings() {
        return (this.ringVerts.length-1);
    }
    
    public DoubleBuffer getPoints(){
        return this.points.duplicate();
    }
    
    public DoubleBuffer getPoints(int ring) {
        // XXX - note that we are not creating a slice as that would make the
        //       Buffer incompatible with the various pointer functions in
        //       Unsafe and it is also unsupported by the C++ SDK
        DoubleBuffer retval = this.points.duplicate();
        retval.position(this.ringOffset[ring]*2);
        retval.limit(retval.position()+this.ringVerts[ring]*2);
        return retval;
    }
}
