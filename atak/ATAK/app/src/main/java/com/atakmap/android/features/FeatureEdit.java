
package com.atakmap.android.features;

import com.atakmap.android.hierarchy.action.Action;
import com.atakmap.map.layer.feature.FeatureDataStore2;
import com.atakmap.map.layer.feature.FeatureDataStore2.FeatureQueryParameters;

import androidx.annotation.NonNull;

/**
 * Action for feature-based items that support bulk editing
 */
interface FeatureEdit extends Action {

    /**
     * Get the feature database associated with this item
     * @return Feature database
     */
    @NonNull
    FeatureDataStore2 getFeatureDatabase();

    /**
     * Get the query parameters for obtaining this set of features
     * @return Query parameters
     */
    @NonNull
    FeatureQueryParameters getFeatureQueryParams();
}
