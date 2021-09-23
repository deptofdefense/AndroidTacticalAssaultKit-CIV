package com.atakmap.map.layer.feature;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.style.Style;
import com.atakmap.util.Disposable;

public interface FeatureDataStore2 extends Disposable {
    public static interface OnDataStoreContentChangedListener {
        public void onDataStoreContentChanged(FeatureDataStore2 dataStore);
        public void onFeatureInserted(FeatureDataStore2 dataStore, long fid, FeatureDefinition2 def, long version);
        public void onFeatureUpdated(FeatureDataStore2 dataStore, long fid, int modificationMask, String name, Geometry geom, Style style, AttributeSet attribs, int attribsUpdateType);
        public void onFeatureDeleted(FeatureDataStore2 dataStore, long fid);
        public void onFeatureVisibilityChanged(FeatureDataStore2 dataStore, long fid, boolean visible);
    }

    public final static int FEATURESET_ID_NONE = 0;
    public final static int FEATURE_ID_NONE = 0;
    public final static int FEATURE_VERSION_NONE = 0;
    public final static int FEATURESET_VERSION_NONE = 0;
    
    public final static int PROPERTY_FEATURE_NAME = 0x01;
    public final static int PROPERTY_FEATURE_GEOMETRY = 0x02;
    public final static int PROPERTY_FEATURE_STYLE = 0x04;
    public final static int PROPERTY_FEATURE_ATTRIBUTES = 0x08;


    public final static int UPDATE_ATTRIBUTES_SET = 0;
    public final static int UPDATE_ATTRIBUTES_ADD_OR_REPLACE = 1;
    
    public final static long TIMESTAMP_NONE = Long.MIN_VALUE;

    /** The visibility of individual features may be controlled. */
    public final static int VISIBILITY_SETTINGS_FEATURE = 0x01;
    
    /** The visibility of individual feature sets may be controlled. */
    public final static int VISIBILITY_SETTINGS_FEATURESET = 0x02;
    
    // datastore level modifications
    
    /**
     * Feature sets may be inserted into the data store
     */
    public final static int MODIFY_FEATURESET_INSERT =              0x000001;

    /**
     * Feature set properties may be updated. At least one of 
     * {@link FeatureDataStore#MODIFY_FEATURESET_NAME} or
     * {@link FeatureDataStore#MODIFY_FEATURESET_DISPLAY_THRESHOLDS} should be
     * additionally specified.
     */
    public final static int MODIFY_FEATURESET_UPDATE =              0x000002;
    
    /**
     * Feature sets may be deleted from the data store.
     */
    public final static int MODIFY_FEATURESET_DELETE =              0x000004;

    // featureset level modifications
    
    /**
     * Features may be inserted into feature sets.
     */
    public final static int MODIFY_FEATURESET_FEATURE_INSERT =      0x000010;
    
    /**
     * Feature properties may be updated. At least one of 
     * {@link FeatureDataStore#MODIFY_FEATURE_NAME},
     * {@link FeatureDataStore#MODIFY_FEATURE_GEOMETRY},
     * {@link FeatureDataStore#MODIFY_FEATURE_STYLE} or
     * {@link FeatureDataStore#MODIFY_FEATURE_ATTRIBUTES} should be
     * additionally specified.
     */
    public final static int MODIFY_FEATURESET_FEATURE_UPDATE =      0x000020;
    
    /**
     * Features may be deleted from feature sets.
     */
    public final static int MODIFY_FEATURESET_FEATURE_DELETE =      0x000040;
    
    /**
     * The feature set name may be modified. Behavior is undefined if this flag
     * is set, but {@link #MODIFY_FEATURESET_UPDATE} is not set.
     */
    public final static int MODIFY_FEATURESET_NAME =                0x000080;
    
    /**
     * The feature set display thresholds be modified. Behavior is undefined if
     * this flag is set, but {@link #MODIFY_FEATURESET_UPDATE} is not set.
     */
    public final static int MODIFY_FEATURESET_DISPLAY_THRESHOLDS =  0x000100;
    
