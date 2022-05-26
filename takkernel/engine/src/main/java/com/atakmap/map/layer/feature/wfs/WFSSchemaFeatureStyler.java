package com.atakmap.map.layer.feature.wfs;

import com.atakmap.map.layer.feature.AttributeSet;
import com.atakmap.map.layer.feature.style.Style;

public interface WFSSchemaFeatureStyler {
    public Style getStyle(AttributeSet metadata);
}
