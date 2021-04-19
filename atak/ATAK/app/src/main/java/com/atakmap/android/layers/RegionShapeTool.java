
package com.atakmap.android.layers;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.atakmap.android.drawing.DrawingPreferences;
import com.atakmap.android.drawing.DrawingToolsMapComponent;
import com.atakmap.android.drawing.mapItems.DrawingRectangle;
import com.atakmap.android.drawing.mapItems.DrawingShape;
import com.atakmap.android.dropdown.DropDownManager;
import com.atakmap.android.gui.RangeEntryDialog;
import com.atakmap.android.gui.TileButtonDialog;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.Shape;
import com.atakmap.android.routes.Route;
import com.atakmap.android.toolbar.Tool;
import com.atakmap.android.toolbar.ToolManagerBroadcastReceiver;
import com.atakmap.android.toolbar.widgets.TextContainer;
import com.atakmap.android.tools.ActionBarReceiver;
import com.atakmap.android.tools.ActionBarView;
import com.atakmap.android.util.EditAction;
import com.atakmap.android.util.Undoable;
import com.atakmap.app.R;
import com.atakmap.coremap.conversions.Span;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

import java.util.Stack;
import java.util.UUID;

/**
 * Tool for the creation of a rectangular map region.
 *
 * Once the user has finished creating a shape with the tool, returns the uid
 * of the newly created Shape in an intent specified by the user by setting
 * the "callback" parcelable when starting the tool.   This is an 
 * intent to fire when the tool is completed.
 */
