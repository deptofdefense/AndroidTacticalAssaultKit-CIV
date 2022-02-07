package com.atakmap.map.layer.feature;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;

import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.coremap.locale.LocaleUtil;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.DistanceCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.database.FilteredRowIterator;
import com.atakmap.database.RowIterator;
import com.atakmap.database.RowIteratorWrapper;
import com.atakmap.map.layer.feature.FeatureDataStore.FeatureQueryParameters.RadiusSpatialFilter;
import com.atakmap.map.layer.feature.FeatureDataStore.FeatureQueryParameters.RegionSpatialFilter;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.style.Style;
import com.atakmap.math.MathUtils;
import com.atakmap.math.PointD;
import com.atakmap.math.Rectangle;
import com.atakmap.util.Filter;
import com.atakmap.util.Quadtree;
import com.atakmap.util.WildCard;

/**
 * @deprecated  use {@link com.atakmap.map.layer.feature.datastore.FeatureSetDatabase} or
 *              {@link com.atakmap.map.layer.feature.datastore.FeatureSetDatabase2}; construct with
 *              <code>null</code> path/file
 */
@Deprecated
@DeprecatedApi(since = "4.4", forRemoval = true, removeAt = "4.7")
public class RuntimeFeatureDataStore extends AbstractFeatureDataStore {

    private final static Comparator<String> INDEX_KEY_STRING_COMPARATOR = new Comparator<String>() {

        @Override
        public int compare(String lhs, String rhs) {
            if(lhs == null && rhs == null)
                return 0;
            else if(lhs == null)
                return 1;
            else if(rhs == null)
                return -1;
            return lhs.compareToIgnoreCase(rhs);
        }
    };

    private final static FeatureDataStore.FeatureQueryParameters EMPTY_FEATURE_PARAMS = new FeatureDataStore.FeatureQueryParameters();
    private final static FeatureDataStore.FeatureSetQueryParameters EMPTY_FEATURESET_PARAMS = new FeatureDataStore.FeatureSetQueryParameters();

    private final static String TAG = "RuntimeFeatureDataStore";

    private Quadtree<FeatureRecord> featureSpatialIndex;
    private Map<Long, FeatureRecord> featureIdIndex;
    private Map<String, Set<FeatureRecord>> featureNameIndex;
    private Map<Class<? extends Geometry>, Set<FeatureRecord>> featureGeometryTypeIndex;
    private Map<Long, Long> featureIdToFeatureSetId;
    private Map<Long, FeatureSetRecord> featureSetIdIndex;
    private Map<String, Set<FeatureSetRecord>> featureSetNameIndex;
    
    private boolean inBulkModify;

    private long nextFeatureSetId;
    private long nextFeatureId;

    public RuntimeFeatureDataStore() {
        this(// modificationFlags
             MODIFY_BULK_MODIFICATIONS |
             MODIFY_FEATURESET_INSERT |
             MODIFY_FEATURESET_UPDATE |
             MODIFY_FEATURESET_DELETE |
             MODIFY_FEATURESET_FEATURE_INSERT |
             MODIFY_FEATURESET_FEATURE_UPDATE |
             MODIFY_FEATURESET_FEATURE_DELETE |
             MODIFY_FEATURESET_NAME |
             MODIFY_FEATURESET_DISPLAY_THRESHOLDS |
             MODIFY_FEATURE_NAME |
             MODIFY_FEATURE_GEOMETRY |
             MODIFY_FEATURE_STYLE |
             MODIFY_FEATURE_ATTRIBUTES,
             // visibility flags
             VISIBILITY_SETTINGS_FEATURE|VISIBILITY_SETTINGS_FEATURESET);
    }

    protected RuntimeFeatureDataStore(int modificationFlags, int visibilityFlags) {
        super(modificationFlags, visibilityFlags);
        
        // XXX - bother using a node limit ???
        this.featureSpatialIndex = new Quadtree<FeatureRecord>(new FeatureRecordQuadtreeFunction(), -180d, -90d, 180d, 90d);

        this.featureIdIndex = new TreeMap<>();
        this.featureNameIndex = new TreeMap<>(INDEX_KEY_STRING_COMPARATOR);
        this.featureGeometryTypeIndex = new HashMap<>();
        this.featureIdToFeatureSetId = new HashMap<>();
        this.featureSetIdIndex = new TreeMap<>();
        this.featureSetNameIndex = new TreeMap<>(INDEX_KEY_STRING_COMPARATOR);
    }

    @Override
    public synchronized Feature getFeature(long fid) {
        final FeatureRecord retval = this.featureIdIndex.get(Long.valueOf(fid));
        if(retval != null)
            return retval.feature;
        else
            return null;
    }

