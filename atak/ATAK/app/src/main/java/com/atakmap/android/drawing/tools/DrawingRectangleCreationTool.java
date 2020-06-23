
package com.atakmap.android.drawing.tools;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

import com.atakmap.android.drawing.DrawingToolsMapComponent;
import com.atakmap.android.drawing.DrawingToolsMapReceiver;
import com.atakmap.android.drawing.mapItems.DrawingRectangle;
import com.atakmap.android.editableShapes.Rectangle.Builder.Mode;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.maps.Shape;
import com.atakmap.android.toolbar.Tool;
import com.atakmap.android.toolbar.widgets.TextContainer;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

import java.util.Arrays;
import java.util.List;

public class DrawingRectangleCreationTool extends Tool implements
        MapEventDispatcher.MapEventDispatchListener {

    public static final String TAG = "DrawingRectangleCreationTool";

    private DrawingRectangle.Builder _b;

    protected final MapGroup _drawingGroup;

    private static final int FIRST_POINT = 0;
    private static final int SECOND_POINT = 1;
    private static final int THIRD_POINT = 2;

    private GeoPointMetaData _first_point;
    private GeoPointMetaData _second_point;

    private int mode = FIRST_POINT;

    private static final String TOOL_IDENTIFIER = "org.android.maps.drawing.tools.RectangleCreationTool";

    private final ImageButton _button;

    private final MapView _mapView;

    /******************** CONSTRUCTOR **********************/

    public DrawingRectangleCreationTool(MapView mapView, MapGroup drawingGroup,
            ImageButton button) {
        super(mapView, TOOL_IDENTIFIER);
        _mapView = mapView;
        _button = button;
        _drawingGroup = drawingGroup;
        initButton();
    }

    /******************** INHERITED METHODS *************************/

    @Override
    public void dispose() {
    }

    @Override
    public boolean onToolBegin(Bundle extras) {
        mode = FIRST_POINT;
        _first_point = _second_point = null;

        _mapView.getMapEventDispatcher().pushListeners();
        this.clearExtraListeners();

        _setup();
        TextContainer.getInstance().displayPrompt(
                _mapView.getResources().getString(
                        R.string.rectangle_corner1_prompt));
        MapGroup childGroup = DrawingToolsMapComponent.getGroup().addGroup(
                _createTitle());
        _b = new DrawingRectangle.Builder(childGroup, Mode.THREE_POINTS);
        _mapView.getMapTouchController().setToolActive(true);
        return super.onToolBegin(extras);
    }

    @Override
    public void onToolEnd() {
        // If the builder didn't finish then destroy it
        if (!_b.built()) {
            _b.dispose();
        } else {

            // If properly built then show the details for the item
            Intent intent = new Intent();
            intent.setAction(DrawingToolsMapReceiver.ZOOM_ACTION);
            intent.putExtra(DrawingToolsMapReceiver.EXTRA_CREATION_MODE, true);
            intent.putExtra("uid", _b.build().getUID());
            AtakBroadcast.getInstance().sendBroadcast(intent);

        }
        _b = null;
        TextContainer.getInstance().closePrompt();
        _mapView.getMapEventDispatcher().popListeners();
        _mapView.getMapTouchController().setToolActive(false);
        super.onToolEnd();
    }

    /******************** PUBLIC METHODS *************************/

    /******************** PRIVATE METHODS *************************/

    private static String _createTitle() {
        String prefix = "Rectangle";
        int num = 1;
        int j = 0;
        List<MapItem> typeList = DrawingToolsMapComponent.getGroup()
                .deepFindItems("type", "u-d-r");
        if (!typeList.isEmpty()) {
            int[] numUsed = new int[typeList.size()];
            // Do this to find the lowest used number for the group
            for (MapItem item : typeList) {
                if (item instanceof DrawingRectangle) {
                    String tTitle = item.getTitle();
                    if (FileSystemUtils.isEmpty(tTitle))
                        continue;
                    String[] n = tTitle.split(" ");
                    try {
                        numUsed[j] = Integer.parseInt(n[n.length - 1]);
                    } catch (NumberFormatException e) {
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

    private void _setup() {
        //_mapView.getMapEventDispatcher().pushListeners();

        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.MAP_CLICK, this);
        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.MAP_LONG_PRESS, this);
        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.ITEM_CLICK, this);

    }

    /******************** MAP LISTENERS *************************/
    @Override
    public void onMapEvent(MapEvent event) {
        GeoPointMetaData point = null;
        MapItem item = event.getItem();
        if (event.getType().equals(MapEvent.MAP_CLICK)
                || event.getType().equals(MapEvent.MAP_LONG_PRESS)) {
            point = _mapView.inverseWithElevation(
                    event.getPoint().x, event.getPoint().y);

        } else if (event.getType().equals(MapEvent.ITEM_CLICK)) {
            if (item instanceof PointMapItem) {
                point = ((PointMapItem) item).getGeoPointMetaData();
                event.getExtras().putBoolean("eventNotHandled", false);
            } else if (item instanceof Shape) {
                point = GeoPointMetaData
                        .wrap((((Shape) item).findTouchPoint()));
                event.getExtras().putBoolean("eventNotHandled", false);
            }
        }

        if (point == null) {
            event.getExtras().putBoolean("eventNotHandled", true);
            return;
        }

        if (mode == FIRST_POINT) {
            _b.setFirstPoint(point);
            _first_point = point;
            TextContainer.getInstance().displayPrompt(
                    _mapView.getResources().getString(
                            R.string.rectangle_corner2_prompt));
            mode = SECOND_POINT;
        } else if (mode == SECOND_POINT) {
            if (point.equals(_first_point)) {
                Toast.makeText(
                        _mapView.getContext(),
                        "Warning: Must be a different point ",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            _second_point = point;
            _b.setSecondPoint(point);

            TextContainer.getInstance().displayPrompt(
                    _mapView.getResources().getString(
                            R.string.rectangle_corner3_prompt));
            mode = THIRD_POINT;
        } else if (mode == THIRD_POINT) {
            if (point.equals(_first_point) || point.equals(_second_point)) {
                Toast.makeText(
                        _mapView.getContext(),
                        "Warning: Must be a different point ",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                _b.setThirdPoint(point);
            } catch (Exception e) {
                Log.e(TAG, "something bad has happened:", e);
                TextContainer.getInstance().displayPrompt(
                        _mapView.getResources().getString(
                                R.string.rectangle_corner1_prompt));
                mode = FIRST_POINT;
                requestEndTool();
                return;
            }

            TextContainer.getInstance().displayPrompt(
                    _mapView.getResources().getString(
                            R.string.rectangle_corner1_prompt));
            mode = FIRST_POINT;

            _b.createCenterMarker();
            DrawingRectangle r = _b.build();
            r.setMetaString("entry", "user");
            DrawingToolsMapComponent.getGroup().addItem(r);
            r.persist(_mapView.getMapEventDispatcher(), null,
                    this.getClass());
            requestEndTool();
        }

    }

    /***
     * Performs button initialization, setting up onclick listeners etc. Overide if the default
     * behavior (start tool if it isn't started, stop if if it is) isn't sufficient.
     */
    protected void initButton() {
        _button.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {

                // end if we're active, begin if we're not
                if (getActive())
                    requestEndTool();
                else
                    requestBeginTool();
            }
        });

        _button.setOnLongClickListener(onLongClickListener);
    }

    View.OnLongClickListener onLongClickListener = new View.OnLongClickListener() {
        @Override
        public boolean onLongClick(View view) {
            Toast.makeText(_mapView.getContext(), R.string.rectangle_tip,
                    Toast.LENGTH_SHORT).show();
            return true;
        }
    };

    @Override
    protected void setActive(boolean active) {
        _button.setSelected(active);
        super.setActive(active);
    }

}
