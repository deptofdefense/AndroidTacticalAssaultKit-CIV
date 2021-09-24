
package com.atakmap.android.maps;

import android.content.res.Resources;

import com.atakmap.coremap.log.Log;

/**
 * Engine resource reference that points to an Android resource id
 * 
 * 
 */
public class ResourceMapDataRef extends MapDataRef {

    /**
     * Create a new ResourceMapDataRef given a resourceId
     * 
     * @param resourceId the resource id
     */
    public ResourceMapDataRef(int resourceId) {
        _resourceId = resourceId;
    }

    public ResourceMapDataRef(String fullyQualified) {
        _fullyQualified = fullyQualified;
    }

    public static ResourceMapDataRef fromFullyQualifiedName(String qualifiedName,
            Resources resources) {
        ResourceMapDataRef resRef = null;
        try {
            /*
             * String[] rootParts = qualifiedName.split("/"); String packageAndType = rootParts[0];
             * String entry = rootParts[1]; String[] packParts = packageAndType.split(":"); String
             * pack = packParts[0]; String type = packParts[1];
             */
            int resourceId = resources.getIdentifier(qualifiedName, null, null);
            resRef = new ResourceMapDataRef(resourceId);
        } catch (Exception ex) {
            Log.e(TAG, "error: ", ex);
        }
        return resRef;
    }

    /**
     * Get the resource id
     * 
     * @return the resource id
     */
    public int getResourceId() {
        return _resourceId;
    }

    public String getFullyQualified() {
        return _fullyQualified;
    }

    public String toString() {
        return "resource: " + _resourceId;
    }

    public static String toUri(int resourceId) {
        return "resource://" + resourceId;
    }

    @Override
    public String toUri() {
        return toUri(_resourceId);
    }

    private int _resourceId;
    private String _fullyQualified;
}