    @Override
    public synchronized FeatureCursor queryFeatures(FeatureQueryParameters params) {
        if(params == null)
            params = EMPTY_FEATURE_PARAMS;
        
        Collection<FeatureRecord> retval = null;
        // XXX - return collection based on sorting
        retval = new HashSet<FeatureRecord>();
        
        final int totalFeatures = this.featureIdIndex.size();
        
        // The 'recordsXXX' value is the approximate number of records that will
        // be produced by performing indexed access. By selecting the index that
        // reduces to the fewest number of records before applying intra-cursor
        // filtering, we should maximize performance.
        int recordsFeatureIds = totalFeatures;
        int recordsSet = totalFeatures;
        int recordsSpatial = totalFeatures;
        int recordsVisual = totalFeatures;
        int recordsGeometry = totalFeatures;
        int recordsNames = totalFeatures;
        
        // flags indicating which intra-cursor filters should be applied. These
        // flags will be toggled off appropriately if the corresponding index is
        // applied
        boolean applyProviders = (params.providers != null);
        boolean applyTypes = (params.types != null);
        boolean applySetIds = (params.featureSetIds != null);
        boolean applySetNames = (params.featureSets != null);
        boolean applyIds = (params.featureIds != null);
        boolean applyNames = (params.featureNames != null);
        boolean applySpatial = (params.spatialFilter != null);
        boolean applyResolution = (!Double.isNaN(params.minResolution) || !Double.isNaN(params.maxResolution));
        boolean applyVisible = params.visibleOnly;
        boolean applyGeometry = (params.geometryTypes != null);

        if(applyIds)
            recordsFeatureIds = params.featureIds.size();
        
        if(applyProviders || applyTypes || applySetIds || applySetNames || applyResolution) {
            if(params.featureSetIds != null)
                recordsSet = params.featureSetIds.size();
            else
                recordsSet = this.featureSetIdIndex.size();

            for(FeatureSetRecord record : this.featureSetIdIndex.values()) {
                if(applySetIds) {
                    if(!params.featureSetIds.contains(Long.valueOf(record.fsid)))
                        continue;
                }
                if(applyTypes) {
                    // XXX - 
                }
                if(applyProviders) {
                    // XXX - 
                }
                if(applySetNames) {
                    // XXX - 
                }
                if(applyResolution) {
                    // XXX - 
                }
                recordsSet += record.features.size();
            }
        }
        
        if(applySpatial) {
            if(params.spatialFilter instanceof RegionSpatialFilter) {
                RegionSpatialFilter region = (RegionSpatialFilter)params.spatialFilter;
                recordsSpatial = this.featureSpatialIndex.size(region.upperLeft.getLongitude(),
                                                               region.lowerRight.getLatitude(),
                                                               region.lowerRight.getLongitude(),
                                                               region.upperLeft.getLatitude());
            }
        }
        if(applyVisible) {
            if(params.featureSetIds != null)
                recordsVisual = params.featureSetIds.size();
            else
                recordsVisual = this.featureSetIdIndex.size();
            for(FeatureSetRecord record : this.featureSetIdIndex.values()) {
                if(params.featureSetIds != null && !params.featureSetIds.contains(Long.valueOf(record.fsid)))
                    continue;
                if(record.visible && record.visibilityDeviations.size() == 0)
                    recordsVisual += record.features.size();
                else if(record.visible && record.visibilityDeviations.size() > 0)
                    recordsVisual += record.features.size()-record.visibilityDeviations.size();
                else if(!record.visible && record.visibilityDeviations.size() > 0)
                    recordsVisual += record.visibilityDeviations.size();
            }
        }
        if(applyGeometry) {
            recordsGeometry = params.geometryTypes.size();
            Set<FeatureRecord> features;
            for(Class<? extends Geometry> c : params.geometryTypes) {
                features = this.featureGeometryTypeIndex.get(c);
                if(features == null)
                    continue;
                recordsGeometry += features.size();
            }
        }
        if(applyNames) {
            recordsNames = params.featureNames.size();
            Set<FeatureRecord> features;
            for(String name : params.featureNames) {
                final boolean isWildcard = (name != null) && (name.indexOf('%') >= 0);
                if(isWildcard) {
                    // wildcard query, we'll check all
                    recordsNames = featureIdIndex.size();
                    break;
                }
                features = this.featureNameIndex.get(name);
                if(features == null)
                    continue;
                recordsNames += features.size();
            }
        }

        final int minRecords = MathUtils.min(new int[] {recordsFeatureIds,
                                                        recordsSet,
                                                        recordsSpatial,
                                                        recordsVisual,
                                                        recordsGeometry,
                                                        recordsNames});
        
/*
        final String tag = "RuntimeFeatureDataStore";

        Log.d(tag, "*** RuntimeFeatureDataStore query totalFeatures=" + totalFeatures);

        Log.d(tag, "applyProviders = " + (params.providers != null));
        Log.d(tag, "applyTypes = " + (params.types != null));
        Log.d(tag, "applySetIds = " + (params.featureSetIds != null));
        Log.d(tag, "applySetNames = " + (params.featureSets != null));
        Log.d(tag, "applyIds = " + (params.featureIds != null));
        Log.d(tag, "applyNames = " + (params.featureNames != null));
        Log.d(tag, "applySpatial = " + (params.spatialFilter != null));
        Log.d(tag, "applyResolution = " + (!Double.isNaN(params.minResolution) || !Double.isNaN(params.maxResolution)));
        Log.d(tag, "applyVisible = " + params.visibleOnly);

        Log.d(tag, "recordsFeatureIds = " + recordsFeatureIds);
        Log.d(tag, "recordsSet = " + recordsSet);
        Log.d(tag, "recordsSpatial = " + recordsSpatial + ":" + this.featureSpatialIndex.size());
        Log.d(tag, "recordsVisual = " + recordsVisual);
        Log.d(tag, "recordsGeometry = " + recordsGeometry);
        Log.d(tag, "recordsNames = " + recordsNames);
*/

        // construct the result set containing the minimum number of
        // pre-filtered records using applicable indices
        if(applyIds && recordsFeatureIds == minRecords) {
            if(params.featureIds == null) {
                // brute force
                retval.addAll(this.featureIdIndex.values());
            } else {
                FeatureRecord record;
                for(Long fid : params.featureIds) {
                    record = this.featureIdIndex.get(fid);
                    if(record != null)
                        retval.add(record);
                }
            }
            applyIds = false;
        } else if(applySetIds && recordsSet == minRecords) {
            if(params.featureSetIds != null) {
                FeatureSetRecord record;
                for(Long fsid : params.featureSetIds) {
                    record = this.featureSetIdIndex.get(fsid);
                    if(record != null)
                        retval.addAll(record.features);
                }
                applySetIds = false;
            } else {
                // XXX - brute force
                retval.addAll(this.featureIdIndex.values());
            }
        } else if(applySpatial && recordsSpatial == minRecords) {
            if(params.spatialFilter instanceof RegionSpatialFilter) {
                Set<FeatureRecord> retvalSet = new HashSet<FeatureRecord>();
                RegionSpatialFilter region = (RegionSpatialFilter)params.spatialFilter;

                double maxY = region.upperLeft.getLatitude();
                double minY = region.lowerRight.getLatitude();
                double minX = region.upperLeft.getLongitude() - 360;
                double maxX = region.lowerRight.getLongitude() - 360;
                for (int i = 0; i < 3; i++) {
                    this.featureSpatialIndex.get(minX, minY, maxX, maxY,
                            retvalSet);
                    minX += 360;
                    maxX += 360;
                }
                retval.addAll(retvalSet);
                applySpatial = false;
            } else if(params.spatialFilter instanceof RadiusSpatialFilter) {
                Envelope region = radiusAsRegion(((RadiusSpatialFilter)params.spatialFilter).point,
                                                 ((RadiusSpatialFilter)params.spatialFilter).radius);
                this.featureSpatialIndex.get(region.minX,
                                             region.minY,
                                             region.maxX,
                                             region.maxY,
                                             retval);
                applySpatial = false;
            } else {
                // XXX - brute force
                retval.addAll(this.featureIdIndex.values());
            }
        } else if(applyVisible && recordsVisual == minRecords) {
            Collection<Long> fsids;
            if(params.featureSetIds != null)
                fsids = params.featureSetIds;
            else
                fsids = this.featureSetIdIndex.keySet();
            
            FeatureSetRecord record;
            for(Long fsid : fsids) {
                record = this.featureSetIdIndex.get(fsid);
                if(record == null)
                    continue;
                if(record.visible && record.visibilityDeviations.size() == 0) {
                    retval.addAll(record.features);
                } else if(record.visible && record.visibilityDeviations.size() > 0) {
                    for(FeatureRecord feature : record.features) {
                        if(!record.visibilityDeviations.contains(Long.valueOf(feature.fid)))
                            retval.add(feature);
                    }
                } else if(!record.visible && record.visibilityDeviations.size() > 0) {
                    for(Long fid : record.visibilityDeviations)
                        retval.add(this.featureIdIndex.get(fid));
                }
            }
            applyVisible = false;
        } else if(applyGeometry && recordsGeometry == minRecords) {
            Set<FeatureRecord> features;
            for(Class<? extends Geometry> c : params.geometryTypes) {
                features = this.featureGeometryTypeIndex.get(c);
                if(features == null)
                    continue;
                retval.addAll(features);
            }
        } else if(applyNames && recordsNames == minRecords) {
            applyNames = false;

            Set<FeatureRecord> features;
            for(String name : params.featureNames) {
                final boolean isWildcard = (name != null) && (name.indexOf('%') >= 0);
                if(isWildcard) {
                    // will need to brute-force using the wildcards
                    retval.addAll(this.featureIdIndex.values());
                    applyNames = true;
                    break;
                }
                features = this.featureNameIndex.get(name);
                if(features == null)
                    continue;
                retval.addAll(features);
            }
        } else {
            retval.addAll(this.featureIdIndex.values());
        }

        Collection<Filter<FeatureRecord>> filters = new LinkedList<Filter<FeatureRecord>>();
        if(applyProviders)
            filters.add(new ProviderFeatureRecordFilter(params.providers, '%'));
        if(applyTypes)
            filters.add(new TypeFeatureRecordFilter(params.types, '%'));
        if(applySetIds)
            filters.add(new SetIdFeatureRecordFilter(params.featureSetIds));
        if(applySetNames)
            filters.add(new SetNameFeatureRecordFilter(params.featureSets, '%'));
        if(applyIds)
            filters.add(new FeatureIdRecordFilter(params.featureIds));
        if(applyNames)
            filters.add(new FeatureNameRecordFilter(params.featureNames, '%'));
        if(applySpatial) {
            if(params.spatialFilter instanceof RegionSpatialFilter)
                filters.add(new RegionSpatialFeatureRecordFilter((RegionSpatialFilter)params.spatialFilter));
            if(params.spatialFilter instanceof RadiusSpatialFilter)
                filters.add(new RadiusSpatialFeatureRecordFilter((RadiusSpatialFilter)params.spatialFilter));
        }
        if(applyResolution)
            filters.add(new ResolutionFeatureRecordFilter(params.minResolution, params.maxResolution));
        if(applyVisible)
            filters.add(new VisibleOnlyFeatureRecordFilter());
        
        RecordCursor<FeatureRecord> cursor = new RecordIteratorCursor<FeatureRecord>(retval.iterator());
        if(filters.size() == 1)
            cursor = new FilteredRecordIteratorCursor<FeatureRecord>(cursor, filters.iterator().next());
        else if(filters.size() > 1)
            cursor = new FilteredRecordIteratorCursor<FeatureRecord>(cursor, new MultiRecordFilter<FeatureRecord>(filters));
        return new FeatureCursorImpl(cursor);
    }


