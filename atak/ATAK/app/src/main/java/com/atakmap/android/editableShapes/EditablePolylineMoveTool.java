
package com.atakmap.android.editableShapes;

import android.content.Intent;
import android.os.Bundle;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.AnchoredMapItem;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.maps.Shape;
import com.atakmap.android.menu.MapMenuReceiver;
import com.atakmap.android.toolbar.Tool;
import com.atakmap.android.toolbar.widgets.TextContainer;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

class EditablePolylineMoveTool extends Tool {

    private String _uidMove;
    private String _deviceUID;
    public static final String TOOL_IDENTIFIER = "com.atakmap.android.editableShapes.MOVE_ASSOCIATIONSET";
    private static final String PROMPT = "Tap the new location.";

    EditablePolylineMoveTool(MapView mapView) {
        super(mapView, TOOL_IDENTIFIER);
        _mapView.getMapEventDispatcher().addMapEventListenerToBase(
                MapEvent.ITEM_LONG_PRESS,
                _moveListener);
    }

    @Override
    public void dispose() {
    }

    @Override
    public boolean onToolBegin(Bundle extras) {
        _deviceUID = _mapView.getSelfMarker().getUID();
        TextContainer.getInstance().displayPrompt(PROMPT);

        _mapView.getMapEventDispatcher().pushListeners();
        clearExtraListeners();
        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.MAP_CLICK, _moveListener);
        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.ITEM_CLICK, _moveListener);
        _mapView.getMapEventDispatcher()
                .addMapEventListener(MapEvent.MAP_LONG_PRESS, _moveListener);
        _mapView.getMapTouchController().skipDeconfliction(true);
        return super.onToolBegin(extras);
    }

    @Override
    public void onToolEnd() {
        AtakBroadcast.getInstance().sendBroadcast(
                new Intent(MapMenuReceiver.HIDE_MENU));
        _uidMove = null;
        TextContainer.getInstance().closePrompt();
        _mapView.getMapTouchController().skipDeconfliction(false);
        _mapView.getMapEventDispatcher().popListeners();
    }

    private final MapEventDispatcher.MapEventDispatchListener _moveListener = new MapEventDispatcher.MapEventDispatchListener() {
        @Override
        public void onMapEvent(MapEvent event) {
            if (event.getType().equals(MapEvent.ITEM_LONG_PRESS)) {
                if (!(event.getItem() instanceof PointMapItem)) {
                    return;
                }
                MapItem item = event.getItem();

                // if it is not movable - do not allow it to move
                if (!item.getMovable())
                    return;

                String type = item.getType();
                if (type.equals("shape_marker") || type.equals("center_u-o-a")
                        || type.equals("center_u-o-a-t-t")
                        || type.equals("center_u-o-a-s-b")
                        || type.equals("center_u-d-r")) {
                    _uidMove = event.getItem().getUID();
                    requestBeginTool();
                }
            } else if (event.getType().equals(MapEvent.MAP_CLICK)
                    || event.getType().equals(MapEvent.MAP_LONG_PRESS)) {
                if (_uidMove != null) {
                    GeoPointMetaData newPoint = _mapView.inverseWithElevation(
                            event.getPointF().x, event.getPointF().y);
                    _handlePoint(newPoint);
                }
            } else if (event.getType().equals(MapEvent.ITEM_CLICK)
                    && _uidMove != null) {
                if (event.getItem() instanceof PointMapItem
                        && event.getItem().getUID().equals(_deviceUID)) {
                    GeoPointMetaData newPoint = ((PointMapItem) event.getItem())
                            .getGeoPointMetaData();
                    _handlePoint(newPoint);
                    event.getExtras().putBoolean("eventNotHandled", false);
                } else {
                    event.getExtras().putBoolean("eventNotHandled", true);
                }
            }

        }
    };

    private void _handlePoint(GeoPointMetaData point) {
        PointMapItem center = (PointMapItem) _mapView.getMapItem(_uidMove);

        if (center == null)
            return;

        MapItem shape = ATAKUtilities.findAssocShape(center);

        // Get the current center point
        GeoPointMetaData oldPoint;
        if (shape instanceof Shape)
            oldPoint = ((Shape) shape).getCenter();
        else if (shape instanceof AnchoredMapItem)
            oldPoint = ((AnchoredMapItem) shape).getAnchorItem()
                    .getGeoPointMetaData();
        else
            return;

        if (oldPoint.getAltitudeSource().equals(GeoPointMetaData.USER)) {
            // Maintain user elevation
            point = GeoPointMetaData.wrap(new GeoPoint(
                    point.get().getLatitude(), point.get().getLongitude(),
                    oldPoint.get().getAltitude()),
                    GeoPointMetaData.USER, GeoPointMetaData.USER);
        }

        if (shape instanceof EditablePolyline
                && ((EditablePolyline) shape).isClosed()) {
            EditablePolyline set = (EditablePolyline) shape;
            set.moveClosedSet(oldPoint, point);
            set.persist(_mapView.getMapEventDispatcher(), null, getClass());
        } else if (shape instanceof Rectangle) {
            Rectangle r = (Rectangle) shape;
            r.move(oldPoint, point);
            r.persist(_mapView.getMapEventDispatcher(), null, getClass());
        } else if (shape instanceof AnchoredMapItem) {
            AnchoredMapItem anc = (AnchoredMapItem) shape;
            PointMapItem pmi = anc.getAnchorItem();
            if (pmi != null) {
                pmi.setPoint(point);
                shape.persist(_mapView.getMapEventDispatcher(),
                        null, getClass());
            }
        }
        requestEndTool();
    }

}
