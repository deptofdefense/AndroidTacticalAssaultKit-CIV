
package com.atakmap.android.grg;

import android.content.SharedPreferences;

import com.atakmap.android.features.FeatureDataStoreDeepMapItemQuery;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.layer.feature.Feature;
import com.atakmap.map.layer.feature.FeatureLayer;
import com.atakmap.map.layer.raster.AbstractDataStoreRasterLayer2;
import com.atakmap.map.layer.raster.DatasetDescriptor;
import com.atakmap.map.layer.raster.LocalRasterDataStore;
import com.atakmap.map.layer.raster.RasterDataStore;
import com.atakmap.map.layer.raster.RasterLayer2;

import java.io.File;
import java.util.Collections;
import java.util.SortedSet;

public class GRGDeepMapItemQuery extends FeatureDataStoreDeepMapItemQuery
        implements SharedPreferences.OnSharedPreferenceChangeListener,
        MapItem.OnVisibleChangedListener {

    private boolean hitTestEnabled;
    private final RasterLayer2 grgLayer;
    private final RasterDataStore grgDataStore;

    public GRGDeepMapItemQuery(FeatureLayer layer,
            AbstractDataStoreRasterLayer2 grgDataStore) {
        super(layer);

        this.grgLayer = grgDataStore;
        this.grgDataStore = grgDataStore.getDataStore();
    }

    @Override
    protected MapItem featureToMapItem(Feature feature) {

        DatasetDescriptor tsInfo;

        RasterDataStore.DatasetDescriptorCursor result = null;
        try {
            RasterDataStore.DatasetQueryParameters params = new RasterDataStore.DatasetQueryParameters();
            params.names = Collections.singleton(feature.getName());
            params.limit = 1;

            result = this.grgDataStore.queryDatasets(params);
            if (!result.moveToNext())
                return null;

            tsInfo = result.get();
        } finally {
            if (result != null)
                result.close();
        }

        ImageOverlay item = new ImageOverlay(tsInfo, getFeatureUID(
                this.spatialDb, feature.getId()),
                true);
        item.setMetaString("layerId", String.valueOf(tsInfo.getLayerId()));
        item.setMetaString("layerUri", tsInfo.getUri());
        item.setMetaString("layerName", tsInfo.getName());
        item.setMetaBoolean("mbbOnly", true);
        item.setTitle(tsInfo.getName());
        item.setMetaBoolean("closed_line", true);

        File layerFile = null;
        if (this.grgDataStore instanceof LocalRasterDataStore)
            layerFile = ((LocalRasterDataStore) this.grgDataStore)
                    .getFile(tsInfo);
        if (layerFile != null)
            item.setMetaString("file", layerFile.getAbsolutePath());

        item.addOnVisibleChangedListener(this);

        return item;
    }

    @Override
    public synchronized MapItem deepHitTest(int xpos, int ypos, GeoPoint point,
            MapView view) {
        if (!this.hitTestEnabled)
            return null;
        return super.deepHitTest(xpos, ypos, point, view);
    }

    @Override
    public synchronized SortedSet<MapItem> deepHitTestItems(int xpos, int ypos,
            GeoPoint point, MapView view) {
        if (!this.hitTestEnabled)
            return null;
        return super.deepHitTestItems(xpos, ypos, point, view);
    }

    /**************************************************************************/

    @Override
    public synchronized void onSharedPreferenceChanged(SharedPreferences prefs,
            String key) {
        if ("prefs_layer_grg_map_interaction".equals(key)) {
            this.hitTestEnabled = prefs.getBoolean(
                    "prefs_layer_grg_map_interaction", true);
        }
    }

    @Override
    public void onVisibleChanged(MapItem item) {
        final String opt = item.getMetaString("layerName", null);
        if (opt == null)
            return;
        this.grgLayer.setVisible(opt, item.getVisible());
    }
}