    @Override
    public synchronized int queryFeaturesCount(FeatureQueryParameters params) {
        int retval = 0;
        FeatureCursor result = null;
        try {
            result = this.queryFeatures(params);
            while(result.moveToNext())
                retval++;
        } finally {
            if(result != null)
                result.close();
        }
        return retval;
    }

    @Override
    public synchronized FeatureSet getFeatureSet(long featureSetId) {
        FeatureSetRecord record = this.featureSetIdIndex.get(Long.valueOf(featureSetId));
        if(record != null)
            return record.featureSet;
        else
            return null;
    }

    @Override
    public synchronized FeatureSetCursor queryFeatureSets(FeatureSetQueryParameters params) {
        if(params == null)
            params = EMPTY_FEATURESET_PARAMS;

        Collection<FeatureSetRecord> retval = null;
        // XXX - return collection based on sorting
        retval = new HashSet<FeatureSetRecord>();

        
        final int totalSets = this.featureSetIdIndex.size();
        int recordsSetIds = totalSets;
        int recordsSetNames = totalSets;
        int recordsVisual = totalSets;
        
        boolean applyProviders = (params.providers != null);
        boolean applyTypes = (params.types != null);
        boolean applyIds = (params.ids != null);
        boolean applyNames = (params.names != null);
        boolean applyVisible = params.visibleOnly;
        
        if(applyIds)
            recordsSetIds = params.ids.size();
        
        if(applyNames) {
            // XXX - use name index
        }
        if(applyVisible) {
            if(params.ids != null)
                recordsVisual = params.ids.size();
            else
                recordsVisual = this.featureSetIdIndex.size();
            for(FeatureSetRecord record : this.featureSetIdIndex.values()) {
                if(params.ids != null && !params.ids.contains(Long.valueOf(record.fsid)))
                    continue;
                if(record.visible && record.visibilityDeviations.size() == 0)
                    recordsVisual += record.features.size();
                else if(record.visible && record.visibilityDeviations.size() > 0)
                    recordsVisual += record.features.size()-record.visibilityDeviations.size();
                else if(!record.visible && record.visibilityDeviations.size() > 0)
                    recordsVisual += record.visibilityDeviations.size();
            }
        }
        
        
        final int minRecords = MathUtils.min(new int[] {recordsSetIds,
                                                        recordsSetNames,
                                                        recordsVisual,
                                             });
        
        // construct the result set containing the minimum number of
        // pre-filtered records using applicable indices
        if(recordsSetIds == minRecords) {
            if(params.ids == null) {
                // brute force
                retval.addAll(this.featureSetIdIndex.values());
            } else {
                FeatureSetRecord record;
                for(Long fid : params.ids) {
                    record = this.featureSetIdIndex.get(fid);
                    if(record != null)
                        retval.add(record);
                }
            }
            applyIds = false;
        } else if(recordsSetNames == minRecords) {
            // XXX - brute force
            retval.addAll(this.featureSetIdIndex.values());
        } else if(recordsVisual == minRecords) {
            Collection<Long> fsids;
            if(params.ids != null)
                fsids = params.ids;
            else
                fsids = this.featureSetIdIndex.keySet();
            
            FeatureSetRecord record;
            for(Long fsid : fsids) {
                record = this.featureSetIdIndex.get(fsid);
                if(record == null || !record.visible)
                    continue;
                retval.add(record);
            }
            applyVisible = false;
        } else {
            // XXX - brute force
            retval.addAll(this.featureSetIdIndex.values());
        }
        
        Collection<Filter<FeatureSetRecord>> filters = new LinkedList<Filter<FeatureSetRecord>>();
        if(applyProviders)
            filters.add(new ProviderFeatureSetRecordFilter(params.providers, '%'));
        if(applyTypes)
            filters.add(new TypeFeatureSetRecordFilter(params.types, '%'));
        if(applyIds)
            filters.add(new FeatureSetIdRecordFilter(params.ids));
        if(applyNames)
            filters.add(new FeatureSetNameRecordFilter(params.names, '%'));
        if(applyVisible)
            filters.add(new VisibleOnlyFeatureSetRecordFilter());
        
        RecordCursor<FeatureSetRecord> cursor = new RecordIteratorCursor<FeatureSetRecord>(retval.iterator());
        if(filters.size() == 1)
            cursor = new FilteredRecordIteratorCursor<FeatureSetRecord>(cursor, filters.iterator().next());
        else if(filters.size() > 1)
            cursor = new FilteredRecordIteratorCursor<FeatureSetRecord>(cursor, new MultiRecordFilter<FeatureSetRecord>(filters));
        return new FeatureSetCursorImpl(cursor);
    }

