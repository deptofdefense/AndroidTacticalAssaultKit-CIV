
package com.atakmap.map.layer.feature;

import com.atakmap.map.layer.feature.datastore.FeatureSetDatabase2;

public class FeatureSetDatabase2Test extends FeatureDataStore3Test {

    @Override
    protected FeatureDataStore3 createDataStore() {
        //File f = new File("/sdcard/fsdb2test.sqlite");
        //f.delete();

        //final FeatureDataStore2 retval;

        return new FeatureSetDatabase2(null);
    }

}
