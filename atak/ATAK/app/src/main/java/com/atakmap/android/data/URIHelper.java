
package com.atakmap.android.data;

import android.net.Uri;
import android.util.Base64;

import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.missionpackage.file.MissionPackageExtractor;
import com.atakmap.android.missionpackage.file.MissionPackageManifest;
import com.atakmap.android.video.ConnectionEntry;
import com.atakmap.android.video.manager.VideoManager;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;

import java.io.File;

/**
 * URI helper and convenience methods
 */
public class URIHelper {

    private static final String TAG = "URIHelper";

    /**
     * Get the "content" of the URI string (everything after the scheme://)
     *
     * @param scheme Expected URI scheme
     * @param uri URI string
     * @return URI content or null if scheme mismatch/empty content
     */
    public static String getContent(String scheme, String uri) {
        if (uri == null || !uri.startsWith(scheme))
            return null;
        return uri.substring(scheme.length());
    }

    /**
     * Get the content/path of the URI string (everything after the scheme://)
     *
     * @param uri URI string
     * @return URI content or null if empty content/scheme undefined
     */
    public static String getContent(String uri) {
        if (uri == null)
            return null;
        int schIdx = uri.indexOf("://");
        if (schIdx == -1)
            return null;
        return uri.substring(schIdx + 3);
    }

    /**
     * Convert a file URI (file://[path]) to a file
     *
     * @param fileURI File URI string
     * @return File object
     */
    public static File getFile(String fileURI) {
        String path = getContent(URIScheme.FILE, fileURI);
        if (FileSystemUtils.isEmpty(path))
            return null;
        path = Uri.decode(path);
        return new File(path);
    }

    /**
     * Convert a file path to a file URI
     *
     * @param file File object
     * @return File URI string
     */
    public static String getURI(File file) {
        return URIScheme.FILE + file.getAbsolutePath();
    }

    /**
     * Find a map item given a URI (map-item://[UID])
     *
     * @param mapView Map view instance
     * @param mapItemURI Map item URI
     * @return Map item or null if not valid/found
     */
    public static MapItem getMapItem(MapView mapView, String mapItemURI) {
        String uid = getContent(URIScheme.MAP_ITEM, mapItemURI);
        if (FileSystemUtils.isEmpty(uid))
            return null;
        return mapView.getRootGroup().deepFindUID(uid);
    }

    /**
     * Convert a map item to a URI string (map-item://[UID])
     *
     * @param item Map item
     * @return Map item URI string
     */
    public static String getURI(MapItem item) {
        return URIScheme.MAP_ITEM + item.getUID();
    }

    /**
     * Find a video alias given a URI (video://[UID])
     *
     * @param videoURI Video alias URI
     * @return Connection entry or null if not valid/found
     */
    public static ConnectionEntry getVideoAlias(String videoURI) {
        String uid = URIHelper.getContent(URIScheme.VIDEO, videoURI);
        if (FileSystemUtils.isEmpty(uid))
            return null;
        return VideoManager.getInstance().getEntry(uid);
    }

    /**
     * Convert a video alias to a URI string (video://[UID])
     *
     * @param entry Connection entry
     * @return Video alias URI
     */
    public static String getURI(ConnectionEntry entry) {
        return URIScheme.VIDEO + entry.getUID();
    }

    /**
     * Convert a MP manifest URI (mpm://[path]/[XML base64]) to a manifest
     *
     * @param uri Mission Package manifest URI
     * @return Mission Package manifest or null if failed
     */
    public static MissionPackageManifest getManifest(String uri) {
        String content = getContent(URIScheme.MPM, uri);
        if (FileSystemUtils.isEmpty(content))
            return null;

        // Path is required, base 64 XML is not
        String path, base64;
        int slash = content.indexOf("\\");
        if (slash == -1) {
            path = content;
            base64 = "";
        } else {
            path = content.substring(0, slash);
            base64 = content.substring(slash + 1);
        }
        if (FileSystemUtils.isEmpty(path))
            return null;

        // Attempt to parse the base64 if supplied
        // This is faster than extracting and reading it from the ZIP
        if (!FileSystemUtils.isEmpty(base64)) {
            try {
                byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
                String xml = new String(bytes, FileSystemUtils.UTF8_CHARSET);
                if (!FileSystemUtils.isEmpty(xml))
                    return MissionPackageManifest.fromXml(xml, path);
            } catch (Exception e) {
                Log.w(TAG, "Failed to deserialize manifest base64 XML: "
                        + path, e);
            }
        }

        // Read the manifest from the file, if it exists
        File file = new File(path);
        if (!IOProviderFactory.exists(file))
            return null;
        return MissionPackageExtractor.GetManifest(file);
    }

    /**
     * Convert a Mission Package manifest to a URI
     * mpm://[path]/[optional XML base 64]
     *
     * @param manifest The Mission Package manifest
     * @param serializeXML True to serialize the manifest XML to base64
     *                     within the URI. Useful for temporary manifests
     *                     (only exists in memory; not on the filesystem).
     * @return
     */
    public static String getURI(MissionPackageManifest manifest,
            boolean serializeXML) {
        String path = manifest.getPath();
        StringBuilder sb = new StringBuilder();
        sb.append(URIScheme.MPM);
        sb.append(path);
        if (serializeXML) {
            String base64 = null;
            try {
                base64 = Base64.encodeToString(manifest.toXml(true).getBytes(),
                        Base64.DEFAULT);
            } catch (Exception e) {
                Log.w(TAG, "Failed to serialize manifest XML to base 64: "
                        + path);
            }
            if (!FileSystemUtils.isEmpty(base64)) {
                sb.append("\\");
                sb.append(base64);
            }
        }
        return sb.toString();
    }

    /**
     * Convert a Mission Package manifest to a URI
     * mpm://[path]/[XML base 64]
     * The URI will only contain base64 if the manifest does not exist locally
     *
     * @param manifest Mission Package manifest
     * @return URI string
     */
    public static String getURI(MissionPackageManifest manifest) {
        return getURI(manifest, !manifest.pathExists());
    }
}
