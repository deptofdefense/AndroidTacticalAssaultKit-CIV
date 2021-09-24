package com.atakmap.map.layer.raster.tilereader.opengl;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;

import android.graphics.PointF;


import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.lang.Unsafe;
import com.atakmap.map.layer.raster.DatasetProjection2;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.math.MathUtils;
import com.atakmap.math.Matrix;
import com.atakmap.math.NoninvertibleTransformException;
import com.atakmap.math.PointD;
import com.atakmap.opengl.GLES20FixedPipeline;
import com.atakmap.opengl.GLTexture;
import com.atakmap.util.ConfigOptions;
import com.atakmap.util.Releasable;

public class GLTileMesh implements Releasable {


    private DoubleBuffer meshCoords;
    private FloatBuffer meshVerts;
    private FloatBuffer meshTexCoords;
    private ByteBuffer meshIndices;
    private int numCoords;
    private int numIndices;
    private final int meshVertsSize;
    private int meshVertsDrawVersion;
    private int vertMode;
    private boolean meshDirty;

    private DatasetProjection2 img2lla;
    private Matrix img2uv;
    private Matrix uv2img;
    private PointD imgUL;
    private PointD imgUR;
    private PointD imgLR;
    private PointD imgLL;
    private int estimatedSubdivisions;

    private GeoPoint centroid;
    private PointD centroidProj;
    private boolean useLCS;
    
    public GLTileMesh(double width,
                      double height,
                      float u,
                      float v,
                      DatasetProjection2 img2lla) {
        
        this(0, 0, width, height, 0f, 0f, u, v, img2lla);
    }
    
    public GLTileMesh(double x,
                      double y,
                      double width,
                      double height,
                      float u0,
                      float v0,
                      float u1,
                      float v1,
                      DatasetProjection2 img2lla) {

        this(new PointD(x, y),
             new PointD(x+width, y),
             new PointD(x+width, y+height),
             new PointD(x, y+height),
             Matrix.mapQuads(x, y,
                             x+width, y,
                             x+width, y+height,
                             x, y+height,
                             u0, v0,
                             u1, v0,
                             u1, v1,
                             u0, v1),
             img2lla,
             estimateSubdivisions(x, y, width, height, img2lla));
    }

    public GLTileMesh(PointD imgUL,
                      PointD imgUR,
                      PointD imgLR,
                      PointD imgLL,
                      Matrix img2uv,
                      DatasetProjection2 img2lla,
                      int estimatedSubdivisions) {

        this.meshVertsSize = 3;
        this.centroid = GeoPoint.createMutable();
        this.centroidProj = new PointD(0d, 0d, 0d);
        this.useLCS = false;
        this.resetMesh(imgUL,
                       imgUR,
                       imgLR,
                       imgLL,
                       img2uv,
                       img2lla,
                       estimatedSubdivisions);
    }

    public void resetMesh(double x,
                          double y,
                          double width,
                          double height,
                          float u0,
                          float v0,
                          float u1,
                          float v1,
                          DatasetProjection2 img2lla) {

        this.resetMesh(new PointD(x, y),
                      new PointD(x+width, y),
                      new PointD(x+width, y+height),
                      new PointD(x, y+height),
                      Matrix.mapQuads(x, y,
                                      x+width, y,
                                      x+width, y+height,
                                      x, y+height,
                                      u0, v0,
                                      u1, v0,
                                      u1, v1,
                                      u0, v1),
                      img2lla,
                      estimateSubdivisions(x, y, width, height, img2lla));
    }
    
