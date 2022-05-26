
package com.atakmap.android.util;

import android.content.Context;

import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.widgets.MarkerDrawableWidget;
import com.atakmap.android.widgets.RootLayoutWidget;
import com.atakmap.app.R;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapItem;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

import java.util.UUID;

public class DragMarkerHelper {

    private static final String TAG = "DragMarkerHelper";

    private final MapView mapView;
    private Marker marker;
    private boolean dragging = false, draggable = false;
    private MapEventDispatcher.MapEventDispatchListener dropEvent;
    private final Marker dragMarker;

    private final MapGroup rootGroup;
    private final MapEventDispatcher dispatcher;
    private final MarkerDrawableWidget reticle;

    private static DragMarkerHelper _instance;

    private DragMarkerHelper() {
        mapView = MapView.getMapView();
        dragMarker = createDragMarker();
        rootGroup = mapView.getRootGroup();
        dispatcher = mapView.getMapEventDispatcher();

        // Reticle widget (drawn over map)
        Context ctx = mapView.getContext();
        float retSize = 80f * ctx.getResources()
                .getDisplayMetrics().density;
        RootLayoutWidget root = (RootLayoutWidget) mapView.getComponentExtra(
                "rootLayoutWidget");
        reticle = new MarkerDrawableWidget(ctx.getDrawable(
                R.drawable.large_reticle_red));
        reticle.setSize(retSize, retSize);
        reticle.setVisible(false);
        root.addChildWidgetAt(0, reticle);
    }

    public synchronized static DragMarkerHelper getInstance() {
        if (_instance == null)
            _instance = new DragMarkerHelper();
        return _instance;
    }

    public static Marker createDragMarker() {
        Marker marker = new Marker(UUID.randomUUID().toString());
        marker.addOnPointChangedListener(
                new PointMapItem.OnPointChangedListener() {
                    @Override
                    public void onPointChanged(PointMapItem item) {
                        getInstance().updateWidget(item);
                    }
                });
        marker.addOnGroupChangedListener(new MapItem.OnGroupChangedListener() {
            @Override
            public void onItemAdded(MapItem item, MapGroup group) {
                getInstance().updateWidget(item);
            }

            @Override
            public void onItemRemoved(MapItem item, MapGroup group) {
                getInstance().hideWidget();
            }
        });
        return marker;
    }

    /**
     * Update the reticle widget's target marker
     * @param mi Marker
     */
    public void updateWidget(MapItem mi) {
        if (!(mi instanceof PointMapItem))
            return;
        PointMapItem pmi = (PointMapItem) mi;
        reticle.setMarker(pmi);
        reticle.setVisible(true);
    }

    /**
     * Hide the reticle widget
     */
    public void hideWidget() {
        reticle.setMarker(null);
        reticle.setVisible(false);
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
        dragging = true;
        // show the drag icon
        draggable = marker.getMetaBoolean("drag", false);
        marker.setMetaBoolean("drag", true);
    }

    /**
     * Method used to clean up after dragging has finished.
     */
    private void stopDragIcon() {
        dragging = false;
        // restore the current icon
        marker.setMetaBoolean("drag", draggable);
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
                            event.getPointF().x, event.getPointF().y);
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
