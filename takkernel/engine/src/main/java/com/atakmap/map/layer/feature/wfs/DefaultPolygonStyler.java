package com.atakmap.map.layer.feature.wfs;

import com.atakmap.map.layer.feature.AttributeSet;
import com.atakmap.map.layer.feature.style.Style;
import com.atakmap.map.layer.feature.style.BasicFillStyle;
import com.atakmap.map.layer.feature.style.BasicStrokeStyle;
import com.atakmap.map.layer.feature.style.CompositeStyle;

public final class DefaultPolygonStyler implements WFSSchemaFeatureStyler {

    private final int strokeColor;
    private final float strokeWidth;
    private final int fillColor;
    private Style instance;

    public DefaultPolygonStyler(int strokeColor, float strokeWidth, int fillColor) {
        this.strokeColor = strokeColor;
        this.strokeWidth = strokeWidth;
        this.fillColor = fillColor;
        
    }
    @Override
    public Style getStyle(AttributeSet metadata) {
        if(this.instance == null) {
            if(this.fillColor != 0 && this.strokeColor != 0) {
                this.instance = new CompositeStyle(new Style[]
                        {
                        new BasicFillStyle(this.fillColor),
                        new BasicStrokeStyle(this.strokeColor, this.strokeWidth),
                        });
            } else if(this.fillColor != 0) {
                this.instance = new BasicFillStyle(this.fillColor);
            } else if(this.strokeColor != 0) {
                this.instance = new BasicStrokeStyle(this.strokeColor, this.strokeWidth);
            }
        }
        return this.instance;
    }

    @Override
    public String toString() {
        return "{stroke={color=" + Integer.toString(strokeColor, 16) + ",width=" + strokeWidth + "},fill={color=" + Integer.toString(fillColor, 16) + "}}";
    }
}
