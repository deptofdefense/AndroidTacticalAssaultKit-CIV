
package com.atakmap.map.layer.raster.mobac;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.layer.raster.AbstractDatasetDescriptorSpi;
import com.atakmap.map.layer.raster.DatasetDescriptor;
import com.atakmap.map.layer.raster.DatasetDescriptorSpi;
import com.atakmap.map.layer.raster.mobileimagery.MobileImageryRasterLayer2;
import com.atakmap.android.maps.tilesets.TilesetInfo;
import com.atakmap.map.projection.EquirectangularMapProjection;
import com.atakmap.map.projection.Projection;
import com.atakmap.spi.InteractiveServiceProvider;
import com.atakmap.util.ConfigOptions;

public class MobacMapSourceLayerInfoSpi extends AbstractDatasetDescriptorSpi {

    
    static {
        MobileImageryRasterLayer2.registerDatasetType("mobac");
    }

    public static final String TAG = "MobacMapSourceLayerInfoSpi";

    public final static DatasetDescriptorSpi INSTANCE = new MobacMapSourceLayerInfoSpi();

    private MobacMapSourceLayerInfoSpi() {
        super("mobac", 0);
    }

    @Override
    protected Set<DatasetDescriptor> create(File f, File workingDir, InteractiveServiceProvider.Callback callback) {
        // Don't do anything with the callback here, the mobac files contain so little data
        // that it isn't worth reporting progress.

        MobacMapSource mapSource = null;
        try {
            mapSource = MobacMapSourceFactory.create(f);
        } catch (IOException e) {
            Log.e(TAG, "IO error creating MOBAC Map Source layer", e);
        }
        if (mapSource == null)
            return null;

        int srid = mapSource.getSRID();
        Projection layerProjection = MobileImageryRasterLayer2.getProjection(srid);
        if(layerProjection == null) {
            Log.w(TAG, "SRID " + srid + " not supported");
            return null;
        }

        int gridZeroWidth = 1;
        int gridZeroHeight = 1;
        if (srid == EquirectangularMapProjection.INSTANCE.getSpatialReferenceID()) {
            gridZeroWidth = 2;
        }


        TilesetInfo.Builder builder = new TilesetInfo.Builder("mobac", "mobac");
        builder.setPathStructure("MOBAC_CUSTOM_MAP_SOURCE");
        builder.setIsOnline(true);
        builder.setSupportSpi(MobacMapSourceTilesetSupport.Spi.INSTANCE.getName());
        builder.setSpatialReferenceID(srid);

        // tiles are normalized into a uniform grid for purposes of aggregation;
        // the internal bounds will always be the full extents of the
        // projection
        
        double north, west, south, east;
        
        north = layerProjection.getMaxLatitude();
        west = layerProjection.getMinLongitude();
        south = layerProjection.getMinLatitude();
        east = layerProjection.getMaxLongitude();

        builder.setFourCorners(new GeoPoint(south, west),
                new GeoPoint(north, west),
                new GeoPoint(north, east),
                new GeoPoint(south, east));
        builder.setZeroHeight((layerProjection.getMaxLatitude() - layerProjection.getMinLatitude())
                / gridZeroHeight);
        builder.setZeroWidth((layerProjection.getMaxLongitude() - layerProjection.getMinLongitude())
                / gridZeroWidth);
        builder.setGridOriginLat(layerProjection.getMinLatitude());
        builder.setGridOffsetX(0);
        builder.setGridOffsetY(0);

        File cacheFile = new File(workingDir, mapSource.getName() + ".sqlite");
        final String cacheDirPath = ConfigOptions.getOption("imagery.offline-cache-dir", null);
        if(cacheDirPath != null) {
            File cacheDir = new File(cacheDirPath);
            if(IOProviderFactory.exists(cacheDir))
                cacheFile = new File(cacheDir, mapSource.getName() + ".sqlite");
        }
        builder.setExtra("offlineCache", cacheFile.getAbsolutePath());

        builder.setExtra("defaultExpiration", String.valueOf(System.currentTimeMillis()));

        // XXX - think all projections can be supported now...verify
        
        // check to see if the projection is supported for aggregation
        switch(srid) {
            case 3857 :
            case 900913 :
            case 4326 :
            case 90094326 :
                builder.setExtra("mobileimagery.aggregate", String.valueOf(1));
                break;
            default :
                // XXX - until the consolidated tile renderer code has
                //       been introduced, offer the legacy
                //       "adjacent tileset" capability for pyramids from
                //       the same provider.  The main drawback for the
                //       legacy will be a lack of tile interleaving from
                //       different datasets; specifically, tiles from
                //       the dataset with the highest resolution tile(s)
                //       will be rendered on top of the other datasets
                break;
        }
        
        builder.setExtra("mobileimagery.type", "mobac");
        
        builder.setUri(f.getAbsolutePath());
        builder.setName(mapSource.getName());
        builder.setImageryType(mapSource.getName());
        builder.setLevelOffset(mapSource.getMinZoom());
        builder.setLevelCount(mapSource.getMaxZoom() - mapSource.getMinZoom() + 1);
        builder.setGridWidth((1 << mapSource.getMinZoom()) * gridZeroWidth);
        builder.setGridHeight((1 << mapSource.getMinZoom()) * gridZeroHeight);
        builder.setImageExt("." + mapSource.getTileType());
        // XXX - forcing everything to 256x256 in the client for 2.0
        builder.setTilePixelWidth(256);
        builder.setTilePixelHeight(256);
        // builder.setTilePixelWidth(mapSource.getTileSize());
        // builder.setTilePixelHeight(mapSource.getTileSize());

        return Collections.singleton(builder.build());
    }

    @Override
    protected boolean probe(File file, InteractiveServiceProvider.Callback callback) {
        // Normally, it wouldn't be a good idea to try and call create like
        // this to test if the file can be loaded, but since the creation of
        // mobac layers is very inexpensive, this should be OK.

        // We aren't going to actually use the result here, so it's OK
        // to pass a fake ResourceSpi to the test method
        File tmp = null;
        try {
            tmp = FileSystemUtils.createTempDir("testmobac", "tmp", null);
            Set<DatasetDescriptor> layers = create(file, tmp, null);

            if(layers != null && layers.size() > 0){
                return true;
            }else{
                return false;
            }
        } catch(IOException e) {
            return false;
        } finally {
            if(tmp != null)
                FileSystemUtils.delete(tmp);
        }
    }

    @Override
    public int parseVersion() {
        return 6;
    }
}
