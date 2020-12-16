
package com.atakmap.android.rubbersheet.tool;

import android.content.Context;
import android.graphics.Point;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import com.atakmap.android.editableShapes.RectangleEditTool;
import com.atakmap.android.mapcompass.CompassArrowMapComponent;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapMode;
import com.atakmap.android.maps.MapTouchController;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.preference.AtakPreferences;
import com.atakmap.android.rubbersheet.maps.AbstractSheet;
import com.atakmap.android.toolbar.ToolManagerBroadcastReceiver;
import com.atakmap.android.toolbar.widgets.TextContainer;
import com.atakmap.android.tools.ActionBarReceiver;
import com.atakmap.android.tools.ActionBarView;
import com.atakmap.android.util.EditAction;
import com.atakmap.app.R;
import com.atakmap.coremap.maps.coords.DistanceCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.map.AtakMapController;

/**
 * Rotation tool
 */
public class RubberSheetEditTool extends RectangleEditTool
        implements MapEventDispatcher.MapEventDispatchListener,
        MapItem.OnGroupChangedListener, View.OnClickListener {

    private static final String TAG = "RubberSheetEditTool";
    public static final String TOOL_NAME = "com.atakmap.android.rubbersheet.tool."
            + TAG;

    protected static final int DRAG = 0;
    protected static final int PITCH = 1;
    protected static final int HEADING = 2;
    protected static final int ROLL = 3;
    protected static final int ELEV = 4;

    protected final ActionBarReceiver _abReceiver;
    protected final ActionBarView _toolbar;
    protected final View[] _buttons = new View[5];

    protected final Context _context;
    protected final AtakPreferences _prefs;
    protected final MapGroup _group;
    protected final TextContainer _cont;
    protected AbstractSheet _sheet;
    protected double[] _p0, _p1, _p2, _p3;
    protected GeoPointMetaData[] _oldPoints;
    protected GeoPointMetaData _center;
    protected double _startAngle;
    private double _oldTilt;
    private boolean _oldTiltEnabled;

    public RubberSheetEditTool(MapView mapView, MapGroup group) {
        super(mapView, null, null);
        _context = mapView.getContext();
        _identifier = TOOL_NAME;
        _prefs = new AtakPreferences(mapView);
        _group = group;
        _cont = TextContainer.getInstance();
        ToolManagerBroadcastReceiver.getInstance().registerTool(
                getIdentifier(), this);

        _abReceiver = ActionBarReceiver.getInstance();
        _toolbar = (ActionBarView) LayoutInflater.from(_context).inflate(
                R.layout.rs_edit_sheet_toolbar, _mapView, false);
        _toolbar.setClosable(true);
        _toolbar.showCloseButton(false);
        _toolbar.setEmbedded(true);
        _toolbar.setEmbedState(ActionBarView.FLOATING);
        _buttons[DRAG] = _toolbar.findViewById(R.id.dragBtn);
        _buttons[PITCH] = _toolbar.findViewById(R.id.pitchBtn);
        _buttons[HEADING] = _toolbar.findViewById(R.id.headingBtn);
        _buttons[ROLL] = _toolbar.findViewById(R.id.rollBtn);
        _buttons[ELEV] = _toolbar.findViewById(R.id.elevBtn);
        _toolbar.findViewById(R.id.close).setOnClickListener(this);
        for (View v : _buttons)
            v.setOnClickListener(this);
    }

    @Override
    public void dispose() {
        super.dispose();
        ToolManagerBroadcastReceiver.getInstance().unregisterTool(
                getIdentifier());
    }

    @Override
    public boolean onToolBegin(Bundle extras) {
        // Find the sheet
        if (extras.containsKey("uid")) {
            MapItem item = _group.deepFindUID(extras.getString("uid"));
            if (item == null)
                return false;
            if (item instanceof AbstractSheet) {
                _sheet = (AbstractSheet) item;
            } else if (item.hasMetaValue("shapeUID")) {
                item = _group.deepFindUID(item.getMetaString("shapeUID", ""));
                if (item instanceof AbstractSheet)
                    _sheet = (AbstractSheet) item;
            }
        }
        if (_sheet == null)
            return false;

        _rectangle = _sheet;
        super.onToolBegin(extras);

        // Focus on the sheet
        _oldTilt = _mapView.getMapTilt();
        _oldTiltEnabled = _prefs.get("status_3d_enabled", false);
        if (_oldTilt != 0d)
            _mapView.getMapController().tiltTo(0, false);
        MapTouchController.goTo(_sheet, false);

        // Register listeners on top of the current stack used by the rectangle
        // edit tool super class
        _mapView.getMapEventDispatcher().clearListeners(MapEvent.MAP_SCALE);
        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.MAP_SCALE, this);
        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.MAP_ROTATE, this);
        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.MAP_TILT, this);
        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.MAP_RELEASE, this);
        _mapView.getMapTouchController().setToolActive(true);
        CompassArrowMapComponent.getInstance().enableSlider(false);
        _sheet.addOnGroupChangedListener(this);

        _abReceiver.setToolView(_toolbar);

        reset();
        displayPrompt();

        // Set default mode
        if (extras.getBoolean("rotate", false))
            _buttons[HEADING].performClick();
        else
            _buttons[DRAG].performClick();

        return true;
    }

    @Override
    public void onToolEnd() {
        if (_abReceiver.getToolView() == _toolbar)
            _abReceiver.setToolView(null, false);
        unregisterListeners();
        _mapView.getMapController().tiltTo(_oldTilt, false);
        CompassArrowMapComponent.getInstance().enable3DControls(
                _oldTiltEnabled);
        CompassArrowMapComponent.getInstance().enableSlider(true);
        super.onToolEnd();
    }

    private boolean freeRotate() {
        MapMode mode = CompassArrowMapComponent.getInstance().getMapMode();
        boolean locked = _prefs.get("status_mapmode_heading_locked", false);
        return mode == MapMode.USER_DEFINED_UP && !locked;
    }

    protected void unregisterListeners() {
        if (_sheet != null) {
            _mapView.getMapTouchController().setToolActive(false);
            _mapView.getMapTouchController().setUserOrientation(freeRotate());
            _cont.closePrompt();
            _sheet.removeOnGroupChangedListener(this);
            _sheet = null;
        }
    }

    /**
     * Get the selected mode
     * @return Current edit mode
     */
    protected int getMode() {
        for (int i = 0; i < _buttons.length; i++) {
            if (_buttons[i].isSelected())
                return i;
        }
        return -1;
    }

    /**
     * Display the help tooltip
     */
    protected void displayPrompt() {
        String msg = null;
        int mode = getMode();
        if (mode == DRAG)
            msg = _context.getString(R.string.drag_sheet_tooltip);
        else if (mode == HEADING)
            msg = _context.getString(R.string.rotate_sheet_tooltip);
        if (msg != null)
            _cont.displayPrompt(msg);
    }

    @Override
    public void onMapEvent(MapEvent event) {
        if (_sheet == null || _sheet.getGroup() == null)
            return;
        int mode = getMode();
        String type = event.getType();

        // Drag mode
        if (mode == DRAG) {
            // Allow map scale gesture when in this mode
            // XXX - 3.12 does not have "eventNotHandled" support for this event
            // so we have to override it and re-implement here
            if (type.equals(MapEvent.MAP_SCALE)) {
                AtakMapController ctrl = _mapView.getMapController();
                Point p = event.getPoint();
                ctrl.zoomBy(event.getScaleFactor(), p.x, p.y, false);
            }
        }

        // Change sheet heading/rotation
        else if (mode == HEADING) {
            switch (type) {
                case MapEvent.MAP_TILT:
                    event.getExtras().putBoolean("eventNotHandled", false);
                    break;
                case MapEvent.MAP_ROTATE:
                    double ang = event.getExtras().getDouble("angle");
                    if (Double.isNaN(_startAngle))
                        _startAngle = ang;
                    ang -= _startAngle;
                    rotate(ang);
                    event.getExtras().putBoolean("eventNotHandled", false);
                    break;
                case MapEvent.MAP_RELEASE:
                    run(new RotateAction(_sheet, _oldPoints));
                    reset();
                    break;
            }
        }

        // Everything else is handled by super and sub-classes
    }

    protected void rotate(double ang) {
        if (_center == null)
            return;
        GeoPoint p0 = DistanceCalculations.computeDestinationPoint(
                _center.get(), _p0[1] - ang, _p0[0]);
        GeoPoint p1 = DistanceCalculations.computeDestinationPoint(
                _center.get(), _p1[1] - ang, _p1[0]);
        GeoPoint p2 = DistanceCalculations.computeDestinationPoint(
                _center.get(), _p2[1] - ang, _p2[0]);
        GeoPoint p3 = DistanceCalculations.computeDestinationPoint(
                _center.get(), _p3[1] - ang, _p3[0]);
        _sheet.setPoints(GeoPointMetaData.wrap(p0), GeoPointMetaData.wrap(p1),
                GeoPointMetaData.wrap(p2), GeoPointMetaData.wrap(p3));
    }

    protected void reset() {
        if (_sheet == null)
            return;
        _startAngle = Double.NaN;
        _center = _sheet.getCenter();
        GeoPoint[] p = _sheet.getPoints();
        _p0 = DistanceCalculations.computeDirection(_center.get(), p[0]);
        _p1 = DistanceCalculations.computeDirection(_center.get(), p[1]);
        _p2 = DistanceCalculations.computeDirection(_center.get(), p[2]);
        _p3 = DistanceCalculations.computeDirection(_center.get(), p[3]);
        _oldPoints = _sheet.getMetaDataPoints();

        int mode = getMode();
        if (mode == HEADING && _center != null) {
            _mapView.getMapController().tiltTo(0, false);
            _mapView.getMapController().panTo(_center.get(), false);
        }
        _sheet.setEditable(mode == DRAG);
        _mapView.getMapTouchController().setUserOrientation(freeRotate()
                || mode == HEADING);
    }

    @Override
    public void onItemAdded(MapItem item, MapGroup group) {
    }

    @Override
    public void onItemRemoved(MapItem item, MapGroup group) {
        if (_sheet == item) {
            requestEndTool();
            unregisterListeners();
        }
    }

    @Override
    public void onClick(View v) {
        // Close button - end tool
        if (v.getId() == R.id.close) {
            requestEndTool();
            return;
        }

        // Select mode
        for (View btn : _buttons)
            btn.setSelected(v == btn);

        // Update
        reset();
        displayPrompt();
    }

    @Override
    public void undo() {
        super.undo();
        reset();
    }

    protected static class RotateAction extends EditAction {

        private final AbstractSheet _sheet;
        private final GeoPointMetaData[] _oldPoints;
        private final GeoPointMetaData[] _newPoints;

        public RotateAction(AbstractSheet sheet, GeoPointMetaData[] oldPoints) {
            _sheet = sheet;
            _oldPoints = oldPoints;
            _newPoints = sheet.getMetaDataPoints();
        }

        @Override
        public String getDescription() {
            return "Rotate Sheet";
        }

        @Override
        public boolean run() {
            _sheet.setPoints(_newPoints[0], _newPoints[1], _newPoints[2],
                    _newPoints[3]);
            return true;
        }

        @Override
        public void undo() {
            _sheet.setPoints(_oldPoints[0], _oldPoints[1], _oldPoints[2],
                    _oldPoints[3]);
        }
    }
}
