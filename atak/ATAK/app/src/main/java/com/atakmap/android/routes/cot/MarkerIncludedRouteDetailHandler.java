
package com.atakmap.android.routes.cot;

import android.os.Bundle;

import com.atakmap.android.cot.detail.CotDetailHandler;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.routes.Route;
import com.atakmap.android.routes.RouteMapReceiver;
import com.atakmap.comms.CommsMapComponent.ImportResult;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;

public class MarkerIncludedRouteDetailHandler extends CotDetailHandler {
    private static final String TAG = "RouteWaypointDetailHandler";
    private static final String WAYPOINT_DETAIL = "_route";
    private final MapView mapView;
    private final RouteImporter routeImporter;

    public MarkerIncludedRouteDetailHandler() {
        super(WAYPOINT_DETAIL);
        mapView = MapView.getMapView();
        routeImporter = new RouteImporter(mapView, new RouteMapReceiver(
                mapView, mapView.getRootGroup(), mapView.getRootGroup(),
                mapView.getRootGroup(), mapView.getContext()));
    }

    @Override
    public ImportResult toItemMetadata(MapItem item, CotEvent event,
            CotDetail detail) {
        final String parentItemUID = item.getUID();
        String senderUID = detail.getAttribute("sender");
        if (senderUID != null
                && senderUID.equalsIgnoreCase(mapView.getSelfMarker().getUID()))
            return ImportResult.IGNORE;
        if (detail.getChildren().isEmpty()) {
            //remove old route
            MapItem existingRte = mapView.getRootGroup().deepFindUID(
                    parentItemUID + "-rte");
            if (existingRte != null)
                existingRte.getGroup().removeItem(existingRte);

        } else {
            CotDetail linkAttrDetail = detail.getFirstChildByName(0,
                    "link_attr");
            TemplateRoute r = new TemplateRoute(mapView,
                    item.getMetaString("callsign", "Route"),
                    Integer.parseInt(linkAttrDetail.getAttribute("color")),
                    linkAttrDetail.getAttribute("prefix"),
                    parentItemUID + "-rte");
            CotEvent ce = r.toCot();
            CotDetail det = new CotDetail("detail");
            for (CotDetail cd : detail.getChildren()) {
                det.addChild(cd);
            }
            ce.setDetail(det);

            final ImportResult result = routeImporter.importData(ce,
                    new Bundle());
            item.addOnGroupChangedListener(
                    new MapItem.OnGroupChangedListener() {
                        public void onItemAdded(MapItem item, MapGroup group) {
                        }

                        public void onItemRemoved(MapItem item,
                                MapGroup group) {
                            if (item.getUID().equalsIgnoreCase(parentItemUID)) {
                                MapItem existingRte = mapView.getRootGroup()
                                        .deepFindUID(
                                                parentItemUID + "-rte");
                                if (existingRte != null)
                                    existingRte.getGroup()
                                            .removeItem(existingRte);
                            }
                        }
                    });

            item.addOnVisibleChangedListener(
                    new MapItem.OnVisibleChangedListener() {
                        @Override
                        public void onVisibleChanged(MapItem item) {
                            if (item.getVisible()) {
                                MapItem existingRte = mapView.getRootGroup()
                                        .deepFindUID(parentItemUID + "-rte");
                                if (existingRte != null)
                                    existingRte.setVisible(true);
                            } else {
                                MapItem existingRte = mapView.getRootGroup()
                                        .deepFindUID(parentItemUID + "-rte");
                                if (existingRte != null)
                                    existingRte.setVisible(false);
                            }
                        }
                    });
        }

        return ImportResult.SUCCESS;
    }

    @Override
    public boolean toCotDetail(MapItem item, CotEvent event, CotDetail root) {
        return false;
    }

    /**
     * Implementation for Route for use of the toCot() method
     */
    private static class TemplateRoute extends Route {
        public TemplateRoute(MapView mapView, String routeName, int color,
                String prefix, String uid) {
            super(mapView, routeName, color, prefix, uid);
            removeMetaData("archive");
        }

        public CotEvent toCot() {
            return super.toCot();
        }
    }
}