    @Override
    public synchronized int queryFeatureSetsCount(FeatureSetQueryParameters params) {
        FeatureSetCursor cursor = null;
        int retval = 0;
        try {
            cursor = this.queryFeatureSets(params);
            while(cursor.moveToNext())
                retval++;
        } finally {
            if(cursor != null)
                cursor.close();
        }
        return retval;
    }

    @Override
    public boolean isInBulkModification() {
        return inBulkModify;
    }
    
    @Override
    public synchronized boolean isFeatureVisible(long fid) {
        final Long fidBoxed = Long.valueOf(fid);
        final FeatureRecord record = this.featureIdIndex.get(fidBoxed);
        if(record == null) {
            Log.w(TAG, "No such Feature ID: " + fid);
            return false;
        }
        if(record.set.visibilityDeviations.contains(fidBoxed))
            return !record.set.visible;
        else
            return record.set.visible;
    }

    @Override
    public synchronized boolean isFeatureSetVisible(long setId) {
        final FeatureSetRecord record = this.featureSetIdIndex.get(Long.valueOf(setId));
        if(record == null) {
            Log.w(TAG, "No such FeatureSet ID: " + setId);
            return false;
        }
        return (record.visible||record.visibilityDeviations.size()>0);
    }

    @Override
    public synchronized boolean isAvailable() {
        return (this.featureIdIndex != null);
    }

    @Override
    public void refresh() {}

    @Override
    public String getUri() {
        return "0x" + Integer.toString(this.hashCode(), 16);
    }

    @Override
    public synchronized void dispose() {
        // XXX - 6297 quick fix for 3.4 NPE 
        /*
        this.featureSpatialIndex = null;
        this.featureIdIndex = null;
        this.featureNameIndex = null;
        this.featureGeometryTypeIndex = null;
        this.featureIdToFeatureSetId = null;
        this.featureSetIdIndex = null;
        this.featureSetNameIndex = null;
        */
        this.featureSpatialIndex.clear();
        this.featureIdIndex.clear();
        this.featureNameIndex.clear();
        this.featureGeometryTypeIndex.clear();
        this.featureIdToFeatureSetId.clear();
        this.featureSetIdIndex.clear();
        this.featureSetNameIndex.clear();
    }

    private void setFeatureVisibleImpl(FeatureSetRecord set, long fid, boolean visible) {
        final Long fidBoxed = Long.valueOf(fid);

        if(set.visible == visible && set.visibilityDeviations.contains(fidBoxed)) {
            // feature is no longer deviant
            set.visibilityDeviations.remove(fidBoxed);
        } else if(set.visible != visible) {
            // mark this feature as deviant
            set.visibilityDeviations.add(fidBoxed);

            // if all features' visibility deviate from the set, reset the set's
            // visibility
            if(set.visibilityDeviations.size() == set.features.size()) {
                set.visibilityDeviations.clear();
                set.visible = visible;
            }
        }
        // else: requested visibility matches visibility of set
    }
    
    @Override
    protected synchronized void setFeatureVisibleImpl(long fid, boolean visible) {
        final Long fidBoxed = Long.valueOf(fid);
        final Long fsid = this.featureIdToFeatureSetId.get(fidBoxed);
        if(fsid == null) {
            Log.e(TAG, "No such Feature ID: " + fid);
            return;
        }
        FeatureSetRecord record = this.featureSetIdIndex.get(fsid);
        
        this.setFeatureVisibleImpl(record, fid, visible);
        
        this.dispatchDataStoreContentChangedNoSync();
    }
    
    @Override
    protected synchronized void setFeaturesVisibleImpl(FeatureQueryParameters params, boolean visible) {
        // XXX - more efficient impl
        FeatureCursorImpl result = null;
        try {
            result = (FeatureCursorImpl)this.queryFeatures(params);
            long fid;
            FeatureSetRecord set;
            while(result.moveToNext()) {
                fid = result.get().getId();
                set = this.featureIdIndex.get(Long.valueOf(fid)).set;
                this.setFeatureVisibleImpl(set, fid, visible);
            }
            this.dispatchDataStoreContentChangedNoSync();
        } finally {
            if(result != null)
                result.close();
        }
    }

    @Override
    protected synchronized void setFeatureSetVisibleImpl(long setId, boolean visible) {
        FeatureSetRecord record = this.featureSetIdIndex.get(Long.valueOf(setId));
        if(record == null) {
            Log.e(TAG, "No such FeatureSet ID: " + setId);
            return;
        }
        record.visible = visible;
        record.visibilityDeviations.clear();
        
        this.dispatchDataStoreContentChangedNoSync();
    }
    
    @Override
    protected synchronized void setFeatureSetsVisibleImpl(FeatureSetQueryParameters params, boolean visible) {
        // XXX - more efficient impl
        
        FeatureSetCursor result = null;
        try {
            result = this.queryFeatureSets(params);
            FeatureSetRecord record;
            while(result.moveToNext()) {
                final long fsid = result.get().getId();

                record = this.featureSetIdIndex.get(Long.valueOf(fsid));
                if(record == null) {
                    Log.e(TAG, "No such FeatureSet ID: " + fsid);
                    return;
                }
                record.visible = visible;
                record.visibilityDeviations.clear();
            }
            
            this.dispatchDataStoreContentChangedNoSync();
        } finally {
            if(result != null)
                result.close();
        }
    }
    
    @Override
    protected synchronized void beginBulkModificationImpl() {
        this.inBulkModify = true;
    }

    @Override
    protected void endBulkModificationImpl(boolean successful) {
        // XXX - rollback on unsuccessful

        this.inBulkModify = false;
        
        if(successful)
            this.dispatchDataStoreContentChangedNoSync();
    }
    
    @Override
    protected void dispatchDataStoreContentChangedNoSync() {
        if(!this.inBulkModify)
            super.dispatchDataStoreContentChangedNoSync();
    }

    @Override
    protected FeatureSet insertFeatureSetImpl(String provider, String type, String name,
            double minResolution, double maxResolution, boolean returnRef) {
        
        return this.insertFeatureSetImpl(FEATURESET_ID_NONE, provider, type, name, minResolution, maxResolution, returnRef);
    }

    protected synchronized FeatureSet insertFeatureSetImpl(long fsid, String provider, String type, String name,
            double minResolution, double maxResolution, boolean returnRef) {
        FeatureSet retval = new FeatureSet(provider, type, name, minResolution, maxResolution);
        if(fsid == FEATURESET_ID_NONE)
            fsid = ++this.nextFeatureSetId;
        FeatureSetRecord record = new FeatureSetRecord(fsid, retval);
        this.adopt(retval, record.fsid, record.version);
        
        this.featureSetIdIndex.put(Long.valueOf(record.fsid), record);
        
        Set<FeatureSetRecord> sets = this.featureSetNameIndex.get(name);
        if(sets == null)
            this.featureSetNameIndex.put(name, sets=new HashSet<FeatureSetRecord>());
        sets.add(record);
        
        this.dispatchDataStoreContentChangedNoSync();
        
        if(returnRef)
            return retval;
        else
            return null;
    }

