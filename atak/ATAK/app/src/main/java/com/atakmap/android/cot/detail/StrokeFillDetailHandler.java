
package com.atakmap.android.cot.detail;

import android.graphics.Color;

import com.atakmap.android.drawing.mapItems.DrawingShape;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.Shape;
import com.atakmap.comms.CommsMapComponent.ImportResult;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;

import java.util.Arrays;
import java.util.HashSet;

/**
 * Handler for commonly used stroke and fill details:
 * <fillColor value="color int"/>
 * <strokeColor value="color int"/>
 * <strokeWeight value="double"/>
 */
class StrokeFillDetailHandler extends CotDetailHandler {

    StrokeFillDetailHandler() {
        super(new HashSet<>(Arrays.asList("fillColor", "strokeColor",
                "strokeWeight")));
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
        }
        return ImportResult.SUCCESS;
    }
}
