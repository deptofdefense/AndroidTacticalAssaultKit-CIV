package com.atakmap.map.layer.feature;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.Set;

import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.style.Style;
import com.atakmap.math.MathUtils;
import com.atakmap.util.WildCard;

public abstract class AbstractFeatureDataStore2 implements FeatureDataStore {

    protected Set<OnDataStoreContentChangedListener> contentChangedListeners;
    protected final int visibilityFlags;
    protected final int modificationFlags;

    private int inBulkModification;

    protected AbstractFeatureDataStore2(int modificationFlags, int visibilityFlags) {
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
        if(this.inBulkModification > 0)
            return;

        for(OnDataStoreContentChangedListener l : this.contentChangedListeners)
            l.onDataStoreContentChanged(this);
    }

    @Override
    public final int getVisibilitySettingsFlags() {
        return this.visibilityFlags;
    }

    @Override
    public final void setFeatureVisible(long fid, boolean visible) {
        if(!MathUtils.hasBits(this.visibilityFlags,VISIBILITY_SETTINGS_FEATURE))
            throw new UnsupportedOperationException();

        synchronized(this) {
            if(this.setFeatureVisibleImpl(fid, visible))
                this.dispatchDataStoreContentChangedNoSync();
        }
    }
    
    protected abstract boolean setFeatureVisibleImpl(long fid, boolean visible);

    @Override
    public final void setFeaturesVisible(FeatureQueryParameters params, boolean visible) {
        if(!MathUtils.hasBits(this.visibilityFlags,VISIBILITY_SETTINGS_FEATURE))
            throw new UnsupportedOperationException();
        synchronized(this) {
            if(this.setFeaturesVisibleImpl(params, visible))
                this.dispatchDataStoreContentChangedNoSync();
        }
    }
    
    protected abstract boolean setFeaturesVisibleImpl(FeatureQueryParameters params, boolean visible);
    
//    @Override
//    public abstract boolean isFeatureVisible(long fid);

    @Override
    public void setFeatureSetVisible(long setId, boolean visible) {
        if(!MathUtils.hasBits(this.visibilityFlags,VISIBILITY_SETTINGS_FEATURESET))
            throw new UnsupportedOperationException();
        synchronized(this) {
            if(this.setFeatureSetVisibleImpl(setId, visible))
                this.dispatchDataStoreContentChangedNoSync();
        }
    }
    
    protected abstract boolean setFeatureSetVisibleImpl(long setId, boolean visible);
    
    @Override
    public void setFeatureSetsVisible(FeatureSetQueryParameters params, boolean visible) {
        if(!MathUtils.hasBits(this.visibilityFlags,VISIBILITY_SETTINGS_FEATURESET))
            throw new UnsupportedOperationException();
        synchronized(this) {
            if(this.setFeatureSetsVisibleImpl(params, visible))
                this.dispatchDataStoreContentChangedNoSync();
        }
    }
    
    protected abstract boolean setFeatureSetsVisibleImpl(FeatureSetQueryParameters params, boolean visible);
    
    @Override
    public final int getModificationFlags() {
        return this.modificationFlags;
    }
    
    @Override
    public synchronized final boolean isInBulkModification() {
        return (this.inBulkModification > 0);
    }

    @Override
    public synchronized final void beginBulkModification() {
        this.checkModificationFlags(MODIFY_BULK_MODIFICATIONS, true);
        
        this.beginBulkModificationImpl();
        this.inBulkModification++;
    }
    
    protected abstract void beginBulkModificationImpl(); 
    
    @Override
    public synchronized final void endBulkModification(boolean successful) {
        this.checkModificationFlags(MODIFY_BULK_MODIFICATIONS, true);

        if(this.inBulkModification == 0)
            return;
        
        // XXX - should be accumulating modifications!!!
        final boolean modified = this.endBulkModificationImpl(successful);
        this.inBulkModification--;

        if((this.inBulkModification == 0) && modified)
            this.dispatchDataStoreContentChangedNoSync();
    }
    
