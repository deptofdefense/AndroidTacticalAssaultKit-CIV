package com.atakmap.map.layer.feature.datastore;

import com.atakmap.map.layer.feature.AbstractFeatureDataStore2;
import com.atakmap.map.layer.feature.AttributeSet;
import com.atakmap.map.layer.feature.Feature;
import com.atakmap.map.layer.feature.FeatureDefinition;
import com.atakmap.map.layer.feature.FeatureSet;
import com.atakmap.map.layer.feature.style.Style;
import com.atakmap.map.layer.feature.geometry.Geometry;

public abstract class AbstractReadOnlyFeatureDataStore extends AbstractFeatureDataStore2 {

    private final static int INVALID_MODIFICATION_FLAGS = 
            MODIFY_FEATURE_ATTRIBUTES |
            MODIFY_FEATURE_GEOMETRY |
            MODIFY_FEATURE_NAME |
            MODIFY_FEATURE_STYLE |
            MODIFY_FEATURESET_DELETE |
            MODIFY_FEATURESET_DISPLAY_THRESHOLDS |
            MODIFY_FEATURESET_FEATURE_DELETE |
            MODIFY_FEATURESET_FEATURE_INSERT |
            MODIFY_FEATURESET_FEATURE_UPDATE |
            MODIFY_FEATURESET_INSERT |
            MODIFY_FEATURESET_NAME |
            MODIFY_FEATURESET_UPDATE;
            
    protected AbstractReadOnlyFeatureDataStore(int modificationFlags, int visibilityFlags) {
        super(modificationFlags, visibilityFlags);
        
        if((modificationFlags&INVALID_MODIFICATION_FLAGS) != 0)
            throw new IllegalArgumentException();
    }

    @Override
    protected boolean insertFeatureSetImpl(String provider, String type, String name,
            double minResolution, double maxResolution, FeatureSet[] returnRef) {

        throw new UnsupportedOperationException();
    }

    @Override
    protected boolean updateFeatureSetImpl(long fsid, String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected boolean updateFeatureSetImpl(long fsid, double minResolution, double maxResolution) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected boolean updateFeatureSetImpl(long fsid, String name, double minResolution,
            double maxResolution) {
        
        throw new UnsupportedOperationException();
    }

    @Override
    protected boolean deleteFeatureSetImpl(long fsid) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected boolean deleteAllFeatureSetsImpl() {
        throw new UnsupportedOperationException();
    }

    @Override
    protected boolean insertFeatureImpl(long fsid, String name, Geometry geom, Style style,
            AttributeSet attributes, Feature[] returnRef) {
        
        throw new UnsupportedOperationException();
    }

    @Override
    protected boolean insertFeatureImpl(long fsid, FeatureDefinition def, Feature[] returnRef) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected boolean updateFeatureImpl(long fid, String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected boolean updateFeatureImpl(long fid, Geometry geom) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected boolean updateFeatureImpl(long fid, Style style) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected boolean updateFeatureImpl(long fid, AttributeSet attributes) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected boolean updateFeatureImpl(long fid, String name, Geometry geom, Style style,
            AttributeSet attributes) {

        throw new UnsupportedOperationException();
    }

    @Override
    protected boolean deleteFeatureImpl(long fsid) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected boolean deleteAllFeaturesImpl(long fsid) {
        throw new UnsupportedOperationException();
    }
}
