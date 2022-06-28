
package com.atakmap.android.maps.tilesets;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.atakmap.android.layers.ScanLayersService;
import com.atakmap.android.maps.AbstractMapComponent;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.tilesets.graphics.GLTilePatch2;
import com.atakmap.android.maps.tilesets.mobac.MobacLayerScanner;
import com.atakmap.map.layer.raster.DatasetDescriptorFactory2;
import com.atakmap.map.layer.raster.gpkg.GeoPackageLayerInfoSpi;
import com.atakmap.map.layer.raster.gpkg.opengl.GLGeoPackageTilesLayer2;
import com.atakmap.map.layer.raster.mobac.MobacMapSource;
import com.atakmap.map.layer.raster.mobac.MobacMapSourceLayerInfoSpi;
import com.atakmap.map.layer.raster.mobac.MobacMapSourceTilesetSupport;
import com.atakmap.map.layer.raster.opengl.GLMapLayerFactory;

public class TilesetMapComponent extends AbstractMapComponent {

    private static boolean initialized = false;

    private final SharedPreferences.OnSharedPreferenceChangeListener prefListener = new SharedPreferences.OnSharedPreferenceChangeListener() {

        @Override
        public void onSharedPreferenceChanged(SharedPreferences prefs,
                String key) {

            if (key == null)
                return;

            if (key.equals("wms_connect_timeout"))
                MobacMapSource.Config.defaults.connectTimeout = prefs.getInt(
                        key,
                        MobacMapSource.Config.defaults.connectTimeout);
        }
    };

    @Override
    public void onCreate(Context context, Intent intent, MapView view) {
        synchronized (TilesetMapComponent.class) {
            if (initialized)
                return;

            TilesetSupport.registerSpi(SimpleUriTilesetSupport.Spi.INSTANCE);
            TilesetSupport
                    .registerSpi(MobacMapSourceTilesetSupport.Spi.INSTANCE);

            GLMapLayerFactory.registerSpi(GLTilePatch2.SPI);
            GLMapLayerFactory.registerSpi(GLGeoPackageTilesLayer2.SPI);

            DatasetDescriptorFactory2
                    .register(TilesetLayerInfoSpi.INSTANCE);
            DatasetDescriptorFactory2
                    .register(MobacMapSourceLayerInfoSpi.INSTANCE);
            DatasetDescriptorFactory2
                    .register(GeoPackageLayerInfoSpi.INSTANCE);

            /** 
             * For legacy support of the old directory structure specifically on the 
             * external sd card.
             */
            ScanLayersService.getInstance()
                    .registerScannerSpi(MobacLayerScanner.SPI);
            ScanLayersService.getInstance()
                    .registerScannerSpi(TilesetLayerScanner.SPI);

            SharedPreferences prefs = PreferenceManager
                    .getDefaultSharedPreferences(context);
            prefs.registerOnSharedPreferenceChangeListener(this.prefListener);
            this.prefListener.onSharedPreferenceChanged(prefs,
                    "wms_connect_timeout");

            initialized = true;
        }

    }

    @Override
    protected void onDestroyImpl(Context context, MapView view) {
        PreferenceManager.getDefaultSharedPreferences(
                context).unregisterOnSharedPreferenceChangeListener(
                        this.prefListener);
    }

}
