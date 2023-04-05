
package com.atakmap.android.features;

import com.atakmap.map.layer.feature.DataStoreException;
import com.atakmap.map.layer.feature.FeatureCursor;
import com.atakmap.map.layer.feature.FeatureDataStore2;
import com.atakmap.map.layer.feature.FeatureDataStore2.FeatureQueryParameters;
import com.atakmap.map.layer.feature.FeatureDataStore2.FeatureSetQueryParameters;

import java.util.Objects;
import java.util.Set;

import androidx.annotation.NonNull;

/**
 * Feature edit request consisting of a database and feature query parameters
 */
class FeatureEditRequest {

    public final FeatureDataStore2 database;
    public final FeatureQueryParameters params;

    FeatureEditRequest(@NonNull FeatureDataStore2 db,
            @NonNull FeatureQueryParameters params) {
        this.database = db;
        this.params = params;
    }

    /**
     * Shortcut method for getting the feature set IDs in this query
     * @return Feature set IDs or null if not defined
     */
    Set<Long> getFeatureSetIds() {
        return params.featureSetFilter != null
                ? params.featureSetFilter.ids
                : null;
    }

    /**
     * Query features in this request
     * @return Feature cursor
     * @throws DataStoreException If something went wrong
     */
    FeatureCursor queryFeatures() throws DataStoreException {
        return database.queryFeatures(params);
    }

    /**
     * Equals checking with options to exclude "deep" parameters
     * @param other Other feature edit request
     * @param excludeIDs True to exclude feature IDs
     * @param excludeSetIDs True to exclude feature set IDs
     * @return True if equal
     */
    boolean equals(FeatureEditRequest other,
            boolean excludeIDs,
            boolean excludeSetIDs) {
        return Objects.equals(database.getUri(), other.database.getUri()) &&
                paramsEquals(params, other.params, excludeIDs, excludeSetIDs);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        FeatureEditRequest that = (FeatureEditRequest) o;
        return Objects.equals(database.getUri(), that.database.getUri()) &&
                paramsEquals(params, that.params, false, false);
    }

    @Override
    public int hashCode() {
        return Objects.hash(database.getUri(), paramsHash(params));
    }

    /**
     * Check if two feature query parameters are equal
     * @param p1 Parameters 1
     * @param p2 Parameters 2
     * @param excludeIDs True ignore feature ID equality check
     * @param excludeSetIDs True to ignore feature set ID equality check
     * @return True if equal
     */
    private boolean paramsEquals(FeatureQueryParameters p1,
            FeatureQueryParameters p2,
            boolean excludeIDs,
            boolean excludeSetIDs) {
        if (p1 == p2)
            return true;
        return p1.visibleOnly == p2.visibleOnly &&
                p1.minimumTimestamp == p2.minimumTimestamp &&
                p1.maximumTimestamp == p2.maximumTimestamp &&
                p1.ignoredFeatureProperties == p2.ignoredFeatureProperties &&
                p1.limit == p2.limit &&
                p1.offset == p2.offset &&
                p1.timeout == p2.timeout &&
                Objects.equals(p1.names, p2.names) &&
                Objects.equals(p1.geometryTypes, p2.geometryTypes) &&
                Objects.equals(p1.attributeFilters, p2.attributeFilters) &&
                Objects.equals(p1.spatialFilter, p2.spatialFilter) &&
                p1.altitudeMode == p2.altitudeMode &&
                Objects.equals(p1.spatialOps, p2.spatialOps) &&
                Objects.equals(p1.order, p2.order) &&
                (excludeIDs || Objects.equals(p1.ids, p2.ids)) &&
                paramsEquals(p1.featureSetFilter, p2.featureSetFilter,
                        excludeSetIDs);
    }

    /**
     * Check if two feature set query parameters are equal
     * @param p1 Parameters 1
     * @param p2 Parameters 2
     * @param excludeIDs True to ignore feature set ID equality check
     * @return True if equal
     */
    private boolean paramsEquals(FeatureSetQueryParameters p1,
            FeatureSetQueryParameters p2,
            boolean excludeIDs) {
        if (p1 == p2)
            return true;
        if (p1 == null || p2 == null)
            return false;
        return Double.compare(p2.minResolution, p1.minResolution) == 0 &&
                Double.compare(p2.maxResolution, p1.maxResolution) == 0 &&
                p1.visibleOnly == p2.visibleOnly &&
                p1.limit == p2.limit &&
                p1.offset == p2.offset &&
                (excludeIDs || Objects.equals(p1.ids, p2.ids)) &&
                Objects.equals(p1.names, p2.names) &&
                Objects.equals(p1.types, p2.types) &&
                Objects.equals(p1.providers, p2.providers);
    }

    /**
     * Get a usable hash code for feature query parameters
     * @param p Parameters
     * @return Hash code
     */
    private int paramsHash(FeatureQueryParameters p) {
        return Objects.hash(paramsHash(p.featureSetFilter), p.ids, p.names,
                p.geometryTypes, p.attributeFilters, p.visibleOnly,
                p.spatialFilter, p.minimumTimestamp, p.maximumTimestamp,
                p.altitudeMode, p.ignoredFeatureProperties, p.spatialOps,
                p.order, p.limit, p.offset, p.timeout);
    }

    /**
     * Get a usable hash code for feature set query parameters
     * @param p Parameters
     * @return Hash code
     */
    private int paramsHash(FeatureSetQueryParameters p) {
        if (p == null)
            return 0;
        return Objects.hash(p.ids, p.names, p.types, p.providers,
                p.minResolution,
                p.maxResolution, p.visibleOnly, p.limit, p.offset);
    }
}
