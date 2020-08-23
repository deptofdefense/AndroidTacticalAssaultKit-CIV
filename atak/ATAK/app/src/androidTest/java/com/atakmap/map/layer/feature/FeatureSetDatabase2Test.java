
package com.atakmap.map.layer.feature;

import com.atakmap.android.androidtest.ATAKInstrumentedTest;
import com.atakmap.map.layer.feature.datastore.FeatureSetDatabase2;
import com.atakmap.map.layer.feature.datastore.RuntimeFeatureDataStore2;
import com.atakmap.map.layer.feature.geometry.LineString;
import com.atakmap.map.layer.feature.geometry.Point;

import junit.framework.Assert;

import org.junit.Test;

import java.io.File;

public class FeatureSetDatabase2Test extends FeatureDataStore3Test {

    @Override
    protected FeatureDataStore3 createDataStore() {
        //File f = new File("/sdcard/fsdb2test.sqlite");
        //f.delete();

        //final FeatureDataStore2 retval;

        return new FeatureSetDatabase2(null);
    }

}
