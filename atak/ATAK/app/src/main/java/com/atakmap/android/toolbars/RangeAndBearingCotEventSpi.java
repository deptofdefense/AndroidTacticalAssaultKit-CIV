
package com.atakmap.android.toolbars;

import com.atakmap.android.cot.detail.CotDetailManager;
import com.atakmap.android.maps.AnchoredMapItem;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.cot.event.CotPoint;
import com.atakmap.coremap.maps.coords.DistanceCalculations;
import com.atakmap.coremap.maps.time.CoordinatedTime;

public class RangeAndBearingCotEventSpi {

    private final MapView mapView;

    public RangeAndBearingCotEventSpi(MapView mapView) {
        this.mapView = mapView;
    }

    public CotEvent createCotEvent(MapItem item) {
        return createCotEvent(this.mapView, item);
    }

    public static CotEvent createCotEvent(MapView mapView, MapItem item) {
        if (item instanceof RangeAndBearingMapItem)
            return createRangeAndBearingCotEvent(mapView,
                    (RangeAndBearingMapItem) item);
        else
            return null;
    }

    private static CotEvent createRangeAndBearingCotEvent(MapView mapView,
            RangeAndBearingMapItem rb) {
        if (rb.hasMetaValue("type") && rb.getType().equals("rb"))
            return null; // Don't send out the CoT event because we want to keep it entirely
                         // internal to this class. Allowing it to be sent out causes UI issues.

        CotEvent cotEvent = new CotEvent();

        CoordinatedTime time = new CoordinatedTime();
        cotEvent.setTime(time);
        cotEvent.setStart(time);
        cotEvent.setStale(time.addDays(1));

        cotEvent.setUID(rb.getUID());
        cotEvent.setVersion(CotEvent.VERSION_2_0);
        cotEvent.setHow("h-e");

        cotEvent.setType("u-rb-a");

        CotDetail detail = new CotDetail("detail");
        cotEvent.setDetail(detail);

        final PointMapItem _pt1 = rb.getPoint1Item();
        final PointMapItem _pt2 = rb.getPoint2Item();

        if (_pt1 != null)
            cotEvent.setPoint(new CotPoint(_pt1.getPoint()));

        // Only use a set Radius if the RangeCircle is not following a marker for the Radius
        if (_pt1 != null && _pt2 != null && isTempPoint(_pt2)) {
            double[] da = DistanceCalculations.computeDirection(
                    _pt1.getPoint(), _pt2.getPoint());
            CotDetail range = new CotDetail("range");
            range.setAttribute("value", Double.toString(da[0]));
            detail.addChild(range);
            CotDetail bearing = new CotDetail("bearing");
            bearing.setAttribute("value", Double.toString(da[1]));
            detail.addChild(bearing);
            CotDetail inclination = new CotDetail("inclination");
            inclination.setAttribute("value",
                    Double.toString(rb.getInclination()));
            detail.addChild(inclination);
        } else if (_pt2 != null) {
            CotDetail range = new CotDetail("rangeUID");
            range.setAttribute("value", getPointUID(_pt2));
            detail.addChild(range);
        }

        if (_pt1 != null && !isTempPoint(_pt1)) {
            CotDetail anchor = new CotDetail("anchorUID");
            anchor.setAttribute("value", getPointUID(_pt1));
            detail.addChild(anchor);
        }

        CotDetail rangeUnits = new CotDetail("rangeUnits");
        CotDetail bearingUnits = new CotDetail("bearingUnits");
        CotDetail northRef = new CotDetail("northRef");

        rangeUnits.setAttribute("value", Integer.toString(rb.getRangeUnits()));

        bearingUnits.setAttribute("value",
                Integer.toString(rb.getBearingUnits().getValue()));

        northRef.setAttribute("value",
                Integer.toString(rb.getNorthReference().getValue()));

        detail.addChild(rangeUnits);
        detail.addChild(bearingUnits);
        detail.addChild(northRef);

        // XXX - Trigger the color detail handler
        rb.setMetaInteger("color", rb.getStrokeColor());
        CotDetailManager.getInstance().addDetails(rb, cotEvent);

        return cotEvent;
    }

    private static boolean isTempPoint(PointMapItem pmi) {
        return pmi instanceof RangeAndBearingEndpoint
                || pmi.getType().equals("self")
                || pmi.getType().equals("b-m-p-s-p-i")
                || pmi.hasMetaValue("atakRoleType")
                || pmi.hasMetaValue("emergency");
    }

    /**
     * Get the point UID
     * If the point is a shape marker, use the shape UID instead
     * @param pmi Point item
     * @return Point/shape UID
     */
    private static String getPointUID(PointMapItem pmi) {
        if (pmi.getType().equals("shape_marker")
                || pmi.getType().equals("center_u-d-r")
                || pmi.hasMetaValue("nevercot")) {
            MapItem shp = ATAKUtilities.findAssocShape(pmi);
            if (shp instanceof AnchoredMapItem
                    && ((AnchoredMapItem) shp).getAnchorItem() == pmi)
                return shp.getUID();
        }
        return pmi.getUID();
    }
}
