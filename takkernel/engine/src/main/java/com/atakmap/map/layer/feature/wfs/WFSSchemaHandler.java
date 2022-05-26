package com.atakmap.map.layer.feature.wfs;

import com.atakmap.map.layer.feature.AttributeSet;
import com.atakmap.map.layer.feature.style.Style;
import com.atakmap.map.layer.feature.geometry.Geometry;

public interface WFSSchemaHandler {
    public String getName();
    public String getUri();
    public boolean ignoreLayer(String layer);
    public boolean isLayerVisible(String layer);
    public String getLayerName(String remote);
    public boolean ignoreFeature(String layer, AttributeSet metadata);
    public boolean isFeatureVisible(String layer, AttributeSet metadata);
    public String getFeatureName(String layer, AttributeSet metadata);
    public Style getFeatureStyle(String layer, AttributeSet metadata, Class<? extends Geometry> geomType);
    
    // XXX - don't display children in overlay manager 
}
