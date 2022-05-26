package com.atakmap.map.layer.raster;

import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;

import com.atakmap.map.opengl.GLMapSurface;
import com.atakmap.map.projection.EquirectangularMapProjection;
import com.atakmap.map.projection.Projection;
import com.atakmap.map.projection.ProjectionFactory;
import com.atakmap.math.Matrix;
import com.atakmap.math.NoninvertibleTransformException;
import com.atakmap.math.PointD;

public class DefaultDatasetProjection2 implements DatasetProjection2 {

    private final static String TAG = "DefaultDatasetProjection";

    private final Projection mapProjection;
    private final Matrix img2proj;
    private final Matrix proj2img;
    private PointD projected;

    public DefaultDatasetProjection2(ImageInfo info) {
        this(info.srid, info.width, info.height, info.upperLeft, info.upperRight, info.lowerRight, info.lowerLeft);
    }

    public DefaultDatasetProjection2(int srid, int width, int height, GeoPoint ul, GeoPoint ur, GeoPoint lr, GeoPoint ll) {
        this(srid, width&0xFFFFFFFFL, height&0xFFFFFFFFL, ul, ur, lr, ll);
    }

    public DefaultDatasetProjection2(int srid, long width, long height, GeoPoint ul, GeoPoint ur, GeoPoint lr, GeoPoint ll) {
        Projection proj = ProjectionFactory.getProjection(srid);
        if(proj == null) {
            proj = EquirectangularMapProjection.INSTANCE;
            Log.w(TAG, "Failed to find EPSG:" + srid + ", defaulting to EPSG:4326; projection errors may result.");
        }
        this.mapProjection = proj;

        PointD imgUL = new PointD(0, 0);
        PointD projUL = this.mapProjection.forward(ul, null);
        checkThrowUnusable(projUL);
        PointD imgUR = new PointD(width, 0);
        PointD projUR = this.mapProjection.forward(ur, null);
        checkThrowUnusable(projUR);
        PointD imgLR = new PointD(width, height);
        PointD projLR = this.mapProjection.forward(lr, null);
        checkThrowUnusable(projLR);
        PointD imgLL = new PointD(0, height);
        PointD projLL = this.mapProjection.forward(ll, null);
        checkThrowUnusable(projLL);
        
        this.img2proj = Matrix.mapQuads(imgUL, imgUR, imgLR, imgLL,
                                        projUL, projUR, projLR, projLL);
        
        Matrix p2i;
        try {
            p2i = this.img2proj.createInverse();
        } catch(NoninvertibleTransformException e) {
            Log.e(TAG, "Failed to invert img2proj, trying manual matrix construction");
            
            p2i = Matrix.mapQuads(projUL, projUR, projLR, projLL,
                                  imgUL, imgUR, imgLR, imgLL);
        }
        this.proj2img = p2i;
        
        this.projected = new PointD(0d, 0d);
    }
    
    @Override
    public boolean imageToGround(PointD image, GeoPoint ground) {
        PointD p;
        if(GLMapSurface.isGLThread())
            p = this.projected;
        else
            p = new PointD(0d, 0d);
        this.img2proj.transform(image, p);
        return (this.mapProjection.inverse(p, ground) != null);
    }

    @Override
    public boolean groundToImage(GeoPoint ground, PointD image) {
        if(this.mapProjection.forward(ground, image) == null)
            return false;
        this.proj2img.transform(image, image);
        return true;
    }

    @Override
    public void release() {}

    private static void checkThrowUnusable(PointD p) {
        checkThrowUnusable(p.x);
        checkThrowUnusable(p.y);
    }
    private static void checkThrowUnusable(double v) {
        if(Double.isInfinite(v) || Double.isNaN(v))
            throw new IllegalArgumentException();
    }
}
