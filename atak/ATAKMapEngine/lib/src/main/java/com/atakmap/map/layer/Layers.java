package com.atakmap.map.layer;

import com.atakmap.map.layer.feature.opengl.GLBatchGeometryFeatureDataStoreRenderer;
import com.atakmap.map.layer.feature.style.opengl.GLBasicFillStyle;
import com.atakmap.map.layer.feature.style.opengl.GLBasicStrokeStyle;
import com.atakmap.map.layer.feature.style.opengl.GLCompositeStyle;
import com.atakmap.map.layer.feature.style.opengl.GLIconPointStyle;
import com.atakmap.map.layer.feature.style.opengl.GLLabelPointStyle;
import com.atakmap.map.layer.feature.style.opengl.GLLabeledIconStyle;
import com.atakmap.map.layer.feature.style.opengl.GLStyleFactory;
import com.atakmap.map.layer.opengl.GLLayerFactory;
import com.atakmap.map.layer.opengl.GLMultiLayer;
import com.atakmap.map.layer.opengl.GLProxyLayer;
import com.atakmap.map.layer.raster.gdal.GdalTileReader;
import com.atakmap.map.layer.raster.gpkg.GeoPackageTileContainer;
import com.atakmap.map.layer.raster.mbtiles.MBTilesSingleTileReader;
import com.atakmap.map.layer.raster.mbtiles.MBTilesTileReader;
import com.atakmap.map.layer.raster.mbtiles.MOMAPTilesTileReader;
import com.atakmap.map.layer.raster.mobac.MobacTileClient2;
import com.atakmap.map.layer.raster.mobac.MobacTileReader;
import com.atakmap.map.layer.raster.mobileimagery.GLMobileImageryRasterLayer2;
import com.atakmap.map.layer.raster.nativeimagery.GLNativeImageryRasterLayer2;
import com.atakmap.map.layer.raster.opengl.GLDatasetRasterLayer2;
import com.atakmap.map.layer.raster.opengl.GLMapLayerFactory;
import com.atakmap.map.layer.raster.osm.OSMDroidTileContainer;
import com.atakmap.map.layer.raster.osm.OSMDroidTileReader;
import com.atakmap.map.layer.raster.sqlite.SQLiteSingleTileReader;
import com.atakmap.map.layer.raster.tilematrix.TileClientFactory;
import com.atakmap.map.layer.raster.tilematrix.TileContainerFactory;
import com.atakmap.map.layer.raster.tilereader.TileReaderFactory;
import com.atakmap.map.layer.raster.tilereader.opengl.GLTiledMapLayer2;

import java.util.Collection;

/**
 * Utility class for {@link Layer} objects.
 *  
 * @author Developer
 */
public final class Layers {

    private static boolean registered;

    private static FlavorSpecificRegistration flavorSpecificRegistration;
    
    private Layers() {}

    
    public static void setFlavorSpecificRegistration(final FlavorSpecificRegistration specificRegistration) { 
         flavorSpecificRegistration = specificRegistration;   
    } 

   
    /**
     * Registers all layer related service providers that are part of the SDK.
     */
    public synchronized static void registerAll() {
        if(registered)
            return;
        
        // utility layer renderers 
        GLLayerFactory.register(GLMultiLayer.SPI2);
        GLLayerFactory.register(GLProxyLayer.SPI2);
        
        // specialized RasterLayer renderers
        GLLayerFactory.register(GLDatasetRasterLayer2.SPI);
        GLLayerFactory.register(GLNativeImageryRasterLayer2.SPI);
        GLLayerFactory.register(GLMobileImageryRasterLayer2.SPI);
        
        // batch based FeatureLayer renderers
        GLLayerFactory.register(GLBatchGeometryFeatureDataStoreRenderer.SPI);

        // generic FeatureLayer renderer
        GLStyleFactory.register(GLBasicStrokeStyle.SPI);
        GLStyleFactory.register(GLBasicFillStyle.SPI);
        GLStyleFactory.register(GLIconPointStyle.SPI);
        GLStyleFactory.register(GLLabelPointStyle.SPI);
        GLStyleFactory.register(GLLabeledIconStyle.SPI);
        GLStyleFactory.register(GLCompositeStyle.SPI);

        TileReaderFactory.registerSpi(GdalTileReader.SPI);
        TileReaderFactory.registerSpi(MobacTileReader.SPI);
        TileReaderFactory.registerSpi(OSMDroidTileReader.SPI);
        TileReaderFactory.registerSpi(SQLiteSingleTileReader.SPI);
        TileReaderFactory.registerSpi(MBTilesTileReader.SPI);
        TileReaderFactory.registerSpi(MBTilesSingleTileReader.SPI);
        TileReaderFactory.registerSpi(MOMAPTilesTileReader.SPI);

        GLMapLayerFactory.registerSpi(GLTiledMapLayer2.SPI);

        if (flavorSpecificRegistration != null) 
             flavorSpecificRegistration.register();
        
        TileClientFactory.registerSpi(MobacTileClient2.SPI);

        TileContainerFactory.registerSpi(OSMDroidTileContainer.SPI);
        TileContainerFactory.registerSpi(GeoPackageTileContainer.SPI);

        registered = true;
    }

    public static void findLayers(Collection<Layer> srcLayers, LayerFilter filter, Collection<Layer> dstLayers, int limit) {
        for(Layer l : srcLayers) {
            do {
                if (filter.accept(l)) {
                    dstLayers.add(l);
                } else if (l instanceof MultiLayer) {
                    findLayers(((MultiLayer) l).getLayers(), filter, dstLayers, limit);
                } else if (l instanceof ProxyLayer) {
                    l = ((ProxyLayer)l).get();
                    continue;
                }

                if (limit > 0 && dstLayers.size() >= limit)
                    break;

                break;
            } while(true);
        }
    }
}
