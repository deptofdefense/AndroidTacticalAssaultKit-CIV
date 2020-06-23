
package com.atakmap.spatial.file;

import com.atakmap.android.maps.MapView;
import com.atakmap.io.ZipVirtualFile;
import com.atakmap.map.layer.feature.DataSourceFeatureDataStore;
import com.atakmap.map.layer.feature.geometry.Envelope;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ShapefileContentResolver extends SpatialDbContentResolver {

    private static final Set<String> EXTS = new HashSet<>();
    static {
        EXTS.add("shp");
        EXTS.add("shpz");
        EXTS.add("zip");
    }

    public ShapefileContentResolver(MapView mv, DataSourceFeatureDataStore db) {
        super(mv, db, EXTS);
    }

    @Override
    protected ShapefileContentHandler createHandler(File file,
            List<Long> featureSetIds, Envelope bounds) {
        if (file instanceof ZipVirtualFile) {
            ZipVirtualFile zvf = (ZipVirtualFile) file;
            file = zvf.getZipFile();
        }
        return new ShapefileContentHandler(_mapView, _database, file,
                featureSetIds, bounds);
    }
}
