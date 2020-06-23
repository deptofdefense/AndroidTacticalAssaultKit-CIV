
package com.atakmap.spatial.file;

import com.atakmap.android.maps.MapView;
import com.atakmap.map.layer.feature.DataSourceFeatureDataStore;
import com.atakmap.map.layer.feature.FeatureDataStore.FeatureSetQueryParameters;
import com.atakmap.map.layer.feature.geometry.Envelope;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Abstract handler for spatial feature databases with a specific
 * set of feature set IDs (KML, GPX, SHP)
 */
public abstract class SpatialDbContentHandler extends FeatureDbContentHandler {

    protected final List<Long> _featureSetIds;

    protected SpatialDbContentHandler(MapView mv, DataSourceFeatureDataStore db,
            File file, List<Long> featureSetIds, Envelope bounds) {
        super(mv, file, db, bounds);
        _featureSetIds = featureSetIds;
    }

    @Override
    public int getTotalFeatureSetsCount() {
        return _featureSetIds.size();
    }

    @Override
    protected FeatureSetQueryParameters buildQueryParams() {
        FeatureSetQueryParameters params = super.buildQueryParams();
        params.ids = _featureSetIds;
        return params;
    }

    /**
     * Get a list of all feature set IDs attached to this file
     *
     * @return List of feature set IDs
     */
    public List<Long> getFeatureSetIds() {
        return new ArrayList<>(_featureSetIds);
    }
}
