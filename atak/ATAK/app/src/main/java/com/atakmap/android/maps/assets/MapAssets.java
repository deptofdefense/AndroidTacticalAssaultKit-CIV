
package com.atakmap.android.maps.assets;

import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.net.Uri;

import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;

import java.io.File;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class MapAssets {

    public static final String TAG = "DefaultMapAssets";

    private final Context _context;

    public MapAssets(final Context context) {
        _context = context;
    }

    /**
     * Obtains an input stream based on a assets Uri.
     * @param assetUri the asset Uri
     * @return the input stream
     * @throws IOException if there is a problem with the uri
     */
    public InputStream getInputStream(Uri assetUri) throws IOException {

        InputStream in = null;
        String scheme = assetUri.getScheme();

        if (scheme == null) {
            // assume it's an asset
            in = _context.getAssets().open(assetUri.getPath());
        } else if (scheme.equals("file")) {
            return IOProviderFactory.getInputStream(
                    new File(FileSystemUtils.validityScan(assetUri
                            .getPath())));
        } else if (scheme.equals("arc")) {
            String path = assetUri.getPath();
            String[] parts = path.split("!/");
            return new ArchiveMapAssetInputStream(parts[0], parts[1]);
        } else if (scheme.equals("android.resource")) {
            List<String> path = assetUri.getPathSegments();

            if (path.size() == 0) {
                // android.resource://id_number BUT: this isn't actually a valid URI according to
                // Android docs; do we really need it?
                // in = _context.getResources().openRawResource(Integer.parseInt(path.get(0)));
            } else if (path.size() == 1) {
                // android.resource://package_name/id_number
                try {
                    Resources res = _context.getPackageManager()
                            .getResourcesForApplication(
                                    assetUri.getHost());
                    in = res.openRawResource(Integer.parseInt(path.get(0)));
                } catch (NameNotFoundException e) {
                    Log.e(TAG, "error: ", e);
                }
            } else if (path.size() == 2) {
                // android.resource://package_name/type/resource_name
                try {
                    Resources res = _context.getPackageManager()
                            .getResourcesForApplication(
                                    assetUri.getHost());

                    int id = res.getIdentifier(path.get(1), path.get(0),
                            assetUri.getHost());

                    in = res.openRawResource(id);
                } catch (NameNotFoundException e) {
                    Log.e(TAG, "error: ", e);
                }
            }
        }

        return in;
    }

}
