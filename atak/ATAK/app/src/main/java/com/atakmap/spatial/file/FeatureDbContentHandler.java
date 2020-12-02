
package com.atakmap.spatial.file;

import com.atakmap.android.hierarchy.action.Visibility2;
import com.atakmap.android.maps.MapView;
import com.atakmap.map.layer.feature.FeatureDataStore;
import com.atakmap.map.layer.feature.FeatureDataStore.FeatureSetQueryParameters;
import com.atakmap.map.layer.feature.geometry.Envelope;

import java.io.File;

/**
 * Abstract handler for feature databases
 */
public abstract class FeatureDbContentHandler extends FileOverlayContentHandler
        implements Visibility2 {

    protected final FeatureDataStore _dataStore;
    protected int _totalFeatureSets = -1;

    protected FeatureDbContentHandler(MapView mv, File file,
            FeatureDataStore db, Envelope bounds) {
        super(mv, file, bounds);
        _dataStore = db;
    }

    /**
     * Get the total number of feature sets owner by this handler
     * This is called post-initialization to allow sub-classes to override
     * Used for partial visibility calculations
     *
     * @return Number of feature sets
     */
    public int getTotalFeatureSetsCount() {
        if (_totalFeatureSets == -1)
            _totalFeatureSets = _dataStore.queryFeatureSetsCount(
                    buildQueryParams());
        return _totalFeatureSets;
    }

    /**
     * Build query parameters for the feature sets owned by this handler
     * If this handler covers all feature sets then leave as is
     *
     * @return Feature set query parameters
     */
    protected FeatureSetQueryParameters buildQueryParams() {
        return new FeatureSetQueryParameters();
    }

    @Override
    public boolean setVisibleImpl(boolean visible) {
        _dataStore.setFeatureSetsVisible(buildQueryParams(), visible);
        return true;
    }

    @Override
    public int getVisibility() {
        if (!isConditionVisible())
            return INVISIBLE;
        FeatureSetQueryParameters params = buildQueryParams();
        params.visibleOnly = true;
        int numVisible = _dataStore.queryFeatureSetsCount(params);
        if (numVisible == 0)
            return INVISIBLE;
        else if (numVisible < getTotalFeatureSetsCount())
            return SEMI_VISIBLE;
        return VISIBLE;
    }

    @Override
    public boolean isVisible() {
        if (!isConditionVisible())
            return false;
        FeatureSetQueryParameters params = buildQueryParams();
        params.visibleOnly = true;
        params.limit = 1;
        return _dataStore.queryFeatureSetsCount(params) > 0;
    }
}
