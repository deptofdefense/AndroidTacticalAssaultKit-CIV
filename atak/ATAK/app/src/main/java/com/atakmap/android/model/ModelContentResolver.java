
package com.atakmap.android.model;

import com.atakmap.android.data.FileContentResolver;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.model.opengl.GLModelLayer;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.map.layer.feature.Feature;
import com.atakmap.map.layer.feature.FeatureCursor;
import com.atakmap.map.layer.feature.FeatureDataStore2;
import com.atakmap.map.layer.feature.FeatureDataStore2.FeatureQueryParameters;
import com.atakmap.map.layer.feature.FeatureDataStore2.FeatureSetQueryParameters;
import com.atakmap.map.layer.feature.FeatureSet;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.model.ModelInfo;

import java.io.File;
import java.util.Collections;

/**
 * Used to map model files to associated metadata (i.e. geo bounds, visibility)
 */
public class ModelContentResolver extends FileContentResolver {

    private static final String TAG = "ModelContentResolver";

    private final MapView _mapView;
    private final FeatureDataStore2 _dataStore;

    public ModelContentResolver(MapView mv, FeatureDataStore2 dataStore) {
        super(null);
        _mapView = mv;
        _dataStore = dataStore;
    }

    /**
     * Add or update the model handler for a specific file
     *
     * @param featureSet Model feature set
     * @param bounds Bounds envelope (sum of feature bounds)
     */
    void addModelHandler(FeatureSet featureSet, Envelope bounds) {
        if (featureSet == null || featureSet.getName() == null)
            return;
        String path = featureSet.getName();
        File f = new File(path);
        if (!IOProviderFactory.exists(f))
            return;
        addHandler(new ModelContentHandler(_mapView, f, _dataStore,
                featureSet, bounds));
    }

    /**
     * Add or update the model handler and query its feature bounds
     * Should only be called on non-UI thread since feature queries can be slow
     *
     * @param featureSet Model feature set
     */
    void addModelHandler(FeatureSet featureSet) {
        if (featureSet == null)
            return;

        // Need to resolve bounds of the entire file
        FeatureCursor c = null;
        Envelope.Builder bounds = new Envelope.Builder();
        try {
            FeatureQueryParameters params = new FeatureQueryParameters();
            params.featureSetFilter = new FeatureSetQueryParameters();
            params.featureSetFilter.ids = Collections.singleton(
                    featureSet.getId());
            c = _dataStore.queryFeatures(params);
            while (c != null && c.moveToNext()) {
                Feature fe = c.get();
                if (fe == null)
                    continue;
                ModelInfo info = GLModelLayer.getModelInfo(fe);
                if (info != null && info.srid != -1) {
                    Geometry g = fe.getGeometry();
                    if (g != null)
                        bounds.add(g.getEnvelope());
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to query feature sets", e);
        } finally {
            if (c != null)
                c.close();
        }
        addModelHandler(featureSet, bounds.build());
    }
}
