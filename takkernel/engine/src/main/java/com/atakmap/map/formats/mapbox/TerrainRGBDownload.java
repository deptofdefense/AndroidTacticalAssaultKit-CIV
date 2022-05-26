package com.atakmap.map.formats.mapbox;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.io.UriFactory;

import java.io.File;
import java.io.FileOutputStream;

final class TerrainRGBDownload implements Runnable {
    String url;
    File local;

    private static final String TAG = "TerrainRGBDownload";

    @Override
    public void run() {
        try {
            try (UriFactory.OpenResult result = UriFactory.open(url)) {
                if (result == null)
                    return;

                if(!local.getParentFile().exists())
                    local.getParentFile().mkdirs();
                FileSystemUtils.copyStream(result.inputStream, false, new FileOutputStream(local), true);
            }
        } catch (Throwable t) {
            Log.e(TAG, "error", t);
        }
    }
}
