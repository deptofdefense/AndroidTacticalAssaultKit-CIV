package com.atakmap.map.layer.feature;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.database.RowIterator;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.style.Style;
import com.atakmap.util.Disposable;

/**
 * A data store for feature data.
 * 
 * <P>Implementations are expected to be thread-safe.  Multiple threads should
 * be able to access an instance concurrently without producing undefined
 * behavior.
 * 
 * @author Developer
 */
public interface FeatureDataStore extends Disposable {

    public final static int FEATURESET_ID_NONE = 0;
    public final static int FEATURE_ID_NONE = 0;
    public final static int FEATURE_VERSION_NONE = 0;
    public final static int FEATURESET_VERSION_NONE = 0;
    
    
    /** The visibility of individual features may be controlled. */
    public final static int VISIBILITY_SETTINGS_FEATURE = 0x01;
    
    /** The visibility of individual feature sets may be controlled. */
    public final static int VISIBILITY_SETTINGS_FEATURESET = 0x02;
    
    // datastore level modifications
    
    /**
     * Bulk modifications are supported.
     */
    public final static int MODIFY_BULK_MODIFICATIONS =             0x000008;

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

    /**************************************************************************/

    /**
     * Callback interface providing notification when the content of the
     * data store has changed.
     * 
     * @author Developer
     */
    public static interface OnDataStoreContentChangedListener {
        /**
         * This method is invoked when the content in the data store has
         * changed.
         * 
         * @param dataStore The data store
         */
        public void onDataStoreContentChanged(FeatureDataStore dataStore);
    }
    
    /**
     * Adds the specified {@link OnDataStoreContentChangedListener}.
     * 
     * @param l The listener
     */
    public void addOnDataStoreContentChangedListener(OnDataStoreContentChangedListener l);
    
    /**
     * Removes the specified {@link OnDataStoreContentChangedListener}.
     * 
     * @param l The listener
     */
    public void removeOnDataStoreContentChangedListener(OnDataStoreContentChangedListener l);

    /**
     * Returns the feature with the specified ID. The returned feature should be
     * considered immutable; behavior is undefined if the feature is actively
     * modified.
     * 
     * @param fid   The feature ID
     * 
     * @return  The feature with the specified ID or <code>null</code> if 
     *          the data store does not contain such a feature.
     */
    public Feature getFeature(long fid);

    /**
     * Returns a cursor over all features satisfying the specified
     * {@link FeatureQueryParameters}. The returned features should be
     * considered immutable; behavior is undefined if the features are actively
     * modified.
     * 
     * @param params    The query parameters. If <code>null</code> all features
     *                  are selected.
     * 
     * @return  A cursor over all features satisfying <code>params</code>.
     */
    public FeatureCursor queryFeatures(FeatureQueryParameters params);
    
    /**
     * Returns the number of features that satisfy the specified
     * {@link FeatureQueryParameters}.
     * 
     * @param params    The query parameters. If <code>null</code> all features
     *                  are selected.
     *                  
     * @return  The number of features satisfying <code>params</code>.
     */
    public int queryFeaturesCount(FeatureQueryParameters params);
    
    /**
     * Returns the feature set with the specified ID.
     * 
     * @param featureSetId  The feature set ID
     * 
     * @return  The feature set with the specified ID or <code>null</code> if 
     *          the data store does not contain such a feature set.
     */
    public FeatureSet getFeatureSet(long featureSetId);
    
    /**
     * Returns a cursor over all feature sets satisfying the specified
     * {@link FeatureSetQueryParameters}.
     * 
     * @param params    The query parameters. If <code>null</code> all feature
     *                  set are selected.
     * 
     * @return  A cursor over all feature sets satisfying <code>params</code>.
     *
     * @see FeatureSet
     */
    public FeatureSetCursor queryFeatureSets(FeatureSetQueryParameters params);
    