    public void resetMesh(PointD imgUL,
                          PointD imgUR,
                          PointD imgLR,
                          PointD imgLL,
                          Matrix img2uv,
                          DatasetProjection2 img2lla,
                          int estimatedSubdivisions) {
        
        this.imgUL = imgUL;
        this.imgUR = imgUR;
        this.imgLR = imgLR;
        this.imgLL = imgLL;
        this.img2uv = img2uv;
        try {
            this.uv2img = this.img2uv.createInverse();
        } catch(NoninvertibleTransformException e) {
            throw new IllegalArgumentException();
        }
        this.img2lla = img2lla;
        this.estimatedSubdivisions = estimatedSubdivisions;

        // if it doesn't compute, that's ok
        this.centroid.set(0d, 0d);
        this.useLCS = this.img2lla.imageToGround(
                new PointD((imgUL.x+imgUR.x+imgLR.x+imgLL.x)/4d,
                           (imgUL.y+imgUR.y+imgLR.y+imgLL.y)/4d,
                        0d),
                this.centroid);
        
        this.meshDirty = true;
    }

    private void validateMesh() {
        if(!this.meshDirty)
            return;
        
        this.numIndices = GLTexture.getNumQuadMeshIndices(this.estimatedSubdivisions,
                                                          this.estimatedSubdivisions);
        this.numCoords = GLTexture.getNumQuadMeshVertices(this.estimatedSubdivisions,
                                                          this.estimatedSubdivisions);

        if (this.meshTexCoords == null
                || this.meshTexCoords.capacity() < (this.numCoords * 2)) {
            
            Unsafe.free(this.meshTexCoords);

            ByteBuffer buf = Unsafe.allocateDirect(this.numCoords * 8);
            buf.order(ByteOrder.nativeOrder());
            
            this.meshTexCoords = buf.asFloatBuffer();
        }

        if (this.numCoords > 4) {
            if (this.meshIndices == null
                    || this.meshIndices.capacity() < (this.numIndices * 2)) {
                
                Unsafe.free(this.meshIndices);

                this.meshIndices = Unsafe.allocateDirect(this.numIndices * 2);
                this.meshIndices.order(ByteOrder.nativeOrder());
            }
        } else {
            Unsafe.free(this.meshIndices);
            this.meshIndices = null;
        }

        if (this.meshVerts == null
                || this.meshVerts.capacity() < (this.numCoords * 3)) {
            
            Unsafe.free(this.meshVerts);
            this.meshVerts = Unsafe.allocateDirect(this.numCoords*3, FloatBuffer.class);
        } else {
            this.meshVerts.clear();
        }

        PointD scratchD = new PointD(0d, 0d, 0d);
        
        img2uv.transform(this.imgUL, scratchD);
        float u0 = (float)scratchD.x;
        float v0 = (float)scratchD.y;
        img2uv.transform(this.imgUR, scratchD);
        float u1 = (float)scratchD.x;
        float v1 = (float)scratchD.y;
        img2uv.transform(this.imgLR, scratchD);
        float u2 = (float)scratchD.x;
        float v2 = (float)scratchD.y;
        img2uv.transform(this.imgLL, scratchD);
        float u3 = (float)scratchD.x;
        float v3 = (float)scratchD.y;
        
        this.meshTexCoords.clear();
        GLTexture.createQuadMeshTexCoords(new PointF(u0, v0),
                                          new PointF(u1, v1),
                                          new PointF(u2, v2),
                                          new PointF(u3, v3),
                                          this.estimatedSubdivisions,
                                          this.estimatedSubdivisions,
                                          this.meshTexCoords);
        this.meshTexCoords.flip();

        if (this.meshIndices != null) {
            GLTexture.createQuadMeshIndexBuffer(
                    this.estimatedSubdivisions,
                    this.estimatedSubdivisions,
                    this.meshIndices.asShortBuffer());
        }
        
        this.vertMode = GLES20FixedPipeline.GL_TRIANGLE_STRIP;
        
        // XXX - generate the LLA coords from the texcoords
        if(this.meshCoords == null || (this.meshCoords.capacity() < (this.numCoords*2))) {
            Unsafe.free(this.meshCoords);
            this.meshCoords = Unsafe.allocateDirect(this.numCoords*2, DoubleBuffer.class);
        }

        scratchD.z = 0d;

        GeoPoint scratchGeo = GeoPoint.createMutable();

        long meshTexCoordsPtr = Unsafe.getBufferPointer(this.meshTexCoords);
        long meshCoordsPtr = Unsafe.getBufferPointer(this.meshCoords);
        for(int i = 0; i < this.numCoords; i++) {
            scratchD.x = Unsafe.getFloat(meshTexCoordsPtr);
            scratchD.y = Unsafe.getFloat(meshTexCoordsPtr+4);
            meshTexCoordsPtr += 8;
            
            // XXX - uv2img transform pointD
            this.uv2img.transform(scratchD, scratchD);
            
            this.img2lla.imageToGround(scratchD, scratchGeo);
            Unsafe.setDoubles(meshCoordsPtr, scratchGeo.getLongitude(), scratchGeo.getLatitude());
            meshCoordsPtr += 16;
        }

        this.meshVertsDrawVersion = -1;
        this.meshDirty = false;
    }

