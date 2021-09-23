
package com.atakmap.android.toolbars;

import android.graphics.Color;
import android.os.Bundle;

import com.atakmap.android.cot.CotUtils;
import com.atakmap.android.cot.detail.CotDetailManager;
import com.atakmap.android.cot.importer.MapItemImporter;
import com.atakmap.android.maps.AnchoredMapItem;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.app.R;
import com.atakmap.coremap.conversions.Angle;
import com.atakmap.coremap.conversions.Span;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.comms.CommsMapComponent.ImportResult;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.coremap.maps.coords.NorthReference;

import java.util.UUID;

public class RangeAndBearingImporter extends MapItemImporter {

    private final static String TAG = "RangeAndBearingImporter";

    public RangeAndBearingImporter(MapView mapView, MapGroup group) {
        super(mapView, group, "u-rb-a");
    }

    @Override
    protected int getNotificationIcon(MapItem item) {
        return R.drawable.pairing_line_white;
    }

    @Override
    protected ImportResult importMapItem(MapItem existing, CotEvent event,
            Bundle extras) {
        if (existing != null && !(existing instanceof RangeAndBearingMapItem))
            return ImportResult.FAILURE;

        RangeAndBearingMapItem arrow = (RangeAndBearingMapItem) existing;

        CotDetail d = event.getDetail();
        if (d == null)
            return ImportResult.FAILURE;

        GeoPoint anchor = event.getGeoPoint();

        double r = -1d; //range
        double b = -1d; //bearing
        double i = Double.NaN; //inclination
        int rUnits = Span.METRIC;
        Angle bUnits = Angle.DEGREE;
        NorthReference nRef = NorthReference.TRUE;
        int c = Color.RED;

        String range = findValue(event, "range");
        String rangeUID = findValue(event, "rangeUID");
        String bearing = findValue(event, "bearing");
        String inclination = findValue(event, "inclination");
        String anchorUID = findValue(event, "anchorUID");
        String rangeUnits = findValue(event, "rangeUnits");
        String bearingUnits = findValue(event, "bearingUnits");
        String northRef = findValue(event, "northRef");
        String color = findValue(event, "color");

        try {
            if (range != null)
                r = Double.parseDouble(range);
            if (bearing != null)
                b = Double.parseDouble(bearing);
            if (inclination != null)
                i = Double.parseDouble(inclination);
            if (rangeUnits != null)
                rUnits = Integer.parseInt(rangeUnits);
            if (bearingUnits != null)
                bUnits = Angle.findFromValue(Integer
                        .parseInt(bearingUnits));
            if (northRef != null)
                nRef = NorthReference.findFromValue(Integer
                        .parseInt(northRef));
            if (color != null)
                c = Integer.parseInt(color);
        } catch (Exception e) {
            Log.e(TAG, "error: ", e);
        }

        String title = CotUtils.getCallsign(event);
        if (FileSystemUtils.isEmpty(title))
            title = "R&B " + getInstanceNum();

        // Find the anchor and range markers
        PointMapItem pt1 = null, pt2 = null;
        boolean anchorPinned = false, radiusPinned = false;
        if (anchorUID != null) {
            MapItem item = findItem(anchorUID);
            if (item != null) {
                if (item instanceof AnchoredMapItem)
                    item = ((AnchoredMapItem) item).getAnchorItem();
                if (item instanceof PointMapItem) {
                    pt1 = (PointMapItem) item;
                    anchorPinned = true;
                }
            }
        }
        if (rangeUID != null) {
            MapItem item = findItem(rangeUID);
            if (item != null) {
                if (item instanceof AnchoredMapItem)
                    item = ((AnchoredMapItem) item).getAnchorItem();
                if (item instanceof PointMapItem) {
                    pt2 = (PointMapItem) item;
                    radiusPinned = true;
                }
            }
        }

        if (arrow != null)
            arrow.setInitializing(true);

        // Fallback to generic endpoints if needed
        if (pt1 == null) {
            if (arrow != null)
                pt1 = arrow.getPoint1Item();
            pt1 = setPoint(pt1, GeoPointMetaData.wrap(anchor));
        }
        if (pt2 == null) {
            GeoPoint rPoint = GeoCalculations.pointAtDistance(anchor, b, r);
            if (!Double.isNaN(i))
                rPoint = GeoCalculations.pointAtDistance(anchor, b, r, i);
            if (arrow != null)
                pt2 = arrow.getPoint2Item();
            pt2 = setPoint(pt2, GeoPointMetaData.wrap(rPoint));
        }

        String uid = event.getUID();
        pt1.setMetaString("rabUUID", uid);
        pt2.setMetaString("rabUUID", uid);

        if (arrow == null) {
            arrow = new RangeAndBearingMapItem(pt1, pt2, _mapView,
                    title, uid, rUnits, bUnits, nRef, c, false);
            arrow.setInitializing(true);
            arrow.removeMetaData("nevercot");
        } else {
            arrow.setPoint1(pt1);
            arrow.setPoint2(pt2);
            arrow.setTitle(title);
            arrow.setMetaString("callsign", title);
            arrow.setStrokeColor(c);
        }

        if (anchorPinned)
            arrow.setMetaBoolean("anchorAvailable", true);
        if (radiusPinned)
            arrow.setMetaBoolean("radiusAvailable", true);
        arrow.toggleMetaData("distanceLockAvailable",
                !anchorPinned || !radiusPinned);

        if (pt1 instanceof RangeAndBearingEndpoint) {
            RangeAndBearingEndpoint rabe = (RangeAndBearingEndpoint) pt1;
            rabe.setParent(arrow);
            rabe.setPart(RangeAndBearingEndpoint.PART_TAIL);
        }
        if (pt2 instanceof RangeAndBearingEndpoint) {
            RangeAndBearingEndpoint rabe = (RangeAndBearingEndpoint) pt2;
            rabe.setParent(arrow);
            rabe.setPart(RangeAndBearingEndpoint.PART_HEAD);
        }

        if (arrow.getGroup() == null) {
            arrow.setClickable(true);
            arrow.setVisible(extras.getBoolean("visible",
                    arrow.getVisible(true)), false);
            arrow.setZOrder(-2d);
            arrow.setMetaString("rabUUID", uid);
            addToGroup(arrow);
            arrow.onPointChanged(null);
            arrow.setMetaString("menu", "menus/rab_menu.xml");
        }

        CotDetailManager.getInstance().processDetails(arrow, event);

        arrow.setInitializing(false);

        // Persist to the statesaver if needed
        persist(arrow, extras);

        // Check if we should attempt to re-import this later in case one of
        // the defined end markers couldn't be found
        if (anchorUID != null && !anchorPinned
                || rangeUID != null && !radiusPinned)
            return ImportResult.DEFERRED;

        return ImportResult.SUCCESS;
    }

    private String findValue(CotEvent event, String detailName) {
        CotDetail d = event.findDetail(detailName);
        return d != null ? d.getAttribute("value") : null;
    }

    private PointMapItem setPoint(PointMapItem pmi, GeoPointMetaData gp) {
        if (pmi != null) {
            pmi.setPoint(gp);
            if (!(pmi instanceof RangeAndBearingEndpoint)
                    && !pmi.hasMetaValue("nevercot"))
                pmi.persist(MapView.getMapView().getMapEventDispatcher(),
                        null, RangeAndBearingMapItem.class);
        } else {
            pmi = new RangeAndBearingEndpoint(gp, UUID
                    .randomUUID().toString());
            pmi.setMetaString("menu", "menus/rab_endpoint_menu.xml");
            addToGroup(pmi);
        }
        return pmi;
    }

    public static int getInstanceNum() {
        MapView mv = MapView.getMapView();
        if (mv == null)
            return 0;
        MapGroup mg = mv.getRootGroup().findMapGroup("Range & Bearing");
        int i = 1;
        while (MapGroup.deepFindItemWithMetaString(mg, "title",
                "R&B " + i) != null)
            i++;
        return i;
    }
}
