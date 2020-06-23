
package com.atakmap.android.vehicle;

import android.graphics.Point;
import android.os.Bundle;
import android.widget.ImageButton;

import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.toolbar.ButtonTool;
import com.atakmap.android.toolbar.ToolManagerBroadcastReceiver;
import com.atakmap.android.toolbar.widgets.TextContainer;
import com.atakmap.android.toolbars.RangeAndBearingMapItem;
import com.atakmap.android.user.PlacePointTool;
import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.assets.Icon;
import com.atakmap.coremap.maps.coords.DistanceCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.coremap.maps.coords.NorthReference;

import java.util.List;
import java.util.UUID;

/**
 * Rotation tool
 */
public class VehicleRotationTool extends ButtonTool
        implements MapEventDispatcher.OnMapEventListener,
        MapItem.OnGroupChangedListener {

    public static final String TAG = "VehicleRotationTool";
    public static final String TOOL_NAME = "vehicle_rotation_tool";

    private VehicleShape _shape;
    private Marker _anchor;
    private RangeAndBearingMapItem _rab;
    private TextContainer _cont;

    public VehicleRotationTool(MapView mapView, ImageButton button) {
        super(mapView, button, TOOL_NAME);
        _cont = TextContainer.getInstance();

        ToolManagerBroadcastReceiver.getInstance()
                .registerTool(TOOL_NAME, this);
    }

    @Override
    public boolean onToolBegin(Bundle extras) {
        Log.d(TAG, "BEGIN ROTATION TOOL");

        if (extras.containsKey("uid")) {
            List<MapItem> items = _mapView.getRootGroup()
                    .deepFindItems("uid", extras.getString("uid"));
            Log.d(TAG, "UID: " + extras.getString("uid"));
            for (MapItem item : items) {
                Log.d(TAG, "ITEM: " + item);
                if (item instanceof VehicleShape) {
                    _shape = (VehicleShape) item;
                    break;
                } else if (item.hasMetaValue("shapeUID")) {
                    _shape = (VehicleShape) _mapView.getRootGroup()
                            .deepFindUID(item.getMetaString("shapeUID", ""));
                }
            }
        }
        if (_shape != null) {
            _cont.displayPrompt(_mapView.getContext().getString(
                    R.string.point_dropper_text57));
            // Take control of all map events
            _mapView.getMapEventDispatcher()
                    .addMapItemEventListener(createAnchor(), this);
            _mapView.getMapTouchController().setToolActive(true);
            _shape.addOnGroupChangedListener(this);
            return true;
        }
        return false;
    }

    @Override
    public void onToolEnd() {
        unregisterListeners();
    }

    private void unregisterListeners() {
        if (_shape != null) {
            _mapView.getMapTouchController().setToolActive(false);
            _mapView.getMapEventDispatcher().removeMapItemEventListener(
                    _anchor, this);
            _cont.closePrompt();
            _shape.removeOnGroupChangedListener(this);
            _shape = null;
        }
        removeAnchor();
    }

    private Marker createAnchor() {
        if (_anchor == null && _shape != null) {
            double len = 1.2d * Math.max(_shape.getMetaDouble("length", 0), 10);

            GeoPointMetaData center = _shape.getCenter();
            GeoPoint anchorPos = DistanceCalculations
                    .computeDestinationPoint(center.get(),
                            _shape.getAzimuth(NorthReference.TRUE), len);

            PlacePointTool.MarkerCreator mc = new PlacePointTool.MarkerCreator(
                    anchorPos);

            mc.showCotDetails(false);
            mc.setNeverPersist(true);
            mc.setType("u-r-b-o-endpoint");
            mc.setCallsign("Rotation Anchor");
            _anchor = mc.placePoint();
            _anchor.setMetaBoolean("ignoreMenu", true);
            _anchor.setMetaBoolean("drag", true);
            _anchor.setMetaDouble("minRenderScale", Double.MAX_VALUE);
            _rab = RangeAndBearingMapItem.createOrUpdateRABLine(
                    UUID.randomUUID().toString(), _shape.getAnchorItem(),
                    _anchor, false);
            _rab.setClickable(false);
            _anchor.getGroup().addItem(_rab);
            _rab.setZOrder(Double.NEGATIVE_INFINITY);
            _rab.setDisplaySlantRange(false);

            Icon anchorIcon = new Icon.Builder()
                    .setImageUri(0, "android.resource://"
                            + _mapView.getContext().getPackageName()
                            + "/" + R.drawable.open_circle_small)
                    .build();
            _anchor.setIcon(anchorIcon);
        }
        return _anchor;
    }

    private void removeAnchor() {
        if (_rab != null)
            _rab.removeFromGroup();
        if (_anchor != null)
            _anchor.removeFromGroup();
        _anchor = null;
    }

    @Override
    public void onMapItemMapEvent(MapItem item, MapEvent event) {
        if (_shape == null || _shape.getGroup() == null || _anchor != item)
            return;
        String type = event.getType();
        if (type.equals(MapEvent.ITEM_DRAG_STARTED)
                || type.equals(MapEvent.ITEM_DRAG_CONTINUED)
                || type.equals(MapEvent.ITEM_DRAG_DROPPED)) {

            Point pt = event.getPoint();
            // rotation is relative about a point, no need to look at terrain
            GeoPoint gp = _mapView.inverse(pt.x, pt.y).get();
            if (!gp.isValid())
                return;
            double ang = _shape.getCenter().get().bearingTo(gp);

            _shape.setAzimuth(ang, NorthReference.TRUE);

            GeoPoint offset = DistanceCalculations.computeDestinationPoint(
                    _shape.getCenter().get(), ang, 1.2d * Math.max(
                            _shape.getMetaDouble("length", 0), 10));
            _anchor.setPoint(offset);

            if (type.equals(MapEvent.ITEM_DRAG_DROPPED)) {
                _shape.save();
                requestEndTool();
                unregisterListeners();
            }
        }
    }

    @Override
    public void onItemAdded(MapItem item, MapGroup group) {
    }

    @Override
    public void onItemRemoved(MapItem item, MapGroup group) {
        if (_shape == item) {
            requestEndTool();
            unregisterListeners();
        }
    }
}
