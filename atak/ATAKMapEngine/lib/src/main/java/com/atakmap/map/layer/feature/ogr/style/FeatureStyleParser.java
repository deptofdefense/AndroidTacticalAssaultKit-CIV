
package com.atakmap.map.layer.feature.ogr.style;

import java.util.LinkedList;
import java.util.List;

import android.util.LruCache;

import com.atakmap.coremap.log.Log;
import com.atakmap.map.layer.feature.style.Style;
import com.atakmap.map.layer.feature.style.BasicFillStyle;
import com.atakmap.map.layer.feature.style.BasicPointStyle;
import com.atakmap.map.layer.feature.style.BasicStrokeStyle;
import com.atakmap.map.layer.feature.style.CompositeStyle;
import com.atakmap.map.layer.feature.style.IconPointStyle;
import com.atakmap.map.layer.feature.style.LabelPointStyle;

public class FeatureStyleParser {

    private static int STATE_TOOL_NAME = 0;
    private static int STATE_PARAM_NAME = 1;
    private static int STATE_PARAM_VALUE = 2;
    private static int STATE_QUOTED_VALUE = 3;
    private static int STATE_TOOL_END = 4;

    private static LruCache<String, Style> styleCache = new LruCache<String, Style>(100);
    
    public static void parse(String style, FeatureStyle retval) {
        int[] pidx = new int[1];

        DrawingTool tool;

        do {
            tool = parseTool(style, pidx);
            if (tool == null)
                break;
            retval.pushDrawingTool(tool);
        } while (true);
    }
    
    public static Style parse2(String ogrStyle) {
        Style style = styleCache.get(ogrStyle);
        if(style != null)
            return style;
        
        List<Style> retval = new LinkedList<Style>();

        int[] pidx = new int[1];

        DrawingTool tool;

        style = null;
        do {
            tool = parseTool(ogrStyle, pidx);
            if (tool == null)
                break;
            // XXX - very simple translation from OGR style. need to account for
            //       patterns and other properties
            if(tool instanceof Pen) {
                Pen pen = (Pen)tool;

                style = new BasicStrokeStyle(pen.color, pen.width);
            } else if(tool instanceof Brush) {
                Brush brush = (Brush)tool;
                
                style = new BasicFillStyle(brush.foreColor);
            } else if(tool instanceof Symbol) {
                Symbol symbol = (Symbol) tool;
                if (symbol.id != null) {
                    if (symbol.scale != 0)
                        style = new IconPointStyle(symbol.color, symbol.id, symbol.scale, 0, 0, 0f, false);
                    else
                        style = new IconPointStyle(symbol.color, symbol.id);
                }
            } else if(tool instanceof Label) {
                Label label = (Label)tool;
                // NOTE: ogr "stretch" is width only, but we map to
                // full scaling for matching KML rendering to google earth.
                style = new LabelPointStyle(label.textString,  label.color, label.backgroundColor,
                        LabelPointStyle.ScrollMode.OFF, label.fontSize, (int)label.dx, (int)label.dy,
                        label.angle, false, 14d, label.stretch / 100.0f);
            } else {
                continue;
            }

            if (style != null)
                retval.add(style);
            
        } while (true);
        
        if(retval.size() < 1)
            style = null;
        else if(retval.size() == 1)
            style = retval.get(0);
        else
            style = new CompositeStyle(retval.toArray(new Style[0]));
        if(style != null)
            styleCache.put(ogrStyle, style);
        return style;
    }

    private static DrawingTool parseTool(String style, int[] pidx) {
        StringBuilder s = new StringBuilder();
        int idx = pidx[0];

        int state = STATE_TOOL_NAME;

        DrawingTool tool = null;

        String paramKey = null;
        String paramVal = null;

        char c;
        while (idx < style.length()) {
            c = style.charAt(idx++);
            if (state == STATE_TOOL_NAME && c == '(') {
                // end tool name
                final String toolName = s.toString();
                s.delete(0, s.length());

                if (toolName.equals("PEN"))
                    tool = new Pen();
                else if (toolName.equals("BRUSH"))
                    tool = new Brush();
                else if (toolName.equals("LABEL"))
                    tool = new Label();
                else if (toolName.equals("SYMBOL"))
                    tool = new Symbol();
                else
                    ; // XXX -

                state = STATE_PARAM_NAME;
            } else if (state == STATE_PARAM_NAME && c == ':') {
                // end param key
                paramKey = s.toString();
                s.delete(0, s.length());

                state = STATE_PARAM_VALUE;
            } else if (state == STATE_PARAM_VALUE && (c == ',' || c == ')')) {
                // end param value
                paramVal = s.toString();
                s.delete(0, s.length());

                if (tool != null && paramKey != null)
                    tool.pushParam(paramKey.trim(), paramVal.trim());

                if (c == ',')
                    state = STATE_PARAM_NAME;
                else if (c == ')')
                    state = STATE_TOOL_END;
                else
                    throw new IllegalStateException();
            } else if (state == STATE_PARAM_VALUE && c == '"') {
                if (s.length() != 0)
                    throw new IllegalStateException();
                state = STATE_QUOTED_VALUE;
            } else if (state == STATE_QUOTED_VALUE && c == '"') {
                if (s.length() > 0 && s.charAt(s.length() - 1) == '\\')
                    s.delete(s.length() - 1, s.length());
                else
                    state = STATE_PARAM_VALUE;
            } else if (state == STATE_TOOL_END && c != ' ') {
                if (c != ';')
                    throw new IllegalStateException();
                break;
            } else {
                s.append(c);
            }
        }

        pidx[0] = idx;

        return tool;
    }