    @Override
    protected synchronized void updateFeatureSetImpl(long fsid, String name) {
        FeatureSetRecord record = this.featureSetIdIndex.get(Long.valueOf(fsid));
        if(record == null)
            throw new IllegalArgumentException("No such Feature Set");
        
        this.updateRecordNoSync(fsid,
                                record,
                                name,
                                record.featureSet.getMinResolution(),
                                record.featureSet.getMaxResolution());
    }

    @Override
    protected synchronized void updateFeatureSetImpl(long fsid, double minResolution, double maxResolution) {
        FeatureSetRecord record = this.featureSetIdIndex.get(Long.valueOf(fsid));
        if(record == null)
            throw new IllegalArgumentException("No such Feature Set");
        
        this.updateRecordNoSync(fsid,
                                record,
                                record.featureSet.getName(),
                                minResolution,
                                maxResolution);
    }

    @Override
    protected synchronized void updateFeatureSetImpl(long fsid, String name, double minResolution,
            double maxResolution) {
        
        FeatureSetRecord record = this.featureSetIdIndex.get(Long.valueOf(fsid));
        if(record == null)
            throw new IllegalArgumentException("No such Feature Set");
        
        this.updateRecordNoSync(fsid,
                                record,
                                name,
                                minResolution,
                                maxResolution);
    }
    
    private void updateRecordNoSync(long fsid, FeatureSetRecord record, String name, double minResolution, double maxResolution) {
        final boolean updateNameIndex = INDEX_KEY_STRING_COMPARATOR.compare(name, record.featureSet.getName()) != 0;
        final boolean updateResolutionIndex = !equal(minResolution, record.featureSet.getMinResolution()) || !equal(maxResolution, record.featureSet.getMaxResolution());
        
        
        // remove any invalid index entries
        if(updateNameIndex) {
            Set<FeatureSetRecord> sets = this.featureSetNameIndex.get(record.featureSet.getName());
            if(sets.size() == 1)
                this.featureSetNameIndex.remove(record.featureSet.getName());
            else
                sets.remove(record);
        }
        
        // update the FeatureSet record
        record.featureSet = new FeatureSet(record.featureSet.getProvider(),
                                           record.featureSet.getType(),
                                           name,
                                           minResolution,
                                           maxResolution);
        record.version++;
        this.adopt(record.featureSet, fsid, record.version);
        
        // update any invalid index entries
        if(updateNameIndex) {
            Set<FeatureSetRecord> sets = this.featureSetNameIndex.get(name);
            if(sets == null)
                this.featureSetNameIndex.put(name, sets=new HashSet<FeatureSetRecord>());
            sets.add(record);
        }
        
        this.dispatchDataStoreContentChangedNoSync();
    }

    @Override
    protected synchronized void deleteFeatureSetImpl(long fsid) {
        // XXX - probably could do something more efficient
        final boolean wasInBulk = this.inBulkModify;
        this.inBulkModify = true;
        try {
            this.deleteAllFeaturesImpl(fsid);
            final FeatureSetRecord record = this.featureSetIdIndex.remove(Long.valueOf(fsid));
            Set<FeatureSetRecord> sets = this.featureSetNameIndex.get(record.featureSet.getName());
            sets.remove(record);
            if(sets.size() == 0)
                this.featureSetNameIndex.remove(record.featureSet.getName());
        } finally {
            this.inBulkModify = wasInBulk;
        }
        this.dispatchDataStoreContentChangedNoSync();
    }

    @Override
    protected synchronized void deleteAllFeatureSetsImpl() {
        this.featureSpatialIndex.clear();
        this.featureIdIndex.clear();
        this.featureNameIndex.clear();
        this.featureGeometryTypeIndex.clear();
        this.featureIdToFeatureSetId.clear();
        this.featureSetIdIndex.clear();
        this.featureSetNameIndex.clear(); 
        
        this.dispatchDataStoreContentChangedNoSync();
    }

    @Override
    protected Feature insertFeatureImpl(long fsid, String name, Geometry geom, Style style,
            AttributeSet attributes, boolean returnRef) {
        
        return this.insertFeatureImpl(fsid, FEATURE_ID_NONE, name, geom, style, attributes, returnRef);
    }

    protected synchronized Feature insertFeatureImpl(long fsid, long fid, String name, Geometry geom, Style style,
            AttributeSet attributes, boolean returnRef) {
        
        FeatureSetRecord set = this.featureSetIdIndex.get(Long.valueOf(fsid));
        if(set == null)
            throw new IllegalArgumentException("No such FeatureSet");
        
        if(fid == FEATURE_ID_NONE)
            fid = ++this.nextFeatureId;

        Feature retval = new Feature(fsid, fid, name, geom, style, attributes, FeatureDataStore2.TIMESTAMP_NONE, 1L);
        FeatureRecord record = new FeatureRecord(set, fid, retval);
        
        this.featureIdIndex.put(Long.valueOf(record.fid), record);
        
        Set<FeatureRecord> features;
        
        features = this.featureNameIndex.get(name);
        if(features == null)
            this.featureNameIndex.put(name, features=new HashSet<FeatureRecord>());
        features.add(record);

        features = this.featureGeometryTypeIndex.get(record.feature.getGeometry().getClass());
        if(features == null)
            this.featureGeometryTypeIndex.put(record.feature.getGeometry().getClass(), features=new HashSet<FeatureRecord>());
        features.add(record);

        this.featureSpatialIndex.add(record);
        this.featureIdToFeatureSetId.put(Long.valueOf(record.fid), Long.valueOf(fsid));
        record.set.features.add(record);

        this.dispatchDataStoreContentChangedNoSync();
        
        if(returnRef)
            return retval;
        else
            return null;
    }

    @Override
    protected void updateFeatureImpl(long fid, String name) {
        FeatureRecord record = this.featureIdIndex.get(Long.valueOf(fid));
        if(record == null)
            throw new IllegalArgumentException("No such Feature ID");
        
        this.updateRecordNoSync(fid, 
                                record,
                                name,
                                record.feature.getGeometry(),
                                record.feature.getStyle(),
                                record.feature.getAttributes());
    }

    @Override
    protected void updateFeatureImpl(long fid, Geometry geom) {
        FeatureRecord record = this.featureIdIndex.get(Long.valueOf(fid));
        if(record == null)
            throw new IllegalArgumentException("No such Feature ID");
        
        this.updateRecordNoSync(fid, 
                                record,
                                record.feature.getName(),
                                geom,
                                record.feature.getStyle(),
                                record.feature.getAttributes());
    }

