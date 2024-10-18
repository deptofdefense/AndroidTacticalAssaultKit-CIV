
package com.atakmap.android.drawing.tools;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

import com.atakmap.android.drawing.DrawingPreferences;
import com.atakmap.android.drawing.DrawingToolsMapComponent;
import com.atakmap.android.drawing.DrawingToolsMapReceiver;
import com.atakmap.android.drawing.mapItems.DrawingEllipse;
import com.atakmap.android.drawing.mapItems.DrawingRectangle;
import com.atakmap.android.editableShapes.Rectangle;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.Ellipse;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.toolbar.ButtonTool;
import com.atakmap.android.toolbar.widgets.TextContainer;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Tool for the creation of new {@link DrawingEllipse}
 */
public class DrawingEllipseCreationTool extends ButtonTool implements
        MapEventDispatcher.MapEventDispatchListener {

    private static final String TAG = "DrawingEllipseCreationTool";
    public static final String TOOL_IDENTIFIER = "com.atakmap.android.drawing.tools."
            + TAG;

    private static final int FIRST_POINT = 0;
    private static final int SECOND_POINT = 1;
    private static final int THIRD_POINT = 2;

    private final MapView _mapView;
    private final DrawingPreferences _prefs;

    private DrawingEllipse _ellipse;
    private int _mode = FIRST_POINT;
    private GeoPointMetaData _p1;
    private GeoPointMetaData _p2;
    private Builder _b;
    private String _title;
    private Intent _callback;

    public DrawingEllipseCreationTool(MapView mapView, ImageButton button) {
        super(mapView, button, TOOL_IDENTIFIER);
        _mapView = mapView;
        _prefs = new DrawingPreferences(mapView);
    }

    @Override
    public void dispose() {
    }

    @Override
    public boolean onToolBegin(Bundle extras) {
        _mode = FIRST_POINT;
        _p1 = _p2 = null;
        _ellipse = null;

        _callback = extras.getParcelable("callback");

        _mapView.getMapEventDispatcher().pushListeners();
        clearExtraListeners();
        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.MAP_CLICK, this);
        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.MAP_LONG_PRESS, this);
        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.ITEM_CLICK, this);

        TextContainer.getInstance()
                .displayPrompt(R.string.ellipse_point1_prompt);
        MapGroup childGroup = DrawingToolsMapComponent.getGroup().addGroup(
                _title = createTitle());
        _b = new Builder(childGroup, Rectangle.Builder.Mode.THREE_POINTS);
        _mapView.getMapTouchController().setToolActive(true);
        return super.onToolBegin(extras);
    }

    @Override
    public void onToolEnd() {
        _b.dispose();
        _b = null;

        if (_ellipse != null) {
            if (_callback != null) {
                Intent callbackIntent = new Intent(_callback);
                callbackIntent.putExtra("uid", _ellipse.getUID());
                AtakBroadcast.getInstance().sendBroadcast(callbackIntent);
            }

            Intent intent = new Intent();
            intent.setAction(DrawingToolsMapReceiver.ZOOM_ACTION);
            intent.putExtra(DrawingToolsMapReceiver.EXTRA_CREATION_MODE, true);
            intent.putExtra("uid", _ellipse.getUID());
            AtakBroadcast.getInstance().sendBroadcast(intent);
        }
        _ellipse = null;

        TextContainer.getInstance().closePrompt();
        _mapView.getMapEventDispatcher().popListeners();
        _mapView.getMapTouchController().setToolActive(false);
        super.onToolEnd();
    }

    private static String createTitle() {
        String prefix = "Ellipse";
        int num = 1;
        int j = 0;
        List<MapItem> typeList = DrawingToolsMapComponent.getGroup()
                .deepFindItems("type", DrawingEllipse.COT_TYPE);
        if (!typeList.isEmpty()) {
            int[] numUsed = new int[typeList.size()];
            // Do this to find the lowest used number for the group
            for (MapItem item : typeList) {
                if (item instanceof DrawingEllipse) {
                    String tTitle = item.getTitle();
                    if (FileSystemUtils.isEmpty(tTitle))
                        continue;
                    String[] n = tTitle.split(" ");

                    try {
                        numUsed[j] = Integer.parseInt(n[n.length - 1]);
                    } catch (Exception e) {
                        // The title has been editedA
                        numUsed[j] = 0;
                    }
                    j++;
                }
            }
            Arrays.sort(numUsed);
            for (int aNumUsed : numUsed) {
                if (num == aNumUsed) {
                    num++;
                }
            }
        }
        return prefix + " " + num;
    }

    @Override
    public void onMapEvent(MapEvent event) {
        GeoPointMetaData point = findPoint(event);
        if (point == null) {
            event.getExtras().putBoolean("eventNotHandled", true);
            return;
        }

        if (_mode == FIRST_POINT) {
            _p1 = point;
            _b.setFirstPoint(point);
            TextContainer.getInstance().displayPrompt(
                    R.string.ellipse_point2_prompt);
            _mode = SECOND_POINT;
        } else if (_mode == SECOND_POINT) {
            if (point.equals(_p1)) {
                Toast.makeText(
                        _mapView.getContext(),
                        R.string.same_point_warning,
                        Toast.LENGTH_SHORT).show();
                return;
            }
            _p2 = point;
            _b.setSecondPoint(point);

            TextContainer.getInstance().displayPrompt(
                    R.string.ellipse_point3_prompt);
            _mode = THIRD_POINT;
        } else if (_mode == THIRD_POINT) {
            if (point.equals(_p1) || point.equals(_p2)) {
                Toast.makeText(
                        _mapView.getContext(),
                        R.string.same_point_warning,
                        Toast.LENGTH_SHORT).show();
                return;
            }

            // Get the rectangle but hide it so it doesn't flash on screen
            Rectangle rect = _b.getRectangle();
            rect.setStrokeColor(0);

            // Set the third and final point, which ends up adding the
            // rectangle to the map
            try {
                _b.setThirdPoint(point);
            } catch (Exception e) {
                Log.e(TAG, "something bad has happened:", e);
                requestEndTool();
                return;
            }

            // Get stats used to build the ellipse
            GeoPoint[] points = rect.getPoints();
            GeoPointMetaData center = rect.getCenter();
            double angle = points[4].bearingTo(points[6]);
            double length = points[4].distanceTo(points[6]) / 2;
            double width = points[5].distanceTo(points[7]) / 2;

            // Create the primary ellipse
            Ellipse ellipse = new Ellipse(UUID.randomUUID().toString());
            ellipse.setDimensions(center, width, length, angle);

            // Attach it to a DrawingEllipse instance
            _ellipse = new DrawingEllipse(_mapView);
            _ellipse.setTitle(_title);
            _ellipse.setCenterPoint(center);
            _ellipse.setEllipses(Collections.singletonList(ellipse));
            _ellipse.setColor(_prefs.getFillColor(), true);
            _ellipse.setStrokeWeight(_prefs.getStrokeWeight());
            _ellipse.setStrokeStyle(_prefs.getStrokeStyle());
            _ellipse.setCenterMarker(null);

            // Add it to the map and persist
            _ellipse.setMetaString("entry", "user");
            DrawingToolsMapComponent.getGroup().addItem(_ellipse);
            _ellipse.persist(_mapView.getMapEventDispatcher(), null,
                    getClass());

            // All done
            requestEndTool();
        }
    }

    /***
     * Performs button initialization
     */
    protected void initButton() {
        super.initButton();
        _imageButton.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                Toast.makeText(_mapView.getContext(), R.string.rectangle_tip,
                        Toast.LENGTH_SHORT).show();
                return true;
            }
        });
    }

    /**
     * Builder sub-class simply used to expose the {@link Rectangle}
     */
    private static class Builder extends DrawingRectangle.Builder {

        public Builder(MapGroup group, Mode mode) {
            super(group, mode);
        }

        Rectangle getRectangle() {
            return _r;
        }
    }
}
