
package com.atakmap.android.cot.detail;

import com.atakmap.android.maps.MapItem;
import com.atakmap.comms.CommsMapComponent.ImportResult;
import com.atakmap.coremap.conversions.Span;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.filesystem.FileSystemUtils;

import java.util.Arrays;
import java.util.HashSet;

/**
 * Map item height
 *
 * XXX - Very poor choice when deciding to include a separate detail
 * element for the height unit. Either assume meters (like everything else)
 * or make the unit an attribute. Separate element is just gross.
 *
 * XXX - Height units aren't even stored as a string! It's an integer that maps
 * to a Span. A mapping that is ONLY used for "height_unit".
 *
 * XXX - Also why are we storing values in the inner text? We don't do this
 * anywhere else except remarks (and CE for some reason)
 *
 * Seems like very little thought went into this detail spec. TAK developers
 * for other platforms don't need to be confused by something as simple as
 * a height value.
 *
 * Expected format:
 * <height value="12.0"/>
 * <height value="20.0" unit="feet"/>
 *
 * Actual format:
 * <height>20.0</height>
 * <height_unit>4</height_unit>
 *
 * 
 */
class HeightDetailHandler extends CotDetailHandler {

    private static final String TAG = "HeightDetailHandler";

    HeightDetailHandler() {
        super(new HashSet<>(Arrays.asList("height", "height_unit")));
    }

    @Override
    public boolean toCotDetail(MapItem item, CotEvent event, CotDetail detail) {
        double height = item.getHeight();
        if (Double.isNaN(height))
            return false;

        // Including the better format along with the old format for compatibility

        CotDetail d = new CotDetail("height");
        d.setInnerText(String.valueOf(height)); // XXX - yuck
        d.setAttribute("value", String.valueOf(height));

        if (item.hasMetaValue("height_unit")) {
            Span span = Span.findFromValue(item.getMetaInteger("height_unit",
                    Span.FOOT.getValue()));
            if (span == null)
                span = Span.FOOT;
            d.setAttribute("unit", span.getPlural());

            // XXX - YUCK
            CotDetail heightUnits = new CotDetail("height_unit");
            heightUnits.setInnerText(String.valueOf(span.getValue()));
            detail.addChild(heightUnits);
        }

        detail.addChild(d);
        return true;
    }

    @Override
    public ImportResult toItemMetadata(MapItem item, CotEvent event,
            CotDetail detail) {
        String name = detail.getElementName();
        if (name.equals("height")) {
            String value = detail.getAttribute("value");
            if (FileSystemUtils.isEmpty(value))
                value = detail.getInnerText();
            double height = parseDouble(value, Double.NaN);
            item.setHeight(height);

            String unit = detail.getAttribute("unit");
            if (!FileSystemUtils.isEmpty(unit)) {
                Span unitSpan = Span.findFromPluralName(unit);
                if (unitSpan != null)
                    item.setMetaInteger("height_unit", unitSpan.getValue());
            }
            return ImportResult.SUCCESS;
        } else if (name.equals("height_unit")) {
            int unit = parseInt(detail.getInnerText(), -1);
            Span unitSpan = Span.findFromValue(unit);
            if (unitSpan != null)
                item.setMetaInteger("height_unit", unitSpan.getValue());
            return ImportResult.SUCCESS;
        }
        return ImportResult.IGNORE;
    }
}
