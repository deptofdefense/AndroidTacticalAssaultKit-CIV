package com.atakmap.map.layer.feature.wfs;

import com.atakmap.map.layer.feature.AttributeSet;
import com.atakmap.map.layer.feature.style.Style;
import com.atakmap.map.layer.feature.style.BasicStrokeStyle;

public final class DefaultLineStyler implements WFSSchemaFeatureStyler {

    private final int color;
    private final float width;
    private Style instance;
    
    public DefaultLineStyler(int color, float width) {
        this.color = color;
        this.width = width;
    }

    @Override
    public Style getStyle(AttributeSet metadata) {
        if(this.instance == null) {
            this.instance = new BasicStrokeStyle(this.color, this.width);
        }
        return this.instance;
    }
    
    @Override
    public String toString() {
        return "{stroke={color=" + Integer.toString(color, 16) + ",width=" + width + "}}";
    }

}
