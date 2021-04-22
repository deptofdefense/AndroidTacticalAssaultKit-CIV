
package com.atakmap.android.drawing;

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;

import com.atakmap.android.importexport.AbstractImporter;
import com.atakmap.android.importexport.KmlMapItemImportFactory;
import com.atakmap.android.importexport.handlers.KmlMapItemImportHandler;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.comms.CommsMapComponent.ImportResult;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Only used for importing KML shapes (which will also probably be refactored at some point)
 */
class DrawingToolsKMLImporter extends AbstractImporter {

    private final static String TAG = "DrawingToolsKMLImporter";

    private final static Set<String> KML_MIME_TYPES = new HashSet<>();
    static {
        KML_MIME_TYPES.add("application/vnd.google-earth.kml+xml");
    }

    private final MapView mapView;
    private final MapGroup drawingGroup;
    private final Map<String, KmlMapItemImportFactory> kmlFactoryMap;
    private final Handler asyncImportHandler;

    DrawingToolsKMLImporter(MapView mapView, MapGroup drawingGroup,
            Handler asyncImportHandler) {
        super("Drawing Objects");
        this.mapView = mapView;
        this.drawingGroup = drawingGroup;
        this.kmlFactoryMap = new HashMap<>();
        this.asyncImportHandler = asyncImportHandler;
    }

    @Override
    public Set<String> getSupportedMIMETypes() {
        return KML_MIME_TYPES;
    }

    synchronized void registerImportFactory(KmlMapItemImportFactory factory) {
        this.kmlFactoryMap.put(factory.getFactoryName(), factory);
    }

    @Override
    public ImportResult importData(Uri uri, String mime,
            Bundle b) throws IOException {

        final String uriPath = uri.getPath();

        if (uriPath == null)
            return ImportResult.FAILURE;

        String path = FileSystemUtils.validityScan(uri.getPath());
        if (!FileSystemUtils.isFile(path))
            return ImportResult.FAILURE;

        try (FileInputStream fis = IOProviderFactory
                .getInputStream(new File(path))) {
            return importKmlImpl(fis);
        } catch (Exception e) {
            Log.e(TAG, "Failed to import KML shape", e);
        }
        return ImportResult.FAILURE;
    }

    @Override
    public ImportResult importData(InputStream source, String mime, Bundle b)
            throws IOException {
        if (KML_MIME_TYPES.contains(mime))
            return this.importKmlImpl(source);
        else
            return ImportResult.FAILURE;
    }

    private ImportResult importKmlImpl(InputStream source) throws IOException {
        try {
            KmlMapItemImportHandler handler = new KmlMapItemImportHandler(
                    this.mapView,
                    this.drawingGroup, null, kmlFactoryMap,
                    this.asyncImportHandler, true);
            handler.importKml(source, false);
            return ImportResult.SUCCESS;
        } catch (IOException e) {
            Log.e(TAG,
                    this.getClass().getName() + ":" + e.getClass().getName()
                            + ":" + e.getMessage());
            Log.e(TAG, "error: ", e);
            return ImportResult.FAILURE;
        }
    }
}
