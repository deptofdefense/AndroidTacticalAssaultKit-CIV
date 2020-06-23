
package com.atakmap.android.util;

import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.maps.assets.Icon;
import com.atakmap.app.R;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapItem;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

import java.util.UUID;

public class DragMarkerHelper implements Marker.OnIconChangedListener {

    private static final String TAG = "DragMarkerHelper";

    private MapView mapView;
    private Marker marker;
    private int markerIconVis;
    private boolean dragging = false, draggable = false;
    private MapEventDispatcher.MapEventDispatchListener dropEvent;
    private Marker dragMarker;

    private final MapGroup rootGroup;
    private final MapEventDispatcher dispatcher;

    private static DragMarkerHelper _instance;

    private DragMarkerHelper() {
        mapView = MapView.getMapView();
        dragMarker = createDragMarker();
        rootGroup = mapView.getRootGroup();
        dispatcher = mapView.getMapEventDispatcher();
    }

    public synchronized static DragMarkerHelper getInstance() {
        if (_instance == null)
            _instance = new DragMarkerHelper();
        return _instance;
    }

    public static Marker createDragMarker() {
        MapView mv = MapView.getMapView();
        Icon ico = null;
        if (mv != null)
            ico = new Icon.Builder().setImageUri(0, "android.resource://"
                    + mv.getContext().getPackageName() + "/"
                    + R.drawable.large_reticle_red).build();
        Marker marker = new Marker(UUID.randomUUID().toString());
        marker.setIcon(ico);
        return marker;
    }

    /**
     * This method will start all of the mechanics of dragging a marker.
     */
    synchronized public void startDrag(final Marker m,
            final MapEventDispatcher.MapEventDispatchListener dropEvent) {
        if (m == marker)
            return;

        endDrag();

        marker = m;
        if (marker == null)
            return;

        this.dropEvent = dropEvent;
        startDragIcon();
        dragMarker.setPoint(marker.getPoint());
        rootGroup.addItem(dragMarker);

        dispatcher.pushListeners();
        dispatcher.clearListeners(MapEvent.ITEM_DRAG_STARTED);
        dispatcher.clearListeners(MapEvent.ITEM_DRAG_CONTINUED);
        dispatcher.clearListeners(MapEvent.ITEM_DRAG_DROPPED);

        dispatcher.addMapEventListener(MapEvent.ITEM_DRAG_STARTED,
                longPressDrag);
        dispatcher.addMapEventListener(MapEvent.ITEM_DRAG_CONTINUED,
                longPressDrag);
        dispatcher.addMapEventListener(MapEvent.ITEM_DRAG_DROPPED,
                longPressDrag);
        // Not used here but required for drag to work
        dispatcher.addMapEventListener(MapEvent.ITEM_PRESS, longPressDrag);
    }

    /**
     * This will stop the dragging of a marker.
     */
    synchronized public void endDrag() {
        if (marker == null)
            return;

        dispatcher.popListeners();

        stopDragIcon();
        rootGroup.removeItem(dragMarker);

        marker = null;

    }

    private void startDragIcon() {
        // just in case an external entity sets the icon
        // while it is showing the drag icon
        marker.addOnIconChangedListener(this);
        dragging = true;
        // show the drag icon
        markerIconVis = marker.getIconVisibility();
        if (markerIconVis == Marker.ICON_VISIBLE)
            marker.setIconVisibility(Marker.ICON_INVISIBLE);
        draggable = marker.getMetaBoolean("drag", false);
        marker.setMetaBoolean("drag", true);
    }

    /**
     * Method used to clean up after dragging has finished.
     */
    private void stopDragIcon() {
        dragging = false;
        // restore the current icon
        marker.setIconVisibility(markerIconVis);
        marker.setMetaBoolean("drag", draggable);
        marker.removeOnIconChangedListener(this);
    }

    @Override
    public void onIconChanged(final Marker marker) {
        int iconVis = marker.getIconVisibility();
        if (iconVis != Marker.ICON_INVISIBLE) {
            markerIconVis = iconVis;
            if (dragging)
                marker.setIconVisibility(Marker.ICON_INVISIBLE);
        }
    }

    private final MapEventDispatcher.MapEventDispatchListener longPressDrag = new MapEventDispatcher.MapEventDispatchListener() {
        @Override
        public void onMapEvent(MapEvent event) {

            final String type = event.getType();
            final MapItem item = event.getItem();
            if (item instanceof PointMapItem) {
                // PointMapItem pmi = (PointMapItem) item;
                if (type.equals(MapEvent.ITEM_DRAG_CONTINUED)
                        || type.equals(MapEvent.ITEM_DRAG_STARTED)) {
                    //Log.d(TAG,
                    //        "dragging: "
                    //                + pmi.getMetaString("callsign", "unknown"));
                    // calculate the new ground point
                    GeoPointMetaData newPoint = mapView.inverseWithElevation(
                            event.getPoint().x, event.getPoint().y);
                    dragMarker.setPoint(newPoint);
                    //pmi.setPoint(newPoint);
                } else if (type.equals(MapEvent.ITEM_DRAG_DROPPED)) {
                    //Log.d(TAG,
                    //        "dropped: "
                    //                + pmi.getMetaString("callsign", "unknown"));

                    // this should likely be modified so the type is a MAP_CLICK
                    dropEvent.onMapEvent(event);
                }
            }
        }
    };

}