    // feature level modifications

    /**
     * The feature name may be modified.
     */
    public final static int MODIFY_FEATURE_NAME =                   0x000200;
    
    /**
     * The feature geometry may be modified.
     */
    public final static int MODIFY_FEATURE_GEOMETRY =               0x000400;

    /**
     * The feature style may be modified.
     */
    public final static int MODIFY_FEATURE_STYLE =                  0x000800;
    
    /**
     * The feature attributes may be modified.
     */
    public final static int MODIFY_FEATURE_ATTRIBUTES =             0x001000;

    // query
    public FeatureCursor queryFeatures(FeatureQueryParameters params) throws DataStoreException;
    public int queryFeaturesCount(FeatureQueryParameters params) throws DataStoreException;
    public FeatureSetCursor queryFeatureSets(FeatureSetQueryParameters params) throws DataStoreException;
    public int queryFeatureSetsCount(FeatureSetQueryParameters params) throws DataStoreException;
    

    // insert
    /**
     * Inserts the specified feature into the data store.
     * 
     * @param fsid      The feature set ID that the feature will belong to.
     * @param fid       The desired feature ID. If {@link #FEATURE_ID_NONE} is
     *                  specified, the data store will assign a unique ID.
     * @param def       The feature definition
     * @param version   The desired feature version. If
     *                  {@link #FEATURE_VERSION_NONE} is specified, the data store
     *                  will assign a version of <code>1L</code>.
     * 
     * @return  The ID of the feature inserted
     * 
     * @throws DataStoreException       If an error occurs with the underlying
     *                                  data store (e.g. IO error writing the
     *                                  feature to some persistent storage)
     *                                  during insert.
     * @throws IllegalStateException    If <code>fsid</code> is not an ID for a
     *                                  feature set that belongs to the data
     *                                  store
     */
    public long insertFeature(long fsid, long fid, FeatureDefinition2 def, long version) throws DataStoreException;
    /**
     * Inserts the specified feature into the data store.
     * 
     * @param feature   The feature to be inserted. The feature must have its
     *                  feature set ID set to a valid value. If the feature ID
     *                  is {@link #FEATURE_ID_NONE}, the data store will assign
     *                  a unique ID; if the feature version is
     *                  {@link #FEATURE_VERSION_NONE}, a version of
     *                  <code>1L</code> will be assigned.
     * 
     * @return  The ID of the feature inserted
     * 
     * @throws DataStoreException       If an error occurs with the underlying
     *                                  data store (e.g. IO error writing the
     *                                  feature to some persistent storage)
     *                                  during insert.
     * @throws IllegalStateException    If feature set ID of the feature is not
     *                                  an ID for a feature set that belongs to
     *                                  the data store
     */
    public long insertFeature(Feature feature) throws DataStoreException;
    /**
     * Performs bulk insertion of the specified features.
     * 
     * @param features  The features to be inserted. Each feature must have its
     *                  feature set ID set to a valid value. If the feature ID
     *                  is {@link #FEATURE_ID_NONE}, the data store will assign
     *                  a unique ID; if the feature version is
     *                  {@link #FEATURE_VERSION_NONE}, a version of
     *                  <code>1L</code> will be assigned.
     * 
     * @return  The ID of the feature inserted
     * 
     * @throws DataStoreException       If an error occurs with the underlying
     *                                  data store (e.g. IO error writing the
     *                                  feature to some persistent storage)
     *                                  during insert.
     * @throws IllegalStateException    If feature set ID of a feature is not an
     *                                  ID for a feature set that belongs to the
     *                                  data store
     */
    public void insertFeatures(FeatureCursor features) throws DataStoreException;

    
    public long insertFeatureSet(FeatureSet featureSet) throws DataStoreException;
    public void insertFeatureSets(FeatureSetCursor featureSet) throws DataStoreException;
    
    
    // update
    public void updateFeature(long fid, int updatePropertyMask, String name, Geometry geometry, Style style, AttributeSet attributes, int attrUpdateType) throws DataStoreException;
    public void updateFeatureSet(long fsid, String name, double minResolution, double maxResolution) throws DataStoreException;
    public void updateFeatureSet(long fsid, String name) throws DataStoreException;
    public void updateFeatureSet(long fsid, double minResolution, double maxResolution) throws DataStoreException;

