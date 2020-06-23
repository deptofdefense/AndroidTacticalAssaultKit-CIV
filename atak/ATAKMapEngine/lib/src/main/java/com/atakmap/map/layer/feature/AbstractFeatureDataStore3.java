package com.atakmap.map.layer.feature;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import android.util.Pair;

import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.style.Style;
import com.atakmap.math.MathUtils;

public abstract class AbstractFeatureDataStore3 implements FeatureDataStore2 {

    
    private Set<OnDataStoreContentChangedListener> listeners;
    private final int visibilityFlags;
    private final int modificationFlags;
    
    
    private final Object modifyLockSync = new Object();
    private Thread modifyLockHolder;
    /**
     * If <code>true</code> events occurring during the current modification
     * operation should be suppressed.
     */
    private boolean bulkModification;
    /**
     * If <code>true</code> one or more successful modifications have occurred
     * within the current <I>bulk modification</I>.
     */
    private boolean batchedModifications;
    
    private int modifyLocks = 0;

    protected AbstractFeatureDataStore3(int modificationFlags, int visibilityFlags) {
        this.modificationFlags = modificationFlags;
        this.visibilityFlags = visibilityFlags;
        
        this.listeners = Collections.newSetFromMap(new IdentityHashMap<OnDataStoreContentChangedListener, Boolean>());
        
        this.modifyLockHolder = null;
        this.bulkModification = false;
        this.batchedModifications = false;
        this.modifyLocks = 0;
    }

    protected final void checkModificationFlags(int action) {
        if(!MathUtils.hasBits(this.modificationFlags, action))
            throw new UnsupportedOperationException();
    }
    
    protected final void checkVisibilityFlags(int action) {
        if(!MathUtils.hasBits(this.visibilityFlags, action))
            throw new UnsupportedOperationException();
    }
    
    protected boolean holdsModifyLock() {
        synchronized(this.modifyLockSync) {
            return (Thread.currentThread() == this.modifyLockHolder);
        }
    }
    
    protected final void internalAcquireModifyLock(boolean bulk) throws DataStoreException {
        internalAcquireModifyLock(this, true, true);
    }

    protected final void internalAcquireModifyLockUninterruptable(boolean bulk) {
        try {
            internalAcquireModifyLock(this, true, false);
        } catch(DataStoreException e) {
            throw new IllegalStateException(e);
        }
    }

    protected final boolean checkBatchSuppressDispatch() {
        if(this.bulkModification) {
            this.batchedModifications = true;
            return true;
        } else {
            return false;
        }
    }

    protected final void dispatchFeatureInserted(long fid, FeatureDefinition2 def, long version) {
        synchronized(this.listeners) {
            for(OnDataStoreContentChangedListener l : this.listeners)
                l.onFeatureInserted(this, fid, def, version);
        }
    }
    
    protected final void dispatchFeatureDeleted(long fid) {
        synchronized(this.listeners) {
            for(OnDataStoreContentChangedListener l : this.listeners)
                l.onFeatureDeleted(this, fid);
        }
    }
    
    protected final void dispatchFeatureVisibilityChanged(long fid, boolean visible) {
        synchronized(this.listeners) {
            for(OnDataStoreContentChangedListener l : this.listeners)
                l.onFeatureVisibilityChanged(this, fid, visible);
        }
    }
    
    protected final void dispatchContentChanged() {
        synchronized(this.listeners) {
            for(OnDataStoreContentChangedListener l : this.listeners)
                l.onDataStoreContentChanged(this);
        }
    }
    
    protected final void dispatchFeatureUpdated(long fid, int updatePropertyMask, String name, Geometry geom,
                                                Style style, AttributeSet attrs, int attrUpdateType) throws DataStoreException {
        
        synchronized(this.listeners) {
            for(OnDataStoreContentChangedListener l : this.listeners)
                l.onFeatureUpdated(this, fid, updatePropertyMask, name, geom, style, attrs, attrUpdateType);
        }
    }