    protected abstract boolean endBulkModificationImpl(boolean successful);
        
    @Override
    public final FeatureSet insertFeatureSet(final String provider, 
          final String type, final String name, 
          final double minResolution, final double maxResolution, 
          final boolean returnRef) {
        this.checkModificationFlags(MODIFY_FEATURESET_INSERT, true);
        
        synchronized(this) {
            FeatureSet[] ref = returnRef ? new FeatureSet[1] : null;
            if(this.insertFeatureSetImpl(provider, type, name, minResolution, maxResolution, ref))
                this.dispatchDataStoreContentChangedNoSync();
            return returnRef ? ref[0] : null;
        }
        
    }
    
    protected abstract boolean insertFeatureSetImpl(String provider, String type, String name, double minResolution, double maxResolution, FeatureSet[] returnRef);
    
    @Override
    public final void updateFeatureSet(long fsid, String name) {
        this.checkModificationFlags(MODIFY_FEATURESET_UPDATE|MODIFY_FEATURESET_NAME, true);
        
        synchronized(this) {
            if(this.updateFeatureSetImpl(fsid, name))
                this.dispatchDataStoreContentChangedNoSync();
        }
    }
    
    protected abstract boolean updateFeatureSetImpl(long fsid, String name);
    
    @Override
    public final void updateFeatureSet(long fsid, double minResolution, double maxResolution) {
        this.checkModificationFlags(MODIFY_FEATURESET_UPDATE|MODIFY_FEATURESET_DISPLAY_THRESHOLDS, true);
        
        synchronized(this) {
            if(this.updateFeatureSetImpl(fsid, minResolution, maxResolution))
                this.dispatchDataStoreContentChangedNoSync();
        }
    }
    
    protected abstract boolean updateFeatureSetImpl(long fsid, double minResolution, double maxResolution);
    
    @Override
    public final void updateFeatureSet(long fsid, String name, double minResolution, double maxResolution) {
        this.checkModificationFlags(MODIFY_FEATURESET_UPDATE|MODIFY_FEATURESET_NAME|MODIFY_FEATURESET_DISPLAY_THRESHOLDS, true);
        
        synchronized(this) {
            if(this.updateFeatureSetImpl(fsid, name, minResolution, maxResolution))
                this.dispatchDataStoreContentChangedNoSync();
        }
    }
    
    protected abstract boolean updateFeatureSetImpl(long fsid, String name, double minResolution, double maxResolution);
    
    @Override
    public final void deleteFeatureSet(long fsid) {
        this.checkModificationFlags(MODIFY_FEATURESET_DELETE, true);
        
        synchronized(this) {
            if(this.deleteFeatureSetImpl(fsid))
                this.dispatchDataStoreContentChangedNoSync();
        }
    }
    
    protected abstract boolean deleteFeatureSetImpl(long fsid);
    
    @Override
    public final void deleteAllFeatureSets() {
        this.checkModificationFlags(MODIFY_FEATURESET_DELETE, true);
        
        synchronized(this) {
            if(this.deleteAllFeatureSetsImpl())
                this.dispatchDataStoreContentChangedNoSync();
        }
    }
    
    protected abstract boolean deleteAllFeatureSetsImpl();
    
    @Override
    public final Feature insertFeature(final long fsid, final String name, final Geometry geom, final Style style, final AttributeSet attributes, final boolean returnRef) {
        this.checkModificationFlags(MODIFY_FEATURESET_FEATURE_INSERT, true);
        
        synchronized(this) {
            Feature[] ref = returnRef ? new Feature[1] : null;
            if(this.insertFeatureImpl(fsid, name, geom, style, attributes, ref))
                this.dispatchDataStoreContentChangedNoSync();
            return returnRef ? ref[0] : null;
        }
    }
    
