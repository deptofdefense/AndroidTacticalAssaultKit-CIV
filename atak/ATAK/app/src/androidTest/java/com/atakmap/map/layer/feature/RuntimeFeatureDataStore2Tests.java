
package com.atakmap.map.layer.feature;

import com.atakmap.map.layer.feature.datastore.RuntimeFeatureDataStore2;

public class RuntimeFeatureDataStore2Tests extends FeatureDataStore3Test {

    @Override
    protected FeatureDataStore3 createDataStore() {
        return new RuntimeFeatureDataStore2();
    }

}
