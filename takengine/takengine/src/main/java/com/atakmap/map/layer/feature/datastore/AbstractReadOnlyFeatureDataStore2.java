package com.atakmap.map.layer.feature.datastore;

import android.util.Pair;

import com.atakmap.map.layer.feature.AbstractFeatureDataStore3;
import com.atakmap.map.layer.feature.AttributeSet;
import com.atakmap.map.layer.feature.DataStoreException;
import com.atakmap.map.layer.feature.Feature;
import com.atakmap.map.layer.feature.FeatureSet;
import com.atakmap.map.layer.feature.style.Style;
import com.atakmap.map.layer.feature.geometry.Geometry;

public abstract class AbstractReadOnlyFeatureDataStore2 extends AbstractFeatureDataStore3 {
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
            
    protected AbstractReadOnlyFeatureDataStore2(int modificationFlags, int visibilityFlags) {
        super(modificationFlags, visibilityFlags);
        
        if((modificationFlags&INVALID_MODIFICATION_FLAGS) != 0)
            throw new IllegalArgumentException();
    }

    @Override
    public final boolean supportsExplicitIDs() {
        return false;
    }

    @Override
    protected Pair<Long, Long> insertFeatureImpl(long fsid, long fid, String name, int geomCoding,
            Object rawGeom, int styleCoding, Object rawStyle, AttributeSet attributes,
            long timestamp, long version) throws DataStoreException {

        throw new UnsupportedOperationException();
    }

    @Override
    protected Pair<Long, Long> insertFeatureImpl(long fsid, long fid, String name, int geomCoding,
                                                 Object rawGeom, int styleCoding, Object rawStyle, AttributeSet attributes,
                                                 Feature.AltitudeMode altitudeMode, double extrude,
                                                 long timestamp, long version) throws DataStoreException {

        throw new UnsupportedOperationException();
    }

    @Override
    protected long insertFeatureSetImpl(FeatureSet featureSet) throws DataStoreException {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void updateFeatureImpl(long fid, int updatePropertyMask, String name,
            Geometry geometry, Style style, AttributeSet attributes, int attrUpdateType)
            throws DataStoreException {
        
        throw new UnsupportedOperationException();
    }

    @Override
    protected boolean deleteFeatureImpl(long fid) throws DataStoreException {
        throw new UnsupportedOperationException();
    }

    @Override
    protected boolean deleteFeatureSetImpl(long fsid) throws DataStoreException {
        throw new UnsupportedOperationException();
    }

    @Override
    protected boolean setFeatureVisibleImpl(long fid, boolean visible) throws DataStoreException {
        throw new UnsupportedOperationException();
    }

    @Override
    protected boolean setFeatureSetVisibleImpl(long fsid, boolean visible)
            throws DataStoreException {
        throw new UnsupportedOperationException();
    }
}