    /**
     * Returns the number of feature sets that satisfy the specified
     * {@link FeatureSetQueryParameters}.
     * 
     * @param params    The query parameters. If <code>null</code> all feature
     *                  sets are selected.
     *                  
     * @return  The number of feature sets satisfying <code>params</code>.
     */
    public int queryFeatureSetsCount(FeatureSetQueryParameters params);

    /**
     * Returns the bit-wise OR of modification flags for the data store. Methods
     * that modify the data store may only be successfully invoked if the
     * associated bit flag is present.
     * 
     * @return  The modification flags for the data store.
     */
    public int getModificationFlags();
    
    /**
     * Notifies the data store that a bulk modification will be occurring. The
     * data store may opt not to commit or notify on content changed until the
     * bulk modification is complete. An invocation of this method should always
     * be paired with an invocation of {@link #endBulkModification(boolean)}
     * once the bulk modification is completed, successfully or otherwise.
     * 
     * <P>Nested bulk modifications are considered undefined; the user should
     * always check {@link #isInBulkModification()} before invoking this method.
     * 
     * <P>Unless otherwise specified by the implementation, it should be assumed
     * that modifications to the data store during a bulk modification may only
     * be made on the thread that began the bulk modification.
     * 
     * @see #MODIFY_BULK_MODIFICATIONS
     * @see #getModificationFlags()
     * @see #endBulkModification(boolean)
     * @see #isInBulkModification()
     */
    public void beginBulkModification();
    
    /**
     * Notifies the data store that the bulk modification is complete. If
     * unsuccessful, the data store is expected to undo all modifications issued
     * during the bulk modification.
     *  
     * @param successful    <code>true</code> if the bulk modification should be
     *                      considered successful, <code>false</code> otherwise.
     *                      
     * @see #MODIFY_BULK_MODIFICATIONS
     * @see #getModificationFlags()
     * @see #beginBulkModification(boolean)
     */
    public void endBulkModification(boolean successful);
    
    /**
     * Checks to see if the data store is currently in a bulk modification.
     *  
     * @return  <code>true</code> if the data store is in a bulk modification,
     *          <code>false</code> otherwise.
     *          
     * @see #beginBulkModification()
     */
    public boolean isInBulkModification();
    
    /**
     * Inserts a new feature set into the data store.
     * 
     * @param provider      The provider that parsed/generated the features
     *                      that will be associated with the feature set
     * @param type          The type of the content
     * @param name          The name of the feature set.
     * @param minResolution The minimum display resolution for the feature set.
     *                      {@link Double#MAX_VALUE} should be used if the set's
     *                      features have no minimum resolution threshold.
     * @param maxResolution The max display resolution for the feature set.
     *                      A value of <code>0.0d</code> should be used if the
     *                      set's features have no minimum resolution threshold.
     * @param returnRef     A flag indicating whether or not a reference to the
     *                      newly inserted feature set should be returned.
     *                      Returning the feature set may introduce unwanted
     *                      overhead in the event that it is not going to be
     *                      immediately used.                      
     *
     * @return  If <code>returnRef</code> is <code>true</code>, returns the
     *          feature set that was newly inserted
     * 
     * @throws UnsupportedOperationException If the modification operation is
     *                                       not supported for this data store
     *                                       
     * @see #MODIFY_FEATURESET_INSERT
     * @see #getModificationFlags()
     */
    public FeatureSet insertFeatureSet(String provider, String type, String name, double minResolution, double maxResolution, boolean returnRef);
    
    /**
     * Updates the feature set name.
     * 
     * @param fsid  The feature set ID
     * @param name  The new name for the feature set
     * 
     * @throws UnsupportedOperationException If the modification operation is
     *                                       not supported for this data store
     * 
     * @see #MODIFY_FEATURESET_NAME
     * @see #MODIFY_FEATURESET_UPDATE
     * @see #getModificationFlags()
     */
    public void updateFeatureSet(long fsid, String name);
    
