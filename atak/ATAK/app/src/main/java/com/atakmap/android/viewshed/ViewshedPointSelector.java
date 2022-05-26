
package com.atakmap.android.viewshed;

import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnKeyListener;
import android.widget.Toast;

import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.toolbar.widgets.TextContainer;
import com.atakmap.app.R;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

import java.util.UUID;

public class ViewshedPointSelector implements
        MapEventDispatcher.MapEventDispatchListener, OnKeyListener {
    private static final String VIEWSHED_MARKER_TYPE = "vsd-marker";

    private final MapView mapView;
    private final ViewshedDropDownReceiver callback;
    private final TextContainer instructionContainer;
    private boolean singlePointSelection = true;
    private MapItem firstMapItem = null;

    private String viewshedMarkerUID = "";
    private MapGroup viewshedGroup;

    private boolean selection = false;

    ViewshedPointSelector(ViewshedDropDownReceiver vddr, MapView mv) {
        mapView = mv;
        callback = vddr;
        instructionContainer = TextContainer.getInstance();
    }

    public void dispose() {
        endPointSelection();
    }

    /**
     * start listening for a map touch event
     */
    void beginPointSelection() {
        synchronized (this) {
            if (selection)
                return;
            singlePointSelection = true;
            selection = true;

            mapView.getMapEventDispatcher().pushListeners();

            // listen for back press
            mapView.addOnKeyListener(this);

            mapView.getMapEventDispatcher().clearListeners(MapEvent.MAP_CLICK);
            mapView.getMapEventDispatcher().clearListeners(MapEvent.ITEM_CLICK);
            mapView.getMapEventDispatcher().addMapEventListener(
                    MapEvent.MAP_CLICK, this);
            mapView.getMapEventDispatcher().addMapEventListener(
                    MapEvent.ITEM_CLICK, this);

            instructionContainer
                    .displayPrompt(mapView.getResources().getString(
                            R.string.viewshed_prompt));
        }
    }

    private void endPointSelection() {
        synchronized (this) {
            if (!selection)
                return;

            selection = false;
            firstMapItem = null;
            instructionContainer.closePrompt();
            mapView.getMapEventDispatcher().clearListeners();
            mapView.getMapEventDispatcher().popListeners();

            mapView.removeOnKeyListener(this);
        }
    }

    /**
     * Begin selecting the first point in the viewshed line
     */
    public void beginFirstPointSelection() {
        synchronized (this) {
            if (selection)
                return;
            singlePointSelection = false;
            firstMapItem = null;

            selection = true;

            mapView.getMapEventDispatcher().pushListeners();

            // listen for back press
            mapView.addOnKeyListener(this);

            mapView.getMapEventDispatcher().clearListeners(MapEvent.MAP_CLICK);
            mapView.getMapEventDispatcher().clearListeners(MapEvent.ITEM_CLICK);
            mapView.getMapEventDispatcher().addMapEventListener(
                    MapEvent.ITEM_CLICK, this);
            mapView.getMapEventDispatcher().addMapEventListener(
                    MapEvent.MAP_CLICK, this);

            instructionContainer
                    .displayPrompt(mapView.getResources().getString(
                            R.string.viewshed_line_prompt1));
        }
    }

    @Override
    public void onMapEvent(final MapEvent event) {
        if (!selection) {
            // this should not actually happen
            return;
        }
        if (singlePointSelection) {
            endPointSelection();

            if (event.getType().equals(MapEvent.ITEM_CLICK)) {
                // the user selected an existing mapItem
                MapItem pt = event.getItem();
                if (pt instanceof PointMapItem) {
                    if (!((PointMapItem) pt).getPoint().isAltitudeValid()) {
                        Toast.makeText(
                                mapView.getContext(),
                                mapView.getContext().getString(
                                        R.string.no_dted),
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (pt.getType().contentEquals(VIEWSHED_MARKER_TYPE)) {
                        callback.setMarker((PointMapItem) pt);
                        return;
                    }
                    callback.setMarker((PointMapItem) pt);
                }
            } else if (event.getType().equals(MapEvent.MAP_CLICK)) {
                //the user chose to move the Viewshed Marker to a point on the map
                if (event.getItem() == null) {
                    final GeoPointMetaData geoPoint = mapView
                            .inverseWithElevation(event.getPointF().x,
                                    event.getPointF().y);
                    if (!geoPoint.get().isAltitudeValid()) {
                        Toast.makeText(
                                mapView.getContext(),
                                mapView.getContext().getString(
                                        R.string.no_dted),
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    createPoint(geoPoint);
                }
            }
        } else {
            if (event.getType().equals(MapEvent.MAP_CLICK)) {
                Toast.makeText(mapView.getContext(),
                        "A Marker must be selected", Toast.LENGTH_SHORT).show();
                return;
            }
            if (firstMapItem == null) {
                if (event.getType().equals(MapEvent.ITEM_CLICK)) {
                    // the user selected an existing mapItem
                    MapItem pt = event.getItem();
                    firstMapItem = pt;

                    instructionContainer
                            .displayPrompt(mapView.getResources().getString(
                                    R.string.viewshed_line_prompt2));
                }
            } else {
                if (event.getType().equals(MapEvent.ITEM_CLICK)) {
                    // the user selected an existing mapItem
                    MapItem pt = event.getItem();
                    callback.setMarkerLine((PointMapItem) firstMapItem,
                            (PointMapItem) pt);
                    endPointSelection();
                }
            }
        }

    }

    /**
     * Create the viewshed Marker at a given point
     * 
     * @param geoPoint - the point to place the viewshed Marker
     */
    private void createPoint(final GeoPointMetaData geoPoint) {
        //let the user give the point a name

        if (viewshedGroup == null) {
            MapGroup checkGroup = MapGroup
                    .deepFindGroupByNameBreadthFirst(mapView.getRootGroup(),
                            "Viewshed Group");
            if (checkGroup == null)
                viewshedGroup = mapView.getRootGroup().addGroup(
                        "Viewshed Group");
            else
                viewshedGroup = checkGroup;
        }
        viewshedMarkerUID = UUID.randomUUID().toString();

        int vsdNum = viewshedGroup.findItems("type", VIEWSHED_MARKER_TYPE)
                .size() + 1;
        Marker vsdMarker = new Marker(geoPoint, viewshedMarkerUID);
        vsdMarker.setTitle("Viewshed " + vsdNum);
        vsdMarker.setType(VIEWSHED_MARKER_TYPE);
        vsdMarker.setMetaString("callsign",
                "Viewshed " + vsdNum);
        vsdMarker.setMetaString("entry", "user");
        vsdMarker.setMetaBoolean("nevercot", true);
        vsdMarker.setMovable(true);
        vsdMarker.setMetaBoolean("removable", true);
        vsdMarker.setMetaBoolean("preciseMove", true);
        vsdMarker.setMetaString("how", "h-g-i-g-o");
        viewshedGroup.addItem(vsdMarker);

        callback.setMarker(vsdMarker);
    }

    @Override
    public boolean onKey(final View v, final int keyCode,
            final KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            endPointSelection();
            return true;
        }
        return false;
    }

}
