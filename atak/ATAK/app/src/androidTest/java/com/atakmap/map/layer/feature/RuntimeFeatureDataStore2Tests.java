
package com.atakmap.map.layer.feature;

import com.atakmap.map.layer.feature.datastore.FeatureSetDatabase2;
import com.atakmap.map.layer.feature.datastore.RuntimeFeatureDataStore2;
import com.atakmap.map.layer.feature.geometry.LineString;
import com.atakmap.map.layer.feature.geometry.Point;

import junit.framework.Assert;

import org.junit.Test;

import java.io.File;

public class RuntimeFeatureDataStore2Tests extends FeatureDataStore3Test {

    @Override
    protected FeatureDataStore3 createDataStore() {
        return new RuntimeFeatureDataStore2();
    }

}
