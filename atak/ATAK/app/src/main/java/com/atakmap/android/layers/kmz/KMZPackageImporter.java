
package com.atakmap.android.layers.kmz;

import android.content.res.XmlResourceParser;
import android.net.Uri;
import android.os.Bundle;

import com.atakmap.android.filesystem.ResourceFile;
import com.atakmap.android.gdal.layers.KmzLayerInfoSpi;
import com.atakmap.android.grg.GRGMapComponent;
import com.atakmap.android.importexport.Importer;
import com.atakmap.android.importexport.ImporterManager;
import com.atakmap.android.model.ModelImporter;
import com.atakmap.comms.CommsMapComponent.ImportResult;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.xml.XMLUtils;
import com.atakmap.io.ZipVirtualFile;
import com.atakmap.spatial.file.KmlFileSpatialDb;
import com.atakmap.util.zip.IoUtils;

import org.xmlpull.v1.XmlPullParser;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Importer for a KMZ containing multiple data types
 */
public class KMZPackageImporter implements Importer {

    public static final String TAG = "KMZPackageImporter";

    public static final String CONTENT_TYPE = "KMZ";
    private static final String PLACEMARK = "placemark";
    private static final String GROUNDOVERLAY = "groundoverlay";
    private static final String MODEL = "model";

    // Pulled from https://developers.google.com/kml/documentation/kmlreference
    private static final Set<String> PLACEMARK_TYPES = new HashSet<>(
            Arrays.asList(
                    "point", "linestring", "linearring", "polygon",
                    "multigeometry",
                    "gx:track", "gx:multitrack"));

    @Override
    public String getContentType() {
        return CONTENT_TYPE;
    }

    @Override
    public Set<String> getSupportedMIMETypes() {
        return Collections.singleton(KmlFileSpatialDb.KMZ_FILE_MIME_TYPE);
    }

    @Override
    public ImportResult importData(InputStream source, String mime, Bundle b)
            throws IOException {
        return ImportResult.FAILURE;
    }

    @Override
    public ImportResult importData(Uri uri, String mime, Bundle b)
            throws IOException {

        File file = FileSystemUtils.getFile(uri);
        if (file == null)
            return ImportResult.FAILURE;

        List<String> cTypes = getContentTypes(file);
        if (cTypes.size() < 2)
            return ImportResult.FAILURE;

        for (String contentType : cTypes) {
            ImportResult res = importFileAs(file, contentType);
            if (res != ImportResult.SUCCESS)
                return ImportResult.FAILURE;
        }

        return ImportResult.SUCCESS;
    }

    @Override
    public boolean deleteData(Uri uri, String mime) throws IOException {

        File file = FileSystemUtils.getFile(uri);
        if (file == null)
            return false;

        deleteFileAs(file, GRGMapComponent.IMPORTER_CONTENT_TYPE);
        deleteFileAs(file, KmlFileSpatialDb.KML_CONTENT_TYPE);
        deleteFileAs(file, ModelImporter.CONTENT_TYPE);

        return true;
    }

    public static List<String> getContentTypes(File file) {

        List<String> types = new ArrayList<>();

        // File must be a KMZ
        if (!FileSystemUtils.checkExtension(file, "kmz"))
            return types;

        boolean hasGRG = false;
        boolean hasVectors = false;
        boolean hasModel = false;

        InputStream inputStream = null;
        XmlPullParser parser = null;
        try {
            ZipVirtualFile docFile = KmzLayerInfoSpi.findDocumentKML(file);
            if (docFile == null)
                return types;

            inputStream = docFile.openStream();
            parser = XMLUtils.getXmlPullParser();
            parser.setInput(inputStream, null);

            boolean inPlacemark = false;

            int eventType;
            do {
                eventType = parser.next();
                String tag = parser.getName();
                if (tag == null)
                    continue;
                tag = tag.toLowerCase(LocaleUtil.getCurrent());
                switch (eventType) {
                    case XmlPullParser.START_TAG:

                        switch (tag) {
                            // GRGs use GroundOverlay
                            case GROUNDOVERLAY:
                                if (!hasGRG) {
                                    types.add(
                                            GRGMapComponent.IMPORTER_CONTENT_TYPE);
                                    hasGRG = true;
                                }
                                break;

                            // 3D models use Model
                            case MODEL:
                                if (!hasModel) {
                                    types.add(ModelImporter.CONTENT_TYPE);
                                    hasModel = true;
                                }
                                break;

                            // Vector elements are inside Placemark nodes
                            case PLACEMARK:
                                inPlacemark = true;
                                break;
                        }

                        // Vector element found
                        if (inPlacemark && PLACEMARK_TYPES.contains(tag)) {
                            if (!hasVectors) {
                                types.add(KmlFileSpatialDb.KML_CONTENT_TYPE);
                                hasVectors = true;
                            }
                        }

                        break;
                    case XmlPullParser.END_TAG:
                        if (tag.equals(PLACEMARK))
                            inPlacemark = false;
                        break;
                }
            } while (eventType != XmlPullParser.END_DOCUMENT);
        } catch (Throwable e) {
            Log.e(TAG, "Failed to get content types for " + file);
        } finally {
            IoUtils.close(inputStream);
            if (parser instanceof XmlResourceParser)
                ((XmlResourceParser) parser).close();
        }
        return types;
    }

    private static ImportResult importFileAs(File file, String contentType)
            throws IOException {
        String mimeType = contentType.equals(ModelImporter.CONTENT_TYPE)
                ? ResourceFile.UNKNOWN_MIME_TYPE
                : KmlFileSpatialDb.KMZ_FILE_MIME_TYPE;
        Importer importer = ImporterManager.findImporter(contentType, mimeType);
        if (importer != null)
            return importer.importData(Uri.fromFile(file), mimeType, null);
        return ImportResult.FAILURE;
    }

    private static void deleteFileAs(File file, String contentType)
            throws IOException {
        String mimeType = contentType.equals(ModelImporter.CONTENT_TYPE)
                ? ResourceFile.UNKNOWN_MIME_TYPE
                : KmlFileSpatialDb.KMZ_FILE_MIME_TYPE;
        Importer importer = ImporterManager.findImporter(contentType, mimeType);
        if (importer != null)
            importer.deleteData(Uri.fromFile(file), mimeType);
    }
}