    protected abstract boolean insertFeatureImpl(final long fsid, final String name, final Geometry geom, Style style, final AttributeSet attributes, final Feature[] returnRef);

    @Override
    public final Feature insertFeature(final long fsid, final FeatureDefinition def, final boolean returnRef) {
        this.checkModificationFlags(MODIFY_FEATURESET_FEATURE_INSERT, true);
        
        synchronized(this) {
            Feature[] ref = returnRef ? new Feature[1] : null;
            if(this.insertFeatureImpl(fsid, def, ref))
                this.dispatchDataStoreContentChangedNoSync();
            return returnRef ? ref[0] : null;
        }
    }
    
    protected abstract boolean insertFeatureImpl(long fsid, FeatureDefinition def, Feature[] returnRef);

    @Override
    public final void updateFeature(long fid, String name) {
        this.checkModificationFlags(MODIFY_FEATURESET_FEATURE_UPDATE|MODIFY_FEATURE_NAME, true);
        
        synchronized(this) {
            if(this.updateFeatureImpl(fid, name))
                this.dispatchDataStoreContentChangedNoSync();
        }
    }
    
    protected abstract boolean updateFeatureImpl(long fid, String name);
    
    @Override
    public void updateFeature(long fid, Geometry geom) {
        this.checkModificationFlags(MODIFY_FEATURESET_FEATURE_UPDATE|MODIFY_FEATURE_GEOMETRY, true);
        
        synchronized(this) {
            if(this.updateFeatureImpl(fid, geom))
                this.dispatchDataStoreContentChangedNoSync();
        }
    }
    
    protected abstract boolean updateFeatureImpl(long fid, Geometry geom);
    
    @Override
    public void updateFeature(long fid, Style style) {
        this.checkModificationFlags(MODIFY_FEATURESET_FEATURE_UPDATE|MODIFY_FEATURE_STYLE, true);
        
        synchronized(this) {
            if(this.updateFeatureImpl(fid, style))
                this.dispatchDataStoreContentChangedNoSync();
        }
    }
    
    protected abstract boolean updateFeatureImpl(long fid, Style style);
    
    @Override
    public final void updateFeature(long fid, AttributeSet attributes) {
        this.checkModificationFlags(MODIFY_FEATURESET_FEATURE_UPDATE|MODIFY_FEATURE_ATTRIBUTES, true);
        
        synchronized(this) {
            if(this.updateFeatureImpl(fid, attributes))
                this.dispatchDataStoreContentChangedNoSync();
        }
    }
    
    protected abstract boolean updateFeatureImpl(long fid, AttributeSet attributes);
    
    @Override
    public final void updateFeature(long fid, String name, Geometry geom, Style style, AttributeSet attributes) {
        this.checkModificationFlags(MODIFY_FEATURESET_FEATURE_UPDATE|MODIFY_FEATURE_NAME|MODIFY_FEATURE_GEOMETRY|MODIFY_FEATURE_STYLE|MODIFY_FEATURE_ATTRIBUTES, true);
        
        synchronized(this) {
            if(this.updateFeatureImpl(fid, name, geom, style, attributes))
                this.dispatchDataStoreContentChangedNoSync();
        }
    }
    
    protected abstract boolean updateFeatureImpl(long fid, String name, Geometry geom, Style style, AttributeSet attributes);
    
    @Override
    public final void deleteFeature(long fid) {
        this.checkModificationFlags(MODIFY_FEATURESET_FEATURE_DELETE, true);
        
        synchronized(this) {
            if(this.deleteFeatureImpl(fid))
                this.dispatchDataStoreContentChangedNoSync();
        }
    }
    
    protected abstract boolean deleteFeatureImpl(long fsid);
    
    @Override
    public final void deleteAllFeatures(long fsid) {
        this.checkModificationFlags(MODIFY_FEATURESET_FEATURE_DELETE, true);
        
        synchronized(this) {
            if(this.deleteAllFeaturesImpl(fsid))
                this.dispatchDataStoreContentChangedNoSync();
        }
    }
    