    public final static int parseOgrColor(String paramValue) {
        final int len = paramValue.length();
        if ((len != 7 && len != 9)
                || (paramValue.charAt(0) != '#') || !isHex(paramValue, 1, len-1)) {
            Log.w("FeatureStyleParser", "Bad color value: " + paramValue + ", default to 0xFFFFFFFF");
            return 0xFFFFFFFF;
        }

        if (len == 7) {
            return 0xFF000000 | Integer.parseInt(paramValue.substring(1), 16);
        } else if (len == 9) {
            final int alpha = Integer.parseInt(paramValue.substring(7), 16);
            return (int) (((long) alpha << 24L) | Long.parseLong(paramValue.substring(1, 7), 16));
        } else {
            throw new IllegalStateException();
        }
    }
    
    private static boolean isHex(String s, int off, int len) {
        char c;
        for(int i = 0; i < len; i++) {
            c = s.charAt(i+off);
            if(c < 48 || (c&~0x20) > 70)
                return false;
            if(c > 57 && (c&~0x20) < 65)
                return false;
        }
        return true;
    }
    
    public static String pack(Style style) {
        if(style == null)
            return null;
        StringBuilder ogr = new StringBuilder();
        style2ogr(ogr, style);
        if(ogr.length() < 1)
            return null;
        else
            return ogr.toString();
    }
    
    private static int argb2rgba(int argb) {
        return ((argb<<8)|((argb>>24)&0xFF));
    }

    private static void style2ogr(StringBuilder ogr, Style style) {
        if(style instanceof BasicStrokeStyle) {
            BasicStrokeStyle stroke = (BasicStrokeStyle)style;
            ogr.append("PEN(c:#");
            ogr.append(String.format("%08X", argb2rgba(stroke.getColor())));
            ogr.append(",w:");
            ogr.append((int)Math.ceil(stroke.getStrokeWidth()));
            ogr.append("px)");
        } else if(style instanceof BasicFillStyle) {
            BasicFillStyle fill = (BasicFillStyle)style;
            ogr.append("BRUSH(fc:#");
            ogr.append(String.format("%08X", argb2rgba(fill.getColor())));
            ogr.append(")");
        } else if(style instanceof BasicPointStyle) {
            BasicPointStyle point = (BasicPointStyle)style;
            ogr.append("SYMBOL(c:#");
            ogr.append(String.format("%08X", argb2rgba(point.getColor())));
            if(point.getSize() > 0) {
                ogr.append(",s:");
                ogr.append((int)Math.ceil(point.getSize()));
                ogr.append("px");
            }
            ogr.append(",id:asset:/icons/reference_point.png)");
        } else if(style instanceof IconPointStyle) {
            IconPointStyle icon = (IconPointStyle)style;
            ogr.append("SYMBOL(c:#");
            ogr.append(String.format("%08X", argb2rgba(icon.getColor())));
            ogr.append(",id:");
            ogr.append(icon.getIconUri());
            ogr.append(")");
        } else if(style instanceof LabelPointStyle) {
            LabelPointStyle label = (LabelPointStyle)style;
            ogr.append("LABEL(t:\"");
            ogr.append(label.getText());
            ogr.append("\",s:");
            ogr.append((int)Math.ceil(label.getTextSize()));
            ogr.append("pt,c:#");
            ogr.append(String.format("%08X", argb2rgba(label.getTextColor())));
            if(label.getBackgroundColor() != 0) {
                ogr.append(",b:#");
                ogr.append(String.format("%08X", argb2rgba(label.getBackgroundColor())));
            }
            if(label.getLabelAlignmentX() != 0 || label.getLabelAlignmentY() != 0) {
                ogr.append(",p:");
                int v;
                int h;
                
                if(label.getLabelAlignmentX() < 0)
                    h = 0;
                else if(label.getLabelAlignmentX() == 0)
                    h = 1;
                else if(label.getLabelAlignmentX() > 0)
                    h = 2;
                else
                    throw new IllegalStateException();
                
                if(label.getLabelAlignmentY() < 0)
                    v = 0;
                else if(label.getLabelAlignmentY() == 0)
                    v = 1;
                else if(label.getLabelAlignmentY() > 0)
                    v = 2;
                else
                    throw new IllegalStateException();
                
                ogr.append(((v*3)+h));
            }
            ogr.append(")");
        } else if(style instanceof CompositeStyle) {
            CompositeStyle composite = (CompositeStyle)style;
            final int numStyles = composite.getNumStyles();
            if(numStyles < 1)
                return;
            
            style2ogr(ogr, composite.getStyle(0));
            StringBuilder compOgr;
            for(int i = 1; i < numStyles; i++) {            
                compOgr = new StringBuilder();
                style2ogr(compOgr, composite.getStyle(1));
                if(compOgr.length() > 0) {
                    ogr.append(";");
                    ogr.append(compOgr);
                }
            }
        }
    }
}