    /**
     * Updates the feature set display thresholds.
     * 
     * @param fsid          The feature set ID
     * @param minResolution The minimum display resolution for the feature set.
     *                      {@link Double#MAX_VALUE} should be used if the set's
     *                      features have no minimum resolution threshold.
     * @param maxResolution The max display resolution for the feature set.
     *                      A value of <code>0.0d</code> should be used if the
     *                      set's features have no minimum resolution threshold.
     *                      
     * @throws UnsupportedOperationException If the modification operation is
     *                                       not supported for this data store
     * 
     * @see #MODIFY_FEATURESET_DISPLAY_THRESHOLDS
     * @see #MODIFY_FEATURESET_UPDATE
     * @see #getModificationFlags()
     */
    public void updateFeatureSet(long fsid, double minResolution, double maxResolution);
    
    /**
     * Updates the feature set name and display thresholds.
     * 
     * @param fsid          The feature set ID
     * @param name          The new name for the feature set
     * @param minResolution The minimum display resolution for the feature set.
     *                      {@link Double#MAX_VALUE} should be used if the set's
     *                      features have no minimum resolution threshold.
     * @param maxResolution The max display resolution for the feature set.
     *                      A value of <code>0.0d</code> should be used if the
     *                      set's features have no minimum resolution threshold.
     *                      
     * @throws UnsupportedOperationException If the modification operation is
     *                                       not supported for this data store
     *
     * @see #MODIFY_FEATURESET_NAME
     * @see #MODIFY_FEATURESET_DISPLAY_THRESHOLDS
     * @see #MODIFY_FEATURESET_UPDATE
     * @see #getModificationFlags()
     */
    public void updateFeatureSet(long fsid, String name, double minResolution, double maxResolution);
    
    /**
     * Deletes the specified feature set from the data store.
     * 
     * @param fsid  The feature set ID
     * 
     * @throws UnsupportedOperationException If the modification operation is
     *                                       not supported for this data store
     *
     * @see #MODIFY_FEATURESET_DELETE
     * @see #getModificationFlags()
     */
    public void deleteFeatureSet(long fsid);
    
    /**
     * Deletes all feature sets from the data store.
     * 
     * @throws UnsupportedOperationException If the modification operation is
     *                                       not supported for this data store
     *
     * @see #MODIFY_FEATURESET_DELETE
     * @see #getModificationFlags()
     */
    public void deleteAllFeatureSets();
    
    /**
     * Inserts the specified feature.
     * 
     * @param fsid          The feature set ID to add the new feature to
     * @param name          The feature's name
     * @param geom          The feature's geometry
     * @param style         The feature's style
     * @param attributes    The feature's attributes
     * @param returnRef     A flag indicating whether or not a reference to the
     *                      newly inserted {@link Feature} should be returned.
     *                      Returning the feature may introduce unwanted 
     *                      overhead in the event that it is not going to be
     *                      immediately used.
     *                      
     * @return  If <code>returnRef</code> is <code>true</code>, returns the
     *          {@link Feature} that was just inserted into the data store. The
     *          {@link Feature} should be considered immutable; external
     *          modifications to its properties will produced undefined results.
     *          
     * @throws UnsupportedOperationException If the modification operation is
     *                                       not supported for this data store
     *
     * @see #MODIFY_FEATURESET_FEATURE_INSERT
     * @see #getModificationFlags()
     */
    public Feature insertFeature(long fsid, String name, Geometry geom, Style style, AttributeSet attributes, boolean returnRef);
    
    /**
     * Inserts the specified feature from the definitoin.
     * 
     * @param fsid          The feature set ID to add the new feature to
     * @param def           The feature's definition
     * @param returnRef     A flag indicating whether or not a reference to the
     *                      newly inserted {@link Feature} should be returned.
     *                      Returning the feature may introduce unwanted 
     *                      overhead in the event that it is not going to be
     *                      immediately used.
     *                      
     * @return  If <code>returnRef</code> is <code>true</code>, returns the
     *          {@link Feature} that was just inserted into the data store. The
     *          {@link Feature} should be considered immutable; external
     *          modifications to its properties will produced undefined results.
     *          
     * @throws UnsupportedOperationException If the modification operation is
     *                                       not supported for this data store
     *
     * @see #MODIFY_FEATURESET_FEATURE_INSERT
     * @see #getModificationFlags()
     */
    public Feature insertFeature(long fsid, FeatureDefinition def, boolean returnRef);
    
