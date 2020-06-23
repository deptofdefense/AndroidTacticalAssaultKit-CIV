
package com.atakmap.android.grg;

import android.graphics.Color;

import com.atakmap.android.data.FileContentHandler;
import com.atakmap.android.data.FileContentResolver;
import com.atakmap.android.data.URIContentManager;
import com.atakmap.android.data.URIScheme;
import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.map.layer.feature.Feature;
import com.atakmap.map.layer.feature.FeatureCursor;
import com.atakmap.map.layer.feature.FeatureDataStore;
import com.atakmap.map.layer.feature.FeatureDataStore.FeatureQueryParameters;
import com.atakmap.map.layer.feature.style.Style;
import com.atakmap.map.layer.feature.style.BasicStrokeStyle;
import com.atakmap.map.layer.raster.DatasetDescriptor;
import com.atakmap.map.layer.raster.DatasetRasterLayer2;
import com.atakmap.map.layer.raster.LocalRasterDataStore;
import com.atakmap.map.layer.raster.LocalRasterDataStore.FileCursor;
import com.atakmap.map.layer.raster.RasterDataStore;
import com.atakmap.map.layer.raster.RasterDataStore.DatasetQueryParameters;
import com.atakmap.map.layer.raster.RasterDataStore.DatasetDescriptorCursor;
import com.atakmap.android.data.URIHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Used to map GRG files to associated metadata (i.e. geo bounds, visibility)
 * TODO: Add/remove from directly when database insert/remove takes place
 * Updating handlers on "data store changed" isn't efficient
 */
public class GRGContentResolver extends FileContentResolver implements
        RasterDataStore.OnDataStoreContentChangedListener,
        FeatureDataStore.OnDataStoreContentChangedListener {

    private static final String TAG = "GRGContentResolver";

    private final MapView _mapView;
    private final LocalRasterDataStore _rasterDB;
    private final DatasetRasterLayer2 _rasterLayer;
    private final FeatureDataStore _outlinesDB;
    private final Set<String> _pathList = new HashSet<>();

    public GRGContentResolver(MapView mv, LocalRasterDataStore rasterDB,
            DatasetRasterLayer2 rasterLayer, FeatureDataStore outlinesDB) {
        super(null);
        _mapView = mv;
        _rasterDB = rasterDB;
        _rasterLayer = rasterLayer;
        _outlinesDB = outlinesDB;
        _rasterDB.addOnDataStoreContentChangedListener(this);
        _outlinesDB.addOnDataStoreContentChangedListener(this);
    }

    @Override
    public synchronized void dispose() {
        _rasterDB.removeOnDataStoreContentChangedListener(this);
        _outlinesDB.removeOnDataStoreContentChangedListener(this);
        _pathList.clear();
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

        // Check if this file is cataloged in this database
        FileContentHandler handler;
        synchronized (this) {
            if (!_pathList.contains(f.getAbsolutePath()))
                return null;
            handler = _handlers.get(uri);
            if (handler != null)
                return handler;
        }

        // Lookup the GRG based on its file path
        DatasetQueryParameters params1 = new DatasetQueryParameters();
        params1.names = new ArrayList<>();
        params1.names.add(f.getName());
        DatasetDescriptorCursor c1 = _rasterDB.queryDatasets(params1);
        DatasetDescriptor desc = null;
        try {
            while (c1 != null && c1.moveToNext()) {
                DatasetDescriptor d = c1.get();
                if (d == null)
                    continue;
                String uriPath = d.getUri();
                if (uriPath != null && uriPath.contains("://"))
                    uriPath = uriPath.substring(uriPath
                            .indexOf("://") + 3);
                if (!FileSystemUtils.isEmpty(uriPath)
                        && uriPath.startsWith(f.getPath())) {
                    desc = d;
                    break;
                }
            }
        } finally {
            if (c1 != null)
                c1.close();
        }

        // Not found
        if (desc == null)
            return null;

        // Get the item's map UID and color
        // Requires finding the associated outline feature
        String uid = null;
        int color = Color.WHITE;
        FeatureQueryParameters params2 = new FeatureQueryParameters();
        params2.featureNames = new ArrayList<>();
        params2.featureNames.add(f.getName());
        params2.limit = 1;
        FeatureCursor c2 = _outlinesDB.queryFeatures(params2);
        try {
            if (c2 != null && c2.moveToNext()) {
                Feature fe = c2.get();
                if (fe != null) {
                    uid = "spatialdb::" + _outlinesDB.getUri() + "::"
                            + fe.getId();
                    Style s = fe.getStyle();
                    if (s instanceof BasicStrokeStyle)
                        color = ((BasicStrokeStyle) s).getColor();
                }
            }
        } finally {
            if (c2 != null)
                c2.close();
        }

        // Create handler
        handler = new GRGContentHandler(_mapView, f, uid, color,
                desc, _rasterLayer);

        // Cache result
        synchronized (_handlers) {
            _handlers.put(uri, handler);
        }
        return handler;
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
                    _pathList.add(f.getAbsolutePath());
                }
            } finally {
                if (c != null)
                    c.close();
            }
            changed = clearHandlers();
            for (int i = 0; i < changed.size(); i++) {
                FileContentHandler h = changed.get(i);
                File f = h.getFile();
                if (f == null || !_pathList.contains(f.getAbsolutePath())) {
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

    @Override
    public void onDataStoreContentChanged(FeatureDataStore ds) {
        List<FileContentHandler> handlers = clearHandlers();
        for (FileContentHandler h : handlers)
            URIContentManager.getInstance().notifyContentChanged(h);
    }
}
