package com.atakmap.map.layer.feature.datastore;

import java.io.File;

import com.atakmap.map.layer.feature.AttributeSet;
import com.atakmap.map.layer.feature.DataStoreException;
import com.atakmap.map.layer.feature.FeatureDefinition;
import com.atakmap.map.layer.feature.style.Style;
import com.atakmap.map.layer.feature.geometry.Geometry;


public final class FeatureSetDatabase2 extends FDB2 {

    private final static int DEFAULT_MODIFICATION_FLAGS =
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

    public FeatureSetDatabase2(File db) {
        super(db, DEFAULT_MODIFICATION_FLAGS, DEFAULT_VISIBILITY_FLAGS);
    }
    
    public FeatureSetDatabase2(File db, int modificationFlags, int visibilityFlags) {
        super(db, modificationFlags, visibilityFlags);
    }
    
    private FeatureSetDatabase2(File db, boolean buildIndices) {
        super(db, buildIndices, DEFAULT_MODIFICATION_FLAGS, DEFAULT_VISIBILITY_FLAGS);
    }
    
    /**************************************************************************/
    // Builder

    public final static class Builder extends FDB2.Builder {
        public Builder(File databaseFile) {
            super(new FeatureSetDatabase2(databaseFile, false));
        }
        
        public FeatureSetDatabase2 build() {
            if(this.done)
                throw new IllegalStateException();
            this.ctx.dispose();
            return (FeatureSetDatabase2)this.db;
        }
        
        public void insertFeature(long fsid, FeatureDefinition def) throws DataStoreException {
            this.insertFeature(fsid, FEATURE_ID_NONE, def);
        }
        
        @Override
        public void insertFeature(long fsid, long fid, FeatureDefinition def) throws DataStoreException {
            super.insertFeature(fsid, fid, def);
        }
        
        public void insertFeature(long fsid, String name, Geometry geometry, Style style, AttributeSet attribs) throws DataStoreException {
            this.insertFeature(fsid, FEATURE_ID_NONE, name, geometry, style, attribs);
        }
        @Override
        public void insertFeature(long fsid, long fid, String name, Geometry geometry, Style style, AttributeSet attribs) throws DataStoreException {
            super.insertFeature(fsid, fid, name, geometry, style, attribs);
        }
        
        @Override
        public void insertFeatureSet(long fsid, String provider, String type, String name) throws DataStoreException {
            super.insertFeatureSet(fsid, provider, type, name);
        }

        @Override
        protected long insertFeatureSet(String provider, String type, String name, double minResolution, double maxResolution) throws DataStoreException {
            return super.insertFeatureSet(provider, type, name, minResolution, maxResolution);
        }

        @Override
        public void updateFeatureSet(long fsid, boolean visible, double minResolution, double maxResolution) throws DataStoreException {
            super.updateFeatureSet(fsid, visible, minResolution, maxResolution);
        }
        
        @Override
        public void deleteFeatureSet(long fsid) throws DataStoreException {
            super.deleteFeatureSet(fsid);
        }
    }
}