    /**
     * Updates the specified feature's name.
     * 
     * @param fid   The feature ID
     * @param name  The feature's new name
     * 
     * @throws UnsupportedOperationException If the modification operation is
     *                                       not supported for this data store
     *
     * @see #MODIFY_FEATURE_NAME
     * @see #getModificationFlags()
     */
    public void updateFeature(long fid, String name);
    
    /**
     * Updates the specified feature's geometry.
     * 
     * @param fid   The feature ID
     * @param geom  The feature's new geometry
     * 
     * @throws UnsupportedOperationException If the modification operation is
     *                                       not supported for this data store
     *
     * @see #MODIFY_FEATURE_GEOMETRY
     * @see #getModificationFlags()
     */
    public void updateFeature(long fid, Geometry geom);
    
    /**
     * Updates the specified feature's style.
     * 
     * @param fid   The feature ID
     * @param style The feature's new style
     * 
     * @throws UnsupportedOperationException If the modification operation is
     *                                       not supported for this data store
     *
     * @see #MODIFY_FEATURE_STYLE
     * @see #getModificationFlags()
     */
    public void updateFeature(long fid, Style style);
    
    /**
     * Updates the specified feature's attributes.
     * 
     * @param fid           The feature ID
     * @param attributes    The feature's new attributes
     * 
     * @throws UnsupportedOperationException If the modification operation is
     *                                       not supported for this data store
     *
     * @see #MODIFY_FEATURE_ATTRIBUTES
     * @see #getModificationFlags()
     */
    public void updateFeature(long fid, AttributeSet attributes);
    
    /**
     * Updates the specified feature's properties.
     * 
     * @param fid           The feature ID
     * @param name          The feature's new name
     * @param geom          The feature's new geometry
     * @param style         The feature's new style
     * @param attributes    The feature's new attributes
     * 
     * @throws UnsupportedOperationException If the modification operation is
     *                                       not supported for this data store
     *
     * @see #MODIFY_FEATURE_NAME
     * @see #MODIFY_FEATURE_GEOMETRY
     * @see #MODIFY_FEATURE_STYLE
     * @see #MODIFY_FEATURE_ATTRIBUTES
     * @see #getModificationFlags()
     */
    public void updateFeature(long fid, String name, Geometry geom, Style style, AttributeSet attributes);
    
    /**
     * Deletes the specified feature.
     * 
     * @param fid   The feature ID
     * 
     * @throws UnsupportedOperationException If the modification operation is
     *                                       not supported for this data store
     *
     * @see #MODIFY_FEATURESET_FEATURE_DELETE
     * @see #getModificationFlags()
     */
    public void deleteFeature(long fid);
    
    /**
     * Deletes all features for the specified feature set.
     * 
     * @param fsid  The feature set ID
     * 
     * @throws UnsupportedOperationException If the modification operation is
     *                                       not supported for this data store
     *
     * @see #MODIFY_FEATURESET_FEATURE_DELETE
     * @see #getModificationFlags()
     */
    public void deleteAllFeatures(long fsid);
    
    /**
     * Returns the visibility settings flags for the data store. These flags
     * reflect what visibility settings are available over the feature sets and
     * features.
     * 
     * @return  The visibility settings flags for the data store
     * 
     * @see #VISIBILITY_SETTINGS_FEATURE
     * @see #VISIBILITY_SETTINGS_FEATURESET
     */
    public int getVisibilitySettingsFlags();
    
