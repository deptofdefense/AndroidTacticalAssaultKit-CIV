
package com.atakmap.map.layer.feature;

import com.atakmap.android.androidtest.ATAKInstrumentedTest;
import com.atakmap.map.layer.feature.geometry.LineString;
import com.atakmap.map.layer.feature.geometry.Point;

import junit.framework.Assert;

import org.junit.Test;

public abstract class FeatureDataStore3Test extends ATAKInstrumentedTest {

    abstract protected FeatureDataStore3 createDataStore();

    @Test
    public void testAltitudeModeLineString() throws DataStoreException {

        FeatureDataStore3 retval = createDataStore();

        long fsid = retval.insertFeatureSet(new FeatureSet(
                "Hello3D", // what generated the features
                "Buildings", // the type of the featuers
                "Buildings", // the feature set name
                Double.MAX_VALUE, // min resolution threshold
                0d));

        retval.setFeatureSetVisible(fsid, true);

        LineString ls = new LineString(3);
        ls.addPoint(-78.78944, 35.77100, 201);
        ls.addPoint(-78.78900, 35.77120, 260);
        ls.addPoint(-78.78877, 35.77000, 101);

        long id_test_relative = retval.insertFeature(new Feature(fsid,
                "route", ls, null, null, Feature.AltitudeMode.Relative, 0.0));

        long id_test_clamp = retval.insertFeature(new Feature(fsid,
                "route", ls, null, null, Feature.AltitudeMode.ClampToGround,
                0.0));

        long id_test_absolute = retval.insertFeature(new Feature(fsid,
                "route", ls, null, null, Feature.AltitudeMode.Absolute, 0.0));

        Assert.assertEquals("relative", Feature.AltitudeMode.Relative,
                Utils.getFeature(retval, id_test_relative).getAltitudeMode());
        Assert.assertEquals("clamp", Feature.AltitudeMode.ClampToGround,
                Utils.getFeature(retval, id_test_clamp).getAltitudeMode());
        Assert.assertEquals("absolute", Feature.AltitudeMode.Absolute,
                Utils.getFeature(retval, id_test_absolute).getAltitudeMode());
    }

    @Test
    public void testAltitudeModePoint() throws DataStoreException {
        FeatureDataStore3 retval = createDataStore();

        long fsid = retval.insertFeatureSet(new FeatureSet(
                "Hello3D", // what generated the features
                "Buildings", // the type of the featuers
                "Buildings", // the feature set name
                Double.MAX_VALUE, // min resolution threshold
                0d));

        retval.setFeatureSetVisible(fsid, true);

        Point point = new Point(1, 1);

        long id_test_relative = retval.insertFeature(new Feature(fsid,
                "route", point, null, null, Feature.AltitudeMode.Relative,
                0.0));

        long id_test_clamp = retval.insertFeature(new Feature(fsid,
                "route", point, null, null, Feature.AltitudeMode.ClampToGround,
                0.0));

        long id_test_absolute = retval.insertFeature(new Feature(fsid,
                "route", point, null, null, Feature.AltitudeMode.Absolute,
                0.0));

        Assert.assertEquals("relative", Feature.AltitudeMode.Relative,
                Utils.getFeature(retval, id_test_relative).getAltitudeMode());
        Assert.assertEquals("clamp", Feature.AltitudeMode.ClampToGround,
                Utils.getFeature(retval, id_test_clamp).getAltitudeMode());
        Assert.assertEquals("absolute", Feature.AltitudeMode.Absolute,
                Utils.getFeature(retval, id_test_absolute).getAltitudeMode());

        retval.updateFeature(id_test_absolute,
                FeatureDataStore3.PROPERTY_FEATURE_ALTITUDE_MODE, null, null,
                null, null, Feature.AltitudeMode.ClampToGround, 1.0, 0);
        Assert.assertEquals("update_absolute_to_clamp",
                Feature.AltitudeMode.ClampToGround,
                Utils.getFeature(retval, id_test_absolute).getAltitudeMode());

        retval.updateFeature(id_test_absolute,
                FeatureDataStore3.PROPERTY_FEATURE_ALTITUDE_MODE, null, null,
                null, null, Feature.AltitudeMode.Relative, 1.0, 0);
        Assert.assertEquals("update_clamp_to_relative",
                Feature.AltitudeMode.Relative,
                Utils.getFeature(retval, id_test_absolute).getAltitudeMode());

        retval.updateFeature(id_test_absolute,
                FeatureDataStore3.PROPERTY_FEATURE_ALTITUDE_MODE, null, null,
                null, null, Feature.AltitudeMode.Absolute, 1.0, 0);
        Assert.assertEquals("update_relative_to_absolute",
                Feature.AltitudeMode.Absolute,
                Utils.getFeature(retval, id_test_absolute).getAltitudeMode());

        retval.updateFeature(id_test_absolute,
                FeatureDataStore3.PROPERTY_FEATURE_EXTRUDE, null, null, null,
                null, Feature.AltitudeMode.Absolute, 1d, 0);
        Assert.assertEquals("extrude", 1d,
                Utils.getFeature(retval, id_test_absolute).getExtrude(),
                0.001d);

        retval.updateFeature(id_test_absolute,
                FeatureDataStore3.PROPERTY_FEATURE_EXTRUDE, null, null, null,
                null, Feature.AltitudeMode.Absolute, -1d, 0);
        Assert.assertEquals("extrude", -1d,
                Utils.getFeature(retval, id_test_absolute).getExtrude(),
                0.001d);

        retval.updateFeature(id_test_absolute,
                FeatureDataStore3.PROPERTY_FEATURE_EXTRUDE, null, null, null,
                null, Feature.AltitudeMode.Absolute, 0d, 0);
        Assert.assertEquals("extrude", 0d,
                Utils.getFeature(retval, id_test_absolute).getExtrude(),
                0.001d);

    }

    @Test
    public void testAltitudeModePoint2() throws DataStoreException {
        FeatureDataStore3 retval = createDataStore();

        long fsid = retval.insertFeatureSet(new FeatureSet(
                "Hello3D", // what generated the features
                "Buildings", // the type of the featuers
                "Buildings", // the feature set name
                Double.MAX_VALUE, // min resolution threshold
                0d));

        retval.setFeatureSetVisible(fsid, true);

        Point point = new Point(1, 1);

        long id_test_absolute = retval.insertFeature(new Feature(fsid,
                "route", point, null, null, Feature.AltitudeMode.Absolute,
                0.0));

        Assert.assertEquals("absolute", Feature.AltitudeMode.Absolute,
                Utils.getFeature(retval, id_test_absolute).getAltitudeMode());
        retval.updateFeature(id_test_absolute,
                FeatureDataStore3.PROPERTY_FEATURE_NAME, "point", null, null,
                null, Feature.AltitudeMode.ClampToGround, -1.0, 0);

        Assert.assertEquals("update_no_change_altitudeMode",
                Feature.AltitudeMode.Absolute,
                Utils.getFeature(retval, id_test_absolute).getAltitudeMode());
        Assert.assertEquals("update_no_change_extrude", 0d,
                Utils.getFeature(retval, id_test_absolute).getExtrude(),
                0.001d);
        Assert.assertEquals("update_nname", "point",
                Utils.getFeature(retval, id_test_absolute).getName());
    }
}
