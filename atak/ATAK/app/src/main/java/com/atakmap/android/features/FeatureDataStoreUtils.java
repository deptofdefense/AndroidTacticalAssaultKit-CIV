
package com.atakmap.android.features;

import com.atakmap.coremap.log.Log;
import com.atakmap.map.layer.feature.Feature;
import com.atakmap.map.layer.feature.FeatureCursor;
import com.atakmap.map.layer.feature.FeatureDataStore;
import com.atakmap.map.layer.feature.FeatureDataStore.FeatureQueryParameters;
import com.atakmap.map.layer.feature.FeatureDataStore.FeatureSetCursor;
import com.atakmap.map.layer.feature.FeatureDataStore.FeatureSetQueryParameters;
import com.atakmap.map.layer.feature.FeatureSet;
import com.atakmap.map.layer.feature.geometry.Envelope;

import java.util.Collections;

/**
 * Helper methods for feature data store operations and queries
 */
public class FeatureDataStoreUtils {

    private static final String TAG = "FeatureDataStoreUtils";

    /**
     * For a given feature data store, query all feature sets and build
     * a bounds envelope which encompasses all features
     *
     * @param dataStore Feature data store
     * @param params Feature set query params (null to query all)
     * @return Bounds envelope or null if failed
     */
    public static Envelope buildEnvelope(FeatureDataStore dataStore,
            FeatureSetQueryParameters params) {
        Envelope.Builder bounds = new Envelope.Builder();
        FeatureSetCursor fsc = null;
        try {
            // Get all feature sets for this file
            fsc = dataStore.queryFeatureSets(params);
            while (fsc != null && fsc.moveToNext()) {
                FeatureSet fs = fsc.get();
                if (fs == null)
                    continue;

                // Build features envelope
                addToEnvelope(dataStore, fs, bounds);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to build features envelope for "
                    + dataStore.getUri(), e);
        } finally {
            if (fsc != null)
                fsc.close();
        }
        return bounds.build();
    }

    /**
     * Given a datastore, build the envelope for it
     * @param dataStore the datastore
     * @return the envelope that contains the datastore
     */
    public static Envelope buildEnvelope(FeatureDataStore dataStore) {
        return buildEnvelope(dataStore, (FeatureSetQueryParameters) null);
    }

    /**
     * Build an envelope for a single feature set by querying its features
     *
     * @param dataStore The associated data store
     * @param featureSet Feature set
     * @return Envelope bounds or null if failed
     */
    public static Envelope buildEnvelope(FeatureDataStore dataStore,
            FeatureSet featureSet) {
        Envelope.Builder bounds = new Envelope.Builder();
        addToEnvelope(dataStore, featureSet, bounds);
        return bounds.build();
    }

    /**
     * Add a feature set to an existing envelope builder
     *
     * @param dataStore The associated data store
     * @param featureSet Feature set
     * @param bounds Envelope bounds
     */
    public static void addToEnvelope(FeatureDataStore dataStore,
            FeatureSet featureSet, Envelope.Builder bounds) {
        if (featureSet == null)
            return;
        FeatureCursor fec = null;
        try {
            FeatureQueryParameters params = new FeatureQueryParameters();
            params.featureSetIds = Collections.singletonList(
                    featureSet.getId());
            fec = dataStore.queryFeatures(params);
            while (fec != null && fec.moveToNext()) {
                Feature feat = fec.get();
                if (feat == null || feat.getGeometry() == null)
                    continue;
                bounds.add(feat.getGeometry().getEnvelope());
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to add to feature set envelope: "
                    + featureSet.getName(), e);
        } finally {
            if (fec != null)
                fec.close();
        }
    }
}