    /**
     * Sets the specified feature's visibility. Invoking this method may result
     * in an update to the parent feature set's visibility.
     * 
     * @param fid       The feature ID
     * @param visible   <code>true</code> to set the feature visible,
     *                  <code>false</code> otherwise.
     *                  
     * @throws UnsupportedOperationException    if
     *                                          {@link #getVisibilitySettingsFlags()}
     *                                          does not have the
     *                                          {@link #VISIBILITY_SETTINGS_FEATURE}
     *                                          bit toggled.
     *                                          
     * @see #VISIBILITY_SETTINGS_FEATURE
     * @see #getVisibilitySettingsFlags()
     * @see #isFeatureSetVisible(long)
     */
    public void setFeatureVisible(long fid, boolean visible);
    
    /**
     * Sets the visibility of the features that match the specified query 
     * parameters. Invoking this method may result in an update to the parent
     * feature set's visibility.
     * 
     * @param params    The feature query parameters
     * @param visible   <code>true</code> to set the feature visible,
     *                  <code>false</code> otherwise.
     *                  
     * @throws UnsupportedOperationException    if
     *                                          {@link #getVisibilitySettingsFlags()}
     *                                          does not have the
     *                                          {@link #VISIBILITY_SETTINGS_FEATURE}
     *                                          bit toggled.
     *                                          
     * @see #VISIBILITY_SETTINGS_FEATURE
     * @see #getVisibilitySettingsFlags()
     * @see #isFeatureSetVisible(long)
     */
    public void setFeaturesVisible(FeatureQueryParameters params, boolean visible);
    
    /**
     * Returns the specified feature's current visibility. If visibility
     * settings are not supported, this method should always return
     * <code>true</code>. If visibility is only supported at the feature set
     * level, this method should return <code>true</code> if the parent feature
     * set is visible.
     * 
     * @param fid   The feature ID
     * 
     * @return  <code>true</code> if the feature is visible, <code>false</code>
     *          otherwise.
     */
    public boolean isFeatureVisible(long fid);
    
    /**
     * Sets the specified feature set's visibility. Invoking this method will
     * update the visibility state of all of the feature set's features to match
     * the specified visibility state for the feature set.
     * 
     * @param fid       The feature set ID
     * @param visible   <code>true</code> to set the feature set and all of its
     *                  features visible, <code>false</code> to set the feature
     *                  set and all of its features invisible.
     *                  
     * @throws UnsupportedOperationException    if
     *                                          {@link #getVisibilitySettingsFlags()}
     *                                          does not have the
     *                                          {@link #VISIBILITY_SETTINGS_FEATURESET}
     *                                          bit toggled.
     * @see #VISIBILITY_SETTINGS_FEATURESET
     * @see #getVisibilitySettingsFlags()
     */
    public void setFeatureSetVisible(long setId, boolean visible);

    /**
     * Sets the visibility of the feature sets matching the specified query
     * parameters. Invoking this method will update the visibility state of all
     * of the feature sets' features to match the specified visibility state for
     * the feature set.
     * 
     * @param params    The feature set query parameters
     * @param visible   <code>true</code> to set the feature set and all of its
     *                  features visible, <code>false</code> to set the feature
     *                  set and all of its features invisible.
     *                  
     * @throws UnsupportedOperationException    if
     *                                          {@link #getVisibilitySettingsFlags()}
     *                                          does not have the
     *                                          {@link #VISIBILITY_SETTINGS_FEATURESET}
     *                                          bit toggled.
     * @see #VISIBILITY_SETTINGS_FEATURESET
     * @see #getVisibilitySettingsFlags()
     */
    public void setFeatureSetsVisible(FeatureSetQueryParameters params, boolean visible);
    
    /**
     * Returns <code>true</code> if the feature set is visible. The feature set
     * is considered visible if one or more of its child features are visible.
     *  
     * @param setId The feature set ID
     * 
     * @return  <code>true</code> if the feature set is visible,
     *          <code>false</code> otherwise
     *          
     * @see #isFeatureVisible(long)
     */
    public boolean isFeatureSetVisible(long setId);
    

