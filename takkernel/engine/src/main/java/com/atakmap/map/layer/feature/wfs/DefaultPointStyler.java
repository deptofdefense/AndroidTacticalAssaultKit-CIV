package com.atakmap.map.layer.feature.wfs;

import com.atakmap.map.layer.feature.AttributeSet;
import com.atakmap.map.layer.feature.style.Style;
import com.atakmap.map.layer.feature.style.IconPointStyle;

public class DefaultPointStyler implements WFSSchemaFeatureStyler {
    private final String iconUri;
    private final int color;
    private final int size;
    private final boolean label;
    
    private Style instance;

    public DefaultPointStyler(String iconUri, int color, int size, boolean label) {
        this.iconUri = iconUri;
        this.color = color;
        this.size = size;
        this.label = label;
    }

    @Override
    public Style getStyle(AttributeSet metadata) {
        if(this.instance == null) {
            if(this.size == 0)
                this.instance = new IconPointStyle(this.color, this.iconUri);
            else
                this.instance = new IconPointStyle(this.color, this.iconUri, this.size, this.size, 0, 0, 0.0f, false);
        }
        return this.instance;
    }
    
    @Override
    public String toString() {
        return "{color=" + Integer.toString(color, 16) + ",icon=" + iconUri + ",size=" + size + ",label=" + label + "}";
    }
}
