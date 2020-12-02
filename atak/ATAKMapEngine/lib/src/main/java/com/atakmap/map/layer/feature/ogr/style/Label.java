
package com.atakmap.map.layer.feature.ogr.style;

public class Label implements DrawingTool {

    public final static String PARAM_FONT_NAME = "f";
    public final static String PARAM_FONT_SIZE = "s";
    public final static String PARAM_TEXT_STRING = "t";
    public final static String PARAM_ANGLE = "a";
    public final static String PARAM_FOREGROUND_COLOR = "c";
    public final static String PARAM_BACKGROUND_COLOR = "b";
    public final static String PARAM_OUTLINE_COLOR = "o";
    public final static String PARAM_SHADOW_COLOR = "h";
    public final static String PARAM_STRETCH = "w";
    public final static String PARAM_PLACEMENT_MODE = "m";
    public final static String PARAM_ANCHOR_POSITION = "p";
    public final static String PARAM_X_OFFSET = "dx";
    public final static String PARAM_Y_OFFSET = "dy";
    public final static String PARAM_PERPENDICULAR_OFFSET = "dp";
    public final static String PARAM_BOLD = "bo";
    public final static String PARAM_ITALIC = "it";
    public final static String PARAM_UNDERLINE = "un";
    public final static String PARAM_STRIKETHROUGH = "st";
    public final static String PARAM_PRIORITY_LEVEL = "l";


    public String fontName = "";
    public float fontSize = 0.0f;
    public String textString = "";
    public float angle = 0.0f;
    public int color = 0xFFFFFFFF;
    public int backgroundColor = 0;
    public int outlineColor = 0;
    public int shadowColor = 0;
    public float stretch = 100f;
    public char placementMode = 'p';
    public int anchorPosition = 0;
    public float dx = 0.0f;


    // XXX for now, set the default for 100 below because the most common use case is
    // when used with an IconStyle as part of a composite style.   This allows for rendering
    // similar to how ATAK currently renders things.
    public float dy = 100.0f;

    public float dp = 0.0f;
    public boolean bold = false;
    public boolean italic = false;
    public boolean underline = false;
    public boolean strikethrough = false;
    public int priorityLevel = 1;

    @Override
    public void pushParam(String key, String value) {
        try {
            switch (key) {
                case PARAM_FONT_NAME:
                    this.fontName = value;
                    break;
                case PARAM_FONT_SIZE:
                    this.fontSize = Float.parseFloat(value);
                    break;
                case PARAM_TEXT_STRING:
                    this.textString = value;
                    break;
                case PARAM_ANGLE:
                    this.angle = Float.parseFloat(value);
                    break;
                case PARAM_FOREGROUND_COLOR:
                    this.color = FeatureStyleParser.parseOgrColor(value);
                    break;
                case PARAM_BACKGROUND_COLOR:
                    this.backgroundColor = FeatureStyleParser.parseOgrColor(value);
                    break;
                case PARAM_OUTLINE_COLOR:
                    this.outlineColor = FeatureStyleParser.parseOgrColor(value);
                    break;
                case PARAM_SHADOW_COLOR:
                    this.shadowColor = FeatureStyleParser.parseOgrColor(value);
                    break;
                case PARAM_STRETCH:
                    this.stretch = Float.parseFloat(value);
                    break;
                case PARAM_PLACEMENT_MODE:
                    this.placementMode = value.charAt(0);
                    break;
                case PARAM_ANCHOR_POSITION:
                    this.anchorPosition = Integer.parseInt(value);
                case PARAM_X_OFFSET:
                    this.dx = Float.parseFloat(value);
                    break;
                case PARAM_Y_OFFSET:
                    this.dy = Float.parseFloat(value);
                    break;
                case PARAM_PERPENDICULAR_OFFSET:
                    this.dp = Float.parseFloat(value);
                    break;
                case PARAM_BOLD:
                    this.bold = value.equals("1");
                    break;
                case PARAM_ITALIC:
                    this.italic = value.equals("1");
                    break;
                case PARAM_UNDERLINE:
                    this.underline = value.equals("1");
                    break;
                case PARAM_STRIKETHROUGH:
                    this.strikethrough = value.equals("1");
                    break;
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
