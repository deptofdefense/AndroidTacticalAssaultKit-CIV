package com.atakmap.map.layer.feature.datastore;

import java.io.File;

import com.atakmap.map.layer.feature.AttributeSet;
import com.atakmap.map.layer.feature.FeatureDataStore;
import com.atakmap.map.layer.feature.FeatureDefinition;
import com.atakmap.map.layer.feature.style.Style;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.math.MathUtils;

public final class FeatureDatabase extends FDB {

    private final static int DEFAULT_MODIFICATION_FLAGS =
            MODIFY_BULK_MODIFICATIONS |
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
    
    public FeatureDatabase(File db) {
        super(db,
              true,
              DEFAULT_MODIFICATION_FLAGS,
              DEFAULT_VISIBILITY_FLAGS);
        
        // this class represents a single featureset; insertion and deletion are
        // a function of the owner and are not supported
        if(MathUtils.hasBits(modificationFlags, FeatureDataStore.MODIFY_FEATURESET_INSERT))
            throw new IllegalArgumentException();
        if(MathUtils.hasBits(modificationFlags, FeatureDataStore.MODIFY_FEATURESET_DELETE))
            throw new IllegalArgumentException();
    }
    
    private FeatureDatabase(File db, boolean buildIndices) {
        super(db,
              buildIndices,
              DEFAULT_MODIFICATION_FLAGS,
              DEFAULT_VISIBILITY_FLAGS);
        
        // this class represents a single featureset; insertion and deletion are
        // a function of the owner and are not supported
        if(MathUtils.hasBits(modificationFlags, FeatureDataStore.MODIFY_FEATURESET_INSERT))
            throw new IllegalArgumentException();
        if(MathUtils.hasBits(modificationFlags, FeatureDataStore.MODIFY_FEATURESET_DELETE))
            throw new IllegalArgumentException();
    }

    public FeatureDatabase(File db, int modificationFlags, int visibilityFlags) {
        super(db, modificationFlags, visibilityFlags);
        
        // this class represents a single featureset; insertion and deletion are
        // a function of the owner and are not supported
        if(MathUtils.hasBits(modificationFlags, FeatureDataStore.MODIFY_FEATURESET_INSERT))
            throw new IllegalArgumentException();
        if(MathUtils.hasBits(modificationFlags, FeatureDataStore.MODIFY_FEATURESET_DELETE))
            throw new IllegalArgumentException();
    }

    private FeatureDatabase(File dbFile,
                            boolean buildIndices,
                            int modificationFlags,
                            int visibilityFlags) {

        super(dbFile, buildIndices, modificationFlags, visibilityFlags);
        
        // this class represents a single featureset; insertion and deletion are
        // a function of the owner and are not supported
        if(MathUtils.hasBits(modificationFlags, FeatureDataStore.MODIFY_FEATURESET_INSERT))
            throw new IllegalArgumentException();
        if(MathUtils.hasBits(modificationFlags, FeatureDataStore.MODIFY_FEATURESET_DELETE))
            throw new IllegalArgumentException();
    }

    /**************************************************************************/
    // Builder

    public final static class Builder extends FDB.Builder {
        private long fsid;

        public Builder(File databaseFile, long fsid, String provider, String type, String name) {
            super(new FeatureDatabase(databaseFile, false));
            
            this.fsid = fsid;
            super.insertFeatureSet(this.fsid, provider, type, name);

        }
        
        public void insertFeature(FeatureDefinition def) {
            super.insertFeature(this.fsid, def);
        }
        
        public void insertFeature(String name, Geometry geometry, Style style, AttributeSet attribs) {
            super.insertFeature(this.fsid, name, geometry, style, attribs);
        }
        
        public void updateSettings(boolean visible, double minResolution, double maxResolution) {
            super.updateFeatureSet(this.fsid, visible, minResolution, maxResolution);
        }
    }
}
