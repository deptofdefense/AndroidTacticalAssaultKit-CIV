
package com.atakmap.android.layers;

import android.graphics.drawable.Drawable;

import com.atakmap.android.maps.CardLayer;
import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.map.layer.Layer;
import com.atakmap.map.layer.raster.DatasetDescriptor;
import com.atakmap.map.layer.raster.RasterLayer2;
import com.atakmap.spatial.file.FileOverlayContentHandler;

import java.io.File;

/**
 * Content handler for external native layer
 */
public class LayerContentHandler extends FileOverlayContentHandler {

    private final CardLayer _rasterLayers;
    private final DatasetDescriptor _desc;

    LayerContentHandler(MapView mapView, CardLayer layer, File file,
            DatasetDescriptor desc) {
        super(mapView, file, desc.getMinimumBoundingBox());
        _rasterLayers = layer;
        _desc = desc;
    }

    @Override
    public Drawable getIcon() {
        return _context.getDrawable(R.drawable.ic_menu_maps);
    }

    @Override
    public String getContentType() {
        return LayersMapComponent.IMPORTER_CONTENT_TYPE;
    }

    @Override
    public String getMIMEType() {
        return LayersMapComponent.IMPORTER_DEFAULT_MIME_TYPE;
    }

    @Override
    public boolean goTo(boolean select) {
        if (FileSystemUtils.isEquals(_desc.getProvider(), "mobac")) {
            _rasterLayers.show("Mobile");
            for (Layer l : _rasterLayers.getLayers()) {
                if (l instanceof RasterLayer2 && l.getName().equals("Mobile"))
                    ((RasterLayer2) l).setSelection(_desc.getName());
            }
        } else {
            _rasterLayers.show("Native");
            super.goTo(select);
        }
        return true;
    }
}