    /**
     * Returns a flag indicating whether or not the data store is currently
     * available. In the event that the data store resides on a remote server,
     * this method may return <code>false</code> when no connection with that
     * server can be established.
     * 
     * <P>This method should always return <code>false</code> after the
     * {@link #dispose()} method has been invoked.
     * 
     * @return  <code>true</code> if the data store is available,
     *          <code>false</code> otherwise.
     */
    public boolean isAvailable();
    
    /**
     * Refreshes the data store. Any invalid entries are dropped.
     */
    public void refresh();

    public String getUri();

    /**************************************************************************/

    /**
     * Feature query parameters. Specifies the common criteria features may be
     * queried against.
     * 
     * @author Developer
     */
    public static class FeatureQueryParameters {
        public final static int FIELD_NAME = 0x01; 
        public final static int FIELD_GEOMETRY = 0x02;
        public final static int FIELD_STYLE = 0x04;
        public final static int FIELD_ATTRIBUTES = 0x08;

        /**
         * The accepted feature set providers. If <code>null</code> all
         * providers are accepted.
         */
        public Collection<String> providers;
        /**
         * The accepted feature set types. If <code>null</code> all
         * types are accepted.
         */
        public Collection<String> types;
        /**
         * The accepted feature set names. If <code>null</code> all feature set
         * names are accepted.
         */
        public Collection<String> featureSets;
        /**
         * The accepted feature set IDs. If <code>null</code> all feature set
         * IDs are accepted.
         */
        public Collection<Long> featureSetIds;
        /**
         * The accepted feature names. If <code>null</code> all feature names
         * are accepted.
         */
        public Collection<String> featureNames;
        /**
         * The accepted feature IDs. If <code>null</code> all feature IDs are
         * accepted.
         */
        public Collection<Long> featureIds;
        /**
         * The spatial filter. Only those features whose geometry satisfies the
         * filter will be accepted. If <code>null</code> no spatial filter is
         * applied.
         */
        public SpatialFilter spatialFilter;
        /**
         * The minimum resolution. Only those feature sets with a maximum
         * resolution higher than the specified value will be accepted. A value
         * of {@link Double#NaN} indicates that there is no minimum resolution
         * constraint.
         */
        public double minResolution;
        /**
         * The maximum resolution. Only those feature sets with a minimum
         * resolution lower than the specified value will be accepted. A value
         * of {@link Double#NaN} indicates that there is no maximum resolution
         * constraint.
         */
        public double maxResolution;
        /**
         * If <code>true</code>, only those features that are currently visible
         * will be accepted.
         */
        public boolean visibleOnly;
        /**
         * The accepted geometry types. If <code>null</code> all geometry types
         * will be accepted.
         */
        public Collection<Class<? extends Geometry>> geometryTypes;
        Map<String, Object> attributes;
        /**
         * The spatial operations to be applied to the results. Operations will
         * be performed on the returned features in iteration order.
         */
        public Collection<SpatialOp> ops;
        /**
         * The order to be applied to the results. Sorting will always be
         * ascending and will take into account the ordering of the elements. If
         * <code>null</code>, the order of the results is undefined.
         * 
         * <P>Note that the performance of sorting the results may depend on
         * some sort of prior indexing being performed over the features.
         * Sorting very large results sets when no applicable index exists may
         * require significant time.
         */
        public Collection<Order> order;
        /**
         * The limit on the number of results returned. If <code>0</code> there
         * shall be no limit.
         */
        public int limit;
        /**
         * The offset into the result list. This argument is only applicable if
         * {@link #limit} is greater-than <code>0</code>. 
         */
        public int offset;

        Object priv;
        /**
         * A bitwise-OR of the fields to be excluded; if <code>0</code>, no
         * fields are excluded (default). Fields specified as ignored may return
         * <code>null</code> via their respective accessor on  {@link Feature}
         * objects returned by a cursor. Ignoring fields that are unnecessary
         * may improve memory and CPU characteristics during query and row
         * iteration. 
         */
        public int ignoredFields;

