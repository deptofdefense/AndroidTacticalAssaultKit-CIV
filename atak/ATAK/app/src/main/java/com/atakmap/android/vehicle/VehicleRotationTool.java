
package com.atakmap.android.vehicle;

import android.graphics.PointF;
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
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.maps.assets.Icon;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.coremap.maps.coords.NorthReference;

import java.util.UUID;

/**
 * Rotation tool
 */
public class VehicleRotationTool extends ButtonTool
        implements MapEventDispatcher.OnMapEventListener,
        MapItem.OnGroupChangedListener {

    public static final String TAG = "VehicleRotationTool";
    public static final String TOOL_NAME = "vehicle_rotation_tool";

    private VehicleMapItem _vehicle;
    private MapItem _item;
    private Marker _anchor;
    private RangeAndBearingMapItem _rab;
    private final TextContainer _cont;

    public VehicleRotationTool(MapView mapView, ImageButton button) {
        super(mapView, button, TOOL_NAME);
        _cont = TextContainer.getInstance();
        ToolManagerBroadcastReceiver.getInstance().registerTool(TOOL_NAME,
                this);
    }

    @Override
    public boolean onToolBegin(Bundle extras) {

        String uid = extras.getString("uid");
        if (FileSystemUtils.isEmpty(uid))
            return false;

        _item = _mapView.getRootGroup().deepFindUID(uid);
        if (_item instanceof VehicleMapItem)
            _vehicle = (VehicleMapItem) _item;

        _item = _mapView.getRootGroup().deepFindUID(uid);
        if (!(_item instanceof VehicleMapItem)) {
            _item = ATAKUtilities.findAssocShape(_item);
            if (!(_item instanceof VehicleMapItem))
                return false;
        }
        _vehicle = (VehicleMapItem) _item;

        if (_item == null || _vehicle == null)
            return false;

        _cont.displayPrompt(_mapView.getContext().getString(
                R.string.point_dropper_text57));
        // Take control of all map events
        _mapView.getMapEventDispatcher()
                .addMapItemEventListener(createAnchor(), this);
        _mapView.getMapTouchController().setToolActive(true);
        _item.addOnGroupChangedListener(this);
        return true;
    }

    @Override
    public void onToolEnd() {
        unregisterListeners();
    }

    private void unregisterListeners() {
        if (_item != null) {
            _mapView.getMapTouchController().setToolActive(false);
            _mapView.getMapEventDispatcher().removeMapItemEventListener(
                    _anchor, this);
            _cont.closePrompt();
            _item.removeOnGroupChangedListener(this);
        }
        _item = null;
        _vehicle = null;
        removeAnchor();
    }

    private Marker createAnchor() {
        if (_anchor == null && _vehicle != null) {
            double len = 1.2d * Math.max(_vehicle.getLength(), 10);

            GeoPointMetaData center = _vehicle.getCenter();
            GeoPoint anchorPos = GeoCalculations.pointAtDistance(center.get(),
                    _vehicle.getAzimuth(NorthReference.TRUE), len, 0);

            PlacePointTool.MarkerCreator mc = new PlacePointTool.MarkerCreator(
                    anchorPos);

            mc.showCotDetails(false);
            mc.setNeverPersist(true);
            mc.setType("u-r-b-o-endpoint");
            mc.setCallsign("Rotation Anchor");
            _anchor = mc.placePoint();
            _anchor.setShowLabel(false);
            _anchor.setMetaBoolean("ignoreMenu", true);
            _anchor.setMetaBoolean("drag", true);
            _anchor.setMetaDouble("minRenderScale", Double.MAX_VALUE);
            _rab = RangeAndBearingMapItem.createOrUpdateRABLine(
                    UUID.randomUUID().toString(), _vehicle.getAnchorItem(),
                    _anchor, false);
            _rab.setClickable(false);
            _anchor.getGroup().addItem(_rab);
            _rab.setZOrder(Double.NEGATIVE_INFINITY);
            _rab.setDisplaySlantRange(false);

            Icon anchorIcon = new Icon.Builder()
                    .setImageUri(0, ATAKUtilities.getResourceUri(
                            R.drawable.open_circle_small))
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
        if (_item == null || _item.getGroup() == null || _anchor != item)
            return;
        String type = event.getType();
        if (type.equals(MapEvent.ITEM_DRAG_STARTED)
                || type.equals(MapEvent.ITEM_DRAG_CONTINUED)
                || type.equals(MapEvent.ITEM_DRAG_DROPPED)) {

            PointF pt = event.getPointF();
            // rotation is relative about a point, no need to look at terrain
            GeoPoint gp = _mapView.inverse(pt.x, pt.y).get();
            if (!gp.isValid())
                return;
            double ang = _vehicle.getCenter().get().bearingTo(gp);

            _vehicle.setAzimuth(ang, NorthReference.TRUE);

            GeoPoint offset = GeoCalculations.pointAtDistance(
                    _vehicle.getCenter().get(), ang, 1.2d * Math.max(
                            _vehicle.getLength(), 10),
                    0);
            _anchor.setPoint(offset);

            if (type.equals(MapEvent.ITEM_DRAG_DROPPED)) {
                _item.persist(_mapView.getMapEventDispatcher(), null,
                        getClass());
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
        if (_vehicle == item) {
            requestEndTool();
            unregisterListeners();
        }
    }
}
