
package com.atakmap.android.rubbersheet.tool;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import com.atakmap.android.editableShapes.RectangleEditTool;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapMode;
import com.atakmap.android.maps.MapTouchController;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.navigation.views.NavView;
import com.atakmap.android.preference.UnitPreferences;
import com.atakmap.android.rubbersheet.maps.AbstractSheet;
import com.atakmap.android.toolbar.ToolManagerBroadcastReceiver;
import com.atakmap.android.toolbar.widgets.TextContainer;
import com.atakmap.android.tools.ActionBarReceiver;
import com.atakmap.android.tools.ActionBarView;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.android.util.AltitudeUtilities;
import com.atakmap.android.util.EditAction;
import com.atakmap.android.widgets.LinearLayoutWidget;
import com.atakmap.android.widgets.RootLayoutWidget;
import com.atakmap.android.widgets.TextWidget;
import com.atakmap.app.R;
import com.atakmap.coremap.conversions.Angle;
import com.atakmap.coremap.conversions.AngleUtilities;
import com.atakmap.coremap.maps.coords.DistanceCalculations;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.coremap.maps.coords.NorthReference;
import com.atakmap.map.CameraController;
import com.atakmap.map.MapRenderer2;
import com.atakmap.map.MapSceneModel;

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
    protected final UnitPreferences _prefs;
    protected final MapGroup _group;
    protected final TextContainer _cont;
    protected final TextWidget _subText;
    protected final LinearLayoutWidget _topEdge;
    protected AbstractSheet _sheet;
    protected double[] _p0, _p1, _p2, _p3;
    protected GeoPointMetaData[] _oldPoints;
    protected GeoPointMetaData _center;
    protected double _startAngle;
    private double _oldTilt;
    private boolean _oldTiltEnabled;
    private boolean _oldFreeRotate;

    public RubberSheetEditTool(MapView mapView, MapGroup group) {
        super(mapView, null, null);
        _context = mapView.getContext();
        _identifier = TOOL_NAME;
        _prefs = new UnitPreferences(mapView);
        _group = group;
        _cont = TextContainer.getInstance();
        ToolManagerBroadcastReceiver.getInstance().registerTool(
                getIdentifier(), this);

        _abReceiver = ActionBarReceiver.getInstance();
        _toolbar = (ActionBarView) LayoutInflater.from(_context).inflate(
                R.layout.rs_edit_sheet_toolbar, _mapView, false);
        _toolbar.setClosable(true);
        _toolbar.showCloseButton(false);
        _toolbar.setPosition(ActionBarView.TOP_RIGHT);
        _toolbar.setEmbedState(ActionBarView.FLOATING);
        _buttons[DRAG] = _toolbar.findViewById(R.id.dragBtn);
        _buttons[PITCH] = _toolbar.findViewById(R.id.pitchBtn);
        _buttons[HEADING] = _toolbar.findViewById(R.id.headingBtn);
        _buttons[ROLL] = _toolbar.findViewById(R.id.rollBtn);
        _buttons[ELEV] = _toolbar.findViewById(R.id.elevBtn);
        _toolbar.findViewById(R.id.close).setOnClickListener(this);
        for (View v : _buttons)
            v.setOnClickListener(this);

        // Widget for displaying heading/elevation
        RootLayoutWidget root = (RootLayoutWidget) _mapView.getComponentExtra(
                "rootLayoutWidget");
        _topEdge = root.getLayout(RootLayoutWidget.TOP_EDGE);
        _subText = new TextWidget("", 2);
        _subText.setBackground(TextWidget.TRANSLUCENT_BLACK);
        _subText.setMargins(0f, 4f, 0f, 0f);
        _subText.setVisible(false);
        _topEdge.addWidget(_subText);
    }

    @Override
    public void dispose() {
        super.dispose();
        ToolManagerBroadcastReceiver.getInstance().unregisterTool(
                getIdentifier());
        _topEdge.removeWidget(_subText);
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
        _oldFreeRotate = isFreeRotate();
        _oldTilt = _mapView.getMapTilt();
        _oldTiltEnabled = _mapView.getMapTouchController()
                .getTiltEnabledState() == MapTouchController.STATE_TILT_ENABLED;
        if (_oldTilt != 0d)
            CameraController.Programmatic.tiltTo(_mapView.getRenderer3(), 0,
                    false);
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
        if (_sheet != null && _sheet.hasMetaValue("archive"))
            _sheet.persist(_mapView.getMapEventDispatcher(), null, getClass());
        unregisterListeners();
        _mapView.getMapTouchController().setUserOrientation(_oldFreeRotate);
        CameraController.Programmatic.tiltTo(_mapView.getRenderer3(), _oldTilt,
                false);
        NavView.getInstance().setTiltEnabled(_oldTiltEnabled);
        _subText.setVisible(false);
        super.onToolEnd();
    }

    private boolean isFreeRotate() {
        MapMode mode = NavView.getInstance().getMapMode();
        return mode == MapMode.USER_DEFINED_UP
                && _mapView.getMapTouchController().isUserOrientationEnabled();
    }

    protected void unregisterListeners() {
        if (_sheet != null) {
            _mapView.getMapTouchController().setToolActive(false);
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
        updateSubText();
    }

    protected void updateSubText() {
        int mode = getMode();
        if (mode == HEADING) {
            double heading = _sheet.getHeading();
            GeoPoint center = _sheet.getCenterPoint();
            Angle unit = _prefs.getBearingUnits();
            NorthReference ref = _prefs.getNorthReference();
            if (ref == NorthReference.MAGNETIC)
                heading = ATAKUtilities.convertFromTrueToMagnetic(center,
                        heading);
            else if (ref == NorthReference.GRID)
                heading -= ATAKUtilities.computeGridConvergence(
                        center, heading, _sheet.getLength() / 2);
            _subText.setText(_context.getString(R.string.heading_fmt,
                    AngleUtilities.format(heading, unit) + ref.getAbbrev()));
        } else if (mode == ELEV)
            _subText.setText(AltitudeUtilities.format(_sheet.getCenter()));
        else {
            _subText.setVisible(false);
            return;
        }
        _subText.setVisible(true);
    }

    @Override
    public void onMapEvent(MapEvent event) {
        if (_sheet == null || _sheet.getGroup() == null)
            return;
        int mode = getMode();
        String type = event.getType();

        // Only allow map scale gesture in drag mode
        if (mode == DRAG && type.equals(MapEvent.MAP_SCALE)) {
            _mapView.getMapTouchController().onScaleEvent(event);
            return;
        }

        // Change sheet heading/rotation
        if (mode == HEADING) {
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
        GeoPoint p0 = GeoCalculations.pointAtDistance(_center.get(),
                _p0[1] - ang, _p0[0]);
        GeoPoint p1 = GeoCalculations.pointAtDistance(_center.get(),
                _p1[1] - ang, _p1[0]);
        GeoPoint p2 = GeoCalculations.pointAtDistance(_center.get(),
                _p2[1] - ang, _p2[0]);
        GeoPoint p3 = GeoCalculations.pointAtDistance(_center.get(),
                _p3[1] - ang, _p3[0]);
        _sheet.setPoints(GeoPointMetaData.wrap(p0), GeoPointMetaData.wrap(p1),
                GeoPointMetaData.wrap(p2), GeoPointMetaData.wrap(p3));
        updateSubText();
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
            final MapSceneModel sm = _mapView.getRenderer3().getMapSceneModel(
                    false, MapRenderer2.DisplayOrigin.UpperLeft);
            _mapView.getMapController().dispatchOnPanRequested();
            _mapView.getRenderer3().lookAt(
                    _center.get(),
                    sm.gsd,
                    sm.camera.azimuth,
                    0d,
                    false);
        }
        _sheet.setEditable(mode == DRAG);
        _mapView.getMapTouchController().setUserOrientation(isFreeRotate()
                || mode == HEADING);
        updateSubText();
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
