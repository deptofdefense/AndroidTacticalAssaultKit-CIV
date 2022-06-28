
package com.atakmap.android.drawing.tools;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import com.atakmap.android.drawing.DrawingToolsToolbar;
import com.atakmap.android.drawing.mapItems.DrawingEllipse;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.toolbar.ButtonTool;
import com.atakmap.android.toolbar.widgets.TextContainer;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.android.util.EditAction;
import com.atakmap.android.util.Undoable;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.map.elevation.ElevationData;
import com.atakmap.map.elevation.ElevationManager;
import com.atakmap.map.layer.feature.Feature.AltitudeMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.UUID;

/**
 * Tool for editing {@link DrawingEllipse}
 */
public class DrawingEllipseEditTool extends ButtonTool implements Undoable,
        PointMapItem.OnPointChangedListener {

    private static final String TAG = "DrawingEllipseEditTool";
    public static final String TOOL_IDENTIFIER = "com.atakmap.android.drawing.tools."
            + TAG;

    private final MapView _mapView;
    private final DrawingToolsToolbar _toolbar;
    private final Button _undoBtn;

    private DrawingEllipse _ellipse;
    private Intent _callback;

    private Marker _back, _left, _right, _front, _center;
    private final List<Marker> _anchors = new ArrayList<>();
    private EllipseState _lastState;
    private final Stack<EditAction> _undoStack = new Stack<>();
    private boolean _ignoreUpdate;
    private boolean _ignoreUndo;

    public DrawingEllipseEditTool(MapView mapView,
            DrawingToolsToolbar toolbar) {
        super(mapView, toolbar.getToggleEditButton(), TOOL_IDENTIFIER);
        _mapView = mapView;
        _toolbar = toolbar;
        _undoBtn = toolbar.getUndoButton();
    }

    @Override
    public void dispose() {
    }

    @Override
    public boolean onToolBegin(Bundle extras) {
        String uid;
        String shapeUID = extras.getString("shapeUID");
        if (!FileSystemUtils.isEmpty(shapeUID))
            uid = shapeUID;
        else
            uid = extras.getString("uid");

        MapItem mi = _mapView.getMapItem(uid);
        if (!(mi instanceof DrawingEllipse))
            return false;

        _ellipse = (DrawingEllipse) mi;

        _callback = extras.getParcelable("callback");

        _center = _ellipse.getCenterMarker();
        if (_center != null)
            _center.addOnPointChangedListener(this);

        _front = createSideMarker(0, _ellipse.getLength() / 2);
        _back = createSideMarker(180, _ellipse.getLength() / 2);
        _right = createSideMarker(90, _ellipse.getWidth() / 2);
        _left = createSideMarker(270, _ellipse.getWidth() / 2);
        _anchors.add(_left);
        _anchors.add(_right);
        _anchors.add(_front);
        _anchors.add(_back);

        pushMapListeners();
        MapEventDispatcher disp = _mapView.getMapEventDispatcher();
        disp.clearListeners(MapEvent.MAP_CLICK);
        disp.clearListeners(MapEvent.MAP_LONG_PRESS);
        disp.clearListeners(MapEvent.ITEM_LONG_PRESS);
        disp.addMapEventListener(MapEvent.ITEM_DRAG_CONTINUED, _dragListener);
        disp.addMapEventListener(MapEvent.ITEM_DRAG_STARTED, _dragListener);
        disp.addMapEventListener(MapEvent.ITEM_DRAG_DROPPED, _dragListener);
        disp.addMapEventListener(MapEvent.ITEM_LONG_PRESS, _longPressListener);

        _toolbar.toggleEditButtons(true);
        TextContainer.getInstance().displayPrompt(R.string.ellipse_edit_prompt);
        _mapView.getMapTouchController().setToolActive(true);
        return super.onToolBegin(extras);
    }

    @Override
    public void onToolEnd() {
        // If properly built then show the details for the item
        if (_callback != null) {
            Intent callbackIntent = new Intent(_callback);
            callbackIntent.putExtra("uid", _ellipse.getUID());
            AtakBroadcast.getInstance().sendBroadcast(callbackIntent);
        }

        if (_center != null)
            _center.removeOnPointChangedListener(this);
        _front.removeFromGroup();
        _back.removeFromGroup();
        _right.removeFromGroup();
        _left.removeFromGroup();
        _front = _back = _right = _left = _center = null;
        _anchors.clear();
        _lastState = null;
        _undoStack.clear();

        _toolbar.toggleEditButtons(false);
        TextContainer.getInstance().closePrompt();
        _mapView.getMapTouchController().setToolActive(false);
        super.onToolEnd();
    }

    @Override
    public void onPointChanged(PointMapItem item) {
        if (!_ignoreUpdate) {
            if (!_ignoreUndo)
                startEditAction();
            updateEllipse(item);
            if (!_ignoreUndo)
                endEditAction();
        }
    }

    /**
     * Create a draggable side marker used to modify the ellipse size and
     * position
     * @param angle Angle/heading of the ellipse
     * @param radius Major or minor radius in meters
     * @return Side marker
     */
    private Marker createSideMarker(double angle, double radius) {
        GeoPoint point = GeoCalculations.pointAtDistance(
                _ellipse.getCenterPoint(), _ellipse.getAngle() + angle, radius);
        Marker m = new Marker(point, UUID.randomUUID().toString());
        m.setEditable(true);
        m.setMovable(true);
        m.setMetaBoolean("drag", true);
        m.setMetaBoolean("addToObjList", false);
        m.setMetaBoolean("nevercot", true);
        m.setMetaBoolean("removable", false);
        m.setType("side_" + DrawingEllipse.COT_TYPE);
        m.setMetaString("how", "h-g-i-g-o");
        m.setMetaString("shapeUID", _ellipse.getUID());
        m.setShowLabel(false);
        m.setTitle(_ellipse.getTitle());
        m.setRadialMenu("menus/drawing_rectangle_corner_menu.xml");
        m.setMetaDouble("angle", angle);
        m.addOnPointChangedListener(this);
        _ellipse.getChildMapGroup().addItem(m);
        return m;
    }

    private final MapEventDispatcher.MapEventDispatchListener _dragListener = new MapEventDispatcher.MapEventDispatchListener() {
        @Override
        public void onMapEvent(MapEvent event) {
            MapItem item = event.getItem();
            if (!(item instanceof PointMapItem))
                return;

            PointMapItem pmi = (PointMapItem) item;

            GeoPointMetaData point = findPoint(event);
            if (point == null) {
                event.getExtras().putBoolean("eventNotHandled", true);
                return;
            }

            switch (event.getType()) {
                case MapEvent.ITEM_DRAG_STARTED:
                    startEditAction();
                    break;
                case MapEvent.ITEM_DRAG_CONTINUED:
                    setPointNoUndo(pmi, point);
                    break;
                case MapEvent.ITEM_DRAG_DROPPED:
                    endEditAction();
                    break;
            }
        }
    };

    private final MapEventDispatcher.MapEventDispatchListener _longPressListener = new MapEventDispatcher.MapEventDispatchListener() {
        @Override
        public void onMapEvent(MapEvent event) {
            MapItem item = event.getItem();

            if (!(item instanceof Marker)
                    || _ellipse != ATAKUtilities.findAssocShape(item))
                return;

            final Marker pmi = (Marker) item;

            //Push new set of listeners for long-press action
            startEditAction();
            pushMapListeners();
            clearExtraListeners();

            MapEventDispatcher.MapEventDispatchListener l = new MapEventDispatcher.MapEventDispatchListener() {
                @Override
                public void onMapEvent(MapEvent event) {
                    GeoPointMetaData point = findPoint(event);
                    if (point != null) {
                        setPointNoUndo(pmi, point);
                        endEditAction();
                        popMapListeners();
                        TextContainer.getInstance().displayPrompt(
                                R.string.ellipse_edit_prompt);
                    }
                }
            };
            _mapView.getMapEventDispatcher().addMapEventListener(
                    MapEvent.ITEM_CLICK, l);
            _mapView.getMapEventDispatcher().addMapEventListener(
                    MapEvent.MAP_CLICK, l);
            _mapView.getMapEventDispatcher().addMapEventListener(
                    MapEvent.MAP_LONG_PRESS, l);
            TextContainer.getInstance().displayPrompt(
                    R.string.rectangle_tap_prompt);
        }
    };

    @Override
    public boolean run(EditAction action) {
        synchronized (_undoStack) {
            _undoStack.push(action);
            _undoBtn.setEnabled(true);
        }
        return action.run();
    }

    @Override
    public void undo() {
        EditAction action;
        synchronized (_undoStack) {
            if (_undoStack.isEmpty())
                return;
            action = _undoStack.pop();
            _undoBtn.setEnabled(!_undoStack.isEmpty());
        }
        action.undo();
    }

    /**
     * Set the point on a marker without triggering the automatic undo
     * @param pmi Marker
     * @param point New point
     */
    private void setPointNoUndo(PointMapItem pmi, GeoPointMetaData point) {
        _ignoreUndo = true;
        pmi.setPoint(point);
        _ignoreUndo = false;
    }

    /**
     * Update the ellipse and its markers
     * @param anchor Anchor point that was recently changed
     */
    private void updateEllipse(PointMapItem anchor) {
        if (_ellipse == null)
            return;

        _ignoreUpdate = true;

        // Center marker update is already taken care of within DrawingEllipse
        if (anchor != _center) {
            // Get new angle and diameter
            GeoPoint b = _back.getPoint();
            GeoPoint f = _front.getPoint();
            GeoPoint l = _left.getPoint();
            GeoPoint r = _right.getPoint();
            GeoPoint center;
            double angle;
            if (anchor == _back || anchor == _front) {
                center = GeoCalculations.midPointWGS84(b, f);
                angle = b.bearingTo(f);
            } else {
                center = GeoCalculations.midPointWGS84(l, r);
                angle = r.bearingTo(l) + 90;
            }
            double length = b.distanceTo(f);
            double width = l.distanceTo(r);

            GeoPointMetaData gpmd = _ellipse.getCenter();

            // Copy user altitude to new point
            boolean userAlt = FileSystemUtils.isEquals(gpmd.getAltitudeSource(),
                    GeoPointMetaData.USER);
            if (userAlt)
                center = new GeoPoint(center.getLatitude(),
                        center.getLongitude(),
                        gpmd.get().getAltitude(),
                        center.getCE(), center.getLE());

            gpmd.set(center);

            if (!userAlt) {
                // Lookup terrain elevation for the center if a user elevation
                // isn't specified and mode is set to clamp-to-ground
                if (_ellipse.getAltitudeMode() == AltitudeMode.ClampToGround) {
                    ElevationManager.QueryParameters params = new ElevationManager.QueryParameters();
                    params.elevationModel = ElevationData.MODEL_TERRAIN;
                    ElevationManager.getElevation(center, params, gpmd);
                } else
                    gpmd.setAltitudeSource(GeoPointMetaData.CALCULATED);
            } else
                gpmd.setAltitudeSource(GeoPointMetaData.USER);

            // Update the ellipse
            _ellipse.setDimensions(gpmd, width, length, angle);
        }

        // Update the anchor points
        updateAnchors();

        _ignoreUpdate = false;
    }

    /**
     * Update the anchor points
     */
    private void updateAnchors() {
        GeoPoint center = _ellipse.getCenterPoint();
        double halfWidth = _ellipse.getWidth() / 2;
        double halfLength = _ellipse.getLength() / 2;
        double angle = _ellipse.getAngle();
        for (PointMapItem pmi : _anchors) {
            double dist = pmi == _left || pmi == _right ? halfWidth
                    : halfLength;
            pmi.setPoint(GeoCalculations.pointAtDistance(center,
                    angle + pmi.getMetaDouble("angle", 0), dist));
        }
    }

    /**
     * Save the current state of the ellipse for use with later undo action
     */
    private void startEditAction() {
        if (_ellipse == null)
            return;
        _lastState = new EllipseState();
    }

    /**
     * Save the last and current ellipse states to the undo stack
     */
    private void endEditAction() {
        final EllipseState lastState = _lastState;
        if (lastState == null)
            return;
        synchronized (_undoStack) {
            _undoStack.push(new EditEllipseAction(lastState,
                    new EllipseState()));
            _undoBtn.setEnabled(true);
        }
    }

    /**
     * Save parameters for an ellipse
     */
    private class EllipseState {

        private final double angle;
        private final double width;
        private final double length;
        private final GeoPointMetaData center;

        EllipseState() {
            angle = _ellipse.getAngle();
            width = _ellipse.getWidth();
            length = _ellipse.getLength();
            center = new GeoPointMetaData(_ellipse.getCenter());
        }

        /**
         * Apply the ellipse state to the current ellipse
         */
        void apply() {
            _ignoreUpdate = true;
            _ellipse.setDimensions(center, width, length, angle);
            updateAnchors();
            _ignoreUpdate = false;
        }
    }

    /**
     * Undo action for applying ellipse states
     */
    private class EditEllipseAction extends EditAction {

        private final EllipseState _state1, _state2;

        EditEllipseAction(EllipseState state1, EllipseState state2) {
            _state1 = state1;
            _state2 = state2;
        }

        @Override
        public boolean run() {
            _state2.apply();
            return true;
        }

        @Override
        public void undo() {
            _state1.apply();
        }

        @Override
        public String getDescription() {
            return null;
        }
    }
}
