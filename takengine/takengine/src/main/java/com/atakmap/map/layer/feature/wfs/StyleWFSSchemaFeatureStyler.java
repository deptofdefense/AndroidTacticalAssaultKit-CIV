package com.atakmap.map.layer.feature.wfs;

import com.atakmap.map.layer.feature.AttributeSet;
import com.atakmap.map.layer.feature.style.Style;

public final class StyleWFSSchemaFeatureStyler implements WFSSchemaFeatureStyler {
    private final Style style;
    
    public StyleWFSSchemaFeatureStyler(Style style) {
        this.style = style;
    }

    @Override
    public Style getStyle(AttributeSet metadata) {
        return this.style;
    }
}
