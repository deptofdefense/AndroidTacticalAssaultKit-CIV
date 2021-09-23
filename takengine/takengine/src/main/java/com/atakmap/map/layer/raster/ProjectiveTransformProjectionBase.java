package com.atakmap.map.layer.raster;

import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;

import com.atakmap.map.projection.EquirectangularMapProjection;
import com.atakmap.map.projection.Projection;
import com.atakmap.map.projection.ProjectionFactory;
import com.atakmap.math.Matrix;
import com.atakmap.math.PointD;

/**
 * This class is inherently NOT thread-safe; the I2G and G2I functions are very,
 * very likely to produce incorrect results if invoked concurrently. Though the
 * documentation for {@link DatasetProjection} explicitly states that instances
 * are not thread-safe, if some guarantee is required, subclasses should
 * override, mark as synchronized and invoke the base method.
 *   
 * @author Developer
 */
abstract class ProjectiveTransformProjectionBase implements DatasetProjection2 {
    private final static String TAG = "ProjectiveTransformProjectionBase";

    private final Projection mapProjection;
    private Matrix img2proj;
    private Matrix proj2img;
    private PointD projected;
    
    ProjectiveTransformProjectionBase(int srid) {
        Projection proj = ProjectionFactory.getProjection(srid);
        if(proj == null) {
            proj = EquirectangularMapProjection.INSTANCE;
            Log.w(TAG, "Failed to find EPSG:" + srid + ", defaulting to EPSG:4326; projection errors may result.");
        }
        this.mapProjection = proj;
        this.projected = new PointD(0d, 0d);
    }
    
    void init(Matrix img2proj, Matrix proj2img) {
        this.img2proj = img2proj;
        this.proj2img = proj2img;
    }
    
    @Override
    public boolean imageToGround(PointD image, GeoPoint ground) {
        this.img2proj.transform(image, this.projected);
        if(ground == null)
            ground = GeoPoint.createMutable();
        return (this.mapProjection.inverse(this.projected, ground) != null);
    }

    @Override
    public boolean groundToImage(GeoPoint ground, PointD image) {
        if(this.mapProjection.forward(ground, this.projected) == null)
            return false;
        this.proj2img.transform(this.projected, image);
        return true;
    }
    
    @Override
    public void release() {}
}
