
package com.atakmap.android.toolbars;

import android.content.Context;
import android.content.Intent;
import android.graphics.PointF;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.util.DragMarkerHelper;
import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.assets.Icon;

import com.atakmap.coremap.maps.coords.DistanceCalculations;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.map.elevation.ElevationManager;

public class DynamicRangeAndBearingEndpoint extends Marker implements
        MapEventDispatcher.OnMapEventListener {

    public static final String TAG = "RangeAndBearingEndpoint";

    static final int PART_TAIL = 0;
    static final int PART_HEAD = 1;

    private final MapView _mapView;

    private final float _hitRadiusSq = 1024; // 32 pixel touch radius, 1024 is 32 squared

    private RangeAndBearingMapItem _parent;
    private int _part;
    protected Icon _defaultIcon;

    private Runnable postDragAction;

    public DynamicRangeAndBearingEndpoint(MapView mapView,
            GeoPointMetaData point,
            String uid) {
        super(point, uid);
        _mapView = mapView;
        Context ctx = mapView.getContext();
        setShowLabel(false);
        setTitle("Orphan Endpoint");
        setMetaString("type", "u-r-b-o-endpoint");
        setMetaBoolean("drag", true); //Allow it to be dragged
        setMetaBoolean("nevercot", true); //Ensure it's never saved
        setMetaBoolean("displayRnB", false);
        setZOrder(Double.NEGATIVE_INFINITY);

        _defaultIcon = new Icon.Builder().setImageUri(0, "android.resource://"
                + ctx.getPackageName() + "/"
                + R.drawable.open_circle_small)
                .build();
        setIcon(_defaultIcon);
        setMetaString("menu", "menus/drab_menu.xml"); //Set the menu

        _mapView.getMapEventDispatcher().addMapItemEventListener(this, this);
    }

    @Override
    public synchronized void dispose() {
        super.dispose();
        _mapView.getMapEventDispatcher().removeMapItemEventListener(this, this); //Shouldn't really need to do
    }

    public void setParent(RangeAndBearingMapItem parent) {
        _parent = parent;
    }

    public RangeAndBearingMapItem getParent() {
        return _parent;
    }

    public void setPart(int part) {
        _part = part;
    }

    public int getPart() {
        return _part;
    }

    /**
     * A runnable to be fired when the action has completed.
     */
    public void setPostDragAction(Runnable r) {
        postDragAction = r;
    }

    @Override
    public void onMapItemMapEvent(MapItem item, MapEvent event) {
        Log.d(TAG, "onMapItemMapEventOccurred - Event info: " + event.getType()
                + " " + item.getUID());
        if (item == this
                && (event.getType().equals(MapEvent.ITEM_DRAG_STARTED) ||
                        event.getType().equals(MapEvent.ITEM_DRAG_CONTINUED))) {
            PointF pt = event.getPointF();
            GeoPointMetaData gp;
            if (getParent() != null && getParent().isLocked()) {
                GeoPoint anchorPoint;
                if (getParent().getPoint1Item().equals(this))
                    anchorPoint = getParent().getPoint2Item().getPoint();
                else
                    anchorPoint = getParent().getPoint1Item().getPoint();
                double bearing = DistanceCalculations.bearingFromSourceToTarget(
                        anchorPoint, _mapView.inverse(pt.x, pt.y,
                                MapView.InverseMode.RayCast).get());
                double range = GeoCalculations.distanceTo(anchorPoint,
                        getPoint());
                gp = GeoPointMetaData
                        .wrap(GeoCalculations.pointAtDistance(anchorPoint,
                                bearing, range));
                try {
                    gp = ElevationManager.getElevationMetadata(gp.get());
                } catch (Exception e) {
                    Log.w(TAG, "Failed to lookup elevation for point", e);
                }

            } else {
                gp = _mapView.inverseWithElevation(pt.x, pt.y);
            }
            // don't allow set if point goes invalid during drag
            final GeoPoint p = gp.get();
            if (!p.isValid())
                return;
            this.setPoint(gp);
            DragMarkerHelper.getInstance().updateWidget(item);
            Intent displayData = new Intent(
                    "com.atakmap.android.action.SHOW_POINT_DETAILS");
            displayData.putExtra("uid", item.getUID());
            displayData.putExtra("displayRnB", false);
            AtakBroadcast.getInstance().sendBroadcast(displayData);
        } else if (item == this
                && event.getType().equals(MapEvent.ITEM_DRAG_DROPPED)) {
            DragMarkerHelper.getInstance().hideWidget();
            if (postDragAction != null)
                postDragAction.run();
        } else if (item == this && event.getType().equals(
                MapEvent.ITEM_REMOVED)) {
            Intent hideData = new Intent(
                    "com.atakmap.android.action.HIDE_POINT_DETAILS");
            hideData.putExtra("uid", item.getUID());
            AtakBroadcast.getInstance().sendBroadcast(hideData);
        }
    }
}
