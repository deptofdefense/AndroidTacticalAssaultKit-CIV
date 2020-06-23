
package com.atakmap.android.importexport;

import android.os.Bundle;
import android.util.Pair;

import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.comms.CommsMapComponent.ImportResult;
import com.atakmap.coremap.log.Log;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class ImporterManager {

    private final static Map<String, Set<Importer>> importers = new HashMap<>();

    public static String TAG = "ImporterManager";

    private ImporterManager() {
    }

    public static synchronized void registerImporter(Importer importer) {
        Set<Importer> s = importers.get(importer.getContentType());
        if (s == null)
            importers.put(importer.getContentType(),
                    s = new LinkedHashSet<>());
        s.add(importer);
    }

    public static synchronized void unregisterImporter(Importer importer) {
        Set<Importer> s = importers.get(importer.getContentType());
        if (s != null)
            s.remove(importer);
    }

    public static synchronized Importer findImporter(String contentType,
            String mime) {
        return findImporterNoSync(contentType, mime);
    }

    private static Importer findImporterNoSync(final String contentType,
            final String mime) {
        Set<Importer> s = importers.get(contentType);
        if (s == null)
            return null;

        Iterator<Importer> iter = s.iterator();
        Importer importer;
        while (iter.hasNext()) {
            importer = iter.next();
            if (importer.getContentType().equals(contentType)
                    && importer.getSupportedMIMETypes().contains(mime))
                return importer;
        }
        return null;
    }

    public static synchronized Set<Importer> findImporters(
            String supportedMimeType) {
        Set<Importer> retval = new LinkedHashSet<>();

        for (Set<Importer> s : importers.values()) {
            Iterator<Importer> iter = s.iterator();
            Importer importer;
            while (iter.hasNext()) {
                importer = iter.next();
                if (importer.getSupportedMIMETypes()
                        .contains(supportedMimeType))
                    retval.add(importer);
            }
        }
        return retval;
    }

    /** 
     * Given an inputStream and a bundle, attempt to determine the content type and mime type 
     * for the stream, then call importData.   
     */
    public static ImportResult importData(final InputStream in,
            final Bundle bundle) throws IOException {

        // XXX: Marshaling could potentially reads past the mark and causes an exception. 
        final int probeSize = 8 * 8 * 1024;
        InputStream source = new BufferedInputStream(in, probeSize);
        source.mark(probeSize);
        Pair<String, String> result = MarshalManager.marshal(source,
                8 * 8 * 1024);
        source.reset();

        if (result == null)
            return ImportResult.FAILURE;
        try {
            return importData(source, result.first, result.second, bundle);
        } catch (Exception e) {
            Log.d(TAG, "error occurred during ingest", e);
            return ImportResult.FAILURE;
        }
    }

    /**
     * Given a CotEvent and a bundle, attempt to import the data.
     **/
    public static ImportResult importData(final CotEvent event,
            final Bundle bundle) throws IOException {
        Pair<String, String> result = MarshalManager.marshal(event);
        if (result == null)
            return ImportResult.FAILURE;

        final Importer importer = findImporter(result.first, result.second);
        if (!(importer instanceof AbstractCotEventImporter))
            return ImportResult.FAILURE;
        try {
            return ((AbstractCotEventImporter) importer).importData(event,
                    bundle);
        } catch (Exception e) {
            Log.d(TAG, "error occurred during ingest", e);
            return ImportResult.FAILURE;
        }
    }

    /** 
     * Given an inputStream, content type, mime type, and  bundle, attempt to find a 
     * valid importer for the inputStream and if found, import the data into the system.
     */
    public static ImportResult importData(final InputStream source,
            final String contentType,
            final String mime,
            final Bundle bundle) throws IOException {
        final Importer importer = findImporter(contentType, mime);
        if (importer == null)
            return ImportResult.FAILURE;
        try {
            return importer.importData(source, mime, bundle);
        } catch (Exception e) {
            Log.d(TAG, "error occurred during ingest", e);
            return ImportResult.FAILURE;
        }
    }

}
