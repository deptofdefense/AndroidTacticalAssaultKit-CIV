
package com.atakmap.android.layers;

import com.atakmap.android.data.FileContentHandler;
import com.atakmap.android.data.FileContentResolver;
import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.map.layer.raster.DatasetDescriptor;
import com.atakmap.map.layer.raster.LocalRasterDataStore;
import com.atakmap.map.layer.raster.LocalRasterDataStore.FileCursor;
import com.atakmap.map.layer.raster.RasterDataStore;
import com.atakmap.map.layer.raster.RasterDataStore.DatasetDescriptorCursor;
import com.atakmap.map.layer.raster.gdal.GdalLayerInfo;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Content resolver for external native layers
 */
public abstract class AbstractLayerContentResolver extends FileContentResolver
        implements RasterDataStore.OnDataStoreContentChangedListener {

    protected final MapView _mapView;
    protected final LocalRasterDataStore _rasterDB;

    public AbstractLayerContentResolver(MapView mapView,
            LocalRasterDataStore rasterDB) {
        super(null);
        _mapView = mapView;
        _rasterDB = rasterDB;
        _rasterDB.addOnDataStoreContentChangedListener(this);
    }

    @Override
    public synchronized void dispose() {
        _rasterDB.removeOnDataStoreContentChangedListener(this);
        super.dispose();
    }

    /**
     * Refresh file handlers by scanning the database
     */
    protected void refresh() {
        // Get all files
        List<String> paths = new ArrayList<>();
        FileCursor fc = null;
        try {
            fc = _rasterDB.queryFiles();
            while (fc != null && fc.moveToNext()) {
                File f = fc.getFile();
                if (f == null)
                    continue;
                paths.add(f.getAbsolutePath());
            }
        } finally {
            if (fc != null)
                fc.close();
        }

        // Check for removed handlers
        Map<String, FileContentHandler> removed;
        synchronized (this) {
            removed = new HashMap<>(_handlers);
            for (String path : paths)
                removed.remove(path);
        }
        for (FileContentHandler h : removed.values())
            removeHandler(h.getFile());

        // Scan for existing/new handlers
        DatasetDescriptorCursor dc = _rasterDB.queryDatasets();
        try {
            while (dc != null && dc.moveToNext()) {
                DatasetDescriptor desc = dc.get();
                if (desc == null)
                    continue;

                // Get file URI
                String descPath = GdalLayerInfo.getGdalFriendlyUri(desc);
                if (FileSystemUtils.isEmpty(descPath))
                    continue;

                if (descPath.startsWith("/vsizip/"))
                    descPath = descPath.substring(7);

                // Find matching file
                for (String path : paths) {
                    if (descPath.startsWith(path)) {
                        File f = new File(path);
                        FileContentHandler h = createHandler(f, desc);
                        addHandler(h, false);
                        break;
                    }
                }
            }
        } finally {
            if (dc != null)
                dc.close();
        }
    }

    protected abstract FileContentHandler createHandler(File file,
            DatasetDescriptor desc);

    @Override
    public void onDataStoreContentChanged(RasterDataStore ds) {
        refresh();
    }
}
