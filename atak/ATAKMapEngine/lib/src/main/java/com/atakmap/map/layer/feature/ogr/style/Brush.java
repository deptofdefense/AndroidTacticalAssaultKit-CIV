
package com.atakmap.map.layer.feature.ogr.style;

public class Brush implements DrawingTool {

    public final static String PARAM_BRUSH_FORE_COLOR = "fc";
    public final static String PARAM_BRUSH_BACK_COLOR = "bc";
    public final static String PARAM_BRUSH_NAME = "id";
    public final static String PARAM_ANGLE = "a";
    public final static String PARAM_SIZE = "s";
    public final static String PARAM_SPACING_HORIZONTAL_DISTANCE = "dx";
    public final static String PARAM_SPACING_VERTICAL_DISTANCE = "dy";
    public final static String PARAM_PRIORITY_LEVEL = "l";

    public int foreColor = 0;
    public int backColor = 0;
    public float angle = 0.0f;
    public float size = 1.0f;
    public float dx = 0.0f;
    public float dy = 0.0f;
    public int priorityLevel = 1;

    @Override
    public void pushParam(String key, String value) {
        if (key.equals(PARAM_BRUSH_FORE_COLOR)) {
            this.foreColor = FeatureStyleParser.parseOgrColor(value);
        } else if (key.equals(PARAM_BRUSH_BACK_COLOR)) {
            this.backColor = FeatureStyleParser.parseOgrColor(value);
        } else if (key.equals(PARAM_BRUSH_NAME)) {
            // XXX -
        } else if (key.equals(PARAM_ANGLE)) {
            this.angle = Float.parseFloat(value);
        } else if (key.equals(PARAM_SIZE)) {
            this.size = Float.parseFloat(value);
        } else if (key.equals(PARAM_SPACING_HORIZONTAL_DISTANCE)) {
            this.dx = Float.parseFloat(value);
        } else if (key.equals(PARAM_SPACING_VERTICAL_DISTANCE)) {
            this.dy = Float.parseFloat(value);
        } else if (key.equals(PARAM_PRIORITY_LEVEL)) {
            this.priorityLevel = Integer.parseInt(value);
        }
    }
}
