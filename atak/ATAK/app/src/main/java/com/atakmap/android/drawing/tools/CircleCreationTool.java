
package com.atakmap.android.drawing.tools;

import android.content.Context;
import android.os.Bundle;
import android.widget.Toast;

import com.atakmap.android.drawing.DrawingPreferences;
import com.atakmap.android.drawing.mapItems.DrawingCircle;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher.MapEventDispatchListener;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapTouchController;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.toolbar.Tool;
import com.atakmap.android.toolbar.ToolManagerBroadcastReceiver;
import com.atakmap.android.toolbar.widgets.TextContainer;
import com.atakmap.android.util.Circle;
import com.atakmap.app.R;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

import java.util.Collection;
import java.util.UUID;

import gov.tak.api.annotation.DontObfuscate;

@DontObfuscate
public abstract class CircleCreationTool extends Tool
        implements MapEventDispatchListener {

    private static final String TAG = "CircleCreationTool";

    protected final Context _context;
    protected final MapGroup _mapGroup;
    protected final DrawingPreferences _prefs;

    protected DrawingCircle _circle;
    protected Marker _tempCenter;

    public CircleCreationTool(MapView mapView, MapGroup mapGroup,
            String toolIdentifier) {
        super(mapView, toolIdentifier);
        _context = mapView.getContext();
        _mapGroup = mapGroup;
        ToolManagerBroadcastReceiver.getInstance().registerTool(
                getIdentifier(), this);
        _prefs = new DrawingPreferences(mapView);
    }

    @Override
    public void dispose() {
        ToolManagerBroadcastReceiver.getInstance().unregisterTool(
                getIdentifier());
    }

    @Override
    public boolean onToolBegin(Bundle extras) {
        _mapView.getMapEventDispatcher().pushListeners();
        clearExtraListeners();
        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.MAP_CLICK, this);
        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.ITEM_CLICK, this);
        showCenterPrompt();
        _circle = createCircle();
        _mapView.getMapTouchController().setToolActive(true);
        return super.onToolBegin(extras);
    }

    /**
     * Show the text prompt for placing the circle center
     */
    protected void showCenterPrompt() {
        TextContainer.getInstance().displayPrompt(
                R.string.circle_center_prompt);
    }

    /**
     * Show the text prompt for placing the radius marker
     */
    protected void showRadiusPrompt() {
        TextContainer.getInstance().displayPrompt(
                R.string.circle_radius_prompt);
    }

    /**
     * Create a new empty circle to be used by this tool
     * @return New circle
     */
    protected DrawingCircle createCircle() {
        return new DrawingCircle(_mapView);
    }

    /**
     * Add the circle to map
     * @param circle The new circle to be added
     */
    protected abstract void addCircle(DrawingCircle circle);

    /**
     * Get teh default color for newly created circles with this tool
     * @return Default color
     */
    protected int getDefaultColor() {
        return _prefs.getShapeColor();
    }

    /**
     * Helper method to generate a new circle name
     * @param prefix Circle name prefix
     * @return New circle name
     */
    protected String getDefaultName(String prefix) {
        int highest = 0;
        String search = prefix.replace('\u00A0', ' ').trim();
        Collection<MapItem> items = _mapGroup.getItems();
        for (MapItem item : items) {
            String title = item.getTitle();
            if (title != null && title.startsWith(search)) {
                String num = title.substring(search.length())
                        .replace('\u00A0', ' ').trim();
                try {
                    highest = Math.max(highest, Integer.parseInt(num));
                } catch (Exception ignore) {
                }
            }
        }
        return prefix + (highest + 1);
    }

    @Override
    public void onMapEvent(MapEvent event) {
        MapItem mi = event.getItem();
        GeoPointMetaData point = findPoint(event);
        if (point == null)
            return;

        if (_circle.getCenterMarker() == null && _tempCenter == null)
            placeCenter(mi, point);
        else
            placeRadius(mi, point);
    }

    protected void placeCenter(MapItem mi, GeoPointMetaData point) {
        if (point == null)
            return;

        if (mi instanceof Marker && !mi.hasMetaValue("nevercot"))
            _circle.setCenterMarker((Marker) mi);
        else {
            if (_tempCenter != null)
                _tempCenter.removeFromGroup();
            _tempCenter = new Marker(point, UUID.randomUUID().toString());
            _tempCenter.setType("u-d-p");
            _tempCenter.setVisible(true);
            _tempCenter.setMetaInteger("color", getDefaultColor());
            _tempCenter.setMetaBoolean("nevercot", true);
            _tempCenter.setMetaString("entry", "user");
            _mapGroup.addItem(_tempCenter);
            _circle.setCenterPoint(point);
        }
        showRadiusPrompt();
    }

    protected void placeRadius(MapItem mi, GeoPointMetaData point) {
        GeoPointMetaData center = _circle.getCenter();
        if (center == null || point == null)
            return;

        double radius = center.get().distanceTo(point.get());
        if (radius >= Circle.MAX_RADIUS) {
            Toast.makeText(_context, R.string.circle_warning_max_radius,
                    Toast.LENGTH_LONG).show();
            return;
        } else if (radius <= 0) {
            Toast.makeText(_context, R.string.circle_warning_min_radius,
                    Toast.LENGTH_LONG).show();
            return;
        }

        _circle.setRadius(radius);
        if (mi instanceof Marker && !mi.hasMetaValue("nevercot"))
            _circle.setRadiusMarker((Marker) mi);
        addCircle(_circle);
        MapTouchController.goTo(_circle, false);
        requestEndTool();
    }

    @Override
    public void onToolEnd() {
        if (_tempCenter != null)
            _tempCenter.removeFromGroup();
        _tempCenter = null;
        _circle = null;
        _mapView.getMapEventDispatcher().clearListeners();
        _mapView.getMapEventDispatcher().popListeners();
        TextContainer.getInstance().closePrompt();
        _mapView.getMapTouchController().setToolActive(false);
        super.onToolEnd();
    }
}
