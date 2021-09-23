package com.atakmap.map.layer.feature;

import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.style.Style;
import com.atakmap.map.layer.feature.Feature.AltitudeMode;

public interface FeatureDataStore3 extends FeatureDataStore2 {

    public final static int PROPERTY_FEATURE_ALTITUDE_MODE = 0x10;
    public final static int PROPERTY_FEATURE_EXTRUDE = 0x20;

    public void updateFeature(long fid, int updatePropertyMask, String name, Geometry geometry,  Style style, AttributeSet attributes, AltitudeMode altitudeMode, double extrude, int attrUpdateType) throws DataStoreException;

}