        /**
         * Creates a new instance. All fields are initialized to values such
         * that there will be no constraints on the results.
         */
        public FeatureQueryParameters() {
            this(null,
                 null,
                 null,
                 null,
                 null,
                 null,
                 null,
                 Double.NaN,
                 Double.NaN,
                 false,
                 null,
                 null,
                 null,
                 null,
                 0,
                 0,
                 0);
        }
        
        public FeatureQueryParameters(FeatureQueryParameters other) {
            this(other.providers != null ? new HashSet<String>(other.providers) : null,
                 other.types != null ? new HashSet<String>(other.types) : null,
                 other.featureSets != null ? new HashSet<String>(other.featureSets) : null,
                 other.featureSetIds != null ? new HashSet<Long>(other.featureSetIds) : null,
                 other.featureNames != null ? new HashSet<String>(other.featureNames) : null,
                 other.featureIds != null ? new HashSet<Long>(other.featureIds) : null,
                 other.spatialFilter,
                 other.minResolution,
                 other.maxResolution,
                 other.visibleOnly,
                 other.geometryTypes != null ? new HashSet<Class<? extends Geometry>>(other.geometryTypes) : null,
                 other.attributes != null ? new HashMap<String, Object>(other.attributes) : null,
                 other.ops != null ? new ArrayList<SpatialOp>(other.ops) : null,
                 other.order != null ? new ArrayList<Order>(other.order) : null,
                 other.ignoredFields,
                 other.limit,
                 other.offset);
        }
        
        private FeatureQueryParameters(Collection<String> providers,
                                       Collection<String> types,
                                       Collection<String> featureSets,
                                       Collection<Long> featureSetIds,
                                       Collection<String> featureNames,
                                       Collection<Long> featureIds,
                                       SpatialFilter spatialFilter,
                                       double minResolution,
                                       double maxResolution,
                                       boolean visibleOnly,
                                       Collection<Class<? extends Geometry>> geometryTypes,
                                       Map<String, Object> attributes,
                                       Collection<SpatialOp> ops,
                                       Collection<Order> order,
                                       int ignoredFields,
                                       int limit,
                                       int offset) {

            this.providers = providers;
            this.types = types;
            this.featureSets = featureSets;
            this.featureSetIds = featureSetIds;
            this.featureNames = featureNames;
            this.featureIds = featureIds;
            this.spatialFilter = spatialFilter;
            this.minResolution = minResolution;
            this.maxResolution = maxResolution;
            this.visibleOnly = visibleOnly;
            this.geometryTypes = geometryTypes;
            this.attributes = attributes;
            this.ops = ops;
            this.order = order;
            this.ignoredFields = ignoredFields;
            this.limit = limit;
            this.offset = offset;
        }

        /**********************************************************************/

        /**
         * A spatial filter.
         * 
         * @author Developer
         */
        public abstract static class SpatialFilter {
            SpatialFilter() {}
            
            abstract boolean isValid();
        }
        
        /**
         * All geometries intersecting the region of interest are accepted.
         *  
         * @author Developer
         */
        public final static class RegionSpatialFilter extends SpatialFilter {
            /** The upper-left corner of the region. */
            public GeoPoint upperLeft;
            /** The lower-right corner of the region. */
            public GeoPoint lowerRight;
            
            public RegionSpatialFilter(GeoPoint upperLeft, GeoPoint lowerRight) {
                this.upperLeft = upperLeft;
                this.lowerRight = lowerRight;
            }

            @Override
            boolean isValid() {
                return (this.upperLeft != null && this.lowerRight != null);
            }
        }
        
        /**
         * All geometries with the specified distance of the specified point are
         * accepted.
         * 
         * @author Developer
         */
        public final static class RadiusSpatialFilter extends SpatialFilter {
            /** The point of interest. */
            public GeoPoint point;
            /** The threshold distance from the point, in meters. */
            public double radius;
            
