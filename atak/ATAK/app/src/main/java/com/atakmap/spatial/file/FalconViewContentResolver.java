
package com.atakmap.spatial.file;

import com.atakmap.android.maps.MapView;
import com.atakmap.map.layer.feature.DataSourceFeatureDataStore;
import com.atakmap.map.layer.feature.geometry.Envelope;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FalconViewContentResolver extends SpatialDbContentResolver {

    private static final Set<String> EXTS = new HashSet<>();
    static {
        EXTS.add(FalconViewSpatialDb.LPT);
        EXTS.add(FalconViewSpatialDb.DRW);
    }

    public FalconViewContentResolver(MapView mv,
            DataSourceFeatureDataStore db) {
        super(mv, db, EXTS);
    }

    @Override
    protected FalconViewContentHandler createHandler(File file,
            List<Long> featureSetIds, Envelope bounds) {
        return new FalconViewContentHandler(_mapView, _database, file,
                featureSetIds, bounds);
    }
}
