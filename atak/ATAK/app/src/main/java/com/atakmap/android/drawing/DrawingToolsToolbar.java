
package com.atakmap.android.drawing;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageButton;

import com.atakmap.android.drawing.tools.DrawingCircleEditTool;
import com.atakmap.android.drawing.tools.DrawingRectangleCreationTool;
import com.atakmap.android.drawing.tools.DrawingRectangleEditTool;
import com.atakmap.android.drawing.tools.ShapeCreationTool;
import com.atakmap.android.drawing.tools.ShapeEditTool;
import com.atakmap.android.drawing.tools.TelestrationTool;
import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.toolbar.IToolbarExtension;
import com.atakmap.android.toolbar.Tool;
import com.atakmap.android.toolbar.ToolbarBroadcastReceiver;
import com.atakmap.android.tools.ActionBarView;
import com.atakmap.android.tools.CircleCreationButtonTool;
import com.atakmap.android.widgets.TextWidget;
import com.atakmap.app.R;

import java.util.ArrayList;
import java.util.List;

public class DrawingToolsToolbar extends DropDownReceiver implements
        IToolbarExtension {

    public static final String TAG = "DrawingToolsToolbar";

    public static final String TOOLBAR_IDENTIFIER = "com.atakmap.android.drawing.DRAWING_TOOLS";

    protected TextWidget _text;
    private final Context _context;
    private final MapGroup _drawingGroup;
    private final DrawingToolsMapReceiver _drawingMapReceiver;
    private final SharedPreferences _prefs;

    private List<Tool> _tools = null;
    private ShapeCreationTool _shapeTool;
    private CircleCreationButtonTool _circleCreationTool;
    private DrawingRectangleCreationTool _rectangleCreationTool;
    private ShapeEditTool _shapeEditTool;
    private DrawingRectangleEditTool _rectEditTool;
    private DrawingCircleEditTool _circleEditTool;
    private TelestrationTool _telestrationTool;

    private final ImageButton _createCircleButton;
    private final ImageButton _createRectangleButton;
    private final ImageButton _createShapeButton;
    private final Button _editShapeButton;
    private final Button _shapeDoneButton;
    private final ImageButton _telestrationButton;
    private final Button _doneButton;
    private final ImageButton _toggleScrollZoomButton;
    private final Button _undoButton;
    private final Button _deleteTelestrationButton;
    private final ImageButton _telestrationColorButton;

    private final ActionBarView _layout;

    public DrawingToolsToolbar(MapView mapView,
            MapGroup drawingGroup,
            Context context,
            DrawingToolsMapReceiver RouteMapReceiver) {
        super(mapView);
        _context = mapView.getContext();

        _drawingGroup = drawingGroup;

        _drawingMapReceiver = RouteMapReceiver;

        _prefs = PreferenceManager.getDefaultSharedPreferences(_context);

        final LayoutInflater inflater = LayoutInflater.from(context);
        _layout = (ActionBarView) inflater.inflate(
                R.layout.drawing_toolbar_view, mapView,
                false);

        _undoButton = _layout.findViewById(R.id.undoButton);
        _undoButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {

                if (_shapeEditTool.getActive()) {
                    _shapeEditTool.undo();
                } else if (_shapeTool.getActive()) {
                    _shapeTool.undo();
                } else if (_rectEditTool.getActive()) {
                    _rectEditTool.undo();
                } else if (_circleEditTool.getActive()) {
                    _circleEditTool.undo();
                } else if (_telestrationTool.getActive()) {
                    _telestrationTool.undo();
                }
            }
        });

        _editShapeButton = _layout.findViewById(R.id.editShapeButton);
        _createCircleButton = _layout
                .findViewById(R.id.newCircleButton);
        _createRectangleButton = _layout
                .findViewById(R.id.newRectangleButton);
        _createShapeButton = _layout
                .findViewById(R.id.newShapeButton);
        _telestrationButton = _layout
                .findViewById(R.id.telestration);
        _doneButton = _layout.findViewById(R.id.doneButton);
        _shapeDoneButton = _layout.findViewById(R.id.doneDrawing);

        _deleteTelestrationButton = _layout
                .findViewById(R.id.deleteTelestrationButton);
        _telestrationColorButton = _layout
                .findViewById(R.id.telestrationColorButton);
        _toggleScrollZoomButton = _layout
                .findViewById(R.id.toggleScrollButton);
        ToolbarBroadcastReceiver.getInstance().registerToolbarComponent(
                TOOLBAR_IDENTIFIER, this);

    }

    @Override
    public void disposeImpl() {

    }

    @Override
    public void onReceive(Context context, Intent intent) {
    }

    @Override
    public List<Tool> getTools() {
        if (_tools == null) {
            _tools = new ArrayList<>();

            _circleCreationTool = new CircleCreationButtonTool(getMapView(),
                    _createCircleButton,
                    _drawingGroup);
            _rectangleCreationTool = new DrawingRectangleCreationTool(
                    getMapView(),
                    _drawingGroup,
                    _createRectangleButton);
            _shapeTool = new ShapeCreationTool(getMapView(),
                    _drawingGroup,
                    _createShapeButton,
                    this,
                    _undoButton,
                    _shapeDoneButton,
                    _context);
            _shapeEditTool = new ShapeEditTool(getMapView(), _editShapeButton,
                    _undoButton, this,
                    _drawingMapReceiver);
            _rectEditTool = new DrawingRectangleEditTool(getMapView(),
                    _editShapeButton, _undoButton,
                    this);
            _circleEditTool = new DrawingCircleEditTool(getMapView(),
                    _editShapeButton, _undoButton,
                    this);

            _telestrationTool = new TelestrationTool(getMapView(),
                    _drawingGroup, _telestrationButton, this,
                    _toggleScrollZoomButton, _undoButton,
                    _doneButton, _deleteTelestrationButton,
                    _telestrationColorButton, _context);

            // TODO: should this class listen to the tools to find out when they're active and thus
            // modify the toolbar?
            // except if that doesn't happen before tool sets it's own button's visibility, it's
            // button might get hidden too...
            // and before the beginTool ends, we don't know if the tool really began. kind of weird
            // architecture either way

            _tools.add(_shapeTool);
            _tools.add(_circleCreationTool);
            _tools.add(_rectangleCreationTool);
            _tools.add(_shapeEditTool);
            _tools.add(_rectEditTool);
            _tools.add(_telestrationTool);
            _tools.add(_circleEditTool);

        }
        return _tools;
    }

    public void setDefaultButtonsVisiblity(int vis) {
        _createCircleButton.setVisibility(vis);
        _createRectangleButton.setVisibility(vis);
        _createShapeButton.setVisibility(vis);
        _telestrationButton.setVisibility(vis);
        // Anytime we change visibility we potentially affect the size of the
        // toolbar view - need to invalidate the action bar so it fits
        ((Activity) _context).invalidateOptionsMenu();
    }

    @Override
    public ActionBarView getToolbarView() {
        return _layout;
    }

    @Override
    public boolean hasToolbar() {
        return true;
    }

    @Override
    public void onToolbarVisible(final boolean vis) {
    }

}
