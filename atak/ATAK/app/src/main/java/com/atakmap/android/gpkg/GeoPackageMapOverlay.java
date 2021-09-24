
package com.atakmap.android.gpkg;

import android.content.Context;

import com.atakmap.android.features.FeatureDataStoreDeepMapItemQuery;
import com.atakmap.android.features.FeatureDataStoreMapOverlay;
import com.atakmap.map.layer.feature.FeatureLayer3;

import java.util.Collections;
import java.util.Set;

public class GeoPackageMapOverlay extends FeatureDataStoreMapOverlay {

    GeoPackageMapOverlay(Context context, FeatureLayer3 layer,
            String iconUri) {
        super(context, layer.getDataStore(), null, layer.getName(), iconUri,
                new FeatureDataStoreDeepMapItemQuery(layer),
                GeoPackageImporter.CONTENT_TYPE,
                GeoPackageImporter.MIME_TYPE);
    }

    @Override
    protected Set<String> getChildFiles() {
        return Collections.singleton(this.spatialDb.getUri());
    }
}