    protected Pair<Long, Long> insertFeatureImpl(Feature feature) throws DataStoreException {
        return this.insertFeatureImpl(feature.getFeatureSetId(),
                                      feature.getId(),
                                      feature.getName(),
                                      FeatureDefinition2.GEOM_ATAK_GEOMETRY,
                                      feature.getGeometry(),
                                      FeatureDefinition2.STYLE_ATAK_STYLE,
                                      feature.getStyle(),
                                      feature.getAttributes(),
                                      feature.getTimestamp(),
                                      feature.getVersion());
    }

    protected abstract Pair<Long, Long> insertFeatureImpl(long fsid,
                                                          long fid,
                                                          String name,
                                                          int geomCoding, Object rawGeom,
                                                          int styleCoding, Object rawStyle,
                                                          AttributeSet attributes,
                                                          long timestamp,
                                                          long version) throws DataStoreException;
    
    protected abstract long insertFeatureSetImpl(FeatureSet featureSet) throws DataStoreException;
    protected abstract void updateFeatureImpl(long fid,
                                              int updatePropertyMask,
                                              String name,
                                              Geometry geometry,
                                              Style style,
                                              AttributeSet attributes,
                                              int attrUpdateType) throws DataStoreException;
    protected abstract boolean deleteFeatureImpl(long fid) throws DataStoreException;
    protected abstract boolean deleteFeatureSetImpl(long fsid) throws DataStoreException;
    
    protected abstract boolean setFeatureVisibleImpl(long fid, boolean visible) throws DataStoreException;
    protected abstract boolean setFeatureSetVisibleImpl(long fsid, boolean visible) throws DataStoreException;
    
    /**************************************************************************/
    @Override
    public int queryFeaturesCount(FeatureQueryParameters params) throws DataStoreException {
        return queryFeaturesCount(this, params);
    }

    @Override
    public int queryFeatureSetsCount(FeatureSetQueryParameters params) throws DataStoreException {
        return queryFeatureSetsCount(this, params);
    }

    @Override
    public final long insertFeature(long fsid, long fid, FeatureDefinition2 def, long version) throws DataStoreException {
        this.checkModificationFlags(MODIFY_FEATURESET_FEATURE_INSERT);

        if(fsid == FEATURESET_ID_NONE)
            throw new IllegalArgumentException();
        if(!this.supportsExplicitIDs() && (fid != FEATURE_ID_NONE || version != FEATURE_VERSION_NONE))
            throw new IllegalArgumentException();

        this.internalAcquireModifyLock(false);
        try {
            Pair<Long, Long> retval = this.insertFeatureImpl(fsid,
                                                             fid,
                                                             def.getName(),
                                                             def.getGeomCoding(),
                                                             def.getRawGeometry(),
                                                             def.getStyleCoding(),
                                                             def.getRawStyle(),
                                                             def.getAttributes(),
                                                             def.getTimestamp(),
                                                             version);
            
            if(!this.checkBatchSuppressDispatch())
                this.dispatchFeatureInserted(retval.first, def, retval.second);
    
            return retval.first;
        } finally {
            this.releaseModifyLock();
        }
    }

    @Override
    public long insertFeature(Feature feature) throws DataStoreException {
        this.checkModificationFlags(MODIFY_FEATURESET_FEATURE_INSERT);

        if(feature.getFeatureSetId() == FEATURESET_ID_NONE)
            throw new IllegalArgumentException();
        if(!this.supportsExplicitIDs() && (feature.getId() != FEATURE_ID_NONE || feature.getVersion() != FEATURE_VERSION_NONE))
            throw new IllegalArgumentException();

        this.internalAcquireModifyLock(false);
        try {
            Pair<Long, Long> retval = this.insertFeatureImpl(feature);
            
            if(!this.checkBatchSuppressDispatch())
                this.dispatchFeatureInserted(retval.first, Adapters.adapt(feature), retval.second);
    
            return retval.first;
        } finally {
            this.releaseModifyLock();
        }
    }

    @Override
    public void insertFeatures(FeatureCursor features) throws DataStoreException {
        this.checkModificationFlags(MODIFY_FEATURESET_FEATURE_INSERT);

        insertFeatures(this, features);
    }