            public RadiusSpatialFilter(GeoPoint point, double radius) {
                this.point = point;
                this.radius = radius;
            }

            @Override
            boolean isValid() {
                return (this.point != null && !Double.isNaN(this.radius));
            }
        }
        
        public static abstract class SpatialOp {
            SpatialOp() {}
        }

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

        /**********************************************************************/
        
        /** Defines the ordering to be applied to the results. */
        public abstract static class Order {
            Order() {}
            
            boolean isValid() {
                return true;
            }
        }
        
        /** Orders the results by maximum resolution. */
        public final static class Resolution extends Order {
            public final static Resolution INSTANCE = new Resolution();            
        }

        /** Orders the results by feature set ID. */
        public final static class FeatureSet extends Order {
            public final static FeatureSet INSTANCE = new FeatureSet();            
        }

        /** Orders the results by feature name. */
        public final static class FeatureName extends Order {
            public final static FeatureName INSTANCE = new FeatureName();            
        }

        /** Orders the results by feature ID. */
        public final static class FeatureId extends Order {
            public final static FeatureId INSTANCE = new FeatureId();            
        }
        
        /** Orders the results by distance from the specified point. */
        public final static class Distance extends Order {
            /** A point */
            public GeoPoint point;
            
            public Distance(GeoPoint point) {
                this.point = point;
            }
            
            @Override
            boolean isValid() {
                return (this.point != null);
            }
        }
        
        /** Orders the results by geometry type */
        public final static class GeometryType extends Order {
            public final static GeometryType INSTANCE = new GeometryType();
        }
    } // FeatureQueryParameters
    
    /**
     * Feature query parameters. Specifies the common criteria features may be
     * queried against.
     * 
     * @author Developer
     */
    public static class FeatureSetQueryParameters {
        /**
         * The accepted feature set providers. If <code>null</code> all
         * providers are accepted.
         */
        public Collection<String> providers;
        /**
         * The accepted feature set types. If <code>null</code> all
         * types are accepted.
         */
        public Collection<String> types;
        /**
         * The accepted feature set names. If <code>null</code> all feature set
         * names are accepted.
         */
        public Collection<String> names;
        /**
         * The accepted feature set IDs. If <code>null</code> all feature set
         * IDs are accepted.
         */
        public Collection<Long> ids;
        /**
         * If <code>true</code>, only those feature sets that are currently
         * visible will be accepted.
         */
        public boolean visibleOnly;
        /**
         * The limit on the number of results returned. If <code>0</code> there
         * shall be no limit.
         */
        public int limit;
        /**
         * The offset into the result list. This argument is only applicable if
         * {@link #limit} is greater-than <code>0</code>. 
         */
        public int offset;

        Object priv;
        
        /**
         * Creates a new instance. All fields are initialized to values such
         * that there will be no constraints on the results.
         */
        public FeatureSetQueryParameters() {
            this.providers = null;
            this.types = null;
            this.names = null;
            this.ids = null;
            this.visibleOnly = false;
            this.limit = 0;
            this.offset = 0;
        }

    } // FeatureSetQueryParameters
    
    /**************************************************************************/

    /**
     * {@link CursorWrapper} subclass that provides direct access to the
     * {@link FeatureSet} object described by the results.
     * 
     * @author Developer
     */
    public static interface FeatureSetCursor extends RowIterator {
        public final static FeatureSetCursor EMPTY = new FeatureSetCursor() {
            @Override
            public FeatureSet get() { return null; }
            @Override
            public boolean moveToNext() { return false; }
            @Override
            public void close() {}
            @Override
            public boolean isClosed() { return false; }
        };

        /**
         * Returns the {@link FeatureSet} corresponding to the current row.
         * 
         * @return  The {@link FeatureSet} corresponding to the current row.
         */
        public abstract FeatureSet get();
    } // FeatureSetCursor
        
} // FeatureDataStore

