package com.atakmap.map.layer.feature.datastore;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import android.util.Pair;

import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.log.Log;
import com.atakmap.database.FilteredRowIterator;
import com.atakmap.database.RowIterator;
import com.atakmap.database.RowIteratorWrapper;
import com.atakmap.map.layer.feature.AbstractFeatureDataStore3;
import com.atakmap.map.layer.feature.AttributeSet;
import com.atakmap.map.layer.feature.DataStoreException;
import com.atakmap.map.layer.feature.Feature;
import com.atakmap.map.layer.feature.FeatureCursor;
import com.atakmap.map.layer.feature.FeatureDataStore2;
import com.atakmap.map.layer.feature.FeatureDefinition2;
import com.atakmap.map.layer.feature.FeatureDefinition3;
import com.atakmap.map.layer.feature.FeatureSet;
import com.atakmap.map.layer.feature.FeatureSetCursor;
import com.atakmap.map.layer.feature.style.Style;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.geometry.GeometryFactory;
import com.atakmap.map.layer.feature.ogr.style.FeatureStyleParser;
import com.atakmap.math.MathUtils;
import com.atakmap.math.PointD;
import com.atakmap.math.Rectangle;
import com.atakmap.util.Filter;
import com.atakmap.util.Quadtree;
import com.atakmap.util.WildCard;

/**
 * @deprecated  use {@link com.atakmap.map.layer.feature.datastore.FeatureSetDatabase2};
 *              construct with <code>null</code> path/file
 */
@Deprecated
@DeprecatedApi(since = "4.4", forRemoval = true, removeAt = "4.7")
public class RuntimeFeatureDataStore2 extends AbstractFeatureDataStore3 {

    private final static FeatureDataStore2.FeatureQueryParameters EMPTY_FEATURE_PARAMS = new FeatureDataStore2.FeatureQueryParameters();
    private final static FeatureDataStore2.FeatureSetQueryParameters EMPTY_FEATURESET_PARAMS = new FeatureDataStore2.FeatureSetQueryParameters();

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

    private final static String TAG = "RuntimeFeatureDataStore2";

    private Quadtree<FeatureRecord> featureSpatialIndex;
    private Map<Long, FeatureRecord> featureIdIndex;
    private Map<String, Set<FeatureRecord>> featureNameIndex;
    private Map<Class<? extends Geometry>, Set<FeatureRecord>> featureGeometryTypeIndex;
    private Map<Long, Long> featureIdToFeatureSetId;
    private Map<Long, FeatureSetRecord> featureSetIdIndex;
    private Map<String, Set<FeatureSetRecord>> featureSetNameIndex;
    
    private long nextFeatureSetId;
    private long nextFeatureId;
    
    private long minTimestamp;
    private long maxTimestamp;