    @Override
    public final long insertFeatureSet(FeatureSet featureSet) throws DataStoreException {
        this.checkModificationFlags(MODIFY_FEATURESET_INSERT);
        
        this.internalAcquireModifyLock(false);
        try {
            final long retval = this.insertFeatureSetImpl(featureSet);
            if(!this.checkBatchSuppressDispatch())
                this.dispatchContentChanged();
            return retval;
        } finally {
            this.releaseModifyLock();
        }
    }

    @Override
    public void insertFeatureSets(FeatureSetCursor featureSets) throws DataStoreException {
        this.checkModificationFlags(MODIFY_FEATURESET_INSERT);

        insertFeatureSets(this, featureSets);
    }

    @Override
    public final void updateFeature(long fid, int updatePropertyMask, String name, Geometry geometry,
            Style style, AttributeSet attributes, int attrUpdateType) throws DataStoreException {
        
        this.checkModificationFlags(MODIFY_FEATURESET_FEATURE_UPDATE);
        
        if(MathUtils.hasBits(updatePropertyMask, PROPERTY_FEATURE_NAME))
            this.checkModificationFlags(MODIFY_FEATURE_NAME);
        if(MathUtils.hasBits(updatePropertyMask, PROPERTY_FEATURE_GEOMETRY))
            this.checkModificationFlags(MODIFY_FEATURE_GEOMETRY);
        if(MathUtils.hasBits(updatePropertyMask, PROPERTY_FEATURE_STYLE))
            this.checkModificationFlags(MODIFY_FEATURE_STYLE);
        if(MathUtils.hasBits(updatePropertyMask, PROPERTY_FEATURE_ATTRIBUTES))
            this.checkModificationFlags(MODIFY_FEATURE_ATTRIBUTES);
        
        this.internalAcquireModifyLock(false);
        try {
            this.updateFeatureImpl(fid,
                                   updatePropertyMask,
                                   name,
                                   geometry,
                                   style,
                                   attributes,
                                   attrUpdateType);

            if(!this.checkBatchSuppressDispatch()) {
                this.dispatchFeatureUpdated(fid,
                                            updatePropertyMask,
                                            name,
                                            geometry,
                                            style,
                                            attributes,
                                            attrUpdateType);
            }
        } finally {
            this.releaseModifyLock();
        }
    }

