
package com.atakmap.android.rubbersheet.tool;

import android.graphics.PointF;
import android.os.Bundle;
import android.view.View;

import com.atakmap.android.editableShapes.Rectangle;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapTouchController;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.rubbersheet.maps.RubberModel;
import com.atakmap.android.util.EditAction;
import com.atakmap.app.R;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPoint.AltitudeReference;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.map.CameraController;
import com.atakmap.map.MapCamera;
import com.atakmap.map.MapRenderer3;
import com.atakmap.map.MapSceneModel;
import com.atakmap.math.MathUtils;

import gov.tak.api.engine.map.IMapRendererEnums.CameraCollision;

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
    private PointF _startTilt;
    private double _oldAlt;
    private AltitudeReference _oldAltRef;
    private double _cameraDist;
    private double _modelHeight;

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
        CameraController.Programmatic.panTo(
                _mapView.getRenderer3(), _model.getCenterPoint(), false);
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
                PointF p = event.getPointF();
                if (_startTilt == null) {
                    _center = _sheet.getCenter();
                    _startTilt = p;
                    return;
                }

                // Get the current model altitude
                GeoPoint c = _center.get();
                double alt = c.getAltitude();
                if (Double.isNaN(alt))
                    alt = 0;

                // Modify altitude based on distance from camera
                // Note: This is not a hard calculation. More of a rule of thumb.
                double newAlt = alt
                        + ((_startTilt.y - p.y) * _modelHeight * 0.01);
                _model.setAltitude(newAlt, c.getAltitudeReference());
                updateSubText();
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
            focusElevation();
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
            CameraController.Programmatic.panTo(
                    _mapView.getRenderer3(), _center.get(), false);
    }

    @Override
    public void onClick(View v) {
        // Tilt the map so the user can see what they're doing
        if (v == _buttons[ELEV]) {
            _mapView.getMapTouchController().setTiltEnabledState(
                    MapTouchController.STATE_TILT_ENABLED);
            double[] dim = _model.getModelDimensions(true);
            _cameraDist = MathUtils.distance(dim[0], dim[1], dim[2], 0, 0, 0)
                    * 1.5;
            _modelHeight = dim[2];
            focusElevation();
        }

        // Tilt the map back when switching from elevation to drag
        else if (v == _buttons[DRAG] && getMode() == ELEV) {
            _mapView.getMapController().dispatchOnPanRequested();
            CameraController.Programmatic.tiltTo(
                    _mapView.getRenderer3(),
                    0d,
                    _center.get(),
                    false);
        }

        super.onClick(v);
    }

    private double getCameraDistance() {
        MapCamera camera = _mapView.getSceneModel().camera;
        return Math.sqrt(Math.pow(camera.location.x - camera.target.x, 2)
                + Math.pow(camera.location.y - camera.target.y, 2)
                + Math.pow(camera.location.z - camera.target.z, 2));
    }

    private void focusElevation() {
        GeoPoint point = _sheet.getCenterPoint();
        MapRenderer3 renderer = _mapView.getRenderer3();
        final MapSceneModel sm = renderer.getMapSceneModel(true,
                MapRenderer3.DisplayOrigin.UpperLeft);
        double gsd = MapSceneModel.gsd(_cameraDist + point.getAltitude(),
                sm.camera.fov, sm.height);
        renderer.lookAt(point, gsd, sm.camera.azimuth,
                _mapView.getMaxMapTilt() * 0.9d,
                CameraCollision.Ignore, false);
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
