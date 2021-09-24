package com.atakmap.map.layer.feature.datastore;

import java.io.File;

import com.atakmap.map.layer.feature.AttributeSet;
import com.atakmap.map.layer.feature.FeatureDefinition;
import com.atakmap.map.layer.feature.style.Style;
import com.atakmap.map.layer.feature.geometry.Geometry;


public final class FeatureSetDatabase extends FDB {

    private final static int DEFAULT_MODIFICATION_FLAGS =
            MODIFY_BULK_MODIFICATIONS |
            MODIFY_FEATURESET_INSERT |
            MODIFY_FEATURESET_DELETE |
            MODIFY_FEATURESET_UPDATE |
            MODIFY_FEATURESET_DISPLAY_THRESHOLDS |
            MODIFY_FEATURESET_NAME |
            MODIFY_FEATURESET_FEATURE_INSERT |
            MODIFY_FEATURESET_FEATURE_DELETE |
            MODIFY_FEATURESET_FEATURE_UPDATE |
            MODIFY_FEATURE_NAME |
            MODIFY_FEATURE_GEOMETRY |
            MODIFY_FEATURE_STYLE |
            MODIFY_FEATURE_ATTRIBUTES;

    public FeatureSetDatabase(File db) {
        super(db, DEFAULT_MODIFICATION_FLAGS, DEFAULT_VISIBILITY_FLAGS);
    }
    
    public FeatureSetDatabase(File db, int modificationFlags, int visibilityFlags) {
        super(db, modificationFlags, visibilityFlags);
    }
    
    private FeatureSetDatabase(File db, boolean buildIndices) {
        super(db, buildIndices, DEFAULT_MODIFICATION_FLAGS, DEFAULT_VISIBILITY_FLAGS);
    }
    
    /**************************************************************************/
    // Builder

    public final static class Builder extends FDB.Builder {
        public Builder(File databaseFile) {
            super(new FeatureSetDatabase(databaseFile, false));
        }
        
        
        @Override
        public void insertFeature(long fsid, FeatureDefinition def) {
            super.insertFeature(fsid, def);
        }
        
        @Override
        public void insertFeature(long fsid, String name, Geometry geometry, Style style, AttributeSet attribs) {
            super.insertFeature(fsid, name, geometry, style, attribs);
        }
        
        @Override
        public void insertFeatureSet(long fsid, String provider, String type, String name) {
            super.insertFeatureSet(fsid, provider, type, name);
        }

        @Override
        protected long insertFeatureSet(String provider, String type, String name, double minResolution, double maxResolution) {
            return super.insertFeatureSet(provider, type, name, minResolution, maxResolution);
        }

        @Override
        public void updateFeatureSet(long fsid, boolean visible, double minResolution, double maxResolution) {
            super.updateFeatureSet(fsid, visible, minResolution, maxResolution);
        }
        
        @Override
        public void deleteFeatureSet(long fsid) {
            super.deleteFeatureSet(fsid);
        }
    }
}
