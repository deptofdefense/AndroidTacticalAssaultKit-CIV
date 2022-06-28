
package com.atakmap.android.cot.detail;

import android.graphics.Color;

import com.atakmap.android.drawing.mapItems.DrawingShape;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.Shape;
import com.atakmap.comms.CommsMapComponent.ImportResult;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import androidx.annotation.Nullable;

/**
 * Handler for commonly used stroke and fill details:
 * <fillColor value="color int"/>
 * <strokeColor value="color int"/>
 * <strokeWeight value="double"/>
 * <strokeStyle value="string"/>
 */
class StrokeFillDetailHandler extends CotDetailHandler {

    // Line style serialization
    private static final String[] LINE_STYLES = {
            "solid", "dashed", "dotted", "outlined"
    };
    private static final Map<String, Integer> LINE_STYLE_MAP = new HashMap<>();
    static {
        for (int i = 0; i < LINE_STYLES.length; i++)
            LINE_STYLE_MAP.put(LINE_STYLES[i], i);
    }

    StrokeFillDetailHandler() {
        super(new HashSet<>(Arrays.asList("fillColor", "strokeColor",
                "strokeWeight", "strokeStyle")));
    }

    @Override
    public boolean isSupported(MapItem item, CotEvent event, CotDetail detail) {
        return item instanceof Shape;
    }

    @Override
    public boolean toCotDetail(MapItem item, CotEvent event, CotDetail detail) {
        Shape shape = (Shape) item;

        CotDetail stroke = new CotDetail("strokeColor");
        stroke.setAttribute("value", Integer.toString(shape.getStrokeColor()));
        detail.addChild(stroke);

        CotDetail weight = new CotDetail("strokeWeight");
        weight.setAttribute("value", Double.toString(shape.getStrokeWeight()));
        detail.addChild(weight);

        CotDetail style = new CotDetail("strokeStyle");
        style.setAttribute("value", serializeLineStyle(shape.getStrokeStyle()));
        detail.addChild(style);

        // Ideally we'd check Shape.STYLE_FILLED_MASK here because that makes
        // sense but due to repeated complaints in ATAK-11301 this is being
        // changed to check for the closed state instead
        if ((shape.getStyle() & DrawingShape.STYLE_CLOSED_MASK) > 0) {
            CotDetail fill = new CotDetail("fillColor");
            fill.setAttribute("value", Integer.toString(shape.getFillColor()));
            detail.addChild(fill);
        }

        return true;
    }

    @Override
    public ImportResult toItemMetadata(MapItem item, CotEvent event,
            CotDetail detail) {
        Shape shape = (Shape) item;
        String name = detail.getElementName();
        String value = detail.getAttribute("value");
        switch (name) {
            case "strokeColor":
                int color = parseInt(value, Color.WHITE);
                shape.setColor(color);
                break;
            case "fillColor":
                shape.setFillColor(parseInt(value, 0x00FFFFFF));
                break;
            case "strokeWeight":
                shape.setStrokeWeight(parseDouble(value, 6.0));
                break;
            case "strokeStyle":
                shape.setStrokeStyle(parseLineStyle(value));
                break;
        }
        return ImportResult.SUCCESS;
    }

    /**
     * Serialize line style integer to a string
     * @param style Line style int (via {@link Shape#getStrokeStyle()}
     * @return Line style string
     */
    private String serializeLineStyle(int style) {
        return style < LINE_STYLES.length ? LINE_STYLES[style] : "solid";
    }

    /**
     * Parse line style from a string
     * @param style Line style string
     * @return Line style int (see {@link Shape#setBasicLineStyle(int)})
     */
    private int parseLineStyle(@Nullable String style) {
        Integer styleInt = LINE_STYLE_MAP.get(style);
        return styleInt != null ? styleInt : Shape.BASIC_LINE_STYLE_SOLID;
    }
}