    @Override
    protected void updateFeatureImpl(long fid, Style style) {
        FeatureRecord record = this.featureIdIndex.get(Long.valueOf(fid));
        if(record == null)
            throw new IllegalArgumentException("No such Feature ID");
        
        this.updateRecordNoSync(fid, 
                                record,
                                record.feature.getName(),
                                record.feature.getGeometry(),
                                style,
                                record.feature.getAttributes());
    }

    @Override
    protected void updateFeatureImpl(long fid, AttributeSet attributes) {
        FeatureRecord record = this.featureIdIndex.get(Long.valueOf(fid));
        if(record == null)
            throw new IllegalArgumentException("No such Feature ID");
        
        this.updateRecordNoSync(fid, 
                                record,
                                record.feature.getName(),
                                record.feature.getGeometry(),
                                record.feature.getStyle(),
                                attributes);
    }

    @Override
    protected synchronized void updateFeatureImpl(long fid, String name, Geometry geom, Style style,
            AttributeSet attributes) {

        FeatureRecord record = this.featureIdIndex.get(Long.valueOf(fid));
        if(record == null)
            throw new IllegalArgumentException("No such Feature ID");
        
        this.updateRecordNoSync(fid, record, name, geom, style, attributes);
    }

    protected void updateRecordNoSync(long fid, FeatureRecord record, String name, Geometry geom, Style style,
            AttributeSet attributes)  {
        
        //final Long fidBoxed = Long.valueOf(fid);
        final boolean updateNameIndex = INDEX_KEY_STRING_COMPARATOR.compare(name, record.feature.getName()) != 0;
        final boolean updateGeometryTypeIndex = !geom.getClass().equals(record.feature.getGeometry().getClass());

        // remove any invalid index entries
        if(updateNameIndex) {
            Set<FeatureRecord> features = this.featureNameIndex.get(record.feature.getName());
            if(features.size() == 1)
                this.featureNameIndex.remove(record.feature.getName());
            else
                features.remove(record);
        }
        if(updateGeometryTypeIndex) {
            Set<FeatureRecord> features = this.featureGeometryTypeIndex.get(record.feature.getGeometry().getClass());
            if(features.size() == 1)
                this.featureGeometryTypeIndex.remove(record.feature.getGeometry().getClass());
            else
                features.remove(record);
        }

        record.version++;
        record.feature = new Feature(record.set.fsid, fid, name, geom, style, attributes, FeatureDataStore2.TIMESTAMP_NONE, record.version);

        // update indices
        
        // update any invalid index entries
        if(updateNameIndex) {
            Set<FeatureRecord> features = this.featureNameIndex.get(name);
            if(features == null)
                this.featureNameIndex.put(name, features=new HashSet<FeatureRecord>());
            features.add(record);
        }
        if(updateGeometryTypeIndex) {
            Set<FeatureRecord> features = this.featureGeometryTypeIndex.get(geom.getClass());
            if(features == null)
                this.featureGeometryTypeIndex.put(geom.getClass(), features=new HashSet<FeatureRecord>());
            features.add(record);
        }
        this.featureSpatialIndex.refresh(record);
        
        this.dispatchDataStoreContentChangedNoSync();
    }

    @Override
    protected synchronized void deleteFeatureImpl(long fid) {
        this.deleteFeatureImplNoSync(fid, true);
    }
    
    private void deleteFeatureImplNoSync(long fid, boolean removeFromSet) {
        final FeatureRecord record = this.featureIdIndex.remove(Long.valueOf(fid));
        if(record == null) {
            Log.w(TAG, "deleteFeatureImplNoSync - No such feature: " + fid);
            return;
        }
        
        Set<FeatureRecord> features;
        
        this.featureSpatialIndex.remove(record);
        final String featureName = record.feature.getName();
        features = this.featureNameIndex.get(featureName);
        if(features == null) {
            Log.w(TAG, "deleteFeatureImplNoSync - No such features set: " + 
                  featureName);
            return;
        }
        
        
        features.remove(record);
        if(features.size() < 1)
            this.featureNameIndex.remove(record.feature.getName());
        features = this.featureGeometryTypeIndex.get(record.feature.getGeometry().getClass());
        features.remove(record);
        if(features.size() < 1)
            this.featureGeometryTypeIndex.remove(record.feature.getGeometry().getClass());
        this.featureIdToFeatureSetId.remove(Long.valueOf(fid));
        
        if(removeFromSet)
            record.set.features.remove(record);
        
        this.dispatchDataStoreContentChangedNoSync();
    }

    @Override
    protected synchronized void deleteAllFeaturesImpl(long fsid) {
        final boolean wasInBulk = this.inBulkModify;
        this.inBulkModify = true;
        try {
            final FeatureSetRecord record = this.featureSetIdIndex.get(Long.valueOf(fsid));
            if(record == null) {
                Log.w(TAG, "deleteAllFeaturesImpl - No such Feature Set: " + fsid);
                return;
            }
            for(FeatureRecord feature : record.features)
                this.deleteFeatureImplNoSync(feature.fid, false);
            record.features.clear();
            record.visibilityDeviations.clear();
        } finally {
            this.inBulkModify = wasInBulk;
        }
        this.dispatchDataStoreContentChangedNoSync();
        
    }
    
    /**************************************************************************/
    
    private static boolean equal(double a, double b) {
        return (a == b) || (Double.isNaN(a) && Double.isNaN(b));
    }

    
    private static Envelope radiusAsRegion(GeoPoint center, double radius) {
        final GeoPoint north = DistanceCalculations.metersFromAtBearing(center, radius, 0.0d);
        final GeoPoint east = DistanceCalculations.metersFromAtBearing(center, radius, 90.0d);
        final GeoPoint south = DistanceCalculations.metersFromAtBearing(center, radius, 180.0d);
        final GeoPoint west = DistanceCalculations.metersFromAtBearing(center, radius, 270.0d);
        
        return new Envelope(MathUtils.min(north.getLongitude(), east.getLongitude(), south.getLongitude(), west.getLongitude()),
                            MathUtils.min(north.getLatitude(), east.getLatitude(), south.getLatitude(), west.getLatitude()),
                            0.0d,
                            MathUtils.max(north.getLongitude(), east.getLongitude(), south.getLongitude(), west.getLongitude()),
                            MathUtils.max(north.getLatitude(), east.getLatitude(), south.getLatitude(), west.getLatitude()),
                            0.0d);
    }

    /**************************************************************************/

    protected static class FeatureRecord {
        public Feature feature;
        public final long fid;
        public long version;
        public FeatureSetRecord set;
        

        public FeatureRecord(FeatureSetRecord set, long fid, Feature feature) {
            this.set = set;
            this.fid = fid;
            this.feature = feature;
            this.version = 1L;
        }
    }
    
    protected static class FeatureSetRecord {
        public FeatureSet featureSet;
        public final long fsid;
        public boolean visible;
        public Set<Long> visibilityDeviations;
        public Set<FeatureRecord> features;
        public long version;
        