    // delete
    public void deleteFeature(long fid) throws DataStoreException;
    public void deleteFeatures(FeatureQueryParameters params) throws DataStoreException;
    public void deleteFeatureSet(long fsid) throws DataStoreException;
    public void deleteFeatureSets(FeatureSetQueryParameters params) throws DataStoreException;

    // visibility
    public void setFeatureVisible(long fid, boolean visible) throws DataStoreException;
    public void setFeaturesVisible(FeatureQueryParameters params, boolean visible) throws DataStoreException;
    public void setFeatureSetVisible(long fsid, boolean visible) throws DataStoreException;
    public void setFeatureSetsVisible(FeatureSetQueryParameters params, boolean visible) throws DataStoreException;    

    // time reference
    public boolean hasTimeReference();
    public long getMinimumTimestamp();
    public long getMaximumTimestamp();

    // utility
    public String getUri();
    public boolean supportsExplicitIDs();
    public int getModificationFlags();
    public int getVisibilityFlags();
    public boolean hasCache();
    public void clearCache();
    public long getCacheSize();
    public void acquireModifyLock(boolean bulkModification) throws InterruptedException;
    public void releaseModifyLock();
    
    // content changed callback
    public void addOnDataStoreContentChangedListener(OnDataStoreContentChangedListener l);
    public void removeOnDataStoreContentChangedListener(OnDataStoreContentChangedListener l);
    
    /**************************************************************************/
    


    public final static class FeatureQueryParameters {
        public FeatureSetQueryParameters featureSetFilter;
        public Set<Long> ids;
        public Set<String> names;
        public Set<Class<? extends Geometry>> geometryTypes;
        public Set<AttributeFilter> attributeFilters;
        
        public boolean visibleOnly;
        public Geometry spatialFilter;
        public long minimumTimestamp;
        public long maximumTimestamp;

        public Feature.AltitudeMode altitudeMode;

        /**
         * If <code>true</code>, accepts only extruded features. If <code>false</code> does not filter
         * based on feature extrude property.
         */
        boolean extrudedOnly;


        public int ignoredFeatureProperties;
        public Collection<SpatialOp> spatialOps;
        
        public Collection<Order> order;

        public int limit;
        public int offset;
        
        public long timeout;
        
        public FeatureQueryParameters() {
            this.featureSetFilter = null;
            this.ids = null;
            this.names = null;
            this.geometryTypes = null;
            this.attributeFilters = null;
            this.visibleOnly = false;
            this.spatialFilter = null;
            this.minimumTimestamp = TIMESTAMP_NONE;
            this.maximumTimestamp = TIMESTAMP_NONE;
            this.altitudeMode = Feature.AltitudeMode.ClampToGround;
            this.extrudedOnly = false;
            this.ignoredFeatureProperties = 0;
            this.spatialOps = null;
            this.order = null;
            this.limit = 0;
            this.offset = 0;
            this.timeout = 5000L;
        }
        
