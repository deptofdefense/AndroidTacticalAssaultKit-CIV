
package com.atakmap.map.layer.feature.ogr.style;

public class Pen implements DrawingTool {

    public final static String PARAM_PEN_COLOR = "c";
    public final static String PARAM_PEN_WIDTH = "w";
    public final static String PARAM_PATTERN = "p";
    public final static String PARAM_PEN_NAME = "id";
    public final static String PARAM_PEN_CAP = "cap";
    public final static String PARAM_PEN_JOIN = "j";
    public final static String PARAM_PERPENDICULAR_OFFSET = "dp";
    public final static String PARAM_PRIORITY_LEVEL = "l";

    public final static int CAP_BUTT = 0;
    public final static int CAP_ROUND = 1;
    public final static int CAP_PROJECTING = 2;

    public final static int JOIN_MITER = 0;
    public final static int JOIN_ROUNDED = 1;
    public final static int JOIN_BEVEL = 2;

    public int color = 0xFF000000;
    public float width = 1.0f;
    public int cap = CAP_BUTT;
    public int join = JOIN_MITER;
    public float perpendicularOffset = 0.0f;
    public int priorityLevel = 1;

    @Override
    public void pushParam(String key, String value) {
        try {
            switch (key) {
                case PARAM_PEN_COLOR:
                    this.color = FeatureStyleParser.parseOgrColor(value);
                    break;
                case PARAM_PEN_WIDTH:
                    if (value.endsWith("px"))
                        this.width = Float.valueOf(value.substring(0, value.length() - 2));
                    // XXX - other units
                    break;
                case PARAM_PATTERN:
                    // XXX -
                    break;
                case PARAM_PEN_NAME:
                    // XXX -
                    break;
                case PARAM_PEN_CAP:
                    if (value.equals("b"))
                        this.cap = CAP_BUTT;
                    else if (value.equals("r"))
                        this.cap = CAP_ROUND;
                    else if (value.equals("p"))
                        this.cap = CAP_PROJECTING;
                    break;
                case PARAM_PEN_JOIN:
                    if (value.equals("m"))
                        this.join = JOIN_MITER;
                    else if (value.equals("r"))
                        this.join = JOIN_ROUNDED;
                    else if (value.equals("b"))
                        this.join = JOIN_BEVEL;
                    break;
                case PARAM_PERPENDICULAR_OFFSET:
                    this.perpendicularOffset = Float.parseFloat(value);
                    break;
                case PARAM_PRIORITY_LEVEL:
                    this.priorityLevel = Integer.parseInt(value);
                    break;
            }
        } catch  (Exception ignore) {
            // skip improperly formed value
        }
    }
}
