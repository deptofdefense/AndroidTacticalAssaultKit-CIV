
package com.atakmap.map.layer.feature;

import com.atakmap.android.androidtest.ATAKInstrumentedTest;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.layer.feature.geometry.Point;
import com.atakmap.map.layer.feature.style.CompositeStyle;
import com.atakmap.map.layer.feature.style.IconPointStyle;
import com.atakmap.map.layer.feature.style.LabelPointStyle;
import com.atakmap.map.layer.feature.style.Style;

import junit.framework.Assert;

import org.junit.Test;

import java.util.Collections;

public class RuntimeFeatureDataStoreTests extends ATAKInstrumentedTest {
    @Test
    public void query_with_spatial_filter_updated_point() {
        // the feature data store is a container for Feature objects. It
        // provides database type semantics for managing membership, iteration
        // and searching.
        final FeatureDataStore retval;

        // the library provides several implementations of the FeatureDataStore
        // interface.  The implementation, RuntimeFeatureDataStore, is an
        // in-memory implementation
        retval = new RuntimeFeatureDataStore();

        // all features must be members of a Feature Set. The Feature Set is
        // some logical grouping of features. Hierarchical representations may
        // be emulated by specifying Feature Sets as paths
        FeatureSet buildings = retval.insertFeatureSet(
                "Hello3D", // what generated the features
                "Buildings", // the type of the featuers
                "Buildings", // the feature set name
                Double.MAX_VALUE, // min resolution threshold
                0d, // max resolution threshold
                true);

        retval.setFeatureSetVisible(buildings.getId(), true);

        // Example on how to draw a style composed of an icon point style and a label point style
        // to the screen.
        IconPointStyle style_icon = new IconPointStyle(0xFF00FFFF,
                "https://maps.google.com/mapfiles/kml/paddle/wht-blank.png",
                0, 0, 0, 0, 0, true);

        LabelPointStyle style_label = new LabelPointStyle(
                "Stylized Text", 0xFFFFFF00, 0xFF000000,
                LabelPointStyle.ScrollMode.OFF, 0f, 0, 100,
                0, false);

        CompositeStyle style_composite = new CompositeStyle(new Style[] {
                style_icon, style_label
        });

        final Feature f = retval.insertFeature(
                buildings.getId(),
                "Landmark with stylized Text",
                new Point(-78.78946, 35.77202),
                style_composite,
                null,
                true);

        FeatureDataStore.FeatureQueryParameters params = new FeatureDataStore.FeatureQueryParameters();
        params.maxResolution = 1.204918672475685;
        params.spatialFilter = new FeatureDataStore.FeatureQueryParameters.RegionSpatialFilter(
                new GeoPoint(35.77466250764105, -78.79421174316589),
                new GeoPoint(35.76361300654775, -78.77226063807349));

        Assert.assertEquals(1, retval.queryFeaturesCount(params));

        int i = 1;

        final GeoPoint gp = new GeoPoint(35.77202, -78.78946 + (i * .00001));
        final String name = "test change - " + (i++);
        long fid = f.getId();
        retval.beginBulkModification();
        retval.updateFeature(fid, name);
        retval.updateFeature(fid,
                new Point(gp.getLongitude(), gp.getLatitude()));
        retval.endBulkModification(true);

        Assert.assertEquals(1, retval.queryFeaturesCount(params));
    }

    @Test
    public void atak15243() {
        RuntimeFeatureDataStore ds = new RuntimeFeatureDataStore();
        final FeatureSet fs = ds.insertFeatureSet("provider", "type", "name",
                Double.MAX_VALUE, 0d, true);
        ds.insertFeature(fs.id, "Test%3a", new Point(0, 0), null,
                new AttributeSet(), false);
        ds.insertFeature(fs.id, "TestX3a", new Point(0, 0), null,
                new AttributeSet(), false);
        ds.insertFeature(fs.id, "TestABC3a", new Point(0, 0), null,
                new AttributeSet(), false);

        FeatureCursor result;

        result = null;
        try {
            FeatureDataStore.FeatureQueryParameters params = new FeatureDataStore.FeatureQueryParameters();
            params.featureNames = Collections.singleton("Test\\%3a");
            final int count = ds.queryFeaturesCount(params);
            Assert.assertEquals(1, count);
        } finally {
            if (result != null)
                result.close();
        }

        result = null;
        try {
            FeatureDataStore.FeatureQueryParameters params = new FeatureDataStore.FeatureQueryParameters();
            params.featureNames = Collections.singleton("Test%3a");
            final int count = ds.queryFeaturesCount(params);
            Assert.assertEquals(3, count);
        } finally {
            if (result != null)
                result.close();
        }
    }
}