        public FeatureQueryParameters(FeatureQueryParameters other) {
            this.featureSetFilter = (other.featureSetFilter == null) ? null : new FeatureSetQueryParameters(other.featureSetFilter);
            this.ids = (other.ids == null) ? null : new HashSet<Long>(other.ids);
            this.names = (other.names == null) ? null : new HashSet<String>(other.names);
            this.geometryTypes = (other.geometryTypes == null) ? null : new HashSet<Class<? extends Geometry>>(other.geometryTypes);
            this.attributeFilters = (other.attributeFilters == null) ? null : new HashSet<AttributeFilter>(other.attributeFilters);
            this.visibleOnly = other.visibleOnly;
            this.spatialFilter = other.spatialFilter; // XXX - should clone here
            this.minimumTimestamp = other.minimumTimestamp;
            this.maximumTimestamp = other.maximumTimestamp;
            this.altitudeMode = other.altitudeMode;
            this.extrudedOnly = other.extrudedOnly;
            this.ignoredFeatureProperties = other.ignoredFeatureProperties;
            this.spatialOps = (other.spatialOps == null) ? null : new ArrayList<SpatialOp>(other.spatialOps);
            this.order = (other.order == null) ? null : new ArrayList<Order>(other.order);
            this.limit = other.limit;
            this.offset = other.offset;
        }
        /**********************************************************************/
        
        public static abstract class Order {
            Order() {}
            
            public final static class Name extends Order {}
            public final static class ID extends Order {}
            
            /** Orders the results by distance from the specified point. */
            public final static class Distance extends Order {
                /** A point */
                public GeoPoint point;
                
                public Distance(GeoPoint point) {
                    if(point == null)
                        throw new IllegalArgumentException();

                    this.point = point;
                }
            }
        }

        public static abstract class SpatialOp {
            SpatialOp() {}
            
            public final static class Simplify extends SpatialOp {
                public final double distance;
                
                public Simplify(double distance) {
                    this.distance = distance;
                }
            }
            
            public final static class Buffer extends SpatialOp {
                public final double distance;
                
                public Buffer(double distance) {
                    this.distance = distance;
                }
            }
        }

        public static abstract class AttributeFilter {
            public final String key;
            
            AttributeFilter(String key) {
                this.key = key;
            }
            
            public final static class Arithmetic extends AttributeFilter {
                enum Comparison {
                    LessThan,
                    LessThanOrEqual,
                    GreaterThan,
                    GreaterThanOrEqual,
                    Equals,
                    NotEqual,
                }

                public final double value;
                public final Comparison comparison;
                
                public Arithmetic(String key, Comparison comparison, double value) {
                    super(key);
                    this.value = value;
                    this.comparison = comparison;
                }
            }
            
            public final static class Text extends AttributeFilter {
                enum Comparison {
                    Matches,
                    Equals,
                    EqualsIgnoreCase,
                    NotEqual,
                }

                public final String value;
                public final Comparison comparison;
                
                public Text(String key, Comparison comparison, String value) {
                    super(key);
                    this.value = value;
                    this.comparison = comparison;
                }
            }
            
            public final static class PossibleValues extends AttributeFilter {
                public final Set<String> values;
                
                public PossibleValues(String key, Set<String> values) {
                    super(key);
                    this.values = values;
                }
            }
        }
    }
    
    public final static class FeatureSetQueryParameters {
        public Set<Long> ids;
        public Set<String> names;
        public Set<String> types;
        public Set<String> providers;
        public double minResolution;
        public double maxResolution;
        public boolean visibleOnly;
        public int limit;
        public int offset;
        
        public FeatureSetQueryParameters() {
            this.ids = null;
            this.names = null;
            this.types = null;
            this.providers = null;
            this.minResolution = Double.NaN;
            this.maxResolution = Double.NaN;
            this.visibleOnly = false;
            this.limit = 0;
            this.offset = 0;
        }
        
        public FeatureSetQueryParameters(FeatureSetQueryParameters others) {
            this.ids = (others.ids == null) ? null : new HashSet<Long>(others.ids);
            this.names = (others.names == null) ? null : new HashSet<String>(others.names);
            this.types = (others.types == null) ? null : new HashSet<String>(others.types);
            this.providers = (others.providers == null) ? null : new HashSet<String>(others.providers);
            this.minResolution = others.minResolution;
            this.maxResolution = others.maxResolution;
            this.visibleOnly = others.visibleOnly;
            this.limit = others.limit;
            this.offset = others.offset;
        }
    }
}
