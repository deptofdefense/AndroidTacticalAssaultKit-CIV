
package com.atakmap.android.toolbars;

import com.atakmap.android.drawing.mapItems.DrawingCircle;
import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;

import java.util.UUID;

/**
 * Range and Bearing circle
 */
public class RangeCircle extends DrawingCircle {

    public static final String COT_TYPE = "u-r-b-c-c";

    public RangeCircle(MapView mapView, String uid) {
        super(mapView, uid, COT_TYPE);
        setMetaString("menu", "menus/range_circle_menu.xml");
    }

    public RangeCircle(MapView mapView) {
        this(mapView, UUID.randomUUID().toString());
    }

    @Override
    public void setColor(int color) {
        // R&B circles do not use fill
        setColor(0xFFFFFF & color, true);
    }

    @Override
    protected CotEvent toCot() {
        CotEvent event = super.toCot();

        // XXX - Old R&B circles required this attribute...
        if (event != null) {
            CotDetail color = new CotDetail("color");
            color.setAttribute("argb", String.valueOf(getStrokeColor()));
            event.getDetail().addChild(color);
        }

        return event;
    }
}
