
package com.atakmap.map.layer.feature.ogr.style;

public class Symbol implements DrawingTool {

    public final static String PARAM_SYMBOL_NAME = "id";
    public final static String PARAM_ANGLE = "a";
    public final static String PARAM_SYMBOL_COLOR = "c";
    public final static String PARAM_SYMBOL_OUTLINE_COLOR = "o";
    public final static String PARAM_SIZE = "s";
    public final static String PARAM_X_OFFSET = "dx";
    public final static String PARAM_Y_OFFSET = "dy";
    public final static String PARAM_SPACING_STEP = "ds";
    public final static String PARAM_SPACING_PERPENDICULAR_DISTANCE = "dp";
    public final static String PARAM_SPACING_INITIAL_OFFSET = "di";
    public final static String PARAM_PRIORITY_LEVEL = "l";

    public String id = null;
    public float angle = 0.0f;
    public int color = 0xFFFFFFFF;
    public int outlineColor = 0;
    public int size = 0;
    public float scale = 1.0f;
    public float dx = 0.0f;
    public float dy = 0.0f;
    public float ds = 0.0f;
    public float dp = 0.0f;
    public float di = 0.0f;
    public int priorityLevel = 1;

    @Override
    public void pushParam(String key, String value) {
        try {
            switch (key) {
                case PARAM_SYMBOL_NAME:
                    this.id = value;
                    break;
                case PARAM_SYMBOL_COLOR:
                    this.color = FeatureStyleParser.parseOgrColor(value);
                    break;
                case PARAM_SYMBOL_OUTLINE_COLOR:
                    this.outlineColor = FeatureStyleParser.parseOgrColor(value);
                    break;
                case PARAM_ANGLE:
                    this.angle = Float.parseFloat(value);
                    break;
                case PARAM_SIZE:
                    if (value.endsWith("px"))
                        this.size = Integer.parseInt(value.substring(0, value.length() - 2));
                    else if (value.matches("\\d+(\\.\\d+)"))
                        this.scale = Float.parseFloat(value);

                    break;
                /**
                 case PARAM_X_OFFSET:
                 this.dx = Float.parseFloat(value);
                 break;
                 case PARAM_Y_OFFSET:
                 this.dy = Float.parseFloat(value);
                 break;
                 case PARAM_SPACING_STEP:
                 this.ds = Float.parseFloat(value);
                 break;
                 case PARAM_SPACING_PERPENDICULAR_DISTANCE:
                 this.dp =
                 Float.parseFloat(value);
                 break;
                 case PARAM_SPACING_INITIAL_OFFSET:
                 this.di = Float.parseFloat(value);

                 break;
                 **/
                case PARAM_PRIORITY_LEVEL:
                    this.priorityLevel = Integer.parseInt(value);
                    break;
                default:
                    break;
            }
        } catch (Exception ignore) {
            // skip improperly formed value
        }
    }

}