    private void setLCS(GLMapView view, boolean primary) {
        view.scratch.matrix.setToIdentity();
        view.scratch.matrix.concatenate(view.scene.forward);
        if(!primary) {
            if(this.centroid.getLongitude() >= 0d)
                view.scratch.geo.set(this.centroid.getLatitude(), this.centroid.getLongitude()-360d, 0d);
            else
                view.scratch.geo.set(this.centroid.getLatitude(), this.centroid.getLongitude()+360d, 0d);
            view.scene.mapProjection.forward(view.scratch.geo, view.scratch.pointD);

            view.scratch.matrix.translate(view.scratch.pointD.x, view.scratch.pointD.y, view.scratch.pointD.z);
        } else {
            view.scratch.matrix.translate(centroidProj.x, centroidProj.y, centroidProj.z);
        }

        view.scratch.matrix.get(view.scratch.matrixD, Matrix.MatrixOrder.COLUMN_MAJOR);
        for(int i = 0; i < 16; i++)
            view.scratch.matrixF[i] = (float)view.scratch.matrixD[i];
        GLES20FixedPipeline.glLoadMatrixf(view.scratch.matrixF, 0);
    }

    public void drawMesh(GLMapView view, int texId, float r, float g, float b, float a) {
        this.validateMesh();

        if(this.meshVertsDrawVersion != view.drawSrid) {
            view.scratch.geo.set(GeoPoint.UNKNOWN);
            if(this.useLCS) {
                view.scene.mapProjection.forward(this.centroid, this.centroidProj);
            } else {
                this.centroidProj.x = 0d;
                this.centroidProj.y = 0d;
                this.centroidProj.z = 0d;
            }

            long meshCoordsPtr = Unsafe.getBufferPointer(this.meshCoords);
            long meshVertsPtr = Unsafe.getBufferPointer(this.meshVerts);
            for(int i = 0; i < this.numCoords; i++) {
                view.scratch.geo.set(Unsafe.getDouble(meshCoordsPtr+8),
                                     Unsafe.getDouble(meshCoordsPtr),
                                     0d);
                meshCoordsPtr += 16;
                
                view.scene.mapProjection.forward(view.scratch.geo, view.scratch.pointD);
                Unsafe.setFloats(meshVertsPtr,
                                 (float)(view.scratch.pointD.x - this.centroidProj.x),
                                 (float)(view.scratch.pointD.y - this.centroidProj.y),
                                 (float)(view.scratch.pointD.z - this.centroidProj.z));
                meshVertsPtr += 12;
            }

            this.meshVertsDrawVersion = view.drawSrid;
        }

        GLES20FixedPipeline.glPushMatrix( );

        setLCS(view, (view.currentPass.drawLng*centroid.getLongitude()) >= 0 || view.drawSrid != 4326);

        if (meshIndices != null) {
            GLTexture.draw(texId,
                    vertMode,
                    numIndices,
                    2, GLES20FixedPipeline.GL_FLOAT, meshTexCoords,
                    meshVertsSize, GLES20FixedPipeline.GL_FLOAT, meshVerts,
                    GLES20FixedPipeline.GL_UNSIGNED_SHORT, meshIndices,
                    r, g, b, a);
        } else {
            GLTexture.draw(texId,
                    vertMode,
                    numCoords,
                    2, GLES20FixedPipeline.GL_FLOAT, meshTexCoords,
                    meshVertsSize, GLES20FixedPipeline.GL_FLOAT, meshVerts,
                    r, g, b, a);
        }

        GLES20FixedPipeline.glPopMatrix();
    }