    public RuntimeFeatureDataStore2() {
        this(// modificationFlags
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

    protected RuntimeFeatureDataStore2(int modificationFlags, int visibilityFlags) {
        super(modificationFlags, visibilityFlags);
        
        // XXX - bother using a node limit ???
        this.featureSpatialIndex = new Quadtree<FeatureRecord>(new FeatureRecordQuadtreeFunction(), 100, -180d, -90d, 180d, 90d);

        this.featureIdIndex = new TreeMap<Long, FeatureRecord>();
        this.featureNameIndex = new TreeMap<String, Set<FeatureRecord>>(INDEX_KEY_STRING_COMPARATOR);
        this.featureGeometryTypeIndex = new HashMap<Class<? extends Geometry>, Set<FeatureRecord>>();
        this.featureIdToFeatureSetId = new HashMap<Long, Long>();
        this.featureSetIdIndex = new TreeMap<Long, FeatureSetRecord>();
        this.featureSetNameIndex = new TreeMap<String, Set<FeatureSetRecord>>(INDEX_KEY_STRING_COMPARATOR);
        
        this.minTimestamp = TIMESTAMP_NONE;
        this.maxTimestamp = TIMESTAMP_NONE;

        this.nextFeatureId = 1L;
        this.nextFeatureSetId = 1L;
    }

    @Override
    public FeatureCursor queryFeatures(FeatureQueryParameters params) throws DataStoreException {
        if(params == null)
            params = EMPTY_FEATURE_PARAMS;

        this.internalAcquireModifyLock(false);
        try {
            return this.queryFeaturesImpl(params);
        } finally {
            this.releaseModifyLock();
        }
    }

    private FeatureCursor queryFeaturesImpl(FeatureQueryParameters params) throws DataStoreException {
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
        boolean applyProviders = (params.featureSetFilter != null && params.featureSetFilter.providers != null);
        boolean applyTypes = (params.featureSetFilter != null && params.featureSetFilter.types != null);
        boolean applySetIds = (params.featureSetFilter != null && params.featureSetFilter.ids != null);
        boolean applySetNames = (params.featureSetFilter != null && params.featureSetFilter.names != null);
        boolean applyIds = (params.ids != null);
        boolean applyNames = (params.names != null);
        boolean applySpatial = (params.spatialFilter != null);
        boolean applyResolution = (params.featureSetFilter != null && (!Double.isNaN(params.featureSetFilter.minResolution) || !Double.isNaN(params.featureSetFilter.maxResolution)));
        boolean applyVisible = params.visibleOnly;
        boolean applyGeometry = (params.geometryTypes != null);

        if(applyIds)
            recordsFeatureIds = params.ids.size();
        
        if(applyProviders || applyTypes || applySetIds || applySetNames || applyResolution) {
            if(params.featureSetFilter != null && params.featureSetFilter.ids != null)
                recordsSet = params.featureSetFilter.ids.size();
            else
                recordsSet = this.featureSetIdIndex.size();

            for(FeatureSetRecord record : this.featureSetIdIndex.values()) {
                if(applySetIds) {
                    if(!params.featureSetFilter.ids.contains(Long.valueOf(record.fsid)))
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
            Envelope filterBounds = params.spatialFilter.getEnvelope();
            recordsSpatial = this.featureSpatialIndex.size(filterBounds.minX,
                                                           filterBounds.minY,
                                                           filterBounds.maxX,
                                                           filterBounds.maxY);
        }
        if(applyVisible) {
            if(params.featureSetFilter != null && params.featureSetFilter.ids != null)
                recordsVisual = params.featureSetFilter.ids.size();
            else
                recordsVisual = this.featureSetIdIndex.size();
            for(FeatureSetRecord record : this.featureSetIdIndex.values()) {
                if(params.featureSetFilter != null && params.featureSetFilter.ids != null && !params.featureSetFilter.ids.contains(Long.valueOf(record.fsid)))
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
            recordsNames = params.names.size();
            Set<FeatureRecord> features;
            for(String name : params.names) {
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
            if(params.ids == null) {
                // brute force
                retval.addAll(this.featureIdIndex.values());
            } else {
                FeatureRecord record;
                for(Long fid : params.ids) {
                    record = this.featureIdIndex.get(fid);
                    if(record != null)
                        retval.add(record);
                }
            }
            applyIds = false;
        } else if(applySetIds && recordsSet == minRecords) {
            if(params.featureSetFilter != null && params.featureSetFilter.ids != null) {
                FeatureSetRecord record;
                for(Long fsid : params.featureSetFilter.ids) {
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
            Envelope filterBounds = params.spatialFilter.getEnvelope();
            this.featureSpatialIndex.get(filterBounds.minX,
                                         filterBounds.minY,
                                         filterBounds.maxX,
                                         filterBounds.maxY,
                                         retval);
            applySpatial = false;
        } else if(applyVisible && recordsVisual == minRecords) {
            Collection<Long> fsids;
            if(params.featureSetFilter != null && params.featureSetFilter.ids != null)
                fsids = params.featureSetFilter.ids;
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
            for(String name : params.names) {
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
            filters.add(new ProviderFeatureRecordFilter(params.featureSetFilter.providers, '%'));
        if(applyTypes)
            filters.add(new TypeFeatureRecordFilter(params.featureSetFilter.types, '%'));
        if(applySetIds)
            filters.add(new SetIdFeatureRecordFilter(params.featureSetFilter.ids));
        if(applySetNames)
            filters.add(new SetNameFeatureRecordFilter(params.featureSetFilter.names, '%'));
        if(applyIds)
            filters.add(new FeatureIdRecordFilter(params.ids));
        if(applyNames)
            filters.add(new FeatureNameRecordFilter(params.names, '%'));
        if(applySpatial) {
            filters.add(new SpatialFeatureRecordFilter(params.spatialFilter));
        }
        if(applyResolution)
            filters.add(new ResolutionFeatureRecordFilter(params.featureSetFilter.minResolution, params.featureSetFilter.maxResolution));
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
    public FeatureSetCursor queryFeatureSets(FeatureSetQueryParameters params)
            throws DataStoreException {
        
        if(params == null)
            params = EMPTY_FEATURESET_PARAMS;

        this.internalAcquireModifyLock(false);
        try {
            return this.queryFeatureSetsImpl(params);
        } finally {
            this.releaseModifyLock();
        }
    }
    
    private FeatureSetCursor queryFeatureSetsImpl(FeatureSetQueryParameters params)
            throws DataStoreException {

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
        boolean applyRes = !(Double.isNaN(params.minResolution) && Double.isNaN(params.maxResolution));
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
        if(applyRes)
            filters.add(new ResolutionFeatureSetRecordFilter(params.minResolution, params.maxResolution));
        
        RecordCursor<FeatureSetRecord> cursor = new RecordIteratorCursor<FeatureSetRecord>(retval.iterator());
        if(filters.size() == 1)
            cursor = new FilteredRecordIteratorCursor<FeatureSetRecord>(cursor, filters.iterator().next());
        else if(filters.size() > 1)
            cursor = new FilteredRecordIteratorCursor<FeatureSetRecord>(cursor, new MultiRecordFilter<FeatureSetRecord>(filters));
        return new FeatureSetCursorImpl(cursor);
    }

    @Override
    public boolean hasTimeReference() {
        return (this.minTimestamp != TIMESTAMP_NONE);
    }

    @Override
    public long getMinimumTimestamp() {
        return this.minTimestamp;
    }

    @Override
    public long getMaximumTimestamp() {
        return this.maxTimestamp;
    }

    @Override
    public boolean supportsExplicitIDs() {
        return true;
    }

    @Override
    public boolean hasCache() {
        return false;
    }

    @Override
    public void clearCache() {}

    @Override
    public long getCacheSize() {
        return 0;
    }

    @Override
    public void dispose() {
        this.internalAcquireModifyLockUninterruptable(false);
        try {
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
        } finally {
            this.releaseModifyLock();
        }
    }

    @Override
    protected Pair<Long, Long> insertFeatureImpl(long fsid, long fid, String name, int geomCoding,
            Object rawGeom, int styleCoding, Object rawStyle, AttributeSet attributes,
            long timestamp, long version) throws DataStoreException {

        return this.insertFeatureImpl(fsid, fid, name, geomCoding, rawGeom, styleCoding, rawStyle, attributes, Feature.AltitudeMode.ClampToGround, 0d, timestamp, version);
    }

    @Override
    protected Pair<Long, Long> insertFeatureImpl(long fsid, long fid, String name, int geomCoding,
            Object rawGeom, int styleCoding, Object rawStyle, AttributeSet attributes, Feature.AltitudeMode altitudeMode, double extrude,
            long timestamp, long version) throws DataStoreException {
        
        FeatureSetRecord set = this.featureSetIdIndex.get(Long.valueOf(fsid));
        if(set == null)
            throw new IllegalArgumentException("No such FeatureSet");
        
        if(fid == FEATURE_ID_NONE)
            fid = this.nextFeatureId;
        else if(this.featureIdIndex.containsKey(fid))
            throw new IllegalArgumentException();
        
        this.nextFeatureId = fid+1;
        
        if(version == FEATURE_VERSION_NONE)
            version = 1;

        Geometry geom;
        switch(geomCoding) {
            case FeatureDefinition2.GEOM_ATAK_GEOMETRY :
                geom = (Geometry)rawGeom;
                break;
            case FeatureDefinition2.GEOM_SPATIALITE_BLOB :
                geom = GeometryFactory.parseSpatiaLiteBlob((byte[])rawGeom);
                break;
            case FeatureDefinition2.GEOM_WKB :
                geom = GeometryFactory.parseWkb((byte[])rawGeom);
                break;
            case FeatureDefinition2.GEOM_WKT :
                geom = GeometryFactory.parseWkt((String)rawGeom);
                break;
            default :
                throw new IllegalArgumentException();
        }
        
        Style style;
        switch(styleCoding) {
            case FeatureDefinition2.STYLE_ATAK_STYLE :
                style = (Style)rawStyle;
                break;
            case FeatureDefinition2.STYLE_OGR :
                style = FeatureStyleParser.parse2((String)rawStyle);
                break;
            default :
                throw new IllegalArgumentException();
        }
        
        Feature retval = new Feature(fsid, fid, name, geom, style, attributes, altitudeMode, extrude, timestamp, version);
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

        return Pair.create(Long.valueOf(fid), Long.valueOf(version));
    }

    @Override
    protected long insertFeatureSetImpl(FeatureSet featureSet) throws DataStoreException {
        long fsid = featureSet.getId();
        long version = featureSet.getVersion();
        
        if(fsid == FEATURESET_ID_NONE)
            fsid = this.nextFeatureSetId;
        else if(this.featureSetIdIndex.containsKey(fsid))
            throw new IllegalArgumentException();
        
        this.nextFeatureSetId = fsid+1;
        
        if(version == FEATURE_VERSION_NONE)
            version = 1;
        
        FeatureSet retval = new FeatureSet(fsid,
                                           featureSet.getProvider(),
                                           featureSet.getType(),
                                           featureSet.getName(),
                                           featureSet.getMinResolution(),
                                           featureSet.getMaxResolution(),
                                           version);

        FeatureSetRecord record = new FeatureSetRecord(fsid, retval);
        
        this.featureSetIdIndex.put(Long.valueOf(record.fsid), record);
        
        Set<FeatureSetRecord> sets = this.featureSetNameIndex.get(retval.getName());
        if(sets == null)
            this.featureSetNameIndex.put(retval.getName(), sets=new HashSet<FeatureSetRecord>());
        sets.add(record);
        
        return fsid;
    }

    @Override
    protected void updateFeatureImpl(long fid, int updatePropertyMask, String name,
            Geometry geometry, Style style, AttributeSet attributes, int attrUpdateType)
            throws DataStoreException {
        this.updateFeatureImpl(fid, updatePropertyMask, name, geometry, style, attributes, Feature.AltitudeMode.ClampToGround, 0, attrUpdateType);
    }

    @Override
    protected void updateFeatureImpl(long fid, int updatePropertyMask, String name,
                                     Geometry geometry, Style style, AttributeSet attributes, Feature.AltitudeMode altitudeMode, double extrude, int attrUpdateType)
            throws DataStoreException {

        FeatureRecord record = this.featureIdIndex.get(Long.valueOf(fid));
        if(record == null)
            throw new IllegalArgumentException("No such Feature ID");
        
        if((updatePropertyMask&PROPERTY_FEATURE_NAME) == 0)
            name = record.feature.getName();
        if((updatePropertyMask&PROPERTY_FEATURE_GEOMETRY) == 0)
            geometry = record.feature.getGeometry();
        if((updatePropertyMask&PROPERTY_FEATURE_STYLE) == 0)
            style = record.feature.getStyle();
        if ((updatePropertyMask&PROPERTY_FEATURE_ALTITUDE_MODE) == 0)
            altitudeMode = record.feature.getAltitudeMode();
        if ((updatePropertyMask&PROPERTY_FEATURE_EXTRUDE) == 0)
            extrude = record.feature.getExtrude();

        if((updatePropertyMask&PROPERTY_FEATURE_ATTRIBUTES) == 0) {
            attributes = record.feature.getAttributes();
        } else {
            switch(attrUpdateType) {
                case UPDATE_ATTRIBUTES_ADD_OR_REPLACE :
                    break;
                case UPDATE_ATTRIBUTES_SET :
                    break;
                default :
                    throw new IllegalArgumentException();
            }
        }

        final String recordName = record.feature.getName();

        //final Long fidBoxed = Long.valueOf(fid);
        final boolean updateNameIndex = (INDEX_KEY_STRING_COMPARATOR.compare(recordName, name) != 0);
        final boolean updateGeometryTypeIndex = !geometry.getClass().equals(record.feature.getGeometry().getClass());

        // remove any invalid index entries
        if(updateNameIndex) {
            Set<FeatureRecord> features = this.featureNameIndex.get(recordName);
            if(features.size() == 1)
                this.featureNameIndex.remove(recordName);
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
        
        record.feature = new Feature(record.set.fsid,
                                     fid,
                                     name,
                                     geometry,
                                     style,
                                     attributes,
                                     altitudeMode,
                                     extrude,
                                     record.feature.getTimestamp(),
                                     ++record.version);
        
        // update indices
        
        // update any invalid index entries
        if(updateNameIndex) {
            Set<FeatureRecord> features = this.featureNameIndex.get(name);
            if(features == null)
                this.featureNameIndex.put(name, features=new HashSet<FeatureRecord>());
            features.add(record);
        }
        if(updateGeometryTypeIndex) {
            Set<FeatureRecord> features = this.featureGeometryTypeIndex.get(geometry.getClass());
            if(features == null)
                this.featureGeometryTypeIndex.put(geometry.getClass(), features=new HashSet<FeatureRecord>());
            features.add(record);
        }
        this.featureSpatialIndex.refresh(record);
    }

    @Override
    protected boolean deleteFeatureImpl(long fid) throws DataStoreException {
        final FeatureRecord record = this.featureIdIndex.remove(Long.valueOf(fid));
        if(record == null)
            return false;;
        
        unindexFeature(record);
        
        record.set.features.remove(record);
        return true;
    }

    private void unindexFeature(FeatureRecord record) {
        Set<FeatureRecord> features;
        
        this.featureSpatialIndex.remove(record);
        final String featureName = record.feature.getName();
        features = this.featureNameIndex.get(featureName);
        features.remove(record);
        if(features.size() < 1)
            this.featureNameIndex.remove(featureName);
        features = this.featureGeometryTypeIndex.get(record.feature.getGeometry().getClass());
        features.remove(record);
        if(features.size() < 1)
            this.featureGeometryTypeIndex.remove(record.feature.getGeometry().getClass());
        this.featureIdToFeatureSetId.remove(Long.valueOf(record.fid));
    }

    @Override
    protected boolean deleteFeatureSetImpl(long fsid) throws DataStoreException {
        // remove FS from ID index
        final FeatureSetRecord record = this.featureSetIdIndex.remove(Long.valueOf(fsid));
        if(record == null)
            return false;

        // collect all of the feature id's associated with the features in a feature set
        List<Long> removalId = new ArrayList<Long>();
        for(FeatureRecord f : record.features) {
            removalId.add(f.fid);
        }
        // call the deletion implementation.  This was done for instead of unindexing the records
        // and calling clear because if the implementation of deleteFeatureImpl changes in the
        // future it could break something.
        for (Long id : removalId)
            deleteFeatureImpl(id);
        
        // remove FS from name index
        Set<FeatureSetRecord> sets = this.featureSetNameIndex.get(record.featureSet.getName());
        sets.remove(record);
        if(sets.size() == 0)
            this.featureSetNameIndex.remove(record.featureSet.getName());
        return true;
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
    protected boolean setFeatureVisibleImpl(long fid, boolean visible) {
        final Long fidBoxed = Long.valueOf(fid);
        final Long fsid = this.featureIdToFeatureSetId.get(fidBoxed);
        if(fsid == null) {
            Log.e(TAG, "No such Feature ID: " + fid);
            return false;
        }
        FeatureSetRecord record = this.featureSetIdIndex.get(fsid);
        
        this.setFeatureVisibleImpl(record, fid, visible);
        
        return true;
    }
    
    @Override
    public void setFeaturesVisible(FeatureQueryParameters params, boolean visible) throws DataStoreException {
        this.internalAcquireModifyLock(false);
        try {
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
            } finally {
                if(result != null)
                    result.close();
            }
            
            this.dispatchContentChanged();
        } finally {
            this.releaseModifyLock();
        }
    }

    @Override
    protected boolean setFeatureSetVisibleImpl(long setId, boolean visible) {
        FeatureSetRecord record = this.featureSetIdIndex.get(Long.valueOf(setId));
        if(record == null) {
            Log.e(TAG, "No such FeatureSet ID: " + setId);
            return false;
        }
        record.visible = visible;
        record.visibilityDeviations.clear();
        
        return true;
    }
    
    @Override
    public void setFeatureSetsVisible(FeatureSetQueryParameters params, boolean visible) throws DataStoreException {
        // XXX - more efficient impl
        
        this.internalAcquireModifyLock(false);
        try {
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
            } finally {
                if(result != null)
                    result.close();
            }
            
            this.dispatchContentChanged();
        } finally {
            this.releaseModifyLock();
        }
    }

    @Override
    public String getUri() {
        return "0x" + Integer.toString(this.hashCode(), 16);
    }

    /**************************************************************************/
    
    private static boolean equal(double a, double b) {
        return (a == b) || (Double.isNaN(a) && Double.isNaN(b));
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
            if(!this.valueIsLowercase)
                value = value.toLowerCase(LocaleUtil.getCurrent());
            if(this.literal.contains(value))
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
    
    private static class SpatialFeatureRecordFilter implements Filter<FeatureRecord> {
        private final Envelope roi;

        public SpatialFeatureRecordFilter(Geometry filter) {
            this.roi = filter.getEnvelope();
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
    
    protected final static class FeatureCursorImpl extends RowIteratorWrapper implements FeatureCursor, FeatureDefinition3 {

        private final RecordCursor<FeatureRecord> filter;
        
        public FeatureCursorImpl(RecordCursor<FeatureRecord> impl) {
            super(impl);
            
            this.filter = impl;
        }
        
        @Override
        public Feature get() {
            return this.filter.get().feature;
        }

        @Override
        public Object getRawGeometry() {
            return this.filter.get().feature.getGeometry();
        }

        @Override
        public int getGeomCoding() {
            return GEOM_ATAK_GEOMETRY;
        }

        @Override
        public String getName() {
            return this.filter.get().feature.getName();

        }

        @Override
        public int getStyleCoding() {
            return STYLE_ATAK_STYLE;
        }

        @Override
        public Object getRawStyle() {
            return this.filter.get().feature.getStyle();
        }

        @Override
        public AttributeSet getAttributes() {
            return this.filter.get().feature.getAttributes();
        }

        @Override
        public long getId() {
            return this.filter.get().feature.getId();
        }

        @Override
        public long getVersion() {
            return this.filter.get().feature.getVersion();
        }
        
        @Override
        public long getFsid() {
            return this.filter.get().feature.getFeatureSetId();
        }
        
        @Override
        public long getTimestamp() {
            return this.filter.get().feature.getTimestamp();
        }

        @Override
        public Feature.AltitudeMode getAltitudeMode() {
            return this.filter.get().feature.getAltitudeMode();
        }

        @Override
        public double getExtrude() {
            return this.filter.get().feature.getExtrude();
        }

    }
    
    protected final static class FeatureSetCursorImpl extends RowIteratorWrapper implements FeatureSetCursor {

        private final RecordCursor<FeatureSetRecord> filter;
        
        public FeatureSetCursorImpl(RecordCursor<FeatureSetRecord> impl) {
            super(impl);
            
            this.filter = impl;
        }
        
        @Override
        public FeatureSet get() {
            return this.filter.get().featureSet;
        }

        @Override
        public long getId() {
            return this.filter.get().fsid;
        }

        @Override
        public String getType() {
            return this.filter.get().featureSet.getType();
        }

        @Override
        public String getProvider() {
            return this.filter.get().featureSet.getProvider();
        }

        @Override
        public String getName() {
            return this.filter.get().featureSet.getName();
        }

        @Override
        public double getMinResolution() {
            return this.filter.get().featureSet.getMinResolution();
        }

        @Override
        public double getMaxResolution() {
            return this.filter.get().featureSet.getMaxResolution();
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

        private final RecordCursor<T> filter;
        private final Filter<T> recordFilter;

        public FilteredRecordIteratorCursor(RecordCursor<T> cursor, Filter<T> filter) {
            super(cursor);
            
            this.filter = cursor;
            this.recordFilter = filter;
        }

        @Override
        protected boolean accept() {
            T record = this.get();
            return this.recordFilter.accept(record);
        }

        @Override
        public T get() {
            return this.filter.get();
        }
        
    }
    
    private static interface RecordCursor<T> extends RowIterator {
        public T get();
    }
    
}
