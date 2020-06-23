package com.atakmap.map.layer.feature.wfs;

import com.atakmap.map.layer.feature.AttributeSet;
import com.atakmap.map.layer.feature.style.Style;
import com.atakmap.map.layer.feature.geometry.Geometry;

public interface WFSSchemaFeatureFilter extends AttributeSetFilter {
    
    public boolean shouldIgnore();
    public boolean isVisible();
    public Style getStyle(AttributeSet metadata, Class<? extends Geometry> geomType);
    public String getName(AttributeSet metadata);
}