    @Override
    public void release() {
        if(this.meshCoords != null) {
            Unsafe.free(this.meshCoords);
            this.meshCoords = null;
        }
        if(this.meshVerts != null) {
            Unsafe.free(this.meshVerts);
            this.meshVerts = null;
        }
        if(this.meshTexCoords != null) {
            Unsafe.free(this.meshTexCoords);
            this.meshTexCoords = null;
        }
        if(this.meshIndices != null) {
            Unsafe.free(this.meshIndices);
            this.meshIndices = null;
        }
        this.numCoords = 0;
        this.numIndices = 0;

        this.meshVertsDrawVersion = -1;
        
        this.meshDirty = true;
    }

    public static int estimateSubdivisions(double ulLat, double ulLng, double lrLat, double lrLng) {
        final int minGridSize = ConfigOptions.getOption("glquadtilenode2.minimum-grid-size", 1);
        final int maxGridSize = ConfigOptions.getOption("glquadtilenode2.maximum-grid-size", 32);

        final int subsX = MathUtils.clamp(MathUtils.nextPowerOf2((int)Math.ceil((ulLat-lrLat) / GLMapView.recommendedGridSampleDistance)), minGridSize, maxGridSize);
        final int subsY = MathUtils.clamp(MathUtils.nextPowerOf2((int)Math.ceil((lrLng-ulLng) / GLMapView.recommendedGridSampleDistance)), minGridSize, maxGridSize);
        
        return Math.max(subsX, subsY);
    }
    
    private static int estimateSubdivisions(double x, double y, double width, double height, DatasetProjection2 img2lla) {
        double minLat;
        double minLng;
        double maxLat;
        double maxLng;
        
        PointD p = new PointD(0d, 0d);
        GeoPoint g = GeoPoint.createMutable();
        
        p.x = x;
        p.y = y;
        img2lla.imageToGround(p, g);
        minLat = g.getLatitude();
        minLng = g.getLongitude();
        maxLat = g.getLatitude();
        maxLng = g.getLongitude();
        
        p.x = x + width;
        p.y = y;
        img2lla.imageToGround(p, g);
        minLat = Math.min(g.getLatitude(), minLat);
        minLng = Math.min(g.getLongitude(), minLng);
        maxLat = Math.max(g.getLatitude(), maxLat);
        maxLng = Math.max(g.getLongitude(), maxLng);
        
        p.x = x + width;
        p.y = y + height;
        img2lla.imageToGround(p, g);
        minLat = Math.min(g.getLatitude(), minLat);
        minLng = Math.min(g.getLongitude(), minLng);
        maxLat = Math.max(g.getLatitude(), maxLat);
        maxLng = Math.max(g.getLongitude(), maxLng);
        
        p.x = x;
        p.y = y + height;
        img2lla.imageToGround(p, g);
        minLat = Math.min(g.getLatitude(), minLat);
        minLng = Math.min(g.getLongitude(), minLng);
        maxLat = Math.max(g.getLatitude(), maxLat);
        maxLng = Math.max(g.getLongitude(), maxLng);
        
        final int minGridSize = ConfigOptions.getOption("glquadtilenode2.minimum-grid-size", 1);
        final int maxGridSize = ConfigOptions.getOption("glquadtilenode2.maximum-grid-size", 32);

        final int subsX = MathUtils.clamp(MathUtils.nextPowerOf2((int)Math.ceil((maxLat-minLat) / GLMapView.recommendedGridSampleDistance)), minGridSize, maxGridSize);
        final int subsY = MathUtils.clamp(MathUtils.nextPowerOf2((int)Math.ceil((maxLng-minLng) / GLMapView.recommendedGridSampleDistance)), minGridSize, maxGridSize);
        
        return Math.max(subsX, subsY);
    }
}
