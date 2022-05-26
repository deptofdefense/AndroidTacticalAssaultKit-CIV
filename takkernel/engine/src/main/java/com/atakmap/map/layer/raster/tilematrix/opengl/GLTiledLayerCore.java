package com.atakmap.map.layer.raster.tilematrix.opengl;

import com.atakmap.android.maps.graphics.GLBitmapLoader;
import com.atakmap.coremap.maps.coords.GeoPoint;

import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.raster.DatasetProjection2;
import com.atakmap.map.layer.raster.mobileimagery.MobileImageryRasterLayer2;
import com.atakmap.map.layer.raster.tilematrix.TileMatrix;
import com.atakmap.map.projection.Projection;
import com.atakmap.math.MathUtils;
import com.atakmap.math.PointD;
import com.atakmap.opengl.GLTextureCache;

/**
 * The core data structure for a Tiled Map Layer, containing properties about
 * the layer that will be utilized through the renderer infrastructure.
 * 
 * @author Developer
 *
 */
public class GLTiledLayerCore {
    /**
     * The base URL of the layer.
     */
    public final String clientSourceUri;
    /**
     * The {@link Projection} of the layer
     */
    public final Projection proj;
    
    /**
     * If <code>true</code>, renderers should perform additional drawing
     */
    public boolean debugDraw;
    
    /**
     * A cache that may be used by renderers to store texture data that has gone
     * out of view.  May be <code>null</code> if no cache is available.
     */
    public GLTextureCache textureCache;
    /**
     * The asynchronous bitmap loader to be used to service tile download
     * requests.
     */
    public GLBitmapLoader bitmapLoader;
    
    /**
     * The red color component that should be applied when rendering the layer.
     */
    public float r = 1f;
    /**
     * The green color component that should be applied when rendering the layer.
     */
    public float g = 1f;
    /**
     * The blue color component that should be applied when rendering the layer.
     */
    public float b = 1f;
    /**
     * The alpha color component that should be applied when rendering the layer.
     */
    public float a = 1f;
    
    /**
     * The minimum latitude of the full extent of the layer.
     */
    public final double fullExtentMinLat;
    /**
     * The minimum longitude of the full extent of the layer.
     */
    public final double fullExtentMinLng;
    /**
     * The maximum latitude of the full extent of the layer.
     */
    public final double fullExtentMaxLat;
    /**
     * The maximum longitude of the full extent of the layer.
     */
    public final double fullExtentMaxLng;
    
    /**
     * Converts between the projected coordinate space of the layer and
     * latitude/longitude.
     */
    public final DatasetProjection2 proj2geo;
    
    /**
     * Tile access for the layer.
     */
    public final TileMatrix matrix;
    
    /**
     * The bounds of the tile containing region, in the projected coordinate
     * space of the layer.
     */
    public final Envelope fullExtent;
    
    public long refreshInterval;
    private long lastRefresh;

    public int tileDrawVersion;

    /**
     * Creates a new <code>GLTiledLayerCore</code>
     * 
     * @param service       The data structure describing the service
     * @param serviceUrl    The base URL of the map server
     */
    public GLTiledLayerCore(TileMatrix matrix, String uri) {
        this.clientSourceUri = uri;
        this.proj = MobileImageryRasterLayer2.getProjection(matrix.getSRID());
        
        this.matrix = matrix;
        this.fullExtent = this.matrix.getBounds();
        
        PointD scratchD = new PointD(0d, 0d, 0d);
        GeoPoint scratchG = GeoPoint.createMutable();

        scratchD.x = this.fullExtent.minX;
        scratchD.y = this.fullExtent.minY;
        this.proj.inverse(scratchD, scratchG);
        double lat0 = scratchG.getLatitude();
        double lng0 = scratchG.getLongitude();
        scratchD.x = this.fullExtent.minX;
        scratchD.y = this.fullExtent.maxY;
        this.proj.inverse(scratchD, scratchG);
        double lat1 = scratchG.getLatitude();
        double lng1 = scratchG.getLongitude();
        scratchD.x = this.fullExtent.maxX;
        scratchD.y = this.fullExtent.maxY;
        this.proj.inverse(scratchD, scratchG);
        double lat2 = scratchG.getLatitude();
        double lng2 = scratchG.getLongitude();
        scratchD.x = this.fullExtent.maxX;
        scratchD.y = this.fullExtent.minY;
        this.proj.inverse(scratchD, scratchG);
        double lat3 = scratchG.getLatitude();
        double lng3 = scratchG.getLongitude();
        
        this.fullExtentMinLat = MathUtils.min(lat0, lat1, lat2, lat3);
        this.fullExtentMinLng = MathUtils.min(lng0, lng1, lng2, lng3);
        this.fullExtentMaxLat = MathUtils.max(lat0, lat1, lat2, lat3);
        this.fullExtentMaxLng = MathUtils.max(lng0, lng1, lng2, lng3);
        
        this.proj2geo = new ProjectionDatasetProjection2(this.proj);
        
        this.lastRefresh = System.currentTimeMillis();
    }
    
    public void requestRefresh() {
        tileDrawVersion++;
    }

    public void drawPump() {
        if(refreshInterval <= 0L)
            return;
        // if the refresh interval has elapsed since the last refresh, bump the
        // version
        final long currentTime = System.currentTimeMillis(); 
        if((currentTime-this.lastRefresh) > this.refreshInterval) {
            this.tileDrawVersion++;
            this.lastRefresh = System.currentTimeMillis();
        }
    }

    private static class ProjectionDatasetProjection2 implements DatasetProjection2 {

        private final Projection impl;

        public ProjectionDatasetProjection2(Projection impl) {
            this.impl = impl;
        }

        @Override
        public void release() {}

        @Override
        public boolean imageToGround(PointD image, GeoPoint ground) {
            this.impl.inverse(image, ground);
            return true;
        }

        @Override
        public boolean groundToImage(GeoPoint ground, PointD image) {
            this.impl.forward(ground, image);
            return true;
        }
        
    }
}
