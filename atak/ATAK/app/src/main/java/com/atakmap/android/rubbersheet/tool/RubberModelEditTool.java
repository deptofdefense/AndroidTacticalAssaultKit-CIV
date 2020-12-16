
package com.atakmap.android.rubbersheet.tool;

import android.graphics.Point;
import android.os.Bundle;
import android.view.View;

import com.atakmap.android.editableShapes.Rectangle;
import com.atakmap.android.mapcompass.CompassArrowMapComponent;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.rubbersheet.maps.RubberModel;
import com.atakmap.android.util.EditAction;
import com.atakmap.app.R;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPoint.AltitudeReference;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

/**
 * Rotation tool for models
 *
 * TODO: Pitch and roll rotation is implemented but doesn't feel right
 * Hidden for now...
 * Probably should switch to quaternion rotation relative to the angle at
 * which the user is viewing the model. Then when finished, transform so
 * the model is centered and the bottom is flush with the ground
 */
public class RubberModelEditTool extends RubberSheetEditTool {

    private static final String TAG = "RubberModelEditTool";
    public static final String TOOL_NAME = "com.atakmap.android.rubbersheet.tool."
            + TAG;

    private final double[] _scratchRot = new double[3];

    protected RubberModel _model;
    private double[] _rotation;
    private Point _startTilt;
    private double _oldAlt;
    private AltitudeReference _oldAltRef;

    public RubberModelEditTool(MapView mapView, MapGroup group) {
        super(mapView, group);
        _identifier = TOOL_NAME;

        // Enable elevation tool
        _buttons[ELEV].setVisibility(View.VISIBLE);
    }

    public PointMapItem getMarker() {
        return _model.getCenterMarker();
    }

    @Override
    public String getIdentifier() {
        return TOOL_NAME;
    }

    @Override
    public boolean onToolBegin(Bundle extras) {
        if (!super.onToolBegin(extras) || !(_sheet instanceof RubberModel))
            return false;
        _model = (RubberModel) _sheet;
        reset();
        return true;
    }

    @Override
    public void onToolEnd() {
        super.onToolEnd();
        _mapView.getMapController().panTo(_model.getCenterPoint(), false);
        _model = null;
    }

    @Override
    protected void displayPrompt() {
        if (_buttons[DRAG].isSelected()) {
            super.displayPrompt();
            return;
        }
        if (_buttons[ELEV].isSelected()) {
            _cont.displayPrompt(_context.getString(
                    R.string.rotate_model_tooltip2));
            return;
        }
        String mode = _context.getString(R.string.heading);
        if (_buttons[PITCH].isSelected())
            mode = _context.getString(R.string.pitch);
        else if (_buttons[ROLL].isSelected())
            mode = _context.getString(R.string.roll);
        mode = mode.toLowerCase(LocaleUtil.getCurrent());
        _cont.displayPrompt(_context.getString(R.string.rotate_model_tooltip1,
                mode));
    }

    @Override
    public void onMapEvent(MapEvent event) {
        super.onMapEvent(event);

        String type = event.getType();
        if (_buttons[ELEV].isSelected()) {
            // Two-finger altitude adjustment
            if (type.equals(MapEvent.MAP_TILT)) {
                event.getExtras().putBoolean("eventNotHandled", false);
                Point p = event.getPoint();
                if (_startTilt == null) {
                    _center = _sheet.getCenter();
                    _startTilt = p;
                    return;
                }
                GeoPoint c = _center.get();
                double alt = c.getAltitude();
                if (Double.isNaN(alt))
                    alt = 0;
                double newAlt = alt + ((_startTilt.y - p.y)
                        * _mapView.getMapResolution());
                _model.setAltitude(newAlt, c.getAltitudeReference());
            }

            // Re-center map view on the new floating point
            else if (type.equals(MapEvent.MAP_RELEASE)) {
                run(new ElevAction(_model, _oldAlt, _oldAltRef));
                reset();
            }
        }
    }

    @Override
    public void onMoved(Rectangle r, GeoPointMetaData[] oldPoints,
            GeoPointMetaData[] newPoints) {
        super.onMoved(r, oldPoints, newPoints);
        if (getMode() == ELEV)
            _mapView.getMapController().panTo(_sheet.getCenterPoint(), false);
    }

    @Override
    protected void rotate(double ang) {
        int mode = getMode();
        if (mode == HEADING)
            super.rotate(ang);
        else if (mode == PITCH || mode == ROLL) {
            System.arraycopy(_rotation, 0, _scratchRot, 0, 3);
            for (int i = 0; i < _buttons.length; i++) {
                if (_buttons[i].isSelected())
                    _scratchRot[i] += ang;
            }
            _model.setModelRotation(_scratchRot);
        }
    }

    @Override
    protected void reset() {
        super.reset();
        if (_model != null)
            _rotation = _model.getModelRotation();
        _oldAlt = _center.get().getAltitude();
        _oldAltRef = _center.get().getAltitudeReference();
        _startTilt = null;
        if (getMode() == ELEV)
            _mapView.getMapController().panTo(_center.get(), false);
    }

    @Override
    public void onClick(View v) {
        // Tilt the map so the user can see what they're doing
        if (v == _buttons[ELEV] && _mapView.getMapTilt() == 0d) {
            CompassArrowMapComponent.getInstance().enable3DControls(true);
            _mapView.getMapController().tiltTo(_mapView.getMaxMapTilt(), false);
        }

        // Tilt the map back when switching from elevation to drag
        else if (v == _buttons[DRAG] && getMode() == ELEV) {
            _mapView.getMapController().tiltTo(0, false);
            _mapView.getMapController().panTo(_center.get(), false);
        }

        super.onClick(v);
    }

    private static class ElevAction extends EditAction {

        private final RubberModel _model;
        private final double _oldAlt, _newAlt;
        private final AltitudeReference _oldAltRef, _newAltRef;

        ElevAction(RubberModel model, double oldAlt,
                AltitudeReference oldAltRef) {
            _model = model;
            _oldAlt = oldAlt;
            _oldAltRef = oldAltRef;
            GeoPoint center = model.getCenterPoint();
            _newAlt = center.getAltitude();
            _newAltRef = center.getAltitudeReference();
        }

        @Override
        public String getDescription() {
            return "Elevate Model";
        }

        @Override
        public boolean run() {
            _model.setAltitude(_newAlt, _newAltRef);
            return true;
        }

        @Override
        public void undo() {
            _model.setAltitude(_oldAlt, _oldAltRef);
        }
    }
}
