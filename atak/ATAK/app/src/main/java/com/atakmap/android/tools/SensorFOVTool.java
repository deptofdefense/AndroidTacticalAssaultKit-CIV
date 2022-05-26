
package com.atakmap.android.tools;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.Toast;

import com.atakmap.android.cot.detail.SensorDetailHandler;
import com.atakmap.android.cotdetails.sensor.SensorDetailsReceiver;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.SensorFOV;
import com.atakmap.android.toolbar.ButtonTool;
import com.atakmap.android.toolbar.ToolManagerBroadcastReceiver;
import com.atakmap.android.toolbar.widgets.TextContainer;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.maps.coords.DistanceCalculations;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;

/**
 * Tool for placing sensor FOV point
 */
public class SensorFOVTool extends ButtonTool
        implements MapEventDispatcher.MapEventDispatchListener,
        MapItem.OnGroupChangedListener {

    public static final String TAG = "SensorFOVTool";
    public static final String TOOL_NAME = "sensor_fov_tool";

    private final MapView _mapView;
    private Marker _marker;
    private final TextContainer _cont;
    private boolean _showDetails = false;
    private String _prompt = null;
    private String _intentAction = null;

    public SensorFOVTool(MapView mapView, ImageButton button) {
        super(mapView, button, TOOL_NAME);
        _mapView = mapView;
        _cont = TextContainer.getInstance();

        ToolManagerBroadcastReceiver.getInstance()
                .registerTool(TOOL_NAME, this);
    }

    @Override
    public boolean onToolBegin(Bundle extras) {
        if (extras.containsKey("uid")) {
            MapItem item = _mapView.getRootGroup()
                    .deepFindUID(extras.getString("uid"));
            if (item instanceof Marker)
                _marker = (Marker) item;
        }

        if (extras.containsKey("prompt")) {
            _prompt = extras.getString("prompt");
        }
        if (extras.containsKey("intent")) {
            _intentAction = extras.getString("intent");
        }
        _showDetails = extras.getBoolean("showDetails", false);
        if (_marker != null) {
            _cont.displayPrompt(FileSystemUtils.isEmpty(_prompt)
                    ? _mapView.getResources()
                            .getString(R.string.point_dropper_sensor_prompt)
                    : _prompt);
            // Take control of all map events
            _mapView.getMapEventDispatcher().addMapEventListener(
                    MapEvent.MAP_CLICK, this);
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
            _cont.closePrompt();
            _mapView.getMapEventDispatcher().removeMapEventListener(
                    MapEvent.MAP_CLICK, this);
            _mapView.getMapTouchController().setToolActive(false);
            _marker.removeOnGroupChangedListener(this);
            _marker = null;
        }
    }

    @Override
    public void onMapEvent(MapEvent event) {
        if (_marker == null)
            return;

        GeoPoint geoPoint = _mapView.inverse(
                event.getPointF().x, event.getPointF().y,
                MapView.InverseMode.RayCast).get();

        //if marker does not have range attribute, it is being created
        //for the first time so make sure FOV is not hidden
        if (_marker.hasMetaValue(SensorDetailHandler.HIDE_FOV) &&
                !_marker.hasMetaValue(SensorDetailHandler.RANGE_ATTRIBUTE))
            _marker.removeMetaData(SensorDetailHandler.HIDE_FOV);

        //set range and azimuth
        double dist = GeoCalculations.distanceTo(_marker.getPoint(), geoPoint);
        if (dist > SensorDetailHandler.MAX_SENSOR_RANGE) {
            dist = SensorDetailHandler.MAX_SENSOR_RANGE;
            Toast.makeText(
                    _mapView.getContext(),
                    _mapView.getResources().getString(
                            R.string.point_dropper_sensor_range_tip),
                    Toast.LENGTH_SHORT)
                    .show();
        }
        _marker.setMetaInteger(SensorDetailHandler.RANGE_ATTRIBUTE,
                (int) Math.round(dist));

        final double trueBearing = DistanceCalculations
                .bearingFromSourceToTarget(_marker.getPoint(), geoPoint);
        _marker.setMetaInteger(SensorDetailHandler.AZIMUTH_ATTRIBUTE,
                (int) Math.round(trueBearing));

        int fov = _marker.getMetaInteger(
                SensorDetailHandler.FOV_ATTRIBUTE, 45);
        int rangeLines = _marker.getMetaInteger(
                SensorDetailHandler.RANGE_LINES_ATTRIBUTE, 100);
        float red = (float) _marker.getMetaDouble(
                SensorDetailHandler.FOV_RED, 1);
        float green = (float) _marker.getMetaDouble(
                SensorDetailHandler.FOV_GREEN, 1);
        float blue = (float) _marker.getMetaDouble(
                SensorDetailHandler.FOV_BLUE, 1);
        float alpha = (float) _marker.getMetaDouble(
                SensorDetailHandler.FOV_ALPHA, 0.3f);
        boolean showFOV = !_marker.hasMetaValue(
                SensorDetailHandler.HIDE_FOV);
        boolean showLabels = _marker.hasMetaValue(
                SensorDetailHandler.DISPLAY_LABELS);
        int sc = _marker.getMetaInteger(SensorDetailHandler.STROKE_COLOR,
                Color.LTGRAY);
        double sw = _marker.getMetaDouble(SensorDetailHandler.STROKE_WEIGHT, 0);

        SensorFOV sens = SensorDetailHandler.addFovToMap(_marker, trueBearing,
                fov, dist,
                new float[] {
                        red, green, blue, alpha
                }, showFOV, showLabels, rangeLines);
        if (sens == null)
            return;

        sens.setStrokeWeight(sw);
        sens.setStrokeColor(sc);

        if (_showDetails || !FileSystemUtils.isEmpty(_intentAction)) {
            String action = FileSystemUtils.isEmpty(_intentAction)
                    ? SensorDetailsReceiver.SHOW_DETAILS
                    : _intentAction;

            Intent detailEditor = new Intent(action);
            detailEditor.putExtra("targetUID", _marker.getUID());
            AtakBroadcast.getInstance().sendBroadcast(detailEditor);
        }

        requestEndTool();
        unregisterListeners();
    }

    @Override
    public void onItemAdded(MapItem item, MapGroup group) {
    }

    @Override
    public void onItemRemoved(MapItem item, MapGroup group) {
        if (item == _marker) {
            requestEndTool();
            unregisterListeners();
        }
    }

}