        public FeatureSetRecord(long fsid, FeatureSet featureSet) {
            this.fsid = fsid;
            this.featureSet = featureSet;
            this.visible = true;
            this.visibilityDeviations = new HashSet<Long>();
            this.features = new HashSet<FeatureRecord>();
            this.version = 1L;
        }
    }

    private static class FeatureRecordQuadtreeFunction implements Quadtree.Function<FeatureRecord> {
        @Override
        public void getBounds(FeatureRecord object, PointD min, PointD max) {
            final Envelope mbb = object.feature.getGeometry().getEnvelope();
            min.x = mbb.minX;
            min.y = mbb.minY;
            max.x = mbb.maxX;
            max.y = mbb.maxY;
        }
    }
    
    /**************************************************************************/
    // Feature Record Filters
    
    private static abstract class IdRecordFilter<T> implements Filter<T> {
        private Collection<Long> ids;
        
        protected IdRecordFilter(Collection<Long> ids) {
            this.ids = ids;
        }
        
        @Override
        public boolean accept(T record) {
            final Long id = this.getId(record);
            return this.ids.contains(id);
        }
        
        protected abstract Long getId(T record);
    }

    private static abstract class WildcardsRecordFilter<T> implements Filter<T> {
        private final boolean valueIsLowercase;
        private Collection<String> startsWith;
        private Collection<String> endsWith;
        private Set<String> literal;
        private Collection<String> regex;
        
        protected WildcardsRecordFilter(Collection<String> args, char wildcardChar, boolean valueIsLowercase) {
            this.valueIsLowercase = valueIsLowercase;

            this.startsWith = new LinkedList<String>();
            this.endsWith = new LinkedList<String>();
            this.literal = new HashSet<String>();
            this.regex = new LinkedList<String>();
            
            for(String arg : args) {
                if(arg == null) {
                    this.literal.add(arg);
                    continue;
                }

                final int firstIdx = arg.indexOf(wildcardChar);
                final boolean singleWildcard = (arg.lastIndexOf(wildcardChar) != firstIdx);
                final boolean startsWith = singleWildcard && (firstIdx == 0);
                final boolean endsWith = singleWildcard && (firstIdx == arg.length()-1);

                if(firstIdx < 0) {
                    this.literal.add(arg.toLowerCase(LocaleUtil.getCurrent()));
                } else if(startsWith) {
                    this.startsWith.add(arg.toLowerCase(LocaleUtil.getCurrent()).substring(1));
                } else if(endsWith) {
                    this.endsWith.add(arg.toLowerCase(LocaleUtil.getCurrent()).substring(0, arg.length()-1));
                } else {
                    this.regex.add(WildCard.wildcardAsRegex(arg.toLowerCase(LocaleUtil.getCurrent())));
                }
            }
        }
        
        protected abstract String getValue(T record);

        @Override
        public boolean accept(T record) {
            String value = this.getValue(record);
            if(value != null && !this.valueIsLowercase)
                value = value.toLowerCase(LocaleUtil.getCurrent());
            
            if(value == null)
                return false;
            else if(this.literal.contains(value))
                return true;

            for(String s : this.startsWith)
                if(value.startsWith(s))
                    return true;
            for(String s : this.endsWith)
                if(value.endsWith(s))
                    return true;
            for(String s : this.regex)
                if(value.matches(s))
                    return true;
            return false;
        }
    }
    
    private static class MultiRecordFilter<T> implements Filter<T> {
        private Collection<Filter<T>> filters;
        
        public MultiRecordFilter(Collection<Filter<T>> filters) {
            this.filters = filters;
        }
        
        @Override
        public boolean accept(T record) {
            for(Filter<T> filter : this.filters)
                if(!filter.accept(record))
                    return false;
            return true;
        }
    }
    
    /**************************************************************************/
    // Feature Record Filters

    private class FeatureIdRecordFilter extends IdRecordFilter<FeatureRecord> {
        public FeatureIdRecordFilter(Collection<Long> ids) {
            super(ids);
        }

        @Override
        protected Long getId(FeatureRecord record) {
            return Long.valueOf(record.fid);
        }
    }
    
    private class FeatureNameRecordFilter extends WildcardsRecordFilter<FeatureRecord> {

        public FeatureNameRecordFilter(Collection<String> args, char wildcardChar) {
            super(args, wildcardChar, false);
        }

        @Override
        protected String getValue(FeatureRecord record) {
            // ATAK-12109 if the record.feature is null, just return null which is handled 
            // higher up for example in WildcardsRecordFilter
            if (record.feature == null)
                 return null;

            return record.feature.getName();
        }
    }
    
    private static class VisibleOnlyFeatureRecordFilter implements Filter<FeatureRecord> {
        @Override
        public boolean accept(FeatureRecord record) {
            if(record.set.visibilityDeviations.contains(Long.valueOf(record.fid)))
                return !record.set.visible;
            else
                return record.set.visible;
        }
    }
    
    private static class RegionSpatialFeatureRecordFilter implements Filter<FeatureRecord> {
        private final Envelope roi;

        public RegionSpatialFeatureRecordFilter(RegionSpatialFilter filter) {
            this.roi = new Envelope(filter.upperLeft.getLongitude(),
                                    filter.lowerRight.getLatitude(),
                                    0.0d,
                                    filter.lowerRight.getLongitude(),
                                    filter.upperLeft.getLatitude(),
                                    0.0d);
        }

        @Override
        public boolean accept(FeatureRecord record) {
            final Envelope featureMbb = record.feature.getGeometry().getEnvelope();
            
            return Rectangle.intersects(this.roi.minX, this.roi.minY,
                                        this.roi.maxX, this.roi.maxY,
                                        featureMbb.minX, featureMbb.minY,
                                        featureMbb.maxX, featureMbb.maxY);
        }
    }
    
    private static class RadiusSpatialFeatureRecordFilter implements Filter<FeatureRecord> {
        private final Envelope roi;

        public RadiusSpatialFeatureRecordFilter(RadiusSpatialFilter filter) {
            this.roi = radiusAsRegion(filter.point, filter.radius);
        }

        @Override
        public boolean accept(FeatureRecord record) {
            final Envelope featureMbb = record.feature.getGeometry().getEnvelope();
            
            return Rectangle.intersects(this.roi.minX, this.roi.minY,
                                        this.roi.maxX, this.roi.maxY,
                                        featureMbb.minX, featureMbb.minY,
                                        featureMbb.maxX, featureMbb.maxY);
        }
    }
    
    private class ProviderFeatureRecordFilter extends WildcardsRecordFilter<FeatureRecord> {

        public ProviderFeatureRecordFilter(Collection<String> providers, char wildcardChar) {
            super(providers, wildcardChar, false);
        }

        @Override
        protected String getValue(FeatureRecord record) {
            return record.set.featureSet.getProvider();
        }
    }
    
    private class TypeFeatureRecordFilter extends WildcardsRecordFilter<FeatureRecord> {

        public TypeFeatureRecordFilter(Collection<String> providers, char wildcardChar) {
            super(providers, wildcardChar, false);
        }

        @Override
        protected String getValue(FeatureRecord record) {
            return record.set.featureSet.getType();
        }
    }
    
