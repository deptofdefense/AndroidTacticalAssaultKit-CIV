
package com.atakmap.android.layers;

import com.atakmap.android.data.FileContentHandler;
import com.atakmap.android.data.FileContentResolver;
import com.atakmap.android.data.URIContentManager;
import com.atakmap.android.data.URIHelper;
import com.atakmap.android.data.URIScheme;
import com.atakmap.android.maps.CardLayer;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Content resolver for external native layers
 */
public class LayerContentResolver extends FileContentResolver
        implements RasterDataStore.OnDataStoreContentChangedListener {

    private final MapView _mapView;
    private final LocalRasterDataStore _rasterDB;
    private final Set<String> _pathList = new HashSet<>();
    private final CardLayer _rasterLayers;

    public LayerContentResolver(MapView mapView, LocalRasterDataStore rasterDB,
            CardLayer rasterLayers) {
        super(null);
        _mapView = mapView;
        _rasterDB = rasterDB;
        _rasterDB.addOnDataStoreContentChangedListener(this);
        _rasterLayers = rasterLayers;
    }

    @Override
    public synchronized void dispose() {
        _rasterDB.removeOnDataStoreContentChangedListener(this);
        super.dispose();
    }

    @Override
    public FileContentHandler getHandler(String tool, String uri) {
        // Valid file checks
        if (uri == null || !uri.startsWith(URIScheme.FILE))
            return null;
        File f = URIHelper.getFile(uri);
        if (f == null)
            return null;

        FileContentHandler h = super.getHandler(tool, uri);
        if (h != null)
            return h;

        synchronized (this) {
            if (!_pathList.contains(uri))
                return null;
        }

        // Lookup the GRG based on its file path
        DatasetDescriptorCursor c = _rasterDB.queryDatasets();
        DatasetDescriptor desc = null;
        try {
            while (c != null && c.moveToNext()) {
                DatasetDescriptor d = c.get();
                if (d == null)
                    continue;
                String uriPath = GdalLayerInfo.getGdalFriendlyUri(d);
                if (uriPath != null && uriPath.contains("://"))
                    uriPath = uriPath.substring(uriPath.indexOf("://") + 3);
                if (!FileSystemUtils.isEmpty(uriPath)
                        && uriPath.startsWith(f.getPath())) {
                    desc = d;
                    break;
                }
            }
        } finally {
            if (c != null)
                c.close();
        }

        // Not found
        if (desc == null)
            return null;

        // Create handler
        h = new LayerContentHandler(_mapView, _rasterLayers, f, desc);

        addHandler(h);
        return h;
    }

    /**
     * Refresh GRG paths in the database
     * Should only be called within the constructor or a synchronized method
     */
    private void refreshPathList() {
        List<FileContentHandler> changed;
        List<FileContentHandler> removed = new ArrayList<>();
        synchronized (this) {
            _pathList.clear();
            FileCursor c = null;
            try {
                c = _rasterDB.queryFiles();
                while (c != null && c.moveToNext()) {
                    File f = c.getFile();
                    if (f == null)
                        continue;
                    _pathList.add(URIHelper.getURI(f));
                }
            } finally {
                if (c != null)
                    c.close();
            }
            changed = clearHandlers();
            for (int i = 0; i < changed.size(); i++) {
                FileContentHandler h = changed.get(i);
                File f = h.getFile();
                if (f == null || !_pathList.contains(URIHelper.getURI(f))) {
                    removed.add(h);
                    changed.remove(i--);
                }
            }
        }
        for (FileContentHandler h : removed)
            URIContentManager.getInstance().notifyContentDeleted(h);
        for (FileContentHandler h : changed)
            URIContentManager.getInstance().notifyContentChanged(h);
    }

    private synchronized List<FileContentHandler> clearHandlers() {
        List<FileContentHandler> handlers = new ArrayList<>(_handlers.values());
        _handlers.clear();
        return handlers;
    }

    @Override
    public void onDataStoreContentChanged(RasterDataStore ds) {
        refreshPathList();
    }
}
