
package com.atakmap.android.vehicle.overhead;

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
import com.atakmap.coremap.maps.assets.Icon;
import com.atakmap.coremap.maps.coords.DistanceCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;

import java.util.UUID;

/**
 * Rotation tool
 */
public class OverheadRotationTool extends ButtonTool
        implements MapEventDispatcher.OnMapEventListener,
        MapItem.OnGroupChangedListener {

    public static final String TAG = "OverheadRotationTool";
    public static final String TOOL_NAME = "atak_overhead_rotation_tool";

    private OverheadMarker _marker;
    private Marker _anchor;
    private RangeAndBearingMapItem _rab;
    private TextContainer _cont;

    public OverheadRotationTool(MapView mapView, ImageButton button) {
        super(mapView, button, TOOL_NAME);
        _cont = TextContainer.getInstance();

        ToolManagerBroadcastReceiver.getInstance()
                .registerTool(TOOL_NAME, this);
    }

    @Override
    public void dispose() {
        super.dispose();
    }

    @Override
    public boolean onToolBegin(Bundle extras) {
        if (extras.containsKey("uid"))
            _marker = findMarker(extras.getString("uid"));
        if (_marker != null) {
            _cont.displayPrompt(_mapView.getContext().getString(
                    R.string.point_dropper_text57));
            // Take control of all map events
            _mapView.getMapEventDispatcher()
                    .addMapItemEventListener(createAnchor(), this);
            _mapView.getMapTouchController().setToolActive(true);
            _marker.addOnGroupChangedListener(this);
            return true;
        }
        return false;
    }

    @Override
    public void onToolEnd() {
        unregisterListeners();
    }

    private void unregisterListeners() {
        if (_marker != null) {
            _marker.persist(_mapView.getMapEventDispatcher(),
                    null, OverheadRotationTool.class);
            _mapView.getMapTouchController().setToolActive(false);
            _mapView.getMapEventDispatcher().removeMapItemEventListener(
                    _anchor, this);
            _cont.closePrompt();
            _marker.removeOnGroupChangedListener(this);
            _marker = null;
        }
        removeAnchor();
    }

    public OverheadMarker findMarker(String uid) {
        if (uid == null)
            return null;
        MapGroup group = _mapView.getRootGroup().findMapGroup(
                OverheadMarker.MAP_GROUP);
        if (group != null) {
            MapItem item = group.deepFindUID(uid);
            if (item instanceof OverheadMarker)
                return (OverheadMarker) item;
        }
        return null;
    }

    private Marker createAnchor() {
        if (_anchor == null && _marker != null) {
            double len = 1.2d * Math.max(_marker.getLength(), 10);

            GeoPoint center = _marker.getPoint();
            GeoPoint anchorPos = DistanceCalculations
                    .computeDestinationPoint(center, _marker.getAzimuth(), len);

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
                    UUID.randomUUID().toString(), _marker, _anchor, false);
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
        if (_marker == null || _marker.getGroup() == null || _anchor != item)
            return;
        String type = event.getType();
        if (type.equals(MapEvent.ITEM_DRAG_STARTED)
                || type.equals(MapEvent.ITEM_DRAG_CONTINUED)
                || type.equals(MapEvent.ITEM_DRAG_DROPPED)) {

            Point pt = event.getPoint();
            // rotation is relative about a point, no need for mesh query
            GeoPoint gp = _mapView.inverse(pt.x, pt.y).get();
            if (!gp.isValid())
                return;
            double ang = _marker.getPoint().bearingTo(gp);
            _marker.setAzimuth(ang);

            GeoPoint offset = DistanceCalculations.computeDestinationPoint(
                    _marker.getPoint(), ang, 1.2d * Math.max(
                            _marker.getLength(), 10));
            _anchor.setPoint(offset);

            if (type.equals(MapEvent.ITEM_DRAG_DROPPED)) {
                //_marker.save();
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
        if (_marker == item) {
            requestEndTool();
            unregisterListeners();
        }
    }
}
