
package com.atakmap.android.importexport.handlers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import android.content.IntentFilter.MalformedMimeTypeException;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;

import com.atakmap.android.importexport.KmlMapItemImportFactory;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;

/**
 * Moved functionality from KmlMapComponent here.
 */
public class KmlImportExportHandler {

    public static final String TAG = "KmlImportExportHandler";

    /**
     * Intent action broadcast to this class indicating that
     */
    public static final String KML_ACTION_RQST_FILE_LIST = "com.atakmap.android.maps.kml.KML_RQST_FILE_LIST";

    /**
     * Intent action broadcast to this component indicating that data requires importing.
     */
    public static final String IMPORT_DATA_ACTION = "com.atakmap.map.IMPORT_DATA";

    private final MapView mapView;

    private final ArrayList<String> selected = new ArrayList<>();

    // Listens for Intents requesting file list
    private final BroadcastReceiver requestFileReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "Request File");

            Intent i = new Intent();
            i.putExtra("FILE_LIST", getKmlFilesByName());
            i.putExtra("SELECTED_LIST", selected);
            i.setAction("com.atakmap.android.maps.kml.app.KML_FILE_LIST");
            AtakBroadcast.getInstance().sendBroadcast(i);
        }
    };

    // Listens for Intents requesting that data be imported into ATAK from KML.
    private final BroadcastReceiver handleKmlDataReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "Handle KML");

            Uri uri = intent.getData();
            String group = intent.getStringExtra("group");
            String subGroup = intent.getStringExtra("subGroup");
            MapGroup thisGroup;
            MapGroup parent = mapView.getRootGroup().findMapGroup(group);
            if (parent != null && subGroup != null) {
                thisGroup = parent.findMapGroup(subGroup);
            } else {
                thisGroup = parent;
            }

            Log.d(TAG, "Received KML data: " + uri.toString());

            if (thisGroup == null) {
                Log.e(TAG,
                        "IMPORT_DATA intent requires \"group\" extra, \"subgroup\" optional.");
                return;
            }

            byte[] data = null;
            try {
                FileSystemUtils.read(new File(FileSystemUtils.validityScan(uri
                        .getPath())));
            } catch (IOException e) {
                // XXX - legacy from KmlFileIO::loadFile(InputStream)
                Log.e(TAG, "error: ", e);
            }

            new KMLParserThread(uri.getLastPathSegment(), thisGroup, data);
        }
    };

    // Handlers need to be created in the GUI thread
    private final Handler handler;

    // Thread to support KML data parsing
    private class KMLParserThread extends Thread {
        private final MapGroup mapGroup;
        private final byte[] data;
        private final String srcName;

        public KMLParserThread(String srcName, MapGroup mapGroup, byte[] data) {
            this.srcName = srcName;
            this.mapGroup = mapGroup;
            this.data = data;

            if (data == null)
                return;
            start();
        }

        @Override
        public void run() {
            ByteArrayInputStream bais = new ByteArrayInputStream(data, 0,
                    data.length);

            KmlMapItemImportHandler importer = new KmlMapItemImportHandler(
                    mapView, this.mapGroup,
                    this.srcName,
                    Collections.<String, KmlMapItemImportFactory> emptyMap(),
                    handler, true);
            try {
                importer.importKml(bais, false);
            } catch (IOException e) {
                Log.e(TAG, "IO error importing KML items.", e);
            }
        }
    }

    /**
     * Create class to handle KML files.
     * 
     * @param mapView
     * @param handler
     */
    public KmlImportExportHandler(MapView mapView, Handler handler) {
        this.mapView = mapView;
        this.handler = handler;

        Log.d(TAG, "Create KmlImportExportHandler");

        DocumentedIntentFilter filter = new DocumentedIntentFilter();
        filter.addAction(KML_ACTION_RQST_FILE_LIST);
        AtakBroadcast.getInstance().registerReceiver(requestFileReceiver,
                filter);

        filter = new DocumentedIntentFilter();
        filter.addAction(IMPORT_DATA_ACTION);
        try {
            filter.addDataType("application/vnd.google-earth.kml+xml");
        } catch (MalformedMimeTypeException e) {
            Log.e(TAG, "error: ", e);
        }
        AtakBroadcast.getInstance().registerReceiver(handleKmlDataReceiver,
                filter);
    }

    public void shutdown() {
        AtakBroadcast.getInstance().unregisterReceiver(requestFileReceiver);
        AtakBroadcast.getInstance().unregisterReceiver(handleKmlDataReceiver);
    }

    public String[] getKmlFilesByName() {
        File kmlFolder = null;
        boolean mExternalStorageAvailable;

        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            mExternalStorageAvailable = true;
        } else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            mExternalStorageAvailable = true;
        } else {
            mExternalStorageAvailable = false;
        }

        if (mExternalStorageAvailable) {
            kmlFolder = FileSystemUtils.getItem("overlays");
        }

        String[] theList = null;
        if (kmlFolder != null) {
            if (!IOProviderFactory.exists(kmlFolder)) {
                if (!IOProviderFactory.mkdir(kmlFolder)) {
                    Log.d(TAG,
                            " Failed to make dir at "
                                    + kmlFolder.getAbsolutePath());
                }
            }
            if (IOProviderFactory.exists(kmlFolder)
                    && mExternalStorageAvailable) {
                theList = IOProviderFactory.list(kmlFolder,
                        new FilenameFilter() {

                            @Override
                            public boolean accept(File dir, String filename) {
                                if (((filename.endsWith(".kml")) || (filename
                                        .endsWith(".kmz")))
                                        && !filename.startsWith(".")) {// make sure it's not hidden
                                    Log.d(TAG, "Kml Found: " + filename);
                                    return true;
                                }
                                return false;
                            }

                        });
            }
        }
        return theList;
    }
}