public class RegionShapeTool extends Tool implements Undoable,
        MapEventDispatcher.MapEventDispatchListener,
        View.OnClickListener, Dialog.OnClickListener {

    private static final String TAG = "RegionShapeTool";

    public static final String TOOL_ID = "com.atakmap.android.layers.RegionShapeTool";

    public enum Mode {
        RECTANGLE(R.drawable.rectangle, R.string.rectangle, R.string.select_area_prompt),
        FREE_FORM(R.drawable.sse_shape, R.string.free_form, R.string.polygon_area_prompt),
        LASSO(R.drawable.ic_lasso, R.string.lasso, R.string.lasso_area_prompt),
        SELECT(R.drawable.select_from_map, R.string.map_select, R.string.map_item_select_prompt);

        final int iconId, titleId, promptId;

        Mode(int iconId, int titleId, int promptId) {
            this.iconId = iconId;
            this.titleId = titleId;
            this.promptId = promptId;
        }

        static Mode findByName(String name) {
            name = name.toUpperCase(LocaleUtil.getCurrent());
            for (Mode m : values()) {
                String n = m.name().replace("_", "");
                if (n.equals(name))
                    return m;
            }
            return null;
        }
    }

    private final MapView _mapView;
    private final MapEventDispatcher _evtDisp;
    protected final Context _context;
    private final DrawingPreferences _prefs;
    private final ActionBarView _toolbar;
    private final Button _undoButton, _endButton;
    private final Stack<EditAction> _undoStack = new Stack<>();
    protected MapGroup _drawingGroup;
    private Mode _mode;
    private Intent _callback;
    private Shape _shape;
    private DrawingShape _drawShape;
    private Marker _firstPoint;
    private double _expandDistance;
    protected DrawingRectangle.Builder _builder;
    private TileButtonDialog _dialog;

    public RegionShapeTool(MapView mapView) {
        super(mapView, TOOL_ID);
        _mapView = mapView;
        _evtDisp = mapView.getMapEventDispatcher();
        _context = mapView.getContext();
        _prefs = new DrawingPreferences(mapView);
        _toolbar = (ActionBarView) LayoutInflater.from(_context).inflate(
                R.layout.drawing_toolbar_view, _mapView, false);
        for (int i = 0; i < _toolbar.getChildCount(); i++)
            _toolbar.getChildAt(i).setVisibility(View.GONE);
        _undoButton = _toolbar.findViewById(R.id.undoButton);
        _undoButton.setVisibility(View.VISIBLE);
        _undoButton.setOnClickListener(this);
        _endButton = _toolbar.findViewById(R.id.doneButton);
        _endButton.setVisibility(View.VISIBLE);
        _endButton.setOnClickListener(this);
        ToolManagerBroadcastReceiver.getInstance().registerTool(TOOL_ID, this);
    }

    @Override
    public void dispose() {
        ToolManagerBroadcastReceiver.getInstance().unregisterTool(TOOL_ID);
    }

    @Override
    protected boolean onToolBegin(Bundle extras) {
        _drawingGroup = DrawingToolsMapComponent.getGroup();

        // Legacy mode select
        if (extras.get("freeform") != null)
            _mode = extras.getBoolean("freeform", false)
                    ? Mode.FREE_FORM : Mode.RECTANGLE;

        // Drawing mode
        Object mode = extras.get("mode");
        if (mode instanceof Mode)
            _mode = (Mode) mode;
        else if (mode instanceof String) {
            _mode = Mode.findByName((String) mode);
            if (_mode == null)
                Log.e(TAG, "Invalid mode: " + mode);
        }

        _callback = extras.getParcelable("callback");
        _builder = null;
        _shape = null;
        _drawShape = null;
        _expandDistance = 0;

        // Prompt to select mode
        if (_mode == null) {
            if (_dialog != null)
                _dialog.dismiss();
            _dialog = new TileButtonDialog(_mapView);
            for (Mode m : Mode.values())
                _dialog.addButton(m.iconId, m.titleId);
            _dialog.setOnClickListener(this);
            _dialog.show(R.string.select_area, true);
            return false;
        }

        _evtDisp.pushListeners();
        _evtDisp.clearUserInteractionListeners(_mode == Mode.LASSO);
        if (_mode == Mode.LASSO) {
            _evtDisp.addMapEventListener(MapEvent.MAP_DRAW, this);
            _evtDisp.addMapEventListener(MapEvent.MAP_RELEASE, this);
            _evtDisp.addMapEventListener(MapEvent.ITEM_DRAG_STARTED, this);
            _evtDisp.addMapEventListener(MapEvent.ITEM_DRAG_CONTINUED, this);
            _evtDisp.addMapEventListener(MapEvent.ITEM_DRAG_DROPPED, this);
        } else {
            _evtDisp.addMapEventListener(MapEvent.ITEM_CLICK, this);
            _evtDisp.addMapEventListener(MapEvent.MAP_CLICK, this);
        }
        _mapView.getMapTouchController().skipDeconfliction(true);

        _undoStack.clear();
        _undoButton.setEnabled(false);
        ActionBarReceiver.getInstance().setToolView(_toolbar);
        TextContainer.getInstance().displayPrompt(_context.getString(
                _mode.promptId));
        DropDownManager.getInstance().hidePane();

        return super.onToolBegin(extras);
    }

    @Override
    protected void onToolEnd() {
        if (_drawShape != null) {
            _drawShape.removeMarker(_firstPoint);
            if (_firstPoint != null)
                _drawingGroup.removeItem(_firstPoint);
        } else if (_builder != null) {
            try {
                _drawingGroup.addItem(_shape = _builder.build());
                flagTempShape();
            } catch (IndexOutOfBoundsException eb) {
                Log.e(TAG,
                        "rectangle is not defined correctly, builder is not valid");
                _builder.dispose();
                if (_firstPoint != null) {
                    MapGroup mg = _firstPoint.getGroup();
                    if (mg != null)
                        mg.removeItem(_firstPoint);

                }
            }
        }

        if (_dialog != null) {
            _dialog.dismiss();
            _dialog = null;
        }

        _mode = null;
        TextContainer.getInstance().closePrompt();
        _evtDisp.popListeners();
        _mapView.getMapTouchController().skipDeconfliction(false);
        _undoStack.clear();
        _undoButton.setEnabled(false);
        DropDownManager.getInstance().unHidePane();
        ActionBarReceiver.getInstance().setToolView(null, false);
        super.onToolEnd();

        // Prompt to set route expansion distance
        if (_shape instanceof Route || _drawShape != null
                && !_drawShape.isClosed()) {
            RangeEntryDialog d = new RangeEntryDialog(_mapView);
            d.show(R.string.route_download_range, 100, Span.METER,
                    new RangeEntryDialog.Callback() {
                        @Override
                        public void onSetValue(double valueM, Span unit) {
                            _expandDistance = valueM;
                            sendCallbackIntent();
                        }
                    });
        } else
            sendCallbackIntent();
    }

    private void sendCallbackIntent() {
        if (_callback == null)
            return;
        Intent i = new Intent(_callback);
        if (_shape != null)
            i.putExtra("uid", _shape.getUID());
        if (_expandDistance > 0)
            i.putExtra("expandDistance", _expandDistance);
        AtakBroadcast.getInstance().sendBroadcast(i);
    }

    private void flagTempShape() {
        if (_shape != null) {
            _shape.setMetaBoolean("addToObjList", false);
            _shape.setMetaBoolean("layerDownload", true);
            _shape.setMetaBoolean("nevercot", true);
        }
    }

    @Override
    public void onClick(View v) {
        if (v == _undoButton)
            undo();
        else if (v == _endButton)
            requestEndTool();
    }

    @Override
    public void onClick(DialogInterface d, int w) {
        Mode[] modes = Mode.values();
        if (w < 0 || w >= modes.length)
            return;

        Bundle b = new Bundle();
        b.putSerializable("mode", modes[w]);
        if (_callback != null)
            b.putParcelable("callback", _callback);
        ToolManagerBroadcastReceiver.getInstance().startTool(TOOL_ID, b);
    }

    @Override
    public boolean run(EditAction action) {
        final boolean active = getActive();
        if (active) {
            synchronized (_undoStack) {
                boolean success = action.run();
                if (success) {
                    _undoStack.push(action);
                    if (_undoButton != null)
                        _undoButton.setEnabled(true);
                }
                return success;
            }
        } else {
            return false;
        }
    }

    @Override
    public void undo() {
        final boolean active = getActive();
        if (active) {
            synchronized (_undoStack) {
                // pop of editaction from top of stack and undo it
                if (_undoStack.size() > 0) {
                    _undoStack.pop().undo();
                }
                if (_undoStack.size() == 0) {
                    // disable the undobutton if the stack is empty
                    if (_undoButton != null)
                        _undoButton.setEnabled(false);
                }
            }
        }
    }

    @Override
    public void onMapEvent(MapEvent event) {
        String e = event.getType();
        MapItem item = event.getItem();
        GeoPointMetaData point = findPoint(event);

        if (point == null)
            return;

        // Select map item mode
        if (_mode == Mode.SELECT) {
            if (!e.equals(MapEvent.ITEM_CLICK))
                return;
            if (!(item instanceof Shape)) {
                Toast.makeText(_context, R.string.cannot_select_item,
                        Toast.LENGTH_SHORT).show();
                return;
            }
            _shape = (Shape) item;
            requestEndTool();
        }

        // Free-form selection
        else if (_mode == Mode.FREE_FORM) {
            if (_drawShape != null && _firstPoint != null
                    && item == _firstPoint) {
                // Close shape
                closeShape();
                requestEndTool();
            } else
                run(new AddPointAction(point));
        }

        // Rectangle selection
        else if (_mode == Mode.RECTANGLE) {
            if (_builder == null) {
                String name = _context.getString(R.string.select_area);
                MapGroup grp = _drawingGroup.addGroup(name);
                grp.setMetaBoolean("addToObjList", false);
                _builder = new DrawingRectangle.Builder(grp,
                        DrawingRectangle.Builder.Mode.START_END_CORNERS);
                _builder.setFirstPoint(point);
                TextContainer.getInstance().displayPrompt(_context.getString(
                        R.string.select_area_prompt2));
            } else {
                _builder.setSecondPoint(point);
                requestEndTool();
            }
        }

        // Lasso
        else if (_mode == Mode.LASSO) {

            // Start/add to lasso
            if (e.equals(MapEvent.MAP_DRAW)
                    || e.equals(MapEvent.ITEM_DRAG_STARTED)
                    || e.equals(MapEvent.ITEM_DRAG_CONTINUED))
                addPointToShape(point);
            else if (_drawShape != null) {
                closeShape();
                requestEndTool();
            }
        }
    }

    private void addPointToShape(GeoPointMetaData point) {
        if (_drawShape == null) {
            _shape = _drawShape = new DrawingShape(_mapView, _drawingGroup,
                    UUID.randomUUID().toString());
            flagTempShape();
            _drawShape.addPoint(point);
            _firstPoint = new Marker(point, UUID.randomUUID().toString());
            _firstPoint.setType("shape_marker");
            _firstPoint.setMetaBoolean("addToObjList", false);
            _firstPoint.setMetaBoolean("nevercot", true);
            _drawShape.setMarker(0, _firstPoint);
            _drawShape.setColor(_prefs.getShapeColor());
            _drawShape.setStrokeWeight(_prefs.getStrokeWeight());
            _drawingGroup.addItem(_firstPoint);
            _drawingGroup.addItem(_drawShape);
        } else
            _drawShape.addPoint(point);
    }

    private void closeShape() {
        if (_drawShape.getNumPoints() < 3)
            return;
        _drawShape.setClosed(true);
        int c = _drawShape.getStrokeColor();
        int alpha = _prefs.getFillAlpha();
        _drawShape.setColor(c);
        _drawShape.setFillColor(Color.argb(alpha, Color.red(c),
                Color.green(c), Color.blue(c)));
    }

    private class AddPointAction extends EditAction {

        private final GeoPointMetaData _point;

        private AddPointAction(GeoPointMetaData gp) {
            _point = gp;
        }

        @Override
        public boolean run() {
            addPointToShape(_point);
            return true;
        }

        @Override
        public void undo() {
            if (_drawShape == null || _firstPoint == null)
                return;
            int numPoints = _drawShape.getNumPoints();
            if (numPoints > 1)
                _drawShape.removePoint(numPoints - 1);
            else {
                if (_drawShape != null)
                    _drawingGroup.removeItem(_drawShape);
                if (_firstPoint != null)
                    _drawingGroup.removeItem(_firstPoint);
                _drawShape = null;
                _firstPoint = null;
            }
        }

        @Override
        public String getDescription() {
            return "Undo a step in the creation process";
        }
    }
}
