package com.atakmap.map.layer.feature;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.style.Style;

public abstract class AbstractFeatureDataStore implements FeatureDataStore {

    protected Set<OnDataStoreContentChangedListener> contentChangedListeners;
    protected int visibilityFlags;
    protected int modificationFlags;

    protected AbstractFeatureDataStore(int modificationFlags, int visibilityFlags) {
        this.modificationFlags = modificationFlags;
        this.visibilityFlags = visibilityFlags;
        
        this.contentChangedListeners = Collections.newSetFromMap(new IdentityHashMap<OnDataStoreContentChangedListener, Boolean>()); 
    }

    protected void adopt(FeatureSet featureSet, long fsid, long version) {
        featureSet.id = fsid;
        featureSet.owner = this;
        featureSet.version = version;
    }

    @Override
    public synchronized void addOnDataStoreContentChangedListener(OnDataStoreContentChangedListener l) {
        this.contentChangedListeners.add(l);
    }

    @Override
    public synchronized void removeOnDataStoreContentChangedListener(OnDataStoreContentChangedListener l) {
        this.contentChangedListeners.remove(l);
    }

    protected void dispatchDataStoreContentChangedNoSync() {
        for(OnDataStoreContentChangedListener l : this.contentChangedListeners)
            l.onDataStoreContentChanged(this);
    }

    @Override
    public int getVisibilitySettingsFlags() {
        return this.visibilityFlags;
    }

    @Override
    public final void setFeatureVisible(long fid, boolean visible) {
        if((this.visibilityFlags&VISIBILITY_SETTINGS_FEATURE) != VISIBILITY_SETTINGS_FEATURE)
            throw new UnsupportedOperationException();
        this.setFeatureVisibleImpl(fid, visible);
    }
    
    protected abstract void setFeatureVisibleImpl(long fid, boolean visible);

    @Override
    public final void setFeaturesVisible(FeatureQueryParameters params, boolean visible) {
        if((this.visibilityFlags&VISIBILITY_SETTINGS_FEATURE) != VISIBILITY_SETTINGS_FEATURE)
            throw new UnsupportedOperationException();
        this.setFeaturesVisibleImpl(params, visible);
    }
    
    protected abstract void setFeaturesVisibleImpl(FeatureQueryParameters params, boolean visible);
    
    @Override
    public boolean isFeatureVisible(long fid) {
        return true;
    }

    @Override
    public void setFeatureSetVisible(long setId, boolean visible) {
        if((this.visibilityFlags&VISIBILITY_SETTINGS_FEATURESET) != VISIBILITY_SETTINGS_FEATURESET)
            throw new UnsupportedOperationException();
        this.setFeatureSetVisibleImpl(setId, visible);
    }
    
    protected abstract void setFeatureSetVisibleImpl(long setId, boolean visible);
    
    @Override
    public void setFeatureSetsVisible(FeatureSetQueryParameters params, boolean visible) {
        if((this.visibilityFlags&VISIBILITY_SETTINGS_FEATURESET) != VISIBILITY_SETTINGS_FEATURESET)
            throw new UnsupportedOperationException();
        this.setFeatureSetsVisibleImpl(params, visible);
    }
    
    protected abstract void setFeatureSetsVisibleImpl(FeatureSetQueryParameters params, boolean visible);
    
    @Override
    public int getModificationFlags() {
        return this.modificationFlags;
    }
    
    @Override
    public void beginBulkModification() {
        this.checkModificationFlags(MODIFY_BULK_MODIFICATIONS, true);
        
        this.beginBulkModificationImpl();
    }
    
    protected abstract void beginBulkModificationImpl(); 
    
    @Override
    public void endBulkModification(boolean successful) {
        this.checkModificationFlags(MODIFY_BULK_MODIFICATIONS, true);
        
        this.endBulkModificationImpl(successful);
    }
    
    protected abstract void endBulkModificationImpl(boolean successful);
        
    @Override
    public FeatureSet insertFeatureSet(String provider, String type, String name, double minResolution, double maxResolution, boolean returnRef) {
        this.checkModificationFlags(MODIFY_FEATURESET_INSERT, true);
        
        return this.insertFeatureSetImpl(provider, type, name, minResolution, maxResolution, returnRef);
    }
    
    protected abstract FeatureSet insertFeatureSetImpl(String provider, String type, String name, double minResolution, double maxResolution, boolean returnRef);
    
    @Override
    public void updateFeatureSet(long fsid, String name) {
        this.checkModificationFlags(MODIFY_FEATURESET_UPDATE|MODIFY_FEATURESET_NAME, true);
        
        this.updateFeatureSetImpl(fsid, name);
    }
    
    protected abstract void updateFeatureSetImpl(long fsid, String name);
    
    @Override
    public void updateFeatureSet(long fsid, double minResolution, double maxResolution) {
        this.checkModificationFlags(MODIFY_FEATURESET_UPDATE|MODIFY_FEATURESET_DISPLAY_THRESHOLDS, true);
        
        this.updateFeatureSetImpl(fsid, minResolution, maxResolution);
    }
    