    @Override
    public void updateFeatureSet(long fsid, String name, double minResolution, double maxResolution)
            throws DataStoreException {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void updateFeatureSet(long fsid, String name) throws DataStoreException {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void updateFeatureSet(long fsid, double minResolution, double maxResolution)
            throws DataStoreException {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void deleteFeature(long fid) throws DataStoreException {
        this.checkModificationFlags(MODIFY_FEATURESET_FEATURE_DELETE);
        
        this.internalAcquireModifyLock(false);
        try {
            final boolean notify = this.deleteFeatureImpl(fid);
            if(notify && !this.checkBatchSuppressDispatch())
                this.dispatchFeatureDeleted(fid);
        } finally {
            this.releaseModifyLock();
        }
    }
    
    @Override
    public void deleteFeatures(FeatureQueryParameters params) throws DataStoreException {
        this.checkModificationFlags(MODIFY_FEATURESET_FEATURE_DELETE);
        
        deleteFeatures(this, params);
    }

    @Override
    public final void deleteFeatureSet(long fsid) throws DataStoreException {
        this.checkModificationFlags(MODIFY_FEATURESET_DELETE);
        
        this.internalAcquireModifyLock(false);
        try {
            final boolean notify = this.deleteFeatureSetImpl(fsid);
            if(notify && !this.checkBatchSuppressDispatch())
                this.dispatchContentChanged();
        } finally {
            this.releaseModifyLock();
        }
    }
    
    @Override
    public void deleteFeatureSets(FeatureSetQueryParameters params) throws DataStoreException {
        this.checkModificationFlags(MODIFY_FEATURESET_DELETE);
        
        deleteFeatureSets(this, params);
    }

    @Override
    public final void setFeatureVisible(long fid, boolean visible) throws DataStoreException {
        this.checkVisibilityFlags(VISIBILITY_SETTINGS_FEATURE);
        
        this.internalAcquireModifyLock(false);
        try {
            final boolean notify = this.setFeatureVisibleImpl(fid, visible);
            if(notify && !this.checkBatchSuppressDispatch())
                this.dispatchFeatureVisibilityChanged(fid, visible);
        } finally {
            this.releaseModifyLock();
        }
    }

    @Override
    public void setFeaturesVisible(FeatureQueryParameters params, boolean visible) throws DataStoreException {
        this.checkVisibilityFlags(VISIBILITY_SETTINGS_FEATURE);
        
        setFeaturesVisible(this, params, visible);
    }

    @Override
    public void setFeatureSetVisible(long fsid, boolean visible) throws DataStoreException {
        this.checkVisibilityFlags(VISIBILITY_SETTINGS_FEATURESET);
        
        this.internalAcquireModifyLock(false);
        try {
            final boolean notify = this.setFeatureSetVisibleImpl(fsid, visible);
            if(notify && !this.checkBatchSuppressDispatch())
                this.dispatchContentChanged();
        } finally {
            this.releaseModifyLock();
        }
    }

    @Override
    public void setFeatureSetsVisible(FeatureSetQueryParameters params, boolean visible) throws DataStoreException {
        this.checkVisibilityFlags(VISIBILITY_SETTINGS_FEATURESET);
        
        setFeatureSetsVisible(this, params, visible);
    }

    @Override
    public final int getModificationFlags() {
        return modificationFlags;
    }

    @Override
    public final int getVisibilityFlags() {
       return this.visibilityFlags;
    }


    @Override
    public final void addOnDataStoreContentChangedListener(OnDataStoreContentChangedListener l) {
        synchronized(this.listeners) {
            this.listeners.add(l);
        }
    }

    @Override
    public final void removeOnDataStoreContentChangedListener(OnDataStoreContentChangedListener l) {
        synchronized(this.listeners) {
            this.listeners.remove(l);
        }
    }

    @Override
    public void acquireModifyLock(boolean bulk) throws InterruptedException {
        synchronized(this.modifyLockSync) {
            final Thread currentThread = Thread.currentThread();
            
            // current thread already holds
            if(this.modifyLockHolder == currentThread) {
                this.modifyLocks++;
                this.bulkModification |= bulk;
                return;
            }
            
            // wait for the lock to become available
            while(this.modifyLockHolder != null)
                this.modifyLockSync.wait();
            
            // take ownership of the lock
            this.modifyLockHolder = currentThread;
            this.bulkModification = bulk;
            this.modifyLocks++;
        }
    }
    
    @Override
    public void releaseModifyLock() {
        synchronized(this.modifyLockSync) {
            if(this.modifyLockHolder != Thread.currentThread())
                throw new IllegalStateException();
            this.modifyLocks--;
            if(this.modifyLocks > 0)
                return;

            this.modifyLockHolder = null;
            this.modifyLockSync.notifyAll();
            
            // reset bulk modification state and dispatch content changed if
            // appropriate
            this.bulkModification = false;
            if(this.batchedModifications) {
                this.batchedModifications = false;
                this.dispatchContentChanged();
            }
        }
    }
    
    /**************************************************************************/
    
    protected static void internalAcquireModifyLock(FeatureDataStore2 dataStore, boolean bulkModify, boolean allowInterrupt) throws DataStoreException {
        while(true) {
            try {
                dataStore.acquireModifyLock(bulkModify);
            } catch(InterruptedException e) {
                if(allowInterrupt)
                    throw new DataStoreException("Interrupted while waiting to acquire modify lock", e);
                else
                    continue;
            }
            break;
        }
    }

    public static int queryFeaturesCount(FeatureDataStore2 dataStore, FeatureQueryParameters params) throws DataStoreException {
        FeatureCursor result = null;
        try {
            int retval = 0;
            
            // XXX - set ignore fields on params
            
            result = dataStore.queryFeatures(params);
            while(result.moveToNext())
                retval++;
            return retval;
        } finally {
            if(result != null)
                result.close();
        }
    }
    
    public static int queryFeatureSetsCount(FeatureDataStore2 dataStore, FeatureSetQueryParameters params) throws DataStoreException {
        FeatureSetCursor result = null;
        try {
            int retval = 0;
            
            // XXX - set ignore fields on params
            
            result = dataStore.queryFeatureSets(params);
            while(result.moveToNext())
                retval++;
            return retval;
        } finally {
            if(result != null)
                result.close();
        }
    }
    
    public static void insertFeatures(FeatureDataStore2 dataStore, FeatureCursor features) throws DataStoreException {
        internalAcquireModifyLock(dataStore, true, true);
        try {
            final FeatureDefinition2 def = Adapters.adapt(features);
            while(features.moveToNext())
                dataStore.insertFeature(features.getFsid(), features.getId(), def, features.getVersion());
        } finally {
            dataStore.releaseModifyLock();
        }
    }
    
    public static void insertFeatureSets(FeatureDataStore2 dataStore, FeatureSetCursor featureSets) throws DataStoreException {
       internalAcquireModifyLock(dataStore, true, true);
        try {
            while(featureSets.moveToNext())
                dataStore.insertFeatureSet(featureSets.get());
        } finally {
            dataStore.releaseModifyLock();
        }
    }

    public static void deleteFeatures(FeatureDataStore2 dataStore, FeatureQueryParameters params) throws DataStoreException {
        internalAcquireModifyLock(dataStore, true, true);
        try {
            FeatureCursor result = null;
            try {
                // XXX - set ignore fields on params
                
                result = dataStore.queryFeatures(params);
                while(result.moveToNext())
                    dataStore.deleteFeature(result.getId());
            } finally {
                if(result != null)
                    result.close();
            }
        } finally {
            dataStore.releaseModifyLock();
        }
    }
    
    public static void deleteFeatureSets(FeatureDataStore2 dataStore, FeatureSetQueryParameters params) throws DataStoreException {
        internalAcquireModifyLock(dataStore, true, true);
        try {
            FeatureSetCursor result = null;
            try {
                // XXX - set ignore fields on params
                
                result = dataStore.queryFeatureSets(params);
                while(result.moveToNext())
                    dataStore.deleteFeatureSet(result.getId());
            } finally {
                if(result != null)
                    result.close();
            }
        } finally {
            dataStore.releaseModifyLock();
        }
    }
    
    public static void setFeaturesVisible(FeatureDataStore2 dataStore, FeatureQueryParameters params, boolean visible) throws DataStoreException {
        internalAcquireModifyLock(dataStore, true, true);
        try {
            FeatureCursor result = null;
            try {
                // XXX - set ignore fields on params
                
                result = dataStore.queryFeatures(params);
                while(result.moveToNext())
                    dataStore.setFeatureVisible(result.getId(), visible);
            } finally {
                if(result != null)
                    result.close();
            }
        } finally {
            dataStore.releaseModifyLock();
        }
    }
    
    public static void setFeatureSetsVisible(FeatureDataStore2 dataStore, FeatureSetQueryParameters params, boolean visible) throws DataStoreException {
        internalAcquireModifyLock(dataStore, true, true);
        try {
            FeatureSetCursor result = null;
            try {
                // XXX - set ignore fields on params
                
                result = dataStore.queryFeatureSets(params);
                while(result.moveToNext())
                    dataStore.setFeatureSetVisible(result.getId(), visible);
            } finally {
                if(result != null)
                    result.close();
            }
        } finally {
            dataStore.releaseModifyLock();
        }
    }

    public static Feature getFeature(FeatureDataStore2 dataStore, long fid) throws DataStoreException {
        FeatureQueryParameters params = new FeatureQueryParameters();
        params.ids = Collections.<Long>singleton(fid);
        params.limit = 1;
        FeatureCursor result = null;
        try {
            result = dataStore.queryFeatures(params);
            if(!result.moveToNext())
                return null;
            return result.get();
        } finally {
            if(result != null)
                result.close();
        }
    }
}
