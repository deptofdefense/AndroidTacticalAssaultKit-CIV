
package com.atakmap.android.routes;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import java.util.UUID;

import com.atakmap.android.maps.Arrow;
import com.atakmap.android.maps.Association;
import com.atakmap.android.maps.MapGroup;

import com.atakmap.android.maps.MultiPolyline;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.maps.Polyline;
import com.atakmap.android.maps.Shape;
import com.atakmap.android.toolbar.Tool;
import com.atakmap.android.toolbar.ToolManagerBroadcastReceiver;
import com.atakmap.android.toolbar.widgets.TextContainer;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.android.maps.MapTouchController.DeconflictionListener;

import java.util.SortedSet;
import java.util.ArrayList;
import java.util.List;

/**
 * Tool used to implement the a polyline selector.    This encapsulates all of the actions required to 
 * push and pop the map event dispatch stack.
 * This tool self registers, so the only thing that needs to be done is that it needs to be added to 
 * the corresponding MapComponent.
 * In order to activate it from your button or gui component, call this code:
 * <pre>
 * Bundle extras = new Bundle();
 * ToolManagerBroadcastReceiver.getInstance().startTool(
 *                 PolylineSelectTool.TOOL_IDENTIFIER, extras);
 * </pre>
 * Please note that the TOOL_IDENTIFER must be unique - otherwise issues might occur.
 * 
 */
public class PolylineSelectTool extends Tool implements
        MapEventDispatcher.MapEventDispatchListener {

    public static final String TAG = "PolylineSelectTool";

    public static final String TOOL_IDENTIFIER = "com.atakmap.android.route.poly.DropSelectTool";
    public static final String TOOL_FINISHED = "com.atakmap.android.route.poly.TOOL_FINISHED";

    private final Context _context;
    private final TextContainer _container;

    private static boolean isSupported(MapItem mi) {
        return !(mi instanceof Route) && (mi instanceof Polyline
                || mi instanceof Arrow || mi instanceof Association);
    }

    private DeconflictionListener decon = new DeconflictionListener() {
        public void onConflict(final SortedSet<MapItem> hitItems) {
            List<MapItem> list = new ArrayList<>(hitItems);
            for (MapItem mi : list) {
                // Shape redirect from marker
                if (mi instanceof PointMapItem) {
                    mi = ATAKUtilities.findAssocShape(mi);
                    hitItems.remove(mi);
                    hitItems.add(mi);
                }
                if (!isSupported(mi))
                    hitItems.remove(mi);
            }
        }
    };

    public PolylineSelectTool(final MapView mapView) {
        super(mapView, TOOL_IDENTIFIER);
        _context = mapView.getContext();
        _container = TextContainer.getInstance();
        ToolManagerBroadcastReceiver.getInstance().registerTool(
                TOOL_IDENTIFIER, this);
    }

    public void dispose() {
        ToolManagerBroadcastReceiver.getInstance().unregisterTool(
                TOOL_IDENTIFIER);
    }

    @Override
    public boolean onToolBegin(Bundle extras) {
        _mapView.getMapEventDispatcher().pushListeners();
        _mapView.getMapEventDispatcher().clearListeners(MapEvent.ITEM_CLICK);
        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.ITEM_CLICK, this);

        _container.displayPrompt(
                "Select any line on the map to convert it to a route.");
        _mapView.getMapTouchController().addDeconflictionListener(decon);
        return true;
    }

    @Override
    public void onToolEnd() {
        _container.closePrompt();

        _mapView.getMapEventDispatcher().popListeners();
        _mapView.getMapTouchController().removeDeconflictionListener(decon);
        _mapView.getMapTouchController().skipDeconfliction(false);
    }

    @Override
    public void onMapEvent(MapEvent event) {
        if (event.getType().equals(MapEvent.ITEM_CLICK)) {
            // Find item click point
            MapItem mi = event.getItem();

            // Check if shape marker
            if (mi instanceof PointMapItem)
                mi = ATAKUtilities.findAssocShape(mi);

            // Item is already a route or not supported
            if (!isSupported(mi))
                return;

            Shape shape = (Shape) mi;
            int color = shape.getStrokeColor();
            if (shape instanceof MultiPolyline)
                color = shape.getIconColor();
            final Route r = new Route(_mapView,
                    shape.getTitle() + " Route",
                    color, "CP",
                    UUID.randomUUID().toString());
            GeoPoint[] list = shape.getPoints();
            PointMapItem[] pts = new PointMapItem[list.length];

            for (int i = 0; i < list.length; ++i) {
                pts[i] = Route.createControlPoint(list[i]);
            }
            r.addMarkers(0, pts);
            r.setRemarks(shape.getRemarks());
            r.setMetaString("entry", "user");
            final MapGroup mapGroup = _mapView.getRootGroup()
                    .findMapGroup("Route");
            mapGroup.addItem(r);
            Intent i = new Intent("com.atakmap.android.maps.ROUTE_DETAILS");
            i.putExtra("routeUID", r.getUID());
            AtakBroadcast.getInstance().sendBroadcast(i);
            requestEndTool();
        }
    }
}