    protected abstract void updateFeatureSetImpl(long fsid, double minResolution, double maxResolution);
    
    @Override
    public void updateFeatureSet(long fsid, String name, double minResolution, double maxResolution) {
        this.checkModificationFlags(MODIFY_FEATURESET_UPDATE|MODIFY_FEATURESET_NAME|MODIFY_FEATURESET_DISPLAY_THRESHOLDS, true);
        
        this.updateFeatureSetImpl(fsid, name, minResolution, maxResolution);
    }
    
    protected abstract void updateFeatureSetImpl(long fsid, String name, double minResolution, double maxResolution);
    
    @Override
    public void deleteFeatureSet(long fsid) {
        this.checkModificationFlags(MODIFY_FEATURESET_DELETE, true);
        
        this.deleteFeatureSetImpl(fsid);
    }
    
    protected abstract void deleteFeatureSetImpl(long fsid);
    
    @Override
    public void deleteAllFeatureSets() {
        this.checkModificationFlags(MODIFY_FEATURESET_DELETE, true);
        
        this.deleteAllFeatureSetsImpl();
    }
    
    protected abstract void deleteAllFeatureSetsImpl();
    
    @Override
    public Feature insertFeature(long fsid, String name, Geometry geom, Style style, AttributeSet attributes, boolean returnRef) {
        this.checkModificationFlags(MODIFY_FEATURESET_FEATURE_INSERT, true);
        
        return this.insertFeatureImpl(fsid, name, geom, style, attributes, returnRef);
    }
    
    protected abstract Feature insertFeatureImpl(long fsid, String name, Geometry geom, Style style, AttributeSet attributes, boolean returnRef);

    @Override
    public Feature insertFeature(long fsid, FeatureDefinition def, boolean returnRef) {
        this.checkModificationFlags(MODIFY_FEATURESET_FEATURE_INSERT, true);
        
        return this.insertFeatureImpl(fsid, def, returnRef);
    }
    
    protected Feature insertFeatureImpl(long fsid, FeatureDefinition def, boolean returnRef) {
        final Feature feature = def.get();
        return this.insertFeatureImpl(fsid,
                                      feature.getName(),
                                      feature.getGeometry(),
                                      feature.getStyle(),
                                      feature.getAttributes(),
                                      returnRef);
    }

    @Override
    public void updateFeature(long fid, String name) {
        this.checkModificationFlags(MODIFY_FEATURESET_FEATURE_UPDATE|MODIFY_FEATURE_NAME, true);
        
        this.updateFeatureImpl(fid, name);
    }
    
    protected abstract void updateFeatureImpl(long fid, String name);
    
    @Override
    public void updateFeature(long fid, Geometry geom) {
        this.checkModificationFlags(MODIFY_FEATURESET_FEATURE_UPDATE|MODIFY_FEATURE_GEOMETRY, true);
        
        this.updateFeatureImpl(fid, geom);
    }
    
    protected abstract void updateFeatureImpl(long fid, Geometry geom);
    
    @Override
    public void updateFeature(long fid, Style style) {
        this.checkModificationFlags(MODIFY_FEATURESET_FEATURE_UPDATE|MODIFY_FEATURE_STYLE, true);
        
        this.updateFeatureImpl(fid, style);
    }
    
    protected abstract void updateFeatureImpl(long fid, Style style);
    
    @Override
    public void updateFeature(long fid, AttributeSet attributes) {
        this.checkModificationFlags(MODIFY_FEATURESET_FEATURE_UPDATE|MODIFY_FEATURE_ATTRIBUTES, true);
        
        this.updateFeatureImpl(fid, attributes);
    }
    
    protected abstract void updateFeatureImpl(long fid, AttributeSet attributes);
    
    @Override
    public void updateFeature(long fid, String name, Geometry geom, Style style, AttributeSet attributes) {
        this.checkModificationFlags(MODIFY_FEATURESET_FEATURE_UPDATE|MODIFY_FEATURE_NAME|MODIFY_FEATURE_GEOMETRY|MODIFY_FEATURE_STYLE|MODIFY_FEATURE_ATTRIBUTES, true);
        
        this.updateFeatureImpl(fid, name, geom, style, attributes);
    }
    
    protected abstract void updateFeatureImpl(long fid, String name, Geometry geom, Style style, AttributeSet attributes);
    
    @Override
    public void deleteFeature(long fid) {
        this.checkModificationFlags(MODIFY_FEATURESET_FEATURE_DELETE, true);
        
        this.deleteFeatureImpl(fid);
    }
    
    protected abstract void deleteFeatureImpl(long fsid);
    
    @Override
    public void deleteAllFeatures(long fsid) {
        this.checkModificationFlags(MODIFY_FEATURESET_FEATURE_DELETE, true);
        
        this.deleteAllFeaturesImpl(fsid);
    }
    
    protected abstract void deleteAllFeaturesImpl(long fsid);
    
    protected boolean checkModificationFlags(int capability, boolean throwUnsupported) {
        final boolean retval = ((this.modificationFlags&capability) == capability);
        if(!retval && throwUnsupported)
            throw new UnsupportedOperationException();
        return retval;
    }
}
