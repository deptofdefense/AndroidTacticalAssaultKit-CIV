
package com.atakmap.android.layers;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;

import com.atakmap.android.importexport.ImportReceiver;
import com.atakmap.android.importexport.Importer;
import com.atakmap.android.importexport.ImporterManager;
import com.atakmap.coremap.log.Log;
import com.atakmap.comms.CommsMapComponent.ImportResult;
import com.atakmap.map.layer.raster.LocalRasterDataStore;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.spatial.file.KmlFileSpatialDb;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

public class ExternalLayerDataImporter implements Importer {

    public static final String TAG = "ExternalLayerDataImporter";

    private final Set<String> mimeTypes;
    private final Set<String> hints;
    private final String contentType;

    private final LocalRasterDataStore database;

    private final Context context;

    public ExternalLayerDataImporter(Context context,
            LocalRasterDataStore database,
            String contentType, String[] mimeTypes, String[] hints) {
        this.database = database;
        this.context = context;

        this.contentType = contentType;
        this.mimeTypes = new HashSet<>(Arrays.asList(mimeTypes));

        // order is important
        this.hints = new LinkedHashSet<>(Arrays.asList(hints));
    }

    // Importer interface methods

    @Override
    public String getContentType() {
        return contentType;
    }

    @Override
    public Set<String> getSupportedMIMETypes() {
        return Collections.unmodifiableSet(mimeTypes);
    }

    @Override
    public ImportResult importData(InputStream source, String mime, Bundle b)
            throws IOException {
        return ImportResult.FAILURE;
    }

    @Override
    public ImportResult importData(Uri uri, String mime, Bundle b)
            throws IOException {

        if (uri.getScheme() != null && !uri.getScheme().equals("file"))
            return ImportResult.FAILURE;

        boolean showNotifications = false;
        if (b != null
                && b.containsKey(ImportReceiver.EXTRA_SHOW_NOTIFICATIONS)) {
            showNotifications = b
                    .getBoolean(ImportReceiver.EXTRA_SHOW_NOTIFICATIONS);
        }

        File file;
        try {
            file = new File(FileSystemUtils.validityScan(uri.getPath()));
        } catch (IOException ioe) {
            Log.d(TAG, "invalid file", ioe);
            return ImportResult.FAILURE;
        }

        LayersManagerBroadcastReceiver.DatasetIngestCallback callback = null;
        if (showNotifications)
            callback = new LayersManagerBroadcastReceiver.DatasetIngestCallback(
                    file);

        // Remove existing data if it exists
        boolean bDelete = this.database.contains(file);
        if (bDelete)
            deleteData(uri, mime);

        if (showNotifications)
            LayersNotificationManager.notifyImportStarted(file);
        long s = android.os.SystemClock.elapsedRealtime();
        boolean success = false;
        try {
            for (String h : hints) {
                success = this.database.add(file, h, callback);
                if (success)
                    break;
            }
            long e = android.os.SystemClock.elapsedRealtime();
            Log.d(TAG, "import: " + file + " in " + (e - s) + "ms");
        } finally {
            if (showNotifications)
                LayersNotificationManager.notifyImportComplete(file, success);
        }

        // Import vector parts of the GRG if there are any
        if (success && FileSystemUtils.checkExtension(file, "kmz")) {
            Importer kmzImporter = ImporterManager.findImporter(
                    KmlFileSpatialDb.KML_CONTENT_TYPE,
                    KmlFileSpatialDb.KMZ_FILE_MIME_TYPE);
            if (kmzImporter != null)
                kmzImporter.importData(Uri.fromFile(file),
                        KmlFileSpatialDb.KMZ_FILE_MIME_TYPE, null);
        }

        return success ? ImportResult.SUCCESS : ImportResult.FAILURE;
    }

    @Override
    public boolean deleteData(Uri uri, String mime) throws IOException {

        File file;
        try {
            file = new File(FileSystemUtils.validityScan(uri.getPath()));
        } catch (IOException ioe) {
            Log.d(TAG, "invalid file", ioe);
            return false;
        }

        Log.d(TAG, "call to remove: " + file + " from " + database.getClass());
        this.database.remove(file);

        // Remove from KMZ handler as well
        if (FileSystemUtils.checkExtension(file, "kmz")) {
            Importer kmzImporter = ImporterManager.findImporter(
                    KmlFileSpatialDb.KML_CONTENT_TYPE,
                    KmlFileSpatialDb.KMZ_FILE_MIME_TYPE);
            if (kmzImporter != null)
                kmzImporter.deleteData(Uri.fromFile(file),
                        KmlFileSpatialDb.KMZ_FILE_MIME_TYPE);
        }

        return true;
    }
}
