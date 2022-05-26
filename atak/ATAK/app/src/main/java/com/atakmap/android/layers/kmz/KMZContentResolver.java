
package com.atakmap.android.layers.kmz;

import com.atakmap.android.data.FileContentHandler;
import com.atakmap.android.data.FileContentResolver;
import com.atakmap.android.data.URIContentHandler;
import com.atakmap.android.data.URIContentListener;
import com.atakmap.android.data.URIContentManager;
import com.atakmap.android.data.URIContentPriority;
import com.atakmap.android.grg.GRGMapComponent;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.model.ModelImporter;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.spatial.file.KmlFileSpatialDb;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Content resolver for KMZ packages
 */
public class KMZContentResolver extends FileContentResolver
        implements URIContentPriority, URIContentListener {

    private static final Set<String> CONTENT_TYPES = new HashSet<>(
            Arrays.asList(KmlFileSpatialDb.KML_CONTENT_TYPE,
                    ModelImporter.CONTENT_TYPE,
                    GRGMapComponent.IMPORTER_CONTENT_TYPE));

    private final MapView _mapView;
    private final Map<String, Set<String>> _tracking = new HashMap<>();

    public KMZContentResolver(MapView mapView) {
        super(Collections.singleton("kmz"));
        _mapView = mapView;
        URIContentManager.getInstance().registerListener(this);
    }

    @Override
    public void dispose() {
        URIContentManager.getInstance().unregisterListener(this);
        super.dispose();
    }

    @Override
    public int getPriority() {
        // High priority so it's used over sub-resolvers
        return HIGH;
    }

    @Override
    public void onContentImported(URIContentHandler handler) {
        if (isSupported(handler)) {
            FileContentHandler fh = (FileContentHandler) handler;
            String uri = fh.getURI();
            synchronized (_tracking) {
                Set<String> types = _tracking.get(uri);
                if (types == null)
                    _tracking.put(uri, types = new HashSet<>());
                types.add(fh.getContentType());
                if (types.size() > 1)
                    addHandler(fh.getFile());
            }
        }
    }

    @Override
    public void onContentDeleted(URIContentHandler handler) {
        if (isSupported(handler)) {
            final FileContentHandler fh = (FileContentHandler) handler;
            _mapView.post(new Runnable() {
                @Override
                public void run() {
                    onContentDeleted(fh);
                }
            });
        }
    }

    private void onContentDeleted(FileContentHandler fh) {
        String uri = fh.getURI();
        File file = fh.getFile();
        FileContentHandler toRemove = null;
        synchronized (_tracking) {
            Set<String> types = _tracking.get(uri);
            if (types == null)
                return;
            boolean fileExists = FileSystemUtils.isFile(file);
            types.remove(fh.getContentType());
            if (types.size() < 2 || !fileExists) {
                if (types.isEmpty() || !fileExists) {
                    _tracking.remove(uri);
                    toRemove = getHandler(file);
                }
                removeHandler(file);
            }
        }
        if (toRemove != null)
            toRemove.deleteContent();
    }

    @Override
    public void onContentChanged(URIContentHandler handler) {
    }

    private boolean isSupported(URIContentHandler handler) {
        // Needs to be a file-based handler that isn't already a KMZ package
        if (!(handler instanceof FileContentHandler)
                || handler instanceof KMZContentHandler)
            return false;

        FileContentHandler fh = (FileContentHandler) handler;

        // Needs to have .kmz extension
        if (!FileSystemUtils.checkExtension(fh.getFile(), "kmz"))
            return false;

        // Content type whitelist
        return CONTENT_TYPES.contains(fh.getContentType());
    }

    private void addHandler(File file) {
        addHandler(new KMZContentHandler(_mapView, file), false);
    }
}
