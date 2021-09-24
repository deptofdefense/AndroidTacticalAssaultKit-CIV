
package com.atakmap.android.toolbars;

import com.atakmap.android.cot.CotUtils;
import com.atakmap.android.cot.detail.CotDetailHandler;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.comms.CommsMapComponent.ImportResult;
import com.atakmap.android.widgets.AngleOverlayShape;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;

public class BullseyeDetailHandler extends CotDetailHandler {

    private final MapView _mapView;

    public BullseyeDetailHandler(MapView mapView) {
        super("bullseye");
        _mapView = mapView;
    }

    @Override
    public boolean isSupported(MapItem item, CotEvent event, CotDetail detail) {
        return item instanceof Marker;
    }

    @Override
    public boolean toCotDetail(MapItem item, CotEvent event, CotDetail detail) {
        if (!item.hasMetaValue("bullseyeUID"))
            return false;

        String bullUID = item.getMetaString("bullseyeUID", "");
        MapItem mi = _mapView.getRootGroup().deepFindUID(bullUID);
        if (!(mi instanceof AngleOverlayShape))
            return false;

        AngleOverlayShape aos = (AngleOverlayShape) mi;

        CotDetail be = new CotDetail("bullseye");
        be.setAttribute("bullseyeUID", bullUID);
        be.setAttribute("title",
                aos.getMetaString("title", "bullseye1"));
        be.setAttribute("edgeToCenter",
                String.valueOf(aos.isShowingEdgeToCenter()));
        be.setAttribute("distance", String.valueOf(aos.getRadius()));
        be.setAttribute("distanceUnits", aos.getRadiusUnits()
                .getAbbrev());
        be.setAttribute("mils", String.valueOf(aos.isShowingMils()));
        be.setAttribute("bearingRef",
                String.valueOf(aos.getNorthRef().getAbbrev()));
        boolean ringsVisible = item.getMetaBoolean("rangeRingVisible", false);
        be.setAttribute("rangeRingVisible",
                String.valueOf(ringsVisible));
        MapGroup subGroup = aos.getGroup();
        if (subGroup != null) {
            mi = subGroup.deepFindUID(aos.getMetaString("rangeRingUID", ""));
            if (mi instanceof RangeCircle) {
                RangeCircle rabCircle = (RangeCircle) mi;
                be.setAttribute("hasRangeRings", String.valueOf(true));
                be.setAttribute("ringDist",
                        String.valueOf(rabCircle.getRadius()));
                be.setAttribute("ringNum", Integer.toString(rabCircle
                        .getNumRings()));
            } else {
                be.setAttribute("hasRangeRings", String.valueOf(false));
            }
        } else {
            be.setAttribute("hasRangeRings", String.valueOf(false));
        }
        detail.addChild(be);
        return true;
    }

    @Override
    public ImportResult toItemMetadata(MapItem item, CotEvent event,
            CotDetail detail) {
        Marker marker = (Marker) item;
        String bullseyeUID = detail.getAttribute("bullseyeUID");
        CotUtils.setString(marker, "bullseyeUID", bullseyeUID);
        CotUtils.setBoolean(marker, "rangeRingVisible",
                detail.getAttribute("rangeRingVisible"));
        if (marker.getType().equalsIgnoreCase(
                BullseyeTool.BULLSEYE_COT_TYPE)) {
            marker.setMetaString("callsign", detail.getAttribute("title"));
            marker.setMetaDouble("minRenderScale", Double.MAX_VALUE);
            marker.setClickable(true);
            marker.setMetaBoolean("ignoreOffscreen", true);
            marker.setMovable(true);
            MapView.getMapView().getRootGroup().findMapGroup(
                    "Range & Bearing").addItem(marker);
        }
        if (Boolean.parseBoolean(detail.getAttribute("mils")))
            marker.setMetaBoolean("mils_mag", true);
        else
            marker.setMetaBoolean("deg_mag", true);
        BullseyeTool.createOrUpdateBullseye(marker, detail);
        return ImportResult.SUCCESS;
    }
}
