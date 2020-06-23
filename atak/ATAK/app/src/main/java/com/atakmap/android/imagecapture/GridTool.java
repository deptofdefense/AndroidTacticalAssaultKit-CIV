
package com.atakmap.android.imagecapture;

import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import com.atakmap.android.gridlines.GridLinesMapComponent;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher.MapEventDispatchListener;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.toolbar.Tool;
import com.atakmap.android.toolbar.widgets.TextContainer;
import com.atakmap.app.R;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

/**
 * Manage custom grid
 */
public class GridTool extends Tool implements MapEventDispatchListener {

    private static final String TAG = "GridTool";

    public static final String GRID_UID = "GRG_GRID";
    public static final String TOOL_IDENTIFIER = "com.atakmap.android.grg.GRID";
    public static final int MAX_GRID_LINES = 200;
    public static final int MAX_GRID_EXTENT = 1000000;

    private final Context _context;
    private final TextContainer _container;
    private final Button _toolButton;
    private final CustomGrid _grid;
    private GeoPointMetaData _lastPoint;

    public GridTool(MapView mapView, Button button) {
        super(mapView, TOOL_IDENTIFIER);
        _toolButton = button;
        _grid = GridLinesMapComponent.getCustomGrid();
        _container = TextContainer.getInstance();
        _context = mapView.getContext();
    }

    @Override
    public boolean onToolBegin(Bundle extras) {
        super.onToolBegin(extras);

        // Reset grid and display help text
        updateTextPrompt();
        _toolButton.setSelected(true);
        _lastPoint = null;

        // Map event listeners
        _mapView.getMapEventDispatcher().pushListeners();
        _mapView.getMapEventDispatcher().clearListeners(MapEvent.MAP_CLICK);
        _mapView.getMapEventDispatcher().clearListeners(MapEvent.ITEM_CLICK);
        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.MAP_CLICK, this);
        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.ITEM_CLICK, this);
        _mapView.getMapTouchController().setToolActive(true);
        _mapView.getMapTouchController().skipDeconfliction(true);

        return true;
    }

    @Override
    public void onToolEnd() {
        // Close help text
        updateButtonText();
        _container.closePrompt();
        _toolButton.setSelected(false);
        _lastPoint = null;

        // Clear listeners
        _mapView.getMapEventDispatcher().clearListeners();
        _mapView.getMapEventDispatcher().popListeners();
        _mapView.getMapTouchController().setToolActive(false);
        _mapView.getMapTouchController().skipDeconfliction(false);
    }

    @Override
    public void dispose() {
        _container.closePrompt();
    }

    private void updateButtonText() {
        _toolButton.setText(_grid.isValid()
                ? _context.getString(R.string.edit_grid)
                : _context.getString(R.string.drop_grid));
    }

    private void updateTextPrompt() {
        String txt;
        if (_grid.isValid())
            txt = _context.getString(R.string.grid_tool3);
        else
            txt = _context.getString(_lastPoint == null ? R.string.grid_tool1
                    : R.string.grid_tool2);
        _container.displayPrompt(txt);
    }

    @Override
    public void onMapEvent(MapEvent event) {
        if (_grid.isValid()) {
            // Open radial if not dropping grid
            if (event.getItem() != null && event.getType()
                    .equals(MapEvent.ITEM_CLICK)) {
                String uid = event.getItem().getUID();
                AtakBroadcast.getInstance().sendBroadcast(
                        new Intent("com.atakmap.android.maps.FOCUS")
                                .putExtra("uid", uid));
                AtakBroadcast.getInstance().sendBroadcast(
                        new Intent("com.atakmap.android.maps.SHOW_MENU")
                                .putExtra("uid", uid));
                AtakBroadcast.getInstance().sendBroadcast(
                        new Intent("com.atakmap.android.maps.SHOW_DETAILS")
                                .putExtra("uid", uid));
            }
            return;
        }
        GeoPointMetaData gp = findPoint(event);
        if (gp == null)
            return;
        if (_lastPoint == null) {
            _lastPoint = gp;
            updateTextPrompt();
            return;
        }

        // Restrict grid size within reason
        float[] ra = new float[3];
        Location.distanceBetween(gp.get().getLatitude(),
                gp.get().getLongitude(),
                _lastPoint.get().getLatitude(), _lastPoint.get().getLongitude(),
                ra);

        double xDist = Math.abs(ra[0] * Math.sin(Math.toRadians(ra[1])));
        double yDist = Math.abs(ra[0] * Math.cos(Math.toRadians(ra[1])));
        int xLines = (int) Math.round(xDist / _grid.getSpacing()),
                yLines = (int) Math
                        .round(yDist / _grid.getSpacing());

        if (xLines + yLines > MAX_GRID_LINES
                || xDist > MAX_GRID_EXTENT || yDist > MAX_GRID_EXTENT) {
            Toast.makeText(_context, R.string.grid_too_large,
                    Toast.LENGTH_LONG).show();
            return;
        }

        _grid.setCorners(_lastPoint, gp);
        updateButtonText();
        updateTextPrompt();
    }

    public void deleteGrid() {
        _grid.clear();
        updateButtonText();
        _lastPoint = null;
        updateTextPrompt();
    }

}
