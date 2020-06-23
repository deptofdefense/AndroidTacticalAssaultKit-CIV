
package com.atakmap.spatial.file;

import com.atakmap.map.layer.feature.DataSourceFeatureDataStore;

import java.io.File;

/**
 * Support injesting ESRI Shapefiles
 * 
 * 
 */
public abstract class OgrSpatialDb extends SpatialDbContentSource {

    protected final String iconPath;

    protected OgrSpatialDb(DataSourceFeatureDataStore spatialDb,
            String groupName, String iconPath, String type) {
        super(spatialDb, groupName, type);

        this.iconPath = iconPath;
    }

    @Override
    public String getIconPath() {
        return this.iconPath;
    }

    @Override
    protected String getProviderHint(File file) {
        return "ogr";
    }
}
