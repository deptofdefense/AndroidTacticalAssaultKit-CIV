
package com.atakmap.android.layers;

import android.content.Context;
import android.content.res.XmlResourceParser;
import android.net.Uri;
import android.os.Bundle;
import android.util.Xml;

import com.atakmap.android.gdal.layers.KmzLayerInfoSpi;
import com.atakmap.android.importexport.ImportReceiver;
import com.atakmap.android.importexport.Importer;
import com.atakmap.android.importexport.ImporterManager;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.log.Log;
import com.atakmap.comms.CommsMapComponent.ImportResult;
import com.atakmap.io.ZipVirtualFile;
import com.atakmap.map.layer.raster.LocalRasterDataStore;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.spatial.file.KmlFileSpatialDb;

import org.xmlpull.v1.XmlPullParser;

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

            // XXX - Hack for ATAK-14355
            // If the GRG is only made up of a ground overlays (no other
            // vector elements) then don't import as a KMZ
            if (!isSimpleGRG(file)) {
                Importer kmzImporter = ImporterManager.findImporter(
                        KmlFileSpatialDb.KML_CONTENT_TYPE,
                        KmlFileSpatialDb.KMZ_FILE_MIME_TYPE);
                if (kmzImporter != null)
                    kmzImporter.importData(Uri.fromFile(file),
                            KmlFileSpatialDb.KMZ_FILE_MIME_TYPE, null);
            }
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

    /**
     * Check if a KMZ file is a "simple" GRG, meaning it has no other elements
     * besides "GroundOverlay"
     * @param kmzFile KMZ file
     * @return True if this is a simple GRG
     */
    private static boolean isSimpleGRG(File kmzFile) {
        if (!FileSystemUtils.checkExtension(kmzFile, "kmz"))
            return false;

        InputStream inputStream = null;
        XmlPullParser parser = null;
        try {
            kmzFile = new ZipVirtualFile(kmzFile);

            ZipVirtualFile docFile = new ZipVirtualFile(kmzFile, "doc.kml");

            // Look for other KML
            if (!IOProviderFactory.exists(docFile)) {
                File[] files = IOProviderFactory.listFiles(kmzFile,
                        KmzLayerInfoSpi.KML_FILTER);
                if (files != null) {
                    for (File f : files) {
                        if (f instanceof ZipVirtualFile) {
                            docFile = (ZipVirtualFile) f;
                            break;
                        }
                    }
                }
            }

            inputStream = docFile.openStream();
            parser = Xml.newPullParser();

            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(inputStream, null);

            boolean hitGroundOverlay = false;
            boolean inGroundOverlay = false;
            int eventType;
            do {
                eventType = parser.next();
                switch (eventType) {
                    case XmlPullParser.START_TAG:
                        String tag = parser.getName().toLowerCase(
                                LocaleUtil.getCurrent());

                        // Top-level metadata elements - ignore
                        if (tag.equals("kml") || tag.equals("document")
                                || tag.equals("name"))
                            break;

                        // Mark that we entered a GroundOverlay tag
                        if (tag.equals("groundoverlay")) {
                            hitGroundOverlay = true;
                            inGroundOverlay = true;
                        }

                        // Element outside of GroundOverlay - not a simple GRG
                        else if (!hitGroundOverlay || !inGroundOverlay)
                            return false;

                        break;
                    case XmlPullParser.END_TAG:
                        // Exiting GroundOverlay tag
                        if (parser.getName()
                                .toLowerCase(LocaleUtil.getCurrent())
                                .equals("groundoverlay"))
                            inGroundOverlay = false;
                        break;
                    case XmlPullParser.TEXT:
                        break;
                    case XmlPullParser.END_DOCUMENT:
                        break;
                    default:
                        break;
                }
            } while (eventType != XmlPullParser.END_DOCUMENT);

            return hitGroundOverlay;
        } catch (Throwable e) {
            return false;
        } finally {
            if (parser != null) {
                if (parser instanceof XmlResourceParser)
                    ((XmlResourceParser) parser).close();
            }
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (Exception e) {
                    // Ignore error at this point.
                }
            }
        }
    }
}
