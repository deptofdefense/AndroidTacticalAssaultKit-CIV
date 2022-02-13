
package com.atakmap.map.layer.feature.ogr.style;


public class FeatureStyle {

    public Brush brush;
    public Pen pen;
    public Symbol symbol;
    public Label label;

    public FeatureStyle() {
        this.brush = null;
        this.pen = null;
        this.symbol = null;
        this.label = null;
    }

    public void clear() {
        this.brush = null;
        this.pen = null;
        this.symbol = null;
        this.label = null;
    }

    public boolean hasStyle() {
        return (this.brush != null ||
                this.pen != null ||
                this.symbol != null || this.label != null);
    }

    public void pushDrawingTool(DrawingTool tool) {
        if (tool instanceof Pen)
            this.pen = (Pen) tool;
        else if (tool instanceof Brush)
            this.brush = (Brush) tool;
        else if (tool instanceof Symbol)
            this.symbol = (Symbol) tool;
        else if (tool instanceof Label)
            this.label = (Label) tool;
    }
}
