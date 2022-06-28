
package com.atakmap.android.drawing.tools;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;

import com.atakmap.android.drawing.DrawingPreferences;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RectShape;
import android.graphics.drawable.shapes.Shape;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;

import com.atakmap.android.drawing.DrawingToolsToolbar;
import com.atakmap.android.drawing.mapItems.DrawingShape;
import com.atakmap.android.dropdown.DropDownManager;
import com.atakmap.android.gui.ColorPalette;
import com.atakmap.android.gui.ColorPalette.OnColorSelectedListener;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher.MapEventDispatchListener;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.MultiPolyline;
import com.atakmap.android.menu.MapMenuReceiver;
import com.atakmap.android.toolbar.Tool;
import com.atakmap.android.toolbar.ToolbarBroadcastReceiver;
import com.atakmap.android.toolbar.widgets.TextContainer;
import com.atakmap.android.tools.ActionBarReceiver;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.app.R;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.spatial.SpatialCalculator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Stack;
import java.util.UUID;

/**
 * 
 * Modified from ShapeCreationTool
 * 
 * Telestration provides the user with functionality to freehand mark on the map surface.
 * Captures the touch and drag points of the user and on release draws a simplified polyline.
 */
public class TelestrationTool extends Tool {

    protected final Stack<DrawingShape> undoStack = new Stack<>();

    private static final String TYPE = "telestration";
    private final String SCROLL_LOCKED;
    private final String SCROLL_UNLOCKED;
    private static final String TITLE_PREFIX = "Freehand";
    private static final String MULTI_PREFIX = "Freehand";
    private static final int PIXELS = 3;

    public static final String TOOL_IDENTIFIER = "com.atakmap.android.drawing.tools.TelestrationTool";

    private final DrawingToolsToolbar _drawingToolsToolbar;
    private final ImageButton _telestrationButton;
    private final Button _doneButton;

    private final ImageButton _toggleScrollZoomButton;
    private final Button _undoButton;
    private final Button _deleteTelestrationButton;
    private final ImageButton _telestrationColorButton;
    private final TextContainer _container;
    private final MapView _mapView;

    /**
     * The collection of points for a telestration
     */
    private List<GeoPointMetaData> _geoPoints;
    private DrawingShape _drawingShape;
    private final SpatialCalculator _spatialCalculator;
    private final Context _context;

    private GeoPointMetaData _currentGeoPoint;
    private float _currentX;
    private float _currentY;
    private int _color;
    private int _numPoints;
    private boolean _scrollLocked;
    private MultiPolyline _multiPolyline;
    private DrawingShape _duplicateShape;
    private boolean adding = false;

    private final DrawingPreferences _prefs;
    protected final MapGroup _mapGroup;

