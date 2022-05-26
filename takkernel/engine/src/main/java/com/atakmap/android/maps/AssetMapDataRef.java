
package com.atakmap.android.maps;

/**
 * Engine resource reference that points to an Android asset from the AssetManager.
 *
 */
public class AssetMapDataRef extends MapDataRef {

    /**
     * Create a new AssetMapDataRef
     * 
     * @param assetPath the path to the asset file
     */
    public AssetMapDataRef(String assetPath) {
        _assetPath = assetPath;
    }

    /**
     * Get the asset path
     * 
     * @return the asset file path
     */
    public String getAssetPath() {
        return _assetPath;
    }

    /**
     * Get a human readable representation of the reference
     */
    public String toString() {
        return "asset: " + _assetPath;
    }

    private String _assetPath;

    @Override
    public String toUri() {
        return toUri(_assetPath);
    }

    public static String toUri(String assetPath) {
        return "asset://" + assetPath;
    }
}
