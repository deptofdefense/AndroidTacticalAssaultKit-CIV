
package com.atakmap.spatial.file;

import com.atakmap.android.maps.MapView;
import com.atakmap.map.layer.feature.DataSourceFeatureDataStore;
import com.atakmap.map.layer.feature.geometry.Envelope;

import java.io.File;
import java.util.Collections;
import java.util.List;

public class GpxContentResolver extends SpatialDbContentResolver {

    public GpxContentResolver(MapView mv, DataSourceFeatureDataStore db) {
        super(mv, db, Collections.singleton("gpx"));
    }

    @Override
    protected GpxContentHandler createHandler(File file,
            List<Long> featureSetIds, Envelope bounds) {
        return new GpxContentHandler(_mapView, _database, file,
                featureSetIds, bounds);
    }
}
