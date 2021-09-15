
package com.atakmap.android.vehicle.model;

import android.graphics.Point;
import android.os.Bundle;

import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.rubbersheet.tool.RubberModelEditTool;
import com.atakmap.android.toolbars.RangeAndBearingMapItem;
import com.atakmap.android.user.PlacePointTool;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.app.R;
import com.atakmap.coremap.maps.assets.Icon;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.coremap.maps.coords.NorthReference;

import java.util.UUID;

/**
 * For editing the position, heading, and altitude of a vehicle model
 */
public class VehicleModelEditTool extends RubberModelEditTool implements
        MapEventDispatcher.OnMapEventListener {

    private static final String TAG = "VehicleModelEditTool";
    public static final String TOOL_NAME = "com.atakmap.android.vehicle.model."
            + TAG;

    private VehicleModel _vehicle;
    private RangeAndBearingMapItem _rab;
    private Marker _anchor;

    public VehicleModelEditTool(MapView mapView, MapGroup group) {
        super(mapView, group);
        _identifier = TOOL_NAME;
    }

    @Override
    public String getIdentifier() {
        return TOOL_NAME;
    }

    @Override
    public boolean onToolBegin(Bundle extras) {
        if (!super.onToolBegin(extras))
            return false;

        _vehicle = (VehicleModel) _model;

        _mapView.getMapEventDispatcher().addMapItemEventListener(
                createAnchor(), this);

        return true;
    }

    @Override
    protected void unregisterListeners() {
        _mapView.getMapEventDispatcher().removeMapItemEventListener(
                _anchor, this);
        removeAnchor();
        super.unregisterListeners();
    }

    @Override
    protected void reset() {
        super.reset();
        if (_anchor != null) {
            boolean visible = getMode() == HEADING;
            _anchor.setVisible(visible);
            _rab.setVisible(visible);
        }
    }

    private Marker createAnchor() {
        if (_anchor == null && _vehicle != null) {
            double len = 1.2d * Math.max(_vehicle.getLength(), 10);

            GeoPointMetaData center = _vehicle.getCenter();
            GeoPoint anchorPos = GeoCalculations.pointAtDistance(center.get(),
                    _vehicle.getAzimuth(NorthReference.TRUE), len);

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
        if (_model == null || _model.getGroup() == null || _anchor != item)
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
            double ang = _vehicle.getCenter().get().bearingTo(gp);

            _vehicle.setAzimuth(ang, NorthReference.TRUE);

            GeoPoint offset = GeoCalculations.pointAtDistance(
                    _vehicle.getCenter().get(), ang, 1.2d * Math.max(
                            _vehicle.getLength(), 10));
            _anchor.setPoint(offset);

            if (type.equals(MapEvent.ITEM_DRAG_DROPPED)) {
                run(new RotateAction(_sheet, _oldPoints));
                reset();
            }
        }
    }
}
