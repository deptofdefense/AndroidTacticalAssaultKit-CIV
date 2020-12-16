
package com.atakmap.android.routes.cot;

import java.util.UUID;

import com.atakmap.android.drawing.importer.EditablePolylineImporter;
import com.atakmap.android.editableShapes.EditablePolyline;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.routes.Route;
import com.atakmap.android.routes.RouteMapReceiver;
import com.atakmap.app.R;
import com.atakmap.comms.CommsMapComponent.ImportResult;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

public class RouteImporter extends EditablePolylineImporter {

    private final static String TAG = "RouteImporter";

    private final Context context;
    private final RouteMapReceiver routeMapReceiver;

    public RouteImporter(MapView mapView, RouteMapReceiver routeMapReceiver) {
        super(mapView, routeMapReceiver.getRouteGroup(), "b-m-r");
        this.context = mapView.getContext();
        this.routeMapReceiver = routeMapReceiver;
    }

    @Override
    protected int getNotificationIcon(MapItem item) {
        return R.drawable.ic_route;
    }

    @Override
    public ImportResult importData(CotEvent event, Bundle extras) {

        MapItem existing = findItem(event);

        super.importData(event, extras);

        MapItem newItem = findItem(event);
        if (!(newItem instanceof Route))
            return ImportResult.FAILURE;

        Route route = (Route) newItem;
        route.setMetaString("from", extras.getString("from"));

        Intent notifyIntent = new Intent(
                RouteMapReceiver.ACTION_ROUTE_IMPORT_FINISHED);
        notifyIntent.putExtra(RouteMapReceiver.EXTRA_ROUTE_UID,
                route.getUID());
        notifyIntent.putExtra(RouteMapReceiver.EXTRA_ROUTE_TITLE,
                route.getTitle());
        notifyIntent.putExtra(RouteMapReceiver.EXTRA_ROUTE_TYPE,
                route.getTransportationType());

        if (existing == null) {
            // PointMapItem firstPoint = route.getPoint(0);
            notifyIntent.putExtra(RouteMapReceiver.EXTRA_ROUTE_IS_NEW,
                    !isLocalImport(extras));
        } else {
            notifyIntent.putExtra(RouteMapReceiver.EXTRA_ROUTE_IS_NEW, false);
            notifyIntent.putExtra(RouteMapReceiver.EXTRA_ROUTE_UPDATED, true);
        }
        AtakBroadcast.getInstance().sendBroadcast(notifyIntent);

        return ImportResult.SUCCESS;
    }

    @Override
    protected void postNotification(MapItem item) {
        // The notification intent above handles this
    }

    @Override
    protected ImportResult importMapItem(MapItem existing, CotEvent event,
            Bundle extras) {
        Route route = (Route) existing;

        // we didn't have it in memory, lets recreate it
        if (route == null) {
            route = routeMapReceiver.getNewRoute(event.getUID());

            // XXX - hack for bug 2331 -- disable refreshes
            route.setMetaBoolean("__ignoreRefresh", true);

            route.setColor(Route.DEFAULT_ROUTE_COLOR);
            route.setTransportationType(context
                    .getString(R.string.routes_text34));
            route.setRouteMethod("Driving");
            route.setRouteDirection("Infil");
            route.setRouteType("Primary");
            route.setPlanningMethod("Infil");
            route.setRouteOrder("Ascending Check Points");

            // Remove automatic archive flag if the CoT doesn't supply one
            if (event.findDetail("archive") == null)
                route.removeMetaData("archive");

        } else {
            // XXX - hack for bug 2331 -- disable refreshes
            route.setMetaBoolean("__ignoreRefresh", true);
        }

        CotDetail linkAttr = event.findDetail("link_attr");
        if (linkAttr != null) {
            route.setColor(Integer.parseInt(linkAttr
                    .getAttribute("color")));
            route.setTransportationType(linkAttr.getAttribute("type"));

            if (linkAttr.getAttribute("prefix") != null)
                route.setPrefix(linkAttr.getAttribute("prefix"));

            if (linkAttr.getAttribute("method") != null)
                route.setRouteMethod(linkAttr.getAttribute("method"));

            if (linkAttr.getAttribute("direction") != null)
                route.setRouteDirection(linkAttr
                        .getAttribute("direction"));

            if (linkAttr.getAttribute("routetype") != null)
                route.setRouteType(linkAttr.getAttribute("routetype"));

            if (linkAttr.getAttribute("planningmethod") != null)
                route.setPlanningMethod(linkAttr
                        .getAttribute("planningmethod"));

            if (linkAttr.getAttribute("order") != null)
                route.setRouteOrder(linkAttr.getAttribute("order"));

            if (linkAttr.getAttribute("stroke") != null) {
                try {
                    route.setStrokeWeight(Integer.parseInt(linkAttr
                            .getAttribute("stroke")));
                } catch (Exception e) {
                    Log.e(TAG, "error: ", e);
                }
            }
        }
        if (!loadPoints(route, event)) {
            route.removeFromGroup();
            return ImportResult.FAILURE;
        }

        // XXX - hack for bug 2331 -- reenable refreshes
        route.setMetaBoolean("__ignoreRefresh", false);
        route.refresh(_mapView.getMapEventDispatcher(), null,
                this.getClass());
        route.setEditable(false);
        return super.importMapItem(route, event, extras);
    }

    @Override
    public PointMapItem createMarker(EditablePolyline poly,
            CotDetail linkDetails) {

        GeoPoint point = GeoPoint.parseGeoPoint(linkDetails
                .getAttribute("point"));
        if (point == null) {
            Log.d(TAG, "invalid point encountered in the route, dropping: "
                    + linkDetails);
            return null;
        }

        String linkUID = linkDetails.getAttribute("uid");
        String linkType = linkDetails.getAttribute("type");

        // In case the UID/type is missing for some reason (see ATAK-10368)
        // assume this is a control point
        if (linkUID == null)
            linkUID = UUID.randomUUID().toString();
        if (linkType == null)
            linkType = Route.CONTROLPOINT_TYPE;

        if (linkType.equals(Route.CONTROLPOINT_TYPE)) {
            // XXX - control points are never added to a mapgroup,
            // so doing a deep find seems to just waste time.
            // i thought it was in there to do something more intelligent
            // with control points.
            return Route.createControlPoint(point, linkUID);
        }

        MapItem mi = _mapView.getRootGroup().deepFindUID(linkUID);
        PointMapItem pmi = mi instanceof PointMapItem ? (PointMapItem) mi
                : null;
        Marker marker = mi instanceof Marker ? (Marker) mi : null;

        if (linkType.equals(Route.WAYPOINT_TYPE)) {
            if (marker == null)
                marker = Route.createWayPoint(
                        GeoPointMetaData.wrap(point), linkUID);
            else
                marker.setPoint(point);
            if (!poly.getVisible())
                marker.setVisible(false);
            String callsign = linkDetails.getAttribute("callsign");
            if (callsign != null) {
                marker.setMetaString("callsign", callsign);
                marker.setTitle(callsign);
            }
            String remarks = linkDetails.getAttribute("remarks");
            if (remarks != null)
                marker.setMetaString("remarks", remarks);
            if (marker.getGroup() == null)
                routeMapReceiver.getWaypointGroup().addItem(marker);
            return marker;
        } else {
            if (pmi == null)
                pmi = Route.createControlPoint(point, linkUID);
            return pmi;
        }
    }
}