    private static class SetIdFeatureRecordFilter implements Filter<FeatureRecord> {
        private final Collection<Long> ids;
        
        public SetIdFeatureRecordFilter(Collection<Long> ids) {
            this.ids = ids;
        }

        @Override
        public boolean accept(FeatureRecord record) {
            return this.ids.contains(Long.valueOf(record.set.fsid));
        }
    }
    
    private class SetNameFeatureRecordFilter extends WildcardsRecordFilter<FeatureRecord> {

        public SetNameFeatureRecordFilter(Collection<String> featureSets, char wildcardChar) {
            super(featureSets, wildcardChar, false);
        }

        @Override
        protected String getValue(FeatureRecord record) {
            return record.set.featureSet.getName();
        }
    }
    
    
    
    private static class ResolutionFeatureRecordFilter implements Filter<FeatureRecord> {
        private final double minResolution;
        private final double maxResolution;
        
        public ResolutionFeatureRecordFilter(double minResolution, double maxResolution) {
            this.minResolution = minResolution;
            this.maxResolution = maxResolution;
        }
        
        @Override
        public boolean accept(FeatureRecord record) {
            if(!Double.isNaN(this.minResolution) && (record.set.featureSet.getMaxResolution() > this.maxResolution))
                return false;
            if(!Double.isNaN(this.maxResolution) && (record.set.featureSet.getMinResolution() < this.maxResolution))
                return false;
            return true;
        }
    }

    /**************************************************************************/
    // Feature Record Filters

    private class FeatureSetIdRecordFilter extends IdRecordFilter<FeatureSetRecord> {
        public FeatureSetIdRecordFilter(Collection<Long> ids) {
            super(ids);
        }

        @Override
        protected Long getId(FeatureSetRecord record) {
            return Long.valueOf(record.fsid);
        }
    }
    
    private class FeatureSetNameRecordFilter extends WildcardsRecordFilter<FeatureSetRecord> {

        public FeatureSetNameRecordFilter(Collection<String> args, char wildcardChar) {
            super(args, wildcardChar, false);
        }

        @Override
        protected String getValue(FeatureSetRecord record) {
            return record.featureSet.getName();
        }
    }
    
    private static class VisibleOnlyFeatureSetRecordFilter implements Filter<FeatureSetRecord> {
        @Override
        public boolean accept(FeatureSetRecord record) {
            return record.visible;
        }
    }
    
    private class ProviderFeatureSetRecordFilter extends WildcardsRecordFilter<FeatureSetRecord> {

        public ProviderFeatureSetRecordFilter(Collection<String> providers, char wildcardChar) {
            super(providers, wildcardChar, false);
        }

        @Override
        protected String getValue(FeatureSetRecord record) {
            return record.featureSet.getProvider();
        }
    }
    
    private class TypeFeatureSetRecordFilter extends WildcardsRecordFilter<FeatureSetRecord> {

        public TypeFeatureSetRecordFilter(Collection<String> providers, char wildcardChar) {
            super(providers, wildcardChar, false);
        }

        @Override
        protected String getValue(FeatureSetRecord record) {
            return record.featureSet.getType();
        }
    }
    
    private static class ResolutionFeatureSetRecordFilter implements Filter<FeatureSetRecord> {
        private final double minResolution;
        private final double maxResolution;
        
        public ResolutionFeatureSetRecordFilter(double minResolution, double maxResolution) {
            this.minResolution = minResolution;
            this.maxResolution = maxResolution;
        }
        
        @Override
        public boolean accept(FeatureSetRecord record) {
            if(!Double.isNaN(this.minResolution) && (record.featureSet.getMaxResolution() > this.maxResolution))
                return false;
            if(!Double.isNaN(this.maxResolution) && (record.featureSet.getMinResolution() < this.maxResolution))
                return false;
            return true;
        }
    }
    
    /**************************************************************************/
    //
    
    protected final static class FeatureCursorImpl extends RowIteratorWrapper implements FeatureCursor {

        public FeatureCursorImpl(RecordCursor<FeatureRecord> impl) {
            super(impl);
        }
        
        @Override
        public Feature get() {
            return ((RecordCursor<FeatureRecord>)this.filter).get().feature;
        }

        @Override
        public Object getRawGeometry() {
            return ((RecordCursor<FeatureRecord>)this.filter).get().feature.getGeometry();
        }

        @Override
        public int getGeomCoding() {
            return GEOM_ATAK_GEOMETRY;
        }

        @Override
        public String getName() {
            return ((RecordCursor<FeatureRecord>)this.filter).get().feature.getName();

        }

        @Override
        public int getStyleCoding() {
            return STYLE_ATAK_STYLE;
        }

        @Override
        public Object getRawStyle() {
            return ((RecordCursor<FeatureRecord>)this.filter).get().feature.getStyle();
        }

        @Override
        public AttributeSet getAttributes() {
            return ((RecordCursor<FeatureRecord>)this.filter).get().feature.getAttributes();
        }

        @Override
        public long getId() {
            return ((RecordCursor<FeatureRecord>)this.filter).get().feature.getId();
        }

        @Override
        public long getVersion() {
            return ((RecordCursor<FeatureRecord>)this.filter).get().feature.getVersion();
        }
        
        @Override
        public long getFsid() {
            return ((RecordCursor<FeatureRecord>)this.filter).get().feature.getFeatureSetId();
        }
    }
    
    protected final static class FeatureSetCursorImpl extends RowIteratorWrapper implements FeatureSetCursor {

        public FeatureSetCursorImpl(RecordCursor<FeatureSetRecord> impl) {
            super(impl);
        }
        
        @Override
        public FeatureSet get() {
            return ((RecordCursor<FeatureSetRecord>)this.filter).get().featureSet;
        }
    }
    
    private final static class RecordIteratorCursor<T> implements RecordCursor<T> {

        private final Iterator<T> recordIterator;
        private T next;

        public RecordIteratorCursor(Iterator<T> recordIterator) {
            this.recordIterator = recordIterator;
        }

        @Override
        public boolean moveToNext() {
            if(!this.recordIterator.hasNext())
                return false;
            this.next = this.recordIterator.next();
            return true;
        }
        
        @Override
        public T get() {
            return this.next;
        }

        @Override
        public void close() {}

        @Override
        public boolean isClosed() {
            return false;
        }
    }
    
    private final static class FilteredRecordIteratorCursor<T> extends FilteredRowIterator implements RecordCursor<T> {

        private final Filter<T> recordFilter;

        public FilteredRecordIteratorCursor(RecordCursor<T> cursor, Filter<T> filter) {
            super(cursor);
            
            this.recordFilter = filter;
        }

        @Override
        protected boolean accept() {
            T record = this.get();
            return this.recordFilter.accept(record);
        }

        @Override
        public T get() {
            return ((RecordCursor<T>)this.filter).get();
        }
        
    }
    
    private static interface RecordCursor<T> extends RowIterator {
        public T get();
    }
    
}
