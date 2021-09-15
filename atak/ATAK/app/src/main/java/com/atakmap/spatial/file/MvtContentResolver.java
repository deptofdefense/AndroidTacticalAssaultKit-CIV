
package com.atakmap.spatial.file;

import com.atakmap.android.maps.MapView;
import com.atakmap.map.layer.feature.DataSourceFeatureDataStore;
import com.atakmap.map.layer.feature.geometry.Envelope;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MvtContentResolver extends SpatialDbContentResolver {

    private static final Set<String> EXTS = new HashSet<>();
    static {
        EXTS.add("mbtiles");
    }

    public MvtContentResolver(MapView mv, DataSourceFeatureDataStore db) {
        super(mv, db, EXTS);
    }

    @Override
    protected MvtContentHandler createHandler(File file,
            List<Long> featureSetIds, Envelope bounds) {
        return new MvtContentHandler(_mapView, _database, file,
                featureSetIds, bounds);
    }
}
