
package com.atakmap.android.grg;

import android.graphics.Color;

import com.atakmap.android.data.FileContentHandler;
import com.atakmap.android.layers.AbstractLayerContentResolver;
import com.atakmap.android.maps.MapView;
import com.atakmap.map.layer.feature.Feature;
import com.atakmap.map.layer.feature.FeatureCursor;
import com.atakmap.map.layer.feature.FeatureDataStore;
import com.atakmap.map.layer.feature.style.Style;
import com.atakmap.map.layer.feature.style.BasicStrokeStyle;
import com.atakmap.map.layer.raster.DatasetDescriptor;
import com.atakmap.map.layer.raster.DatasetRasterLayer2;
import com.atakmap.map.layer.raster.LocalRasterDataStore;
import com.atakmap.annotations.ModifierApi;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Used to map GRG files to associated metadata (i.e. geo bounds, visibility)
 */
public class GRGContentResolver extends AbstractLayerContentResolver implements
        FeatureDataStore.OnDataStoreContentChangedListener {

    private final DatasetRasterLayer2 _rasterLayer;
    private final FeatureDataStore _outlinesDB;

    @ModifierApi(since = "4.5", target = "4.8", modifiers = {})
    public GRGContentResolver(MapView mv, LocalRasterDataStore rasterDB,
            DatasetRasterLayer2 rasterLayer, FeatureDataStore outlinesDB) {
        super(mv, rasterDB);
        _rasterLayer = rasterLayer;
        _outlinesDB = outlinesDB;
        _outlinesDB.addOnDataStoreContentChangedListener(this);
    }

    @Override
    public synchronized void dispose() {
        _outlinesDB.removeOnDataStoreContentChangedListener(this);
        super.dispose();
    }

    @Override
    protected FileContentHandler createHandler(File f, DatasetDescriptor d) {
        return new GRGContentHandler(_mapView, f, d, _rasterLayer);
    }

    @Override
    protected void refresh() {
        super.refresh();
        updateMetadata();
    }

    @Override
    public void onDataStoreContentChanged(FeatureDataStore ds) {
        updateMetadata();
    }

    private void updateMetadata() {
        // Map file name to handler for quick lookup in the query
        List<FileContentHandler> handlers = getHandlers();
        Map<String, GRGContentHandler> nameToHandler = new HashMap<>();
        for (FileContentHandler h : handlers) {
            String name = h.getFile().getName();
            nameToHandler.put(name, (GRGContentHandler) h);
        }

        // Get the item's map UID and color
        // Requires finding the associated outline feature
        FeatureCursor fc = _outlinesDB.queryFeatures(null);
        try {
            while (fc != null && fc.moveToNext()) {
                Feature fe = fc.get();
                if (fe == null)
                    continue;

                // Name corresponds to GRG file name
                // Not perfect, but it's the best we got
                String name = fe.getName();
                GRGContentHandler h = nameToHandler.get(name);
                if (h == null)
                    continue;

                String uid = "spatialdb::" + _outlinesDB.getUri() + "::"
                        + fe.getId();
                Style s = fe.getStyle();
                int color = Color.WHITE;
                if (s instanceof BasicStrokeStyle)
                    color = ((BasicStrokeStyle) s).getColor();

                h.setUID(uid);
                h.setColor(color);
            }
        } finally {
            if (fc != null)
                fc.close();
        }
    }
}
