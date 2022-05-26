
package com.atakmap.android.wfs;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import android.net.Uri;
import android.os.Bundle;

import com.atakmap.android.importexport.AbstractImporter;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.comms.CommsMapComponent.ImportResult;
import com.atakmap.filesystem.HashingUtils;
import com.atakmap.map.layer.feature.FeatureDataStore;
import com.atakmap.map.layer.feature.FeatureLayer;
import com.atakmap.map.layer.feature.wfs.WFSFeatureDataStore3;
import com.atakmap.map.layer.feature.wfs.WFSSchemaHandler;
import com.atakmap.map.layer.feature.wfs.WFSSchemaHandlerRegistry;
import com.atakmap.map.layer.feature.wfs.XMLWFSSchemaHandler;

public class WFSImporter extends AbstractImporter {

    public final static String CONTENT = "WFS";
    public final static String MIME_URL = "text/url";
    public final static String MIME_XML = "application/xml";

    private final static String TAG = "WFSImporter";

    private static final Set<String> SUPPORTED_MIME_TYPES = new HashSet<>();
    static {
        SUPPORTED_MIME_TYPES.add(MIME_URL);
        SUPPORTED_MIME_TYPES.add(MIME_XML);
    }

    private final WFSManager wfs;

    private final Map<File, WFSSchemaHandler> configToHandler = new HashMap<>();

    public WFSImporter(WFSManager wfs) {
        super(CONTENT);
        this.wfs = wfs;
    }

    @Override
    public Set<String> getSupportedMIMETypes() {
        return SUPPORTED_MIME_TYPES;
    }

    @Override
    public ImportResult importData(InputStream source, String mime, Bundle b)
            throws IOException {
        return ImportResult.FAILURE;
    }

    @Override
    public synchronized ImportResult importData(Uri uri, String mime, Bundle b)
            throws IOException {
        if (!SUPPORTED_MIME_TYPES.contains(mime))
            return ImportResult.FAILURE;

        File config;
        try {
            config = new File(FileSystemUtils.validityScan(uri.getPath()));
        } catch (IOException ioe) {
            Log.d(TAG, "invalid file", ioe);
            return ImportResult.FAILURE;
        }

        String address;
        switch (mime) {
            case MIME_XML:
                if (uri.getScheme() != null && !uri.getScheme().equals("file"))
                    return ImportResult.FAILURE;

                // parse the config, register the handler and obtain the URI
                try {
                    WFSSchemaHandler handler = new XMLWFSSchemaHandler(config);
                    WFSSchemaHandlerRegistry.register(handler);
                    address = handler.getUri();
                    this.configToHandler.put(config, handler);
                } catch (Throwable t) {
                    Log.e(TAG, "Error parsing config " + config.getName(), t);
                    return ImportResult.FAILURE;
                }
                break;
            case MIME_URL:
                address = uri.toString();
                break;
            default:
                throw new IllegalStateException();
        }

        // create the data store
        FeatureDataStore dataStore;
        final File workingDir;
        try {
            workingDir = getWorkingDir(HashingUtils.md5sum(config));
            if (!IOProviderFactory.exists(workingDir)) {
                boolean s = IOProviderFactory.mkdirs(workingDir);
                if (!s)
                    Log.e(TAG, "could not make wfs working directory: "
                            + workingDir);
            }
            dataStore = new WFSFeatureDataStore3(address, workingDir);
        } catch (Throwable e) {
            Log.e("WFSImporter", "Failed to open WFS datastore for " + address,
                    e);
            return ImportResult.FAILURE;
        }

        // create the layer
        WFSSchemaHandler schema = WFSSchemaHandlerRegistry.get(address);
        String name = (schema != null) ? schema.getName() : address;
        final FeatureLayer layer = new FeatureLayer(name, dataStore);

        this.wfs.add(uri.toString(), mime, layer);

        return ImportResult.SUCCESS;
    }

    @Override
    public synchronized boolean deleteData(Uri uri, String mime)
            throws IOException {

        File toDelete = null;
        WFSSchemaHandler handler = null;

        try {
            switch (mime) {
                case MIME_XML:
                    if (uri.getScheme() != null
                            && !uri.getScheme().equals("file"))
                        return false;

                    toDelete = new File(
                            FileSystemUtils.validityScan(uri.getPath()));
                    handler = this.configToHandler.remove(toDelete);
                    break;
                case MIME_URL:
                    if (wfs.contains(uri.toString()))
                        toDelete = new File(
                                FileSystemUtils.validityScan(uri.getPath()));
                    break;
                default:
                    return false;
            }
        } catch (IOException ioe) {
            Log.d(TAG, "invalid file", ioe);
            return false;
        }
        boolean dataDeleted = false;
        if (toDelete != null) {
            FileSystemUtils.deleteFile(toDelete);
            dataDeleted |= true;
        }
        String uriStr = handler != null ? handler.getUri() : uri.toString();
        if (!FileSystemUtils.isEmpty(uriStr)) {
            FeatureLayer layer = this.wfs.remove(uriStr);
            if (layer != null)
                layer.getDataStore().dispose();
            File workingDir = getWorkingDir(uriStr);
            FileSystemUtils.deleteDirectory(workingDir, false);
            if (handler != null)
                WFSSchemaHandlerRegistry.unregister(handler);
            dataDeleted |= true;
        }
        return dataDeleted;
    }

    /**************************************************************************/

    private static File getWorkingDir(String address) {
        try {
            return new File(FileSystemUtils.getItem("wfs/.datastore"),
                    URLEncoder
                            .encode(address,
                                    FileSystemUtils.UTF8_CHARSET.name())
                            .replace('%', '_'));
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException();
        }
    }
}