    public TelestrationTool(MapView mapView, MapGroup drawingGroup,
            ImageButton teleStrationButton,
            DrawingToolsToolbar drawingToolsToolbar,
            ImageButton toggleButton,
            Button undoButton,
            Button doneButton,
            Button deleteTelestrationButton,
            ImageButton telestrationColorButton,
            Context context) {

        super(mapView, TOOL_IDENTIFIER);
        _mapView = mapView;
        _mapGroup = drawingGroup;
        _prefs = new DrawingPreferences(mapView);
        _drawingToolsToolbar = drawingToolsToolbar;
        _telestrationButton = teleStrationButton;
        _doneButton = doneButton;
        _toggleScrollZoomButton = toggleButton;
        _undoButton = undoButton;
        _deleteTelestrationButton = deleteTelestrationButton;
        _telestrationColorButton = telestrationColorButton;
        _container = TextContainer.getInstance();
        _context = context;
        SCROLL_LOCKED = mapView.getContext().getString(
                R.string.telestrate_locked_prompt);
        SCROLL_UNLOCKED = mapView.getContext().getString(
                R.string.telestrate_unlocked_prompt);

        //Used on release to simplify the polyline
        _spatialCalculator = new SpatialCalculator.Builder().inMemory().build();

        _geoPoints = new ArrayList<>();

        initButton();
    }

    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        return super.onKey(v, keyCode, event);
    }

    @Override
    public void dispose() {
        _spatialCalculator.dispose();
    }

    @Override
    protected boolean onToolBegin(Bundle extras) {
        super.onToolBegin(extras);

        _menuShowing = false;
        _color = _prefs.getShapeColor();
        updateColor(_color);

        AtakBroadcast.getInstance().sendBroadcast(new Intent(
                ActionBarReceiver.REFRESH_ACTION_BAR));

        DocumentedIntentFilter menuFilter = new DocumentedIntentFilter();
        menuFilter.addAction(MapMenuReceiver.SHOW_MENU);
        menuFilter.addAction(MapMenuReceiver.HIDE_MENU);
        AtakBroadcast.getInstance().registerReceiver(_menuReceiver, menuFilter);

        //Override the scroll listener which scroll locks the map
        _scrollLocked = true;

        //Manipulate the visibility of the hidden buttons to build the telestration toolbar
        //Pushes the listeners so we can catch the events first
        _mapView.getMapEventDispatcher().pushListeners();

        //Clear out the listeners we are not interested in
        this.clearExtraListeners();

        //Add our own listeners for the gestures and events we are interested in
        registerListeners();
        //Set up all the buttons and everything
        _container.displayPrompt(SCROLL_LOCKED);
        _drawingToolsToolbar.toggleShapeButtons(false);
        _telestrationButton.setVisibility(Button.GONE);
        _toggleScrollZoomButton.setVisibility(Button.VISIBLE);
        _toggleScrollZoomButton.setSelected(true);
        _undoButton.setVisibility(Button.VISIBLE);
        _undoButton.setEnabled(true);
        _doneButton.setVisibility(Button.VISIBLE);

        // SHB do not set as visible -- not sure this 
        // functionality is beneficial and more importantly
        // it breaks NW devices because the action view 
        // grows and bumps an icon down to the overflow.
        // see bugs 5826
        //_deleteTelestrationButton.setVisibility(Button.VISIBLE);

        _telestrationColorButton.setVisibility(Button.VISIBLE);

        //If the user is adding to an already existing shape
        if (extras.containsKey("adding")) {
            adding = true;
            //_doneButton.setVisibility(Button.GONE);
            String _MPUID = (String) extras.get("adding");
            //Set the multi-polyline to the shape
            _multiPolyline = (MultiPolyline) _mapGroup.findItem("uid", _MPUID);

        } else {
            //Else make a new shape
            adding = false;

            _multiPolyline = new MultiPolyline(_mapView, _mapGroup, UUID
                    .randomUUID().toString());
            // To generate a title find first unused polyine group; start with the count of the new shape
            int count = 1;
            for (MapItem i : _mapGroup.getItems()) {
                if (i instanceof MultiPolyline) {
                    count++;
                }
            }

            // Then find the first unused name from there on
            while (_mapGroup.findItem("title", MULTI_PREFIX
                    + " " + count) != null)
                count++;
            //Set up the multipolyline, add it to corresponding groups etc etc
            _multiPolyline.setTitle(MULTI_PREFIX + " " + count);
            _multiPolyline.setColor(_color);
        }
        return super.onToolBegin(extras);
    }

    /**
     * On the press event initialize the polyline and capture the first point
     */
    private final MapEventDispatchListener pressListener = new MapEventDispatchListener() {
        @Override
        public void onMapEvent(MapEvent event) {

            //If we deleted the group at some point also make a new one here?
            if (!_mapGroup.containsItem(_multiPolyline)) {
                _multiPolyline = new MultiPolyline(_mapView, _mapGroup, UUID
                        .randomUUID().toString());
                // To generate a title find first unused polyine group;
                // start with the count of the new shape
                int count = 1;
                for (MapItem i : _mapGroup.getItems()) {
                    if (i instanceof MultiPolyline) {
                        count++;
                    }
                }

                // Then find the first unused name from there on
                while (_mapGroup.findItem("title", MULTI_PREFIX
                        + " " + count) != null)
                    count++;
                _multiPolyline.setTitle(MULTI_PREFIX + " " + count);
                _multiPolyline.setColor(_color);
                _multiPolyline.setEditable(false);
                _multiPolyline.setMetaString("entry", "user");
                _multiPolyline.setMetaBoolean("creating", true);
                _mapGroup.addItem(_multiPolyline);

            }

            startDrawing(event);
        }
    };

    private void startDrawing(MapEvent event) {
        _drawingShape = new DrawingShape(_mapView, _mapGroup, UUID
                .randomUUID().toString());
        // To generate a title find first unused shape name; start with the count of the new shape
        int count = 1;
        for (MapItem i : _mapGroup.getItems()) {
            if (i instanceof DrawingShape) {
                count++;
            }
        }
        // Then find the first unused name from there on
        while (_mapGroup.findItem("title", TITLE_PREFIX
                + " " + count) != null)
            count++;

        _drawingShape.setTitle(TITLE_PREFIX + " " + count);
        _drawingShape.setType(TYPE);

        _drawingShape.setStrokeWeight(_prefs.getStrokeWeight());
        int _alpha = _drawingShape.getFillColor() >>> 24;
        _drawingShape.setStrokeStyle(_prefs.getStrokeStyle());
        _drawingShape.setStrokeColor(_color);
        _drawingShape.setFillColor(Color.argb(_alpha, Color.red(_color),
                Color.green(_color), Color.blue(_color)));
        _currentX = event.getPointF().x;
        _currentY = event.getPointF().y;
        GeoPointMetaData _geoPoint = _mapView.inverse(event.getPointF().x,
                event.getPointF().y, MapView.InverseMode.RayCast);
        _geoPoints.add(_geoPoint);
        //So this is pretty hacky but was having some threading issues where the shape was not
        //updating with the GL MultiPolyline, so throughout drawing we render a duplicate shape
        //then on release we delete the duplicate
        removeDuplicate();
        _duplicateShape = new DrawingShape(_mapView, _mapGroup, UUID
                .randomUUID().toString());
        _duplicateShape.setMetaBoolean("addToObjList", false);
        _duplicateShape.setStrokeColor(_color);
        _duplicateShape.setStrokeWeight(_prefs.getStrokeWeight());
        _duplicateShape.setStrokeStyle(_prefs.getStrokeStyle());
        _duplicateShape.setFillColor(Color.argb(_alpha, Color.red(_color),
                Color.green(_color), Color.blue(_color)));
        _mapGroup.addItem(_duplicateShape);
    }

    /**
     * On the scroll events capture and the new point
     */
    private final MapEventDispatchListener dragListener = new MapEventDispatchListener() {
        @Override
        public void onMapEvent(MapEvent event) {
            if (_drawingShape == null)
                startDrawing(event);

            //get screen coordinates based on top-left corner
            _currentX = event.getPointF().x;
            _currentY = event.getPointF().y;

            //find the GeoPoint of the touch event
            _currentGeoPoint = _mapView.inverse(_currentX, _currentY,
                    MapView.InverseMode.RayCast);
            _geoPoints.add(_currentGeoPoint);

            _numPoints = _geoPoints.size();
            _drawingShape.setPoints(_geoPoints
                    .toArray(new GeoPointMetaData[0]));
            _duplicateShape.setPoints(_geoPoints
                    .toArray(new GeoPointMetaData[0]));
        }
    };

    private final MapEventDispatchListener scrollListener = new MapEventDispatchListener() {
        @Override
        public void onMapEvent(MapEvent event) {
        }
    };

    /**
     * On the release event simplify the collection of GeoPoints and create a persistent polyline
     */
    private final MapEventDispatchListener releaseListener = new MapEventDispatchListener() {
        @Override
        public void onMapEvent(MapEvent event) {
            if (_drawingShape == null)
                return;
            _numPoints = _geoPoints.size();
            if (_numPoints > 1) {

                //simplification is controlled by a threshold value (in degrees).  Points that deviate from the
                //    topology of the line that are within the threshold are eliminated
                List<GeoPoint> gpts = ATAKUtilities.simplifyPoints(
                        _spatialCalculator,
                        GeoPointMetaData.unwrap(_geoPoints));

                // valid simplification, rewrap and continue forward
                if (gpts != null) {
                    _geoPoints = GeoPointMetaData.wrap(gpts);
                }
                _numPoints = _geoPoints.size();

                //So at this point our drawing shape is complete, we can now add it to the Multi-
                //polyline and have it render correctly
                _drawingShape.setPoints(_geoPoints
                        .toArray(new GeoPointMetaData[0]));
                _drawingShape.setMovable(true);
                _multiPolyline.add(_drawingShape);
                _multiPolyline.setStrokeWeight(_prefs.getStrokeWeight());
                _multiPolyline.setStrokeStyle(_prefs.getStrokeStyle());
                _multiPolyline.setMetaString("entry", "user");
                //Remove the duplicate that we have been rendering up until now
                _mapGroup.removeItem(_duplicateShape);
                _mapGroup.addItem(_multiPolyline);
                undoStack.push(_drawingShape);

            }
            _geoPoints.clear();
            _drawingShape = null;
        }
    };

    @Override
    protected void onToolEnd() {
        super.onToolEnd();

        //If they started the tool but didn't draw anything
        if (_multiPolyline.isEmpty()) {
            //Remove the group as it is empty
            _mapGroup.removeItem(_multiPolyline);
        } else {
            //If they drew something with lines make it persist
            _multiPolyline.removeMetaData("creating");
            _multiPolyline.persist(_mapView.getMapEventDispatcher(), null,
                    this.getClass());
        }
        removeDuplicate();
        _drawingToolsToolbar.toggleShapeButtons(true);
        _toggleScrollZoomButton.setVisibility(Button.GONE);
        _undoButton.setVisibility(Button.GONE);
        _undoButton.setEnabled(false);
        _deleteTelestrationButton.setVisibility(Button.GONE);
        _telestrationColorButton.setVisibility(Button.GONE);
        _doneButton.setVisibility(Button.GONE);
        _telestrationButton.setVisibility(Button.VISIBLE);

        AtakBroadcast.getInstance().sendBroadcast(new Intent(
                ActionBarReceiver.REFRESH_ACTION_BAR));

        if (_menuShowing) {
            // Hide menu and pop listeners if necessary
            AtakBroadcast.getInstance().sendBroadcast(
                    new Intent(MapMenuReceiver.HIDE_MENU));
            if (_scrollLocked)
                _mapView.getMapEventDispatcher().popListeners();
            _menuShowing = false;
        } else
            _mapView.getMapEventDispatcher().popListeners();

        _spatialCalculator.clear();
        _container.closePrompt();
        undoStack.clear();

        //If the user was adding to an existing shape, unHide the details once they are done
        //drawing
        if (adding) {
            DropDownManager ddm = DropDownManager.getInstance();
            ddm.unHidePane();
            Intent intent = new Intent();
            intent.setAction(ToolbarBroadcastReceiver.SET_TOOLBAR);
            AtakBroadcast.getInstance().sendBroadcast(intent);

        }
    }

    @Override
    public boolean shouldEndOnBack() {
        return true;
    }

    public void undo() {
        if (undoStack.size() > 0) {
            _multiPolyline.removeItem(undoStack.pop());
        }
    }

    @Override
    protected void setActive(boolean active) {
        _telestrationButton.setSelected(active);
        super.setActive(active);
    }

    @Override
    protected void clearExtraListeners() {

        _mapView.getMapEventDispatcher().clearListeners(MapEvent.MAP_DRAW);
        _mapView.getMapEventDispatcher().clearListeners(MapEvent.MAP_SCROLL);
        _mapView.getMapEventDispatcher().clearListeners(MapEvent.MAP_MOVED);
        _mapView.getMapEventDispatcher().clearListeners(MapEvent.MAP_SCALE);

        super.clearExtraListeners();
    }

    private void updateColor(int c) {
        //wrap shape for the image button
        Shape rect = new RectShape();
        rect.resize(10, 10);
        ShapeDrawable color = new ShapeDrawable();
        color.setBounds(0, 0, 10, 10);
        color.setIntrinsicHeight(10);
        color.setIntrinsicWidth(10);
        color.setShape(rect);
        color.getPaint().setColor(c);

        _telestrationColorButton.setImageDrawable(color);
        _color = c;
    }

    /**
     * Performs button initialization, setting up onclick listeners etc. Overide if the default
     * behavior (start tool if it isn't started, stop if if it is) isn't sufficient.
     */
    protected void initButton() {

        //set on click listeners//
        updateColor(_color);

        _telestrationButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {

                requestBeginTool();
            }
        });

        _telestrationButton.setOnLongClickListener(onLongClickListener);

        _doneButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                requestEndTool();
            }
        });

        _deleteTelestrationButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {

                AlertDialog.Builder alert = new AlertDialog.Builder(_context);

                alert.setTitle(_context.getString(R.string.tele_delete_warn));
                alert.setMessage(_context
                        .getString(R.string.del_all_tele_confirm));

                alert.setNegativeButton(R.string.cancel, null);
                alert.setPositiveButton(R.string.confirm_btn,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {

                                Collection<MapItem> items = _mapGroup
                                        .getItems();

                                for (MapItem item : items) {
                                    if (item.getType() != null
                                            && item.getType()
                                                    .equals("u-d-f-m")) {
                                        _mapGroup.removeItem(_multiPolyline);
                                        _multiPolyline
                                                .setLines(
                                                        new ArrayList<DrawingShape>());
                                    }
                                }
                                _mapGroup.removeItem(_duplicateShape);
                            }
                        });
                alert.show();
            }
        });

        _telestrationColorButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {

                //wrap color palette alert dialogue
                AlertDialog.Builder b = new AlertDialog.Builder(_context)
                        .setTitle(_context
                                .getString(R.string.choose_tele_color));

                Drawable drawable = _telestrationColorButton.getDrawable();
                ShapeDrawable shapeDrawable = (ShapeDrawable) drawable;
                ColorPalette palette = new ColorPalette(_context, shapeDrawable
                        .getPaint().getColor());
                b.setView(palette);
                final AlertDialog alert = b.create();

                OnColorSelectedListener l = new OnColorSelectedListener() {
                    @Override
                    public void onColorSelected(int color, String label) {

                        alert.cancel();
                        _prefs.setShapeColor(color);
                        updateColor(color);
                        _telestrationColorButton.invalidate();
                    }
                };
                palette.setOnColorSelectedListener(l);
                alert.show();
            }
        });

        _toggleScrollZoomButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                if (_scrollLocked) {

                    _toggleScrollZoomButton.setSelected(false);

                    _container.displayPrompt(SCROLL_UNLOCKED);

                    _mapView.getMapEventDispatcher().popListeners();

                    clearListeners();

                    _scrollLocked = false;
                } else {

                    _toggleScrollZoomButton.setSelected(true);

                    _container.displayPrompt("      " + SCROLL_LOCKED);

                    //Pushes the listeners so we can catch the events first
                    _mapView.getMapEventDispatcher().pushListeners();

                    clearExtraListeners();

                    registerListeners();
                    _scrollLocked = true;
                }
            }
        });
    }

    View.OnLongClickListener onLongClickListener = new View.OnLongClickListener() {
        @Override
        public boolean onLongClick(View view) {
            Toast.makeText(_mapView.getContext(), R.string.telestrate_tip,
                    Toast.LENGTH_SHORT).show();
            return true;
        }
    };

    /**
     * Register the overridden listeners we are interested in
     */
    private void registerListeners() {
        //initial touch event
        _mapView.getMapEventDispatcher()
                .addMapEventListener(MapEvent.MAP_PRESS, pressListener);

        //Draw points after initial touch
        _mapView.getMapEventDispatcher()
                .addMapEventListener(MapEvent.MAP_DRAW, dragListener);

        //Block scroll event
        _mapView.getMapEventDispatcher()
                .addMapEventListener(MapEvent.MAP_SCROLL, scrollListener);

        //Final point and release event
        _mapView.getMapEventDispatcher()
                .addMapEventListener(MapEvent.MAP_RELEASE, releaseListener);
    }

    /**
     * clear out our listeners we no longer are interested in
     */
    private void clearListeners() {
        _mapView.getMapEventDispatcher().clearListeners(MapEvent.MAP_PRESS);
        _mapView.getMapEventDispatcher().clearListeners(MapEvent.MAP_RELEASE);
    }

    private void removeDuplicate() {
        if (_duplicateShape != null)
            // In case user taps the screen (release not called)
            _duplicateShape.removeFromGroup();
    }

    private boolean _menuShowing = false;
    private final BroadcastReceiver _menuReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null)
                return;

            if (action.equals(MapMenuReceiver.SHOW_MENU))
                _menuShowing = true;
            else if (action.equals(MapMenuReceiver.HIDE_MENU))
                _menuShowing = false;
        }
    };
}
