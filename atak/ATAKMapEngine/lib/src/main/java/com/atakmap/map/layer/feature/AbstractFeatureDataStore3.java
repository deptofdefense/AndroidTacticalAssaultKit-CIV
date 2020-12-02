package com.atakmap.map.layer.feature;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import android.util.Pair;

import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.style.Style;
import com.atakmap.math.MathUtils;

public abstract class AbstractFeatureDataStore3 implements FeatureDataStore3 {


    private final Set<OnDataStoreContentChangedListener> listeners;
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

    private int modifyLocks;

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
        if (!MathUtils.hasBits(this.modificationFlags, action))
            throw new UnsupportedOperationException();
    }

    protected final void checkVisibilityFlags(int action) {
        if (!MathUtils.hasBits(this.visibilityFlags, action))
            throw new UnsupportedOperationException();
    }

    protected boolean holdsModifyLock() {
        synchronized (this.modifyLockSync) {
            return (Thread.currentThread() == this.modifyLockHolder);
        }
    }

    protected final void internalAcquireModifyLock(boolean bulk) throws DataStoreException {
        Utils.internalAcquireModifyLock(this, true, true);
    }

    protected final void internalAcquireModifyLockUninterruptable(boolean bulk) {
        try {
            Utils.internalAcquireModifyLock(this, true, false);
        } catch (DataStoreException e) {
            throw new IllegalStateException(e);
        }
    }

    protected final boolean checkBatchSuppressDispatch() {
        if (this.bulkModification) {
            this.batchedModifications = true;
            return true;
        } else {
            return false;
        }
    }

    protected final void dispatchFeatureInserted(long fid, FeatureDefinition2 def, long version) {
        synchronized (this.listeners) {
            for (OnDataStoreContentChangedListener l : this.listeners)
                l.onFeatureInserted(this, fid, def, version);
        }
    }

    protected final void dispatchFeatureDeleted(long fid) {
        synchronized (this.listeners) {
            for (OnDataStoreContentChangedListener l : this.listeners)
                l.onFeatureDeleted(this, fid);
        }
    }

    protected final void dispatchFeatureVisibilityChanged(long fid, boolean visible) {
        synchronized (this.listeners) {
            for (OnDataStoreContentChangedListener l : this.listeners)
                l.onFeatureVisibilityChanged(this, fid, visible);
        }
    }

    protected final void dispatchContentChanged() {
        synchronized (this.listeners) {
            for (OnDataStoreContentChangedListener l : this.listeners)
                l.onDataStoreContentChanged(this);
        }
    }

    protected final void dispatchFeatureUpdated(long fid, int updatePropertyMask, String name, Geometry geom,
                                                Style style, AttributeSet attrs, Feature.AltitudeMode altitudeMode, double extrude, int attrUpdateType) throws DataStoreException {

        synchronized (this.listeners) {
            for (OnDataStoreContentChangedListener l : this.listeners)
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
                feature.getAltitudeMode(),
                feature.getExtrude(),
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


    /**
     * Implementations that support AltitudeMode and Extrude will need to implement this method.
     */
    protected Pair<Long, Long> insertFeatureImpl(long fsid,
                                                 long fid,
                                                 String name,
                                                 int geomCoding, Object rawGeom,
                                                 int styleCoding, Object rawStyle,
                                                 AttributeSet attributes,
                                                 Feature.AltitudeMode altitudeMode,
                                                 double extrude,
                                                 long timestamp,
                                                 long version) throws DataStoreException {

        return insertFeatureImpl(fsid, fid, name, geomCoding, rawGeom, styleCoding, rawStyle, attributes, timestamp, version);
    }


    protected abstract long insertFeatureSetImpl(FeatureSet featureSet) throws DataStoreException;

    protected abstract void updateFeatureImpl(long fid,
                                              int updatePropertyMask,
                                              String name,
                                              Geometry geometry,
                                              Style style,
                                              AttributeSet attributes,
                                              int attrUpdateType) throws DataStoreException;

    /**
     * Must be overridden if a datastore supports AltitudeMode and extrude.
     */
    protected void updateFeatureImpl(long fid,
                                     int updatePropertyMask,
                                     String name,
                                     Geometry geometry,
                                     Style style,
                                     AttributeSet attributes,
                                     Feature.AltitudeMode altitudeMode,
                                     double extrude,
                                     int attrUpdateType) throws DataStoreException {
        this.updateFeatureImpl(fid, updatePropertyMask, name, geometry, style, attributes, attrUpdateType);
    }

    protected abstract boolean deleteFeatureImpl(long fid) throws DataStoreException;

    protected abstract boolean deleteFeatureSetImpl(long fsid) throws DataStoreException;

    protected abstract boolean setFeatureVisibleImpl(long fid, boolean visible) throws DataStoreException;

    protected abstract boolean setFeatureSetVisibleImpl(long fsid, boolean visible) throws DataStoreException;

    /**************************************************************************/
    @Override
    public int queryFeaturesCount(FeatureQueryParameters params) throws DataStoreException {
        return Utils.queryFeaturesCount(this, params);
    }

    @Override
    public int queryFeatureSetsCount(FeatureSetQueryParameters params) throws DataStoreException {
        return Utils.queryFeatureSetsCount(this, params);
    }

    @Override
    public final long insertFeature(long fsid, long fid, FeatureDefinition2 def, long version) throws DataStoreException {
        this.checkModificationFlags(MODIFY_FEATURESET_FEATURE_INSERT);

        if (fsid == FEATURESET_ID_NONE)
            throw new IllegalArgumentException("FEATURESET_ID_NONE");
        if (!this.supportsExplicitIDs() && (fid != FEATURE_ID_NONE || version != FEATURE_VERSION_NONE))
            throw new IllegalArgumentException("supports explicit ids but FEATURESET_ID_NON not set");

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
                    getAltitudeMode(def),
                    getExtrude(def),
                    def.getTimestamp(),
                    version);

            if (!this.checkBatchSuppressDispatch())
                this.dispatchFeatureInserted(retval.first, def, retval.second);

            return retval.first;
        } finally {
            this.releaseModifyLock();
        }
    }

    @Override
    public long insertFeature(Feature feature) throws DataStoreException {
        this.checkModificationFlags(MODIFY_FEATURESET_FEATURE_INSERT);

        if (feature.getFeatureSetId() == FEATURESET_ID_NONE)
            throw new IllegalArgumentException("FEATURESET_ID_NONE");
        if (!this.supportsExplicitIDs() && (feature.getId() != FEATURE_ID_NONE || feature.getVersion() != FEATURE_VERSION_NONE))
            throw new IllegalArgumentException("supports explicit ids but FEATURESET_ID_NONE not set");


        this.internalAcquireModifyLock(false);
        try {
            Pair<Long, Long> retval = this.insertFeatureImpl(feature);

            if (!this.checkBatchSuppressDispatch())
                this.dispatchFeatureInserted(retval.first, Adapters.adapt(feature), retval.second);

            return retval.first;
        } finally {
            this.releaseModifyLock();
        }
    }

    @Override
    public void insertFeatures(FeatureCursor features) throws DataStoreException {
        this.checkModificationFlags(MODIFY_FEATURESET_FEATURE_INSERT);

        Utils.insertFeatures(this, features);
    }

    @Override
    public final long insertFeatureSet(FeatureSet featureSet) throws DataStoreException {
        this.checkModificationFlags(MODIFY_FEATURESET_INSERT);

        this.internalAcquireModifyLock(false);
        try {
            final long retval = this.insertFeatureSetImpl(featureSet);
            if (!this.checkBatchSuppressDispatch())
                this.dispatchContentChanged();
            return retval;
        } finally {
            this.releaseModifyLock();
        }
    }

    @Override
    public void insertFeatureSets(FeatureSetCursor featureSets) throws DataStoreException {
        this.checkModificationFlags(MODIFY_FEATURESET_INSERT);

        Utils.insertFeatureSets(this, featureSets);
    }
    @Override
    public final void updateFeature(long fid, int updatePropertyMask, String name, Geometry geometry,
                                    Style style, AttributeSet attributes, int attrUpdateType) throws DataStoreException {
        this.updateFeature(fid, updatePropertyMask, name, geometry, style, attributes, Feature.AltitudeMode.ClampToGround, 0d, attrUpdateType);
    }

    @Override
    public final void updateFeature(long fid, int updatePropertyMask, String name, Geometry geometry,
                                    Style style, AttributeSet attributes, Feature.AltitudeMode altitudeMode, double extrude, int attrUpdateType) throws DataStoreException {

        this.checkModificationFlags(MODIFY_FEATURESET_FEATURE_UPDATE);

        if (MathUtils.hasBits(updatePropertyMask, PROPERTY_FEATURE_NAME))
            this.checkModificationFlags(MODIFY_FEATURE_NAME);
        if (MathUtils.hasBits(updatePropertyMask, PROPERTY_FEATURE_GEOMETRY))
            this.checkModificationFlags(MODIFY_FEATURE_GEOMETRY);
        if (MathUtils.hasBits(updatePropertyMask, PROPERTY_FEATURE_STYLE))
            this.checkModificationFlags(MODIFY_FEATURE_STYLE);
        if (MathUtils.hasBits(updatePropertyMask, PROPERTY_FEATURE_ATTRIBUTES))
            this.checkModificationFlags(MODIFY_FEATURE_ATTRIBUTES);

        this.internalAcquireModifyLock(false);
        try {
            this.updateFeatureImpl(fid,
                    updatePropertyMask,
                    name,
                    geometry,
                    style,
                    attributes,
                    altitudeMode,
                    extrude,
                    attrUpdateType);

            if (!this.checkBatchSuppressDispatch()) {
                this.dispatchFeatureUpdated(fid,
                        updatePropertyMask,
                        name,
                        geometry,
                        style,
                        attributes,
                        altitudeMode,
                        extrude,
                        attrUpdateType);
            }
        } finally {
            this.releaseModifyLock();
        }
    }


    @Override
    public void updateFeatureSet(long fsid, String name, double minResolution, double maxResolution)
            throws DataStoreException {
    }

    @Override
    public void updateFeatureSet(long fsid, String name) throws DataStoreException {
    }

    @Override
    public void updateFeatureSet(long fsid, double minResolution, double maxResolution)
            throws DataStoreException {
    }

    @Override
    public void deleteFeature(long fid) throws DataStoreException {
        this.checkModificationFlags(MODIFY_FEATURESET_FEATURE_DELETE);

        this.internalAcquireModifyLock(false);
        try {
            final boolean notify = this.deleteFeatureImpl(fid);
            if (notify && !this.checkBatchSuppressDispatch())
                this.dispatchFeatureDeleted(fid);
        } finally {
            this.releaseModifyLock();
        }
    }

    @Override
    public void deleteFeatures(FeatureQueryParameters params) throws DataStoreException {
        this.checkModificationFlags(MODIFY_FEATURESET_FEATURE_DELETE);

        Utils.deleteFeatures(this, params);
    }

    @Override
    public final void deleteFeatureSet(long fsid) throws DataStoreException {
        this.checkModificationFlags(MODIFY_FEATURESET_DELETE);

        this.internalAcquireModifyLock(false);
        try {
            final boolean notify = this.deleteFeatureSetImpl(fsid);
            if (notify && !this.checkBatchSuppressDispatch())
                this.dispatchContentChanged();
        } finally {
            this.releaseModifyLock();
        }
    }

    @Override
    public void deleteFeatureSets(FeatureSetQueryParameters params) throws DataStoreException {
        this.checkModificationFlags(MODIFY_FEATURESET_DELETE);

        Utils.deleteFeatureSets(this, params);
    }

    @Override
    public final void setFeatureVisible(long fid, boolean visible) throws DataStoreException {
        this.checkVisibilityFlags(VISIBILITY_SETTINGS_FEATURE);

        this.internalAcquireModifyLock(false);
        try {
            final boolean notify = this.setFeatureVisibleImpl(fid, visible);
            if (notify && !this.checkBatchSuppressDispatch())
                this.dispatchFeatureVisibilityChanged(fid, visible);
        } finally {
            this.releaseModifyLock();
        }
    }

    @Override
    public void setFeaturesVisible(FeatureQueryParameters params, boolean visible) throws DataStoreException {
        this.checkVisibilityFlags(VISIBILITY_SETTINGS_FEATURE);

        Utils.setFeaturesVisible(this, params, visible);
    }

    @Override
    public void setFeatureSetVisible(long fsid, boolean visible) throws DataStoreException {
        this.checkVisibilityFlags(VISIBILITY_SETTINGS_FEATURESET);

        this.internalAcquireModifyLock(false);
        try {
            final boolean notify = this.setFeatureSetVisibleImpl(fsid, visible);
            if (notify && !this.checkBatchSuppressDispatch())
                this.dispatchContentChanged();
        } finally {
            this.releaseModifyLock();
        }
    }

    @Override
    public void setFeatureSetsVisible(FeatureSetQueryParameters params, boolean visible) throws DataStoreException {
        this.checkVisibilityFlags(VISIBILITY_SETTINGS_FEATURESET);

        Utils.setFeatureSetsVisible(this, params, visible);
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
        synchronized (this.listeners) {
            this.listeners.add(l);
        }
    }

    @Override
    public final void removeOnDataStoreContentChangedListener(OnDataStoreContentChangedListener l) {
        synchronized (this.listeners) {
            this.listeners.remove(l);
        }
    }

    @Override
    public void acquireModifyLock(boolean bulk) throws InterruptedException {
        synchronized (this.modifyLockSync) {
            final Thread currentThread = Thread.currentThread();

            // current thread already holds
            if (this.modifyLockHolder == currentThread) {
                this.modifyLocks++;
                this.bulkModification |= bulk;
                return;
            }

            // wait for the lock to become available
            while (this.modifyLockHolder != null)
                this.modifyLockSync.wait();

            // take ownership of the lock
            this.modifyLockHolder = currentThread;
            this.bulkModification = bulk;
            this.modifyLocks++;
        }
    }

    @Override
    public void releaseModifyLock() {
        synchronized (this.modifyLockSync) {
            if (this.modifyLockHolder != Thread.currentThread())
                throw new IllegalStateException("lock holder not on current thread");
            this.modifyLocks--;
            if (this.modifyLocks > 0)
                return;

            this.modifyLockHolder = null;
            this.modifyLockSync.notifyAll();

            // reset bulk modification state and dispatch content changed if
            // appropriate
            this.bulkModification = false;
            if (this.batchedModifications) {
                this.batchedModifications = false;
                this.dispatchContentChanged();
            }
        }
    }

    protected static void internalAcquireModifyLock(FeatureDataStore2 dataStore, boolean bulkModify, boolean allowInterrupt) throws DataStoreException {
        Utils.internalAcquireModifyLock(dataStore, bulkModify, allowInterrupt);
    }

    /**
     * @deprecated use {@link Utils#queryFeaturesCount(FeatureDataStore2, FeatureQueryParameters)} instead
     */
    @Deprecated
    @DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.4")
    public static int queryFeaturesCount(FeatureDataStore2 dataStore, FeatureDataStore2.FeatureQueryParameters params) throws DataStoreException {
        return Utils.queryFeaturesCount(dataStore, params);
    }

    /**
     * @deprecated use {@link Utils#queryFeatureSetsCount(FeatureDataStore2, FeatureSetQueryParameters)} instead
     */
    @Deprecated
    @DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.4")
    public static int queryFeatureSetsCount(FeatureDataStore2 dataStore, FeatureDataStore2.FeatureSetQueryParameters params) throws DataStoreException {
        return Utils.queryFeatureSetsCount(dataStore, params);
    }

    /**
     * @deprecated use {@link Utils#insertFeatures(FeatureDataStore2, FeatureCursor)} instead
     */
    @Deprecated
    @DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.4")
    public static void insertFeatures(FeatureDataStore2 dataStore, FeatureCursor features) throws DataStoreException {
        Utils.insertFeatures(dataStore, features);
    }

    /**
     * @deprecated use {@link Utils#insertFeatureSets(FeatureDataStore2, FeatureSetCursor)} instead
     */
    @Deprecated
    @DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.4")
    public static void insertFeatureSets(FeatureDataStore2 dataStore, FeatureSetCursor featureSets) throws DataStoreException {
        Utils.insertFeatureSets(dataStore, featureSets);
    }

    /**
     * @deprecated use {@link Utils#deleteFeatures(FeatureDataStore2, FeatureQueryParameters)}
     *     instead
     */
    @Deprecated
    @DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.4")
    public static void deleteFeatures(FeatureDataStore2 dataStore, FeatureDataStore2.FeatureQueryParameters params) throws DataStoreException {
        Utils.deleteFeatures(dataStore, params);
    }

    /**
     * @deprecated use {@link Utils#deleteFeatureSets(FeatureDataStore2,
     *     FeatureSetQueryParameters)} instead
     */
    @Deprecated
    @DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.4")
    public static void deleteFeatureSets(FeatureDataStore2 dataStore, FeatureDataStore2.FeatureSetQueryParameters params) throws DataStoreException {
        Utils.deleteFeatureSets(dataStore, params);
    }

    /**
     * @deprecated use {@link Utils#setFeaturesVisible(FeatureDataStore2, FeatureQueryParameters,
     *     boolean)} instead
     */
    @Deprecated
    @DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.4")
    public static void setFeaturesVisible(FeatureDataStore2 dataStore, FeatureDataStore2.FeatureQueryParameters params, boolean visible) throws DataStoreException {
        Utils.setFeaturesVisible(dataStore, params, visible);
    }

    /**
     * @deprecated use {@link Utils#setFeatureSetsVisible(FeatureDataStore2,
     *     FeatureSetQueryParameters, boolean)} instead
     */
    @Deprecated
    @DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.4")
    public static void setFeatureSetsVisible(FeatureDataStore2 dataStore, FeatureDataStore2.FeatureSetQueryParameters params, boolean visible) throws DataStoreException {
        Utils.setFeatureSetsVisible(dataStore, params, visible);
    }

    /**
     * @deprecated use {@link Utils#getFeature(FeatureDataStore2, long)} instead
     */
    @Deprecated
    @DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.4")
    public static Feature getFeature(FeatureDataStore2 dataStore, long fid) throws DataStoreException {
        return Utils.getFeature(dataStore, fid);
    }

    /**
     * @deprecated use {@link Utils#getFeatureSet(FeatureDataStore2, long)} instead
     */
    @Deprecated
    @DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.4")
    public static FeatureSet getFeatureSet(FeatureDataStore2 dataStore, long fid) throws DataStoreException {
        return Utils.getFeatureSet(dataStore, fid);
    }

    /**
     * @deprecated use {@link Utils#deleteAllFeatureSets(FeatureDataStore2)} instead
     */
    @Deprecated
    @DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.4")
    public static void deleteAllFeatureSets(FeatureDataStore2 dataStore) throws DataStoreException {
        Utils.deleteAllFeatureSets(dataStore);
    }

    /**
     * @deprecated use {@link Utils#isFeatureSetVisible(FeatureDataStore2, long)} instead
     */
    @Deprecated
    @DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.4")
    public static boolean isFeatureSetVisible(FeatureDataStore2 dataStore, long fid) throws DataStoreException {
        return Utils.isFeatureSetVisible(dataStore,fid);
    }

    /**
     * @deprecated use {@link Utils#isFeatureVisible(FeatureDataStore2, long)} instead
     */
    @Deprecated
    @DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.4")
    public static boolean isFeatureVisible(FeatureDataStore2 dataStore, long fid) throws DataStoreException {
        return Utils.isFeatureVisible(dataStore, fid);
    }


    /**
     * copied since this FDB2 will be transitioned to native in the near future.
     */
    final protected static Feature.AltitudeMode getAltitudeMode(FeatureDefinition def) {
        if(def instanceof FeatureDefinition3)
            return ((FeatureDefinition3)def).getAltitudeMode();
        else
            return Feature.AltitudeMode.ClampToGround;
    }

    /**
     * copied since this FDB2 will be transitioned to native in the near future.
     */
    final protected static double getExtrude(FeatureDefinition def) {
        if(def instanceof FeatureDefinition3)
            return ((FeatureDefinition3)def).getExtrude();
        else
            return 0d;
    }


}