    protected abstract boolean deleteAllFeaturesImpl(long fsid);
    
    protected final boolean checkModificationFlags(int capability, boolean throwUnsupported) {
        final boolean retval = ((this.modificationFlags&capability) == capability);
        if(!retval && throwUnsupported)
            throw new UnsupportedOperationException();
        return retval;
    }

    /**************************************************************************/
    
    public static void setFeatureSetsVisible(FeatureDataStore dataStore, FeatureSetQueryParameters params, boolean visible) {
        LinkedList<Long> fsids = new LinkedList<Long>();

        FeatureSetCursor result = null;
        try {
            result = dataStore.queryFeatureSets(params);
            while(result.moveToNext())
                fsids.add(Long.valueOf(result.get().getId()));
        } finally {
            if(result != null)
                result.close();
        }
        
        for(Long fsid : fsids)
            dataStore.setFeatureSetVisible(fsid.longValue(), visible);
    }
    
    public static void setFeaturesVisible(FeatureDataStore dataStore, FeatureQueryParameters params, boolean visible) {
        LinkedList<Long> fids = new LinkedList<Long>();

        FeatureCursor result = null;
        try {
            result = dataStore.queryFeatures(params);
            while(result.moveToNext())
                fids.add(Long.valueOf(result.getId()));
        } finally {
            if(result != null)
                result.close();
        }
        
        for(Long fid : fids)
            dataStore.setFeatureVisible(fid.longValue(), visible);
    }
    
    public static int queryFeatureSetsCount(FeatureDataStore dataStore, FeatureSetQueryParameters params) {
        FeatureSetCursor result = null;
        try {
            result = dataStore.queryFeatureSets(params);
            int retval = 0;
            while(result.moveToNext())
                retval++;
            return retval;
        } finally {
            if(result != null)
                result.close();
        }
    }
    
    public static int queryFeaturesCount(FeatureDataStore dataStore, FeatureQueryParameters params) {
        FeatureCursor result = null;
        try {
            // since we're only counting the results, try to reduce the amount
            // of data we're pulling out of the datastore
            if(params != null)
                params = new FeatureQueryParameters(params);
            else
                params = new FeatureQueryParameters();
            
            params.ignoredFields = FeatureQueryParameters.FIELD_ATTRIBUTES |
                                   FeatureQueryParameters.FIELD_GEOMETRY |
                                   FeatureQueryParameters.FIELD_NAME |
                                   FeatureQueryParameters.FIELD_STYLE;

            result = dataStore.queryFeatures(params);
            final int limit = (params.limit+params.offset);
            int retval = 0;
            while(result.moveToNext()) {
                retval++;
                if(retval == limit)
                    break;
            }
            return Math.max(retval-params.offset, 0);
        } finally {
            if(result != null)
                result.close();
        }
    }
    
    public static Feature insertFeature(FeatureDataStore dataStore, long fsid, FeatureDefinition def, boolean returnRef) {
        final Feature feature = def.get();
        return dataStore.insertFeature(fsid,
                                       feature.getName(),
                                       feature.getGeometry(),
                                       feature.getStyle(),
                                       feature.getAttributes(),
                                       returnRef);
    }
 
    /**
     * Test to see if a value matches a test string containing optional wildcard characters.
     * @return true if the value matches the test, if the value is null, the method will return
     * false.
     */
    public static boolean matches(final String test, final String value, final char wildcard) {
        if (value == null)
             return false;

        if(test.indexOf(wildcard) < 0) 
            return value.equals(test);

        return value.matches(WildCard.wildcardAsRegex(test, wildcard));
    }

    public static boolean matches(Collection<String> test, String value, char wildcard) {
        for(String arg : test)
            if(matches(arg, value, wildcard))
                return true;
        return false;
    }
}
