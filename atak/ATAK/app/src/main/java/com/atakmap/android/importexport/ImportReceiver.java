
package com.atakmap.android.importexport;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Pair;

import com.atakmap.android.hierarchy.HierarchyListReceiver;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class ImportReceiver extends BroadcastReceiver {

    public static final String TAG = "ImportReceiver";

    public final static String EXTRA_CONTENT = "contentType";
    public final static String EXTRA_MIME_TYPE = "mimeType";
    public final static String EXTRA_URI = "uri";
    public final static String EXTRA_URI_LIST = "uriList";
    public final static String EXTRA_SHOW_NOTIFICATIONS = "showNotifications";
    public final static String EXTRA_ZOOM_TO_FILE = "zoomToFile";
    public final static String EXTRA_HIDE_FILE = "hideFile";
    public final static String EXTRA_ADVANCED_OPTIONS = "advanced";

    ImportReceiver() {
    }

    /******************* PRIVATE PARSING THREAD ******************/
    private enum ACTION {
        IMPORT,
        DELETE
    }

    private static class ImportParserThread implements Runnable {

        private final ACTION action;
        private final List<Uri> uris;
        private String application;
        private String mime;
        private final Bundle bundle;

        public ImportParserThread(ACTION action, List<Uri> uris,
                String application, String mime, Bundle bundle) {
            this.action = action;
            this.uris = uris;
            this.application = application;
            this.mime = mime;
            this.bundle = bundle;
        }

        @Override
        public void run() {
            for (Uri uri : this.uris) {
                try {
                    if (this.application == null || this.mime == null) {
                        if (this.application != null && this.mime == null) {
                            this.mime = MarshalManager
                                    .marshal(application, uri);
                        } else if (this.application == null) {
                            Pair<String, String> info = MarshalManager
                                    .marshal(uri);
                            if (info == null) {
                                Log.e(TAG,
                                        "Unable to determine content for Uri "
                                                + uri);
                                return;
                            }

                            this.application = info.first;
                            this.mime = info.second;
                        }
                    }

                    Importer importer = ImporterManager.findImporter(
                            this.application, this.mime);
                    if (importer != null && this.mime != null) {
                        if (action == ACTION.DELETE) {
                            importer.deleteData(uri, mime);
                            Log.d(TAG, importer.getClass()
                                    + " delete from database: " + uri
                                    + " scheme: " + uri.getScheme());
                            //TODO NPE hard crash with ":" in filename
                            File f = new File(FileSystemUtils
                                    .validityScan(uri.getPath()));
                            if (uri.getScheme() != null
                                    && uri.getScheme().equals("file") ||
                                    IOProviderFactory.exists(f)) {
                                Log.d(TAG, "delete from file system: " + f);
                                FileSystemUtils.deleteFile(f);
                            } else {
                                Log.d(TAG, "cannot delete non-file: " + uri);
                            }
                        } else {
                            importer.importData(uri, mime, bundle);
                            Log.d(TAG, importer.getClass() + " import: " + uri);
                            Intent importComplete = new Intent(
                                    ImportExportMapComponent.IMPORT_COMPLETE);
                            importComplete.putExtra(EXTRA_URI, uri.toString());
                            AtakBroadcast.getInstance()
                                    .sendBroadcast(importComplete);
                        }
                    } else {
                        Log.e(TAG, "failed to import " + uri.toString()
                                + ", no Importer found.");
                    }
                } catch (FileNotFoundException e) {
                    Log.e(TAG, "failed to open Uri for import " + uri, e);
                } catch (Exception e) {
                    Log.e(TAG, "failed to open Uri for import " + uri, e);
                }

            }

            // Refresh Overlay Manager
            AtakBroadcast.getInstance().sendBroadcast(new Intent(
                    HierarchyListReceiver.REFRESH_HIERARCHY));
        }
    }

    /**************************************************************************/
    // Broadcast Receiver

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "import intent received: " + intent.getAction()
                + " uri=" + intent.getStringExtra(EXTRA_URI)
                + " content=" + intent.getStringExtra(EXTRA_CONTENT)
                + " mime=" + intent.getStringExtra(EXTRA_MIME_TYPE));

        List<Uri> uris = null;
        String uriStr = intent.getStringExtra(EXTRA_URI);
        if (!FileSystemUtils.isEmpty(uriStr)) {
            Uri uri = null;
            try {
                uri = Uri.parse(uriStr);
            } catch (Exception e) {
                Log.e(TAG, "Failed to parse URI string: " + uriStr, e);
            }
            if (uri != null)
                uris = Collections.singletonList(uri);
        } else if (intent.hasExtra(EXTRA_URI_LIST)) {
            ArrayList<String> uriList = intent
                    .getStringArrayListExtra(EXTRA_URI_LIST);

            if (uriList != null) {
                uris = new LinkedList<>();
                Uri uri;
                for (String u : uriList) {
                    uri = Uri.parse(u);
                    if (uri != null)
                        uris.add(uri);
                }
            }
        } else {
            Log.e(TAG, "Import requires URI or URI list.");
            return;
        }
        if (uris == null || uris.size() < 1) {
            Log.e(TAG, "Unable to import from null Uri");
            return;
        }

        final String content = intent.getStringExtra(EXTRA_CONTENT);
        final String mimeType = intent.getStringExtra(EXTRA_MIME_TYPE);

        ACTION action = ACTION.IMPORT;
        if (ImportExportMapComponent.ACTION_IMPORT_DATA.equals(intent
                .getAction())) {
            action = ACTION.IMPORT;
        } else if (ImportExportMapComponent.ACTION_DELETE_DATA.equals(intent
                .getAction())) {
            action = ACTION.DELETE;
        } else {
            Log.w(TAG, "Ignoring action: " + intent.getAction());
            return;
        }

        Thread t = new Thread(new ImportParserThread(action, uris, content,
                mimeType, intent.getExtras()));
        t.setPriority(Thread.NORM_PRIORITY);
        t.start();
    }

    /**
     * Remove list of URIs from import manager
     * Same as ACTION_DELETE_DATA except it's not an intent
     */
    public static void remove(List<Uri> uris, String content, String mimeType) {
        Thread t = new Thread(new ImportParserThread(ACTION.DELETE, uris,
                content, mimeType, null));
        t.setPriority(Thread.NORM_PRIORITY);
        t.start();
    }

    public static void remove(Uri uri, String content, String mimeType) {
        List<Uri> uris = new ArrayList<>();
        uris.add(uri);
        remove(uris, content, mimeType);
    }
}
