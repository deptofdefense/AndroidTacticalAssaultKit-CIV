
package com.atakmap.android.maps;

import java.io.UnsupportedEncodingException;
import java.net.JarURLConnection;
import java.net.URLDecoder;

import android.net.Uri;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

/**
 * Abstract engine resource reference
 * 
 * @see FileMapDataRef
 * @see ArchiveEntryMapDataRef
 * @see ResourceMapDataRef
 * @see AssetMapDataRef
 * 
 */
public abstract class MapDataRef {

    public static final String TAG = "MapDataRef";



    /**
     * Supported Schemes * file://[path] * arc:[archive_path]!/[entry_path] * asset://[asset_path] *
     * res://res_id
     * 
     * @param uri the screme to parse into a MapDataRef
     * @return the MapDataRef that represents the URI
     */
    public static MapDataRef parseUri(final String uri) {

        String uriDecoded;
        try {
            uriDecoded = URLDecoder.decode(uri, FileSystemUtils.UTF8_CHARSET.name());
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, "unable to process uri: " + uri, e);
            return null;
        }
        Uri u = Uri.parse(uriDecoded);

        String scheme = u.getScheme();
        if (scheme == null) {
            scheme = "file";
        }

        switch (scheme) {
            case "file":
                return new FileMapDataRef(u.getPath());
            case "arc":
                try {
                    String arcUri = u.getPath();
                    if (arcUri != null) {
                        String[] parts = arcUri.split("!/");
                        return new ArchiveEntryMapDataRef(parts[0], parts[1]);
                    }
                } catch (Exception ex) {
                    Log.e(TAG, "error: ", ex);
                }
                break;
            case "jar":
                try  {
                    //jar URLs are best parsed by JarURLConnection
                    java.net.URL url = java.net.URI.create(uriDecoded).toURL();

                    JarURLConnection connection = (JarURLConnection)url.openConnection();

                    return new ArchiveEntryMapDataRef(connection.getJarFileURL().getPath(), connection.getEntryName());
                } catch (Exception ex) {
                    Log.e(TAG, "error: ", ex);
                }
                break;
            case "asset": {
                String path = u.getHost() + u.getPath();
                if (path.startsWith("/")) {
                    path = path.substring(1);
                }
                return new AssetMapDataRef(path);
            }
            case "android.resource": {
                String pack = u.getHost();
                String path = u.getPath();
                return new ResourceMapDataRef(pack + path);
            }
            case "base64": {
                // Make it into a base64MapDataRef
                String path = u.getHost() + u.getPath();
                if (path.startsWith("/")) {
                    path = path.substring(1);
                }
                return new Base64MapDataRef(path);
            }
        }
        return null;
    }

    public abstract String toUri();


}
