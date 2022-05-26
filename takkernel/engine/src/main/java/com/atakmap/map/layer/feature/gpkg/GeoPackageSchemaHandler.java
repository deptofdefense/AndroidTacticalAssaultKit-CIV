package com.atakmap.map.layer.feature.gpkg;

import java.util.Set;

import com.atakmap.map.gpkg.GeoPackage;
import com.atakmap.map.layer.feature.AttributeSet;
import com.atakmap.map.layer.feature.FeatureDataStore.FeatureQueryParameters;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.spi.InteractiveServiceProvider;


public
interface GeoPackageSchemaHandler
  {
    public
    interface Factory extends InteractiveServiceProvider<GeoPackageSchemaHandler, GeoPackage>
      {
        public
        int
        getPriority ();

      }


    /**
     * Callback interface providing notification when the definition of features
     * (e.g., styling, geometry, attributes) has changed.
     */
    public
    interface OnFeatureDefinitionsChangedListener
      {
        /**
         * This method is invoked when feature definitions have changed.
         * 
         * @param dataStore The data store
         */
        public
        void
        onFeatureDefinitionsChanged (GeoPackageSchemaHandler handler);
    }
    

    /**
     * Adds the specified {@link OnFeatureDefinitionsChangedListener}.
     * 
     * @param l The listener
     */
    public
    void
    addOnFeatureDefinitionsChangedListener (OnFeatureDefinitionsChangedListener l);
    
    public
    boolean
    getDefaultFeatureSetVisibility(String layer, String featureSet);

    public
    Class<? extends Geometry>
    getGeometryType (String layer);


    /**
     * Returns the feature sets to be associated with the specified feature
     * layer.
     * 
     * @param layerName The layer name
     * 
     * @return  The names of the feature sets to be associated with the
     *          specified feature layer.
     */
    public
    Set<String>
    getLayerFeatureSets (String layerName);


    /**
     * Returns the maximum display resolution of the layer, in meters per pixel.
     * Smaller values equate to higher resolutions.
     * 
     * @return  The maximum display resolution of the layer (or NaN for no
     *          maximum).
     */
    public
    double
    getMaxResolution (String layer);


    /**
     * Returns the minimum display resolution of the layer, in meters per pixel.
     * Larger values equate to lower resolutions.
     * 
     * @return  The minimum display resolution of the layer (or NaN for no
     *          minimum).
     */
    public
    double
    getMinResolution (String layer);


    /**
     * @return  The type of the Extended GeoPackage.
     **/
    public
    String
    getSchemaType ();


    public
    long
    getSchemaVersion ();


    public
    boolean
    ignoreFeature (String layer,
                   AttributeSet metadata);


    public
    boolean
    ignoreLayer (String layer);


    public
    boolean
    isFeatureVisible (String layer,
                      AttributeSet metadata);


    public
    boolean
    isLayerVisible (String layer);


    /**
     *  @param layer    The layer to be queried for features.
     *  @param params   The constraints on the features to be selected from the
     *                  supplied layer (or null for all features).
     *  @return         A cursor for the features in the supplied layer that
     *                  satisfy the supplied FeatureQueryParameters.
     **/
    public
    GeoPackageFeatureCursor
    queryFeatures (String layer,
                   FeatureQueryParameters params);


    /**
     *  @param layer    The layer to be queried for feature count.
     *  @param params   The constraints on the features to be selected from the
     *                  supplied layer (or null for all features).
     *  @return         The number of features in the supplied layer that
     *                  satisfy the supplied FeatureQueryParameters.
     **/
    public
    int
    queryFeaturesCount (String layer,
                        FeatureQueryParameters params);
    
    
    /**
     * Removes the specified {@link OnFeatureDefinitionsChangedListener}.
     * 
     * @param l The listener
     */
    public
    void
    removeOnFeatureDefinitionsChangedListener (OnFeatureDefinitionsChangedListener l);
  }
