package com.atakmap.map.layer.feature;

import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.conversion.EGM96;
import com.atakmap.interop.NotifyCallback;
import com.atakmap.interop.Pointer;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.style.Style;
import com.atakmap.math.MathUtils;
import com.atakmap.util.ReadWriteLock;

import java.lang.ref.WeakReference;
import java.util.IdentityHashMap;
import java.util.Map;

import gov.tak.api.annotation.DontObfuscate;

@DontObfuscate
public class NativeFeatureDataStore2 implements FeatureDataStore2 {
    final static String TAG = "NativeFeatureDataStore2";

    final ReadWriteLock rwlock = new ReadWriteLock();
    Pointer pointer;
    Map<OnDataStoreContentChangedListener, Pointer> listeners;

    NativeFeatureDataStore2(Pointer pointer) {
        this.pointer = pointer;
        this.listeners = new IdentityHashMap<>();
    }

    @Override
    public FeatureCursor queryFeatures(FeatureQueryParameters params) throws DataStoreException {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            Pointer cparams = null;
            try {
                if(params != null) {
                    cparams = FeatureQueryParameters_create();
                    FeatureQueryParameters_adapt(params, cparams.raw);
                }

                Pointer retval = queryFeatures(this.pointer.raw, (cparams != null) ? cparams.raw : 0L);
                if(retval == null)
                    throw new IllegalStateException();
                return new NativeFeatureCursor(retval, this);
            } finally {
                if(cparams != null)
                    FeatureQueryParameters_destruct(cparams);
            }
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public int queryFeaturesCount(FeatureQueryParameters params) throws DataStoreException {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException("raw pointer has been cleaned up");
            Pointer cparams = null;
            try {
                if(params != null) {
                    cparams = FeatureQueryParameters_create();
                    FeatureQueryParameters_adapt(params, cparams.raw);
                }

                return queryFeaturesCount(this.pointer.raw, (cparams != null) ? cparams.raw : 0L);
            } finally {
                if(cparams != null)
                    FeatureQueryParameters_destruct(cparams);
            }
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public FeatureSetCursor queryFeatureSets(FeatureSetQueryParameters params) throws DataStoreException {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            Pointer cparams = null;
            try {
                if(params != null) {
                    cparams = FeatureSetQueryParameters_create();
                    FeatureSetQueryParameters_adapt(params, cparams.raw);
                }

                Pointer retval = queryFeatureSets(this.pointer.raw, (cparams != null) ? cparams.raw : 0L);
                if(retval == null)
                    throw new IllegalStateException();
                return new NativeFeatureSetCursor(retval, this);
            } finally {
                if(cparams != null)
                    FeatureSetQueryParameters_destruct(cparams);
            }
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public int queryFeatureSetsCount(FeatureSetQueryParameters params) throws DataStoreException {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            Pointer cparams = null;
            try {
                if(params != null) {
                    cparams = FeatureSetQueryParameters_create();
                    FeatureSetQueryParameters_adapt(params, cparams.raw);
                }

                return queryFeatureSetsCount(this.pointer.raw, (cparams != null) ? cparams.raw : 0L);
            } finally {
                if(cparams != null)
                    FeatureSetQueryParameters_destruct(cparams);
            }
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public long insertFeature(long fsid, long fid, FeatureDefinition2 def, long version) throws DataStoreException {
        if(def == null)
            throw new NullPointerException();

        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            final AttributeSet mattrs = def.getAttributes();

            long cfsid = getFEATURESET_ID_NONE();
            if(fsid != FEATURESET_ID_NONE)
                cfsid = fsid;
            long cfid = getFEATURE_ID_NONE();
            if(fid != FEATURE_ID_NONE)
                cfid = fid;
            long cversion = getFEATURE_VERSION_NONE();
            if(version != FEATURE_VERSION_NONE)
                cversion = version;
            final int cgeomCoding;
            Object rawGeom = def.getRawGeometry();
            switch(def.getGeomCoding()) {
                case FeatureDefinition2.GEOM_ATAK_GEOMETRY :
                    cgeomCoding = NativeFeatureDataSource.getFeatureDefinition_GeometryEncoding_GeomGeom();
                    if(rawGeom != null)
                        rawGeom = Interop.getPointer((Geometry)rawGeom);
                    break;
                case FeatureDefinition2.GEOM_SPATIALITE_BLOB :
                    cgeomCoding = NativeFeatureDataSource.getFeatureDefinition_GeometryEncoding_GeomBlob();
                    if(rawGeom != null && !(rawGeom instanceof byte[]))
                        throw new ClassCastException();
                    break;
                case FeatureDefinition2.GEOM_WKB :
                    cgeomCoding = NativeFeatureDataSource.getFeatureDefinition_GeometryEncoding_GeomWkb();
                    if(rawGeom != null && !(rawGeom instanceof byte[]))
                        throw new ClassCastException();
                    break;
                case FeatureDefinition2.GEOM_WKT :
                    cgeomCoding = NativeFeatureDataSource.getFeatureDefinition_GeometryEncoding_GeomWkt();
                    if(rawGeom != null && !(rawGeom instanceof String))
                        throw new ClassCastException();
                    break;
                default :
                    throw new IllegalArgumentException();
            }

            final int cstyleCoding;
            Object rawStyle = def.getRawStyle();
            switch(def.getStyleCoding()) {
                case FeatureDefinition2.STYLE_ATAK_STYLE :
                    cstyleCoding = NativeFeatureDataSource.getFeatureDefinition_StyleEncoding_StyleStyle();
                    if(rawStyle != null)
                        rawStyle = Interop.getPointer((Style)rawGeom);
                    break;
                case FeatureDefinition2.STYLE_OGR :
                    cstyleCoding = NativeFeatureDataSource.getFeatureDefinition_StyleEncoding_StyleOgr();
                    if(rawStyle != null && !(rawStyle instanceof String))
                        throw new ClassCastException();
                    break;
                default :
                    throw new IllegalArgumentException();
            }

            final long ctimestamp = (def.getTimestamp() == TIMESTAMP_NONE) ? getTIMESTAMP_NONE() : def.getTimestamp();

            return insertFeature(this.pointer.raw, cfsid, cfid, def.getName(), cgeomCoding, rawGeom, cstyleCoding, rawStyle, (mattrs != null) ? mattrs.pointer.raw : 0L, ctimestamp, cversion);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public long insertFeature(Feature feature) throws DataStoreException {
        if(feature == null)
            throw new NullPointerException();

        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            return insertFeature(this.pointer.raw, feature.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public void insertFeatures(FeatureCursor features) throws DataStoreException {
        FeatureDefinition2 def = Adapters.adapt(features);
        while(features.moveToNext())
            insertFeature(features.getFsid(), features.getId(), def, features.getVersion());
    }

    @Override
    public long insertFeatureSet(FeatureSet featureSet) throws DataStoreException {
        if(featureSet == null)
            throw new NullPointerException();

        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            long fsid = getFEATURESET_ID_NONE();
            if(featureSet.getId() != FEATURESET_ID_NONE)
                fsid = featureSet.getId();
            long version = getFEATURESET_VERSION_NONE();
            if(featureSet.getVersion() != FEATURESET_VERSION_NONE)
                version = featureSet.getVersion();
            return insertFeatureSet(this.pointer.raw, fsid, featureSet.getName(), featureSet.getProvider(), featureSet.getType(), featureSet.getMinResolution(), featureSet.getMaxResolution(), version);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public void insertFeatureSets(FeatureSetCursor featureSet) throws DataStoreException {
        while(featureSet.moveToNext())
            insertFeatureSet(featureSet.get());
    }

    @Override
    public void updateFeature(long fid, int updatePropertyMask, String name, Geometry geometry, Style style, AttributeSet attributes, int attrUpdateType) throws DataStoreException {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            int cupdatePropertyMask = 0;
            if(MathUtils.hasBits(updatePropertyMask, PROPERTY_FEATURE_ATTRIBUTES))
                cupdatePropertyMask |= getFIELD_ATTRIBUTES();
            if(MathUtils.hasBits(updatePropertyMask, PROPERTY_FEATURE_NAME))
                cupdatePropertyMask |= getFIELD_NAME();
            if(MathUtils.hasBits(updatePropertyMask, PROPERTY_FEATURE_GEOMETRY))
                cupdatePropertyMask |= getFIELD_GEOMETRY();
            if(MathUtils.hasBits(updatePropertyMask, PROPERTY_FEATURE_STYLE))
                cupdatePropertyMask |= getFIELD_STYLE();

            int cattrUpdateType = 0;
            switch (attrUpdateType) {
                case UPDATE_ATTRIBUTES_ADD_OR_REPLACE :
                    cattrUpdateType = getUPDATE_ATTRIBUTES_ADD_OR_REPLACE();
                    break;
                case UPDATE_ATTRIBUTES_SET :
                    cattrUpdateType = getUPDATE_ATTRIBUTES_SET();
                    break;
                default :
                    throw new IllegalArgumentException();
            }

            updateFeature(this.pointer.raw,
                          fid,
                          cupdatePropertyMask,
                          name,
                          Interop.getRawPointer(geometry),
                          Interop.getRawPointer(style),
                          (attributes != null) ? attributes.pointer.raw : 0L,
                          cattrUpdateType);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public void updateFeatureSet(long fsid, String name, double minResolution, double maxResolution) throws DataStoreException {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            updateFeatureSet(this.pointer.raw, fsid, name, minResolution, maxResolution);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public void updateFeatureSet(long fsid, String name) throws DataStoreException {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            updateFeatureSet(this.pointer.raw, fsid, name);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public void updateFeatureSet(long fsid, double minResolution, double maxResolution) throws DataStoreException {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            updateFeatureSet(this.pointer.raw, fsid, minResolution, maxResolution);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public void deleteFeature(long fid) throws DataStoreException {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            deleteFeature(this.pointer.raw, fid);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public void deleteFeatures(FeatureQueryParameters params) throws DataStoreException {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            Pointer cparams = null;
            try {
                if(params != null) {
                    cparams = FeatureQueryParameters_create();
                    FeatureQueryParameters_adapt(params, cparams.raw);
                }

                deleteFeatures(this.pointer.raw, (cparams != null) ? cparams.raw : 0L);
            } finally {
                if(cparams != null)
                    FeatureQueryParameters_destruct(cparams);
            }
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public void deleteFeatureSet(long fsid) throws DataStoreException {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            deleteFeatureSet(this.pointer.raw, fsid);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public void deleteFeatureSets(FeatureSetQueryParameters params) throws DataStoreException {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            Pointer cparams = null;
            try {
                if(params != null) {
                    cparams = FeatureSetQueryParameters_create();
                    FeatureSetQueryParameters_adapt(params, cparams.raw);
                }

                deleteFeatureSets(this.pointer.raw, (cparams != null) ? cparams.raw : 0L);
            } finally {
                if(cparams != null)
                    FeatureSetQueryParameters_destruct(cparams);
            }
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public void setFeatureVisible(long fid, boolean visible) throws DataStoreException {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            setFeatureVisible(this.pointer.raw, fid, visible);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public void setFeaturesVisible(FeatureQueryParameters params, boolean visible) throws DataStoreException {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            Pointer cparams = null;
            try {
                if(params != null) {
                    cparams = FeatureQueryParameters_create();
                    FeatureQueryParameters_adapt(params, cparams.raw);
                }

                setFeaturesVisible(this.pointer.raw, (cparams != null) ? cparams.raw : 0L, visible);
            } finally {
                if(cparams != null)
                    FeatureQueryParameters_destruct(cparams);
            }
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public void setFeatureSetVisible(long fsid, boolean visible) throws DataStoreException {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            setFeatureSetVisible(this.pointer.raw, fsid, visible);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public void setFeatureSetsVisible(FeatureSetQueryParameters params, boolean visible) throws DataStoreException {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            Pointer cparams = null;
            try {
                if(params != null) {
                    cparams = FeatureSetQueryParameters_create();
                    FeatureSetQueryParameters_adapt(params, cparams.raw);
                }

                setFeatureSetsVisible(this.pointer.raw, (cparams != null) ? cparams.raw : 0L, visible);
            } finally {
                if(cparams != null)
                    FeatureSetQueryParameters_destruct(cparams);
            }
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public boolean hasTimeReference() {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            return hasTimeReference(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public long getMinimumTimestamp() {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            return getMinimumTimestamp(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public long getMaximumTimestamp() {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            return getMaximumTimestamp(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public String getUri() {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            return getUri(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public boolean supportsExplicitIDs() {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            return supportsExplicitIDs(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public int getModificationFlags() {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            return getModificationFlags(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public int getVisibilityFlags() {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            return getVisibilityFlags(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public boolean hasCache() {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            return hasCache(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public void clearCache() {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            clearCache(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public long getCacheSize() {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            return getCacheSize(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public void acquireModifyLock(boolean bulkModification) throws InterruptedException {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            acquireModifyLock(this.pointer.raw, bulkModification);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public void releaseModifyLock() {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            releaseModifyLock(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public void addOnDataStoreContentChangedListener(OnDataStoreContentChangedListener l) {
        synchronized(listeners) {
            if(listeners.containsKey(l))
                return;
            this.rwlock.acquireRead();
            try {
                if(this.pointer.raw == 0L)
                    throw new IllegalStateException();
                listeners.put(l, addOnDataStoreContentChangedListener(this.pointer.raw, new CallbackForwarder(this, l)));
            } finally {
                this.rwlock.releaseRead();
            }
        }
    }

    @Override
    public void removeOnDataStoreContentChangedListener(OnDataStoreContentChangedListener l) {
        final Pointer callback;
        synchronized(listeners) {
            callback = this.listeners.remove(l);
            if (callback == null)
                return;

            this.rwlock.acquireRead();
            try {
                removeOnDataStoreContentChangedListener(this.pointer.raw, callback);
            } finally {
                this.rwlock.releaseRead();
            }
        }
    }

    @Override
    public void dispose() {
        this.rwlock.acquireWrite();
        try {
            if(this.pointer.raw != 0L)
                destruct(this.pointer);
        } finally {
            this.rwlock.releaseWrite();
        }

        synchronized(this.listeners) {
            for(Pointer callback : this.listeners.values())
                removeOnDataStoreContentChangedListener(0L, callback);
            this.listeners.clear();
        }
    }

    @Override
    public void finalize() {
        if(this.pointer.raw != 0L)
            Log.w(TAG, "Native FeatureDataStore2 leaked");
    }

    /*************************************************************************/

    static class CallbackForwarder implements NotifyCallback {
        final WeakReference<FeatureDataStore2> sourceRef;
        final OnDataStoreContentChangedListener impl;

        CallbackForwarder(FeatureDataStore2 source, OnDataStoreContentChangedListener impl) {
            this.sourceRef = new WeakReference<>(source);
            this.impl = impl;
        }


        @Override
        public boolean onEvent() {
            final FeatureDataStore2 source = this.sourceRef.get();
            if(source == null)
                return false;
            this.impl.onDataStoreContentChanged(source);
            return true;
        }
    }

    /*************************************************************************/

    static long getPointer(FeatureDataStore2 managed) {
        if(managed instanceof NativeFeatureDataStore2)
            return ((NativeFeatureDataStore2)managed).pointer.raw;
        else
            return 0L;
    }
    static FeatureDataStore2 create(Pointer pointer, Object ownerRef) {
        return new NativeFeatureDataStore2(pointer);
    }
    static native void destruct(Pointer pointer);

    static native Pointer FeatureQueryParameters_create();
    static native void FeatureQueryParameters_destruct(Pointer pointer);
    static native void FeatureQueryParameters_setFeatureSetFilter(long pointer, long[] ids,
            String[] names,
            String[] types,
            String[] providers,
            double minResolution,
            double maxResolution,
            boolean visibleOnly,
            int limit,
            int offset);

    /**
     * <P>NOTE: the feature set filter should be configured via
     * {@link #FeatureQueryParameters_setFeatureSetFilter(long, long[], String[], String[], String[], double, double, boolean, int, int)};
     *
     * @param pointer
     */
    static native void FeatureQueryParameters_set(long pointer,
                                                  long[] ids,
                                                  String[] names,
                                                  int[] geometryTypes,
                                                  //public Set<FeatureQueryParameters.AttributeFilter> attributeFilters;
                                                  boolean visibleOnly,
                                                  long spatialFilterPtr,
                                                  long minimumTimestamp,
                                                  long maximumTimestamp,
                                                  int ignoredFeatureProperties,
                                                  int numSpatialOps,
                                                  int[] spatialOpTypes,
                                                  double[] spatialOpArgs,
                                                  int numOrders,
                                                  int[] orderTypes,
                                                  double[] orderArgs,
                                                  int limit,
                                                  int offset,
                                                  long timeout);

    static void FeatureQueryParameters_adapt(FeatureQueryParameters mparams, long cparams) {
        if(mparams.featureSetFilter != null) {
            long[] fsids = null;
            if(mparams.featureSetFilter.ids != null) {
                fsids = new long[mparams.featureSetFilter.ids.size()];
                int idx = 0;
                for(Long fsid : mparams.featureSetFilter.ids)
                    fsids[idx++] = fsid.longValue();
            }
            String[] providers = null;
            if(mparams.featureSetFilter.providers != null)
                providers = mparams.featureSetFilter.providers.toArray(new String[0]);
            String[] types = null;
            if(mparams.featureSetFilter.types != null)
                types = mparams.featureSetFilter.types.toArray(new String[0]);
            String[] names = null;
            if(mparams.featureSetFilter.names != null)
                names = mparams.featureSetFilter.names.toArray(new String[0]);

            FeatureQueryParameters_setFeatureSetFilter(cparams,
                                                       fsids,
                                                       names,
                                                       types,
                                                       providers,
                                                       mparams.featureSetFilter.minResolution,
                                                       mparams.featureSetFilter.maxResolution,
                                                       mparams.featureSetFilter.visibleOnly,
                                                       mparams.featureSetFilter.limit,
                                                       mparams.featureSetFilter.offset);
        }

        // XXX - attribute filters
        mparams.attributeFilters = null;
        int[] geometryTypes = null;
        if(mparams.geometryTypes != null) {
            geometryTypes = new int[mparams.geometryTypes.size()];
            int idx = 0;
            for(Class<? extends Geometry> c : mparams.geometryTypes) {
                if(c.equals(Geometry.class)) {
                    geometryTypes = null;
                    break;
                } else {
                    geometryTypes[idx++] = Interop.getGeometryClass(c);
                }
            }
        }
        long[] ids = null;
        if(mparams.ids != null) {
            ids = new long[mparams.ids.size()];
            int idx = 0;
            for(Long fid : mparams.ids)
                ids[idx++] = fid.longValue();
        }
        int ignoredFeatureProperties = 0;
        if(mparams.ignoredFeatureProperties != 0) {
            if(MathUtils.hasBits(mparams.ignoredFeatureProperties, FeatureDataStore2.PROPERTY_FEATURE_GEOMETRY))
                ignoredFeatureProperties |= getFeatureQueryParamters_IgnoreFields_GeometryField();
            if(MathUtils.hasBits(mparams.ignoredFeatureProperties, FeatureDataStore2.PROPERTY_FEATURE_ATTRIBUTES))
                ignoredFeatureProperties |= getFeatureQueryParamters_IgnoreFields_AttributesField();
            if(MathUtils.hasBits(mparams.ignoredFeatureProperties, FeatureDataStore2.PROPERTY_FEATURE_NAME))
                ignoredFeatureProperties |= getFeatureQueryParamters_IgnoreFields_NameField();
            if(MathUtils.hasBits(mparams.ignoredFeatureProperties, FeatureDataStore2.PROPERTY_FEATURE_STYLE))
                ignoredFeatureProperties |= getFeatureQueryParamters_IgnoreFields_StyleField();
        }
        long minimumTimestamp = getTIMESTAMP_NONE();
        if(mparams.minimumTimestamp != FeatureDataStore2.TIMESTAMP_NONE)
            minimumTimestamp = mparams.minimumTimestamp;
        long maximimTimestamp = getTIMESTAMP_NONE();
        if(mparams.maximumTimestamp != FeatureDataStore2.TIMESTAMP_NONE)
            maximimTimestamp = mparams.maximumTimestamp;
        String[] names = null;
        if(mparams.names != null) {
            names = new String[mparams.names.size()];
            int idx = 0;
            for(String name : mparams.names)
                names[idx++] = name;
        }
        long spatialFilter = 0L;
        if(mparams.spatialFilter != null)
            spatialFilter = Interop.getPointer(mparams.spatialFilter).raw;
        int numSpatialOps = 0;
        int[] spatialOpTypes = null;
        double[] spatialOpArgs = null;
        if(mparams.spatialOps != null) {
            spatialOpTypes = new int[mparams.spatialOps.size()];
            spatialOpArgs = new double[mparams.spatialOps.size()];
            for(FeatureQueryParameters.SpatialOp op : mparams.spatialOps) {
                if(op instanceof FeatureQueryParameters.SpatialOp.Simplify) {
                    spatialOpTypes[numSpatialOps] = getFeatureQueryParamters_SpatialOp_Type_Simplify();
                    spatialOpArgs[numSpatialOps] = ((FeatureQueryParameters.SpatialOp.Simplify)op).distance;
                    numSpatialOps++;
                } else if(op instanceof FeatureQueryParameters.SpatialOp.Buffer) {
                    spatialOpTypes[numSpatialOps] = getFeatureQueryParamters_SpatialOp_Type_Buffer();
                    spatialOpArgs[numSpatialOps] = ((FeatureQueryParameters.SpatialOp.Buffer)op).distance;
                    numSpatialOps++;
                } else {
                    Log.w("NativeFeatureDataStore2", "Unrecognized spatial op: " + op);
                }
            }
        }
        int numOrders = 0;
        int[] orderTypes = null;
        double[] orderArgs = null;
        if(mparams.order != null) {
            final int maxArgsPerOrder = 3;
            orderTypes = new int[mparams.order.size()];
            orderArgs = new double[mparams.order.size()*maxArgsPerOrder];
            int argIdx = 0;
            for(FeatureQueryParameters.Order order : mparams.order) {
                if(order instanceof FeatureQueryParameters.Order.ID) {
                    orderTypes[numOrders++] = getFeatureQueryParamters_Order_Type_FeatureId();
                } else if(order instanceof FeatureQueryParameters.Order.Distance) {
                    orderTypes[numOrders++] = getFeatureQueryParamters_Order_Type_Distance();
                    FeatureQueryParameters.Order.Distance distance = (FeatureQueryParameters.Order.Distance)order;
                    orderArgs[argIdx++] = distance.point.getLongitude();
                    orderArgs[argIdx++] = distance.point.getLatitude();
                    orderArgs[argIdx++] = !Double.isNaN(distance.point.getAltitude()) ? EGM96.getHAE(distance.point) : 0d;
                } else if(order instanceof FeatureQueryParameters.Order.Name) {
                    orderTypes[numOrders++] = getFeatureQueryParamters_Order_Type_FeatureName();
                } else {
                    Log.w("NativeFeatureDataStore2", "Unrecognized order: " + order);
                }
            }
        }

        FeatureQueryParameters_set(cparams,
                                   ids,
                                   names,
                                   geometryTypes,
                                   mparams.visibleOnly,
                                   spatialFilter,
                                   minimumTimestamp,
                                   maximimTimestamp,
                                   ignoredFeatureProperties,
                                   numSpatialOps,
                                   spatialOpTypes,
                                   spatialOpArgs,
                                   numOrders,
                                   orderTypes,
                                   orderArgs,
                                   mparams.limit,
                                   mparams.offset,
                                   mparams.timeout);
    }

    static native Pointer FeatureSetQueryParameters_create();
    static native void FeatureSetQueryParameters_destruct(Pointer pointer);
    static native void FeatureSetQueryParameters_set(long pointer,
                                                     long[] ids,
                                                     String[] names,
                                                     String[] types,
                                                     String[] providers,
                                                     double minResolution,
                                                     double maxResolution,
                                                     boolean visibleOnly,
                                                     int limit,
                                                     int offset);

    static void FeatureSetQueryParameters_adapt(FeatureSetQueryParameters mparams, long cparams) {
        long[] fsids = null;
        if(mparams.ids != null) {
            fsids = new long[mparams.ids.size()];
            int idx = 0;
            for(Long fsid : mparams.ids)
                fsids[idx++] = fsid.longValue();
        }
        String[] providers = null;
        if(mparams.providers != null)
            providers = mparams.providers.toArray(new String[0]);
        String[] types = null;
        if(mparams.types != null)
            types = mparams.types.toArray(new String[0]);
        String[] names = null;
        if(mparams.names != null)
            names = mparams.names.toArray(new String[0]);

        FeatureSetQueryParameters_set(
                cparams,
                fsids,
                names,
                types,
                providers,
                mparams.minResolution,
                mparams.maxResolution,
                mparams.visibleOnly,
                mparams.limit,
                mparams.offset);
    }

    static native Pointer queryFeatures(long cdataStore, long cparams);
    static native int queryFeaturesCount(long cdataStore, long cparams);
    static native Pointer queryFeatureSets(long cdataStore, long cparams);
    static native int queryFeatureSetsCount(long cdataStore, long cparams);
    static native long insertFeature(long cdataStore, long fsid, long fid, String name, int cgeomCoding, Object rawGeom, int cstyleCoding, Object rawStyle, long cattrs, long timestamp, long version);
    static native long insertFeature(long cdataStore, long cfeature);
    static native long insertFeatureSet(long cdataStore, long fsid, String name, String provider, String type, double minResolution, double maxResolution, long version);
    static native void updateFeature(long pointer, long fid, int cupdatePropertyMask, String name, long geom, long style, long attrs, int cattrUpdateType);
    static native void updateFeatureSet(long pointer, long fsid, String name, double minResolution, double maxResolution);
    static native void updateFeatureSet(long pointer, long fsid, String name);
    static native void updateFeatureSet(long pointer, long fsid, double minResolution, double maxResolution);
    static native void deleteFeature(long pointer, long fid);
    static native void deleteFeatures(long cdataStore, long cparams);
    static native void deleteFeatureSet(long pointer, long fsid);
    static native void deleteFeatureSets(long cdataStore, long cparams);
    static native void setFeatureVisible(long pointer, long fid, boolean visible);
    static native void setFeaturesVisible(long pointer, long cparams, boolean visible);
    static native void setFeatureSetVisible(long pointer, long fsid, boolean visible);
    static native void setFeatureSetsVisible(long cdataStore, long cparams, boolean visible);
    static native boolean hasTimeReference(long pointer);
    static native long getMinimumTimestamp(long pointer);
    static native long getMaximumTimestamp(long pointer);
    static native String getUri(long pointer);
    static native boolean supportsExplicitIDs(long pointer);
    static native int getModificationFlags(long pointer);
    static native int getVisibilityFlags(long pointer);
    static native boolean hasCache(long pointer);
    static native void clearCache(long pointer);
    static native long getCacheSize(long pointer);
    static native void acquireModifyLock(long pointer, boolean bulkModification);
    static native void releaseModifyLock(long pointer);

    static native Pointer addOnDataStoreContentChangedListener(long pointer, NotifyCallback callback);
    static native void removeOnDataStoreContentChangedListener(long pointer, Pointer callback);

    static native int getFEATURE_ID_NONE();
    static native int getFEATURESET_ID_NONE();
    static native int getFEATURE_VERSION_NONE();
    static native int getFEATURESET_VERSION_NONE();
    static native int getVISIBILITY_SETTINGS_FEATURE();
    static native int getVISIBILITY_SETTINGS_FEATURESET();
    static native int getMODIFY_BULK_MODIFICATIONS();
    static native int getMODIFY_FEATURESET_INSERT();
    static native int getMODIFY_FEATURESET_UPDATE();
    static native int getMODIFY_FEATURESET_DELETE();
    static native int getMODIFY_FEATURESET_FEATURE_INSERT();
    static native int getMODIFY_FEATURESET_FEATURE_UPDATE();
    static native int getMODIFY_FEATURESET_FEATURE_DELETE();
    static native int getMODIFY_FEATURESET_NAME();
    static native int getMODIFY_FEATURESET_DISPLAY_THRESHOLDS();
    static native int getMODIFY_FEATURE_NAME();
    static native int getMODIFY_FEATURE_GEOMETRY();
    static native int getMODIFY_FEATURE_STYLE();
    static native int getMODIFY_FEATURE_ATTRIBUTES();
    static native long getTIMESTAMP_NONE();
    static native int getFIELD_GEOMETRY();
    static native int getFIELD_NAME();
    static native int getFIELD_ATTRIBUTES();
    static native int getFIELD_STYLE();
    static native int getUPDATE_ATTRIBUTES_ADD_OR_REPLACE();
    static native int getUPDATE_ATTRIBUTES_SET();

    static native int getFeatureQueryParamters_IgnoreFields_GeometryField();
    static native int getFeatureQueryParamters_IgnoreFields_StyleField();
    static native int getFeatureQueryParamters_IgnoreFields_AttributesField();
    static native int getFeatureQueryParamters_IgnoreFields_NameField();

    static native int getFeatureQueryParamters_Order_Type_Resolution();
    static native int getFeatureQueryParamters_Order_Type_FeatureSet();
    static native int getFeatureQueryParamters_Order_Type_FeatureName();
    static native int getFeatureQueryParamters_Order_Type_FeatureId();
    static native int getFeatureQueryParamters_Order_Type_Distance();
    static native int getFeatureQueryParamters_Order_Type_GeometryType();

    static native int getFeatureQueryParamters_SpatialOp_Type_Buffer();
    static native int getFeatureQueryParamters_SpatialOp_Type_Simplify();
}
