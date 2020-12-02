
package com.atakmap.android.layers;

import com.atakmap.android.data.FileContentHandler;
import com.atakmap.android.maps.CardLayer;
import com.atakmap.android.maps.MapView;
import com.atakmap.map.layer.raster.DatasetDescriptor;
import com.atakmap.map.layer.raster.LocalRasterDataStore;

import java.io.File;

/**
 * Content resolver for external native layers
 */
public class LayerContentResolver extends AbstractLayerContentResolver {

    private final CardLayer _rasterLayers;

    public LayerContentResolver(MapView mapView, LocalRasterDataStore rasterDB,
            CardLayer rasterLayers) {
        super(mapView, rasterDB);
        _rasterLayers = rasterLayers;
    }

    @Override
    protected FileContentHandler createHandler(File f, DatasetDescriptor d) {
        return new LayerContentHandler(_mapView, _rasterLayers, f, d);
    }
}
