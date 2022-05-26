
package com.atakmap.android.vehicle.model;

import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * Utils for asset and file reading
 */
public class VehicleModelAssetUtils {

    private static final String TAG = "VehicleModelAssetUtils";

    private static final File TOOLS = FileSystemUtils.getItem("tools");

    /**
     * Get the input stream for an APK asset given its "atak/tools" counterpart
     * @param f File under "atak/tools" directory
     * @return Asset input stream
     */
    public static InputStream getAssetStream(File f) {
        MapView mv = MapView.getMapView();
        if (mv == null)
            return null;
        String assetPath = f.getAbsolutePath().substring(
                TOOLS.getAbsolutePath().length() + 1);
        try {
            return mv.getContext().getAssets().open(assetPath);
        } catch (IOException e) {
            Log.e(TAG, "Error loading asset file: " + assetPath, e);
        }
        return null;
    }

    /**
     * Read asset file bytes
     * @param f File under "atak/tools"
     * @return File bytes
     */
    public static byte[] readAssetBytes(File f) {
        InputStream is = getAssetStream(f);
        if (is == null)
            return null;
        try {
            return FileSystemUtils.read(is);
        } catch (Exception e) {
            Log.e(TAG, "Failed to read bytes from asset: " + f.getName(), e);
        } finally {
            try {
                if (is != null)
                    is.close();
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    /**
     * Read file to string
     * @param f File
     * @param asset True if asset
     * @return File content as string
     */
    public static String readFileString(File f, boolean asset) {
        try {
            byte[] bytes = asset ? readAssetBytes(f) : FileSystemUtils.read(f);
            if (bytes == null)
                return null;
            return new String(bytes, FileSystemUtils.UTF8_CHARSET);
        } catch (Exception e) {
            Log.e(TAG, "Failed to read string from file: " + f, e);
        }
        return null;
    }

    public static JSONObject readFileJSON(File f, boolean asset) {
        String string = readFileString(f, asset);
        if (string == null)
            return null;
        try {
            return new JSONObject(string);
        } catch (Exception e) {
            Log.e(TAG, "Failed to read file as JSON: " + f.getName(), e);
        }
        return null;
    }

    public static boolean copyAssetToFile(File f) {
        InputStream is = getAssetStream(f);
        if (is == null)
            return false;
        try {
            FileSystemUtils.copyStream(is,
                    IOProviderFactory.getOutputStream(f));
            return IOProviderFactory.exists(f)
                    && IOProviderFactory.length(f) > 0;
        } catch (Exception e) {
            Log.e(TAG, "Failed to copy asset to file: " + f, e);
        }
        return false;
    }
}
