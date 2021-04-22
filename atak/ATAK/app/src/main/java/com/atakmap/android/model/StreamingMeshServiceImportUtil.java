
package com.atakmap.android.model;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import com.atakmap.android.importexport.ImportExportMapComponent;
import com.atakmap.android.importexport.ImportReceiver;
import com.atakmap.android.importexport.Importer;
import com.atakmap.android.importexport.Marshal;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.comms.CommsMapComponent;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Set;

final class StreamingMeshServiceImportUtil {
    final static String CONTENT_TYPE = "Streaming Mesh Configuration";

    final static Marshal marshal = new Marshal() {
        @Override
        public String getContentType() {
            return CONTENT_TYPE;
        }

        @Override
        public String marshal(InputStream inputStream, int i)
                throws IOException {
            return null;
        }

        @Override
        public String marshal(Uri uri) throws IOException {
            File f = new File(uri.getPath());
            if (!IOProviderFactory.exists(f))
                return null;
            try {
                byte[] read = new byte[4096];
                try (FileInputStream fis = IOProviderFactory
                        .getInputStream(f)) {
                    int count = fis.read(read);
                    if (count > 0) {
                        String s = new String(read, 0, count,
                                FileSystemUtils.UTF8_CHARSET);
                        if (!s.contains("ModelInfo"))
                            return null;
                    } else {
                        return null;
                    }
                } catch (IOException ignored) {
                    return null;
                }

                JSONObject obj = new JSONObject(FileSystemUtils
                        .copyStreamToString(f));
                JSONObject info = obj.optJSONObject("ModelInfo");
                if (info == null)
                    return null;
                final String info_uri = info.optString("uri", null);
                if (info_uri == null)
                    return null;
            } catch (JSONException e) {
                return null;
            }
            return "application/json";
        }

        @Override
        public int getPriorityLevel() {
            return 2;
        }
    };

    final static Importer importer = new Importer() {
        @Override
        public String getContentType() {
            return CONTENT_TYPE;
        }

        @Override
        public Set<String> getSupportedMIMETypes() {
            return Collections.singleton("application/json");
        }

        @Override
        public CommsMapComponent.ImportResult importData(
                InputStream inputStream, String s, Bundle bundle)
                throws IOException {
            return CommsMapComponent.ImportResult.FAILURE;
        }

        @Override
        public CommsMapComponent.ImportResult importData(Uri uri, String s,
                Bundle bundle) throws IOException {
            try {
                JSONObject obj = new JSONObject(FileSystemUtils
                        .copyStreamToString(new File(uri.getPath())));
                JSONObject info = obj.optJSONObject("ModelInfo");
                if (info == null)
                    return CommsMapComponent.ImportResult.FAILURE;
                final String info_uri = info.optString("uri", null);
                if (info_uri == null)
                    return CommsMapComponent.ImportResult.FAILURE;

                Intent i = new Intent(
                        ImportExportMapComponent.ACTION_IMPORT_DATA);
                // pass through any options
                if (bundle != null)
                    i.putExtras(bundle);
                // overwrite the specifics
                i.putExtra(ImportReceiver.EXTRA_URI, info_uri);
                i.putExtra(ImportReceiver.EXTRA_CONTENT,
                        ModelImporter.CONTENT_TYPE);
                // clear the MIME type -- 3D marshal/importer will configure its own
                i.removeExtra(ImportReceiver.EXTRA_MIME_TYPE);

                AtakBroadcast.getInstance().sendBroadcast(i);
                return CommsMapComponent.ImportResult.SUCCESS;
            } catch (JSONException e) {
                return CommsMapComponent.ImportResult.FAILURE;
            }
        }

        @Override
        public boolean deleteData(Uri uri, String s) throws IOException {
            return false;
        }
    };
}
