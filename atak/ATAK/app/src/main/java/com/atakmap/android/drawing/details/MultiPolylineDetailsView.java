
package com.atakmap.android.drawing.details;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.ShapeDrawable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.ToggleButton;
import android.widget.ImageButton;
import android.widget.SeekBar;

import com.atakmap.android.cot.exporter.DispatchMapItemTask;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.util.AfterTextChangedWatcher;
import android.text.Editable;

import com.atakmap.android.util.SimpleSeekBarChangeListener;
import com.atakmap.android.drawing.DrawingToolsToolbar;
import com.atakmap.android.drawing.mapItems.DrawingShape;
import com.atakmap.android.drawing.tools.TelestrationTool;
import com.atakmap.android.dropdown.DropDownManager;
import com.atakmap.android.gui.RangeAndBearingTableHandler;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.MultiPolyline;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.toolbar.ToolManagerBroadcastReceiver;
import com.atakmap.android.toolbar.ToolbarBroadcastReceiver;
import com.atakmap.android.toolbar.widgets.TextContainer;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.app.R;
import com.atakmap.coremap.conversions.Span;
import com.atakmap.coremap.conversions.SpanUtilities;
import com.atakmap.android.util.AttachmentManager;

public class MultiPolylineDetailsView extends GenericDetailsView implements
        MapEventDispatcher.MapEventDispatchListener {

    private AttachmentManager attachmentManager;
    private ImageButton _attachmentButton;
    private ToggleButton _multiPolylineColor;
    private ToggleButton _deleteLines;
    private int _color;
    private final Context _context;
    private boolean _listeningToMap = false;
    private MultiPolyline _shape;

    public MultiPolylineDetailsView(Context context) {
        super(context);
        _context = context;
    }

    public MultiPolylineDetailsView(Context context, final AttributeSet inAtr) {
        super(context, inAtr);
        _context = context;
    }

    @Override
    public boolean setItem(MapView mapView, MapItem item) {
        if (!(item instanceof DrawingShape))
            return false;
        super.setItem(mapView, item);
        _shape = (MultiPolyline) item;
        _color = _shape.getColor();
        _init();
        return true;
    }

    /**
     * Callback for when the Dropdown closes. Updates the multi-polyline meta data if something has
     * changed.
     */
    @Override
    public void onClose() {

        super.onClose();

        _onDoneDeleting();
        _onDoneColoring();
        _onDoneAdding();
        attachmentManager.cleanup();
        // Update the name if the user changed it.
        String name = _nameEdit.getText().toString();
        if (!name.equals(_prevName)) {
            _shape.setTitle(name);
        }

        // Update the remarks if the user changed them.
        String remarks = _remarksLayout.getText();
        if (!remarks.equals(_prevRemarks)) {
            _shape.setMetaString("remarks", remarks);
        }
    }

    /**
     * *************************** PRIVATE METHODS ***************************
     */

    private void _init() {

        Intent showDetails = new Intent();
        showDetails.setAction("com.atakmap.android.maps.SHOW_DETAILS");
        showDetails.putExtra("uid", _shape.getUID());
        AtakBroadcast.getInstance().sendBroadcast(showDetails);
        //Gather all of our stuff from the dispaly that we will work with
        _nameEdit = this.findViewById(R.id.drawingShapeNameEdit);
        _thickSeek = this
                .findViewById(R.id.drawingMultiStrokeSeek);
        _remarksLayout = this.findViewById(R.id.remarksLayout);
        _remarksLayout.addTextChangedListener(new AfterTextChangedWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (_shape != null) {
                    String remarks = _remarksLayout.getText();
                    if (!remarks.equals(_prevRemarks)) {
                        _shape.setMetaString("remarks", remarks);
                    }
                }
            }
        });

        _noGps = this.findViewById(R.id.drawingShapeRangeBearingNoGps);
        rabtable = new RangeAndBearingTableHandler(this);
        _centerButton = this
                .findViewById(R.id.drawingShapeCenterButton);
        _centerButton.setEnabled(false);
        _heightButton = this
                .findViewById(R.id.drawingShapeHeightButton);
        _colorButton = this
                .findViewById(R.id.drawingShapeColorButton);
        /*
        *************************** PRIVATE FIELDS ***************************
        */
        ImageButton _sendButton = this
                .findViewById(R.id.drawingShapeSendButton);

        _multiPolylineColor = this
                .findViewById(R.id.multipolylineColorButton);
        _deleteLines = this.findViewById(R.id.deleteLines);
        Button _addLines = this.findViewById(R.id.addLines);
        _deleteLines.setChecked(false);
        // Save an instance of the name, so we know if it changed when the dropdown closes
        _prevName = _shape.getTitle();
        _nameEdit.setText(_prevName);

        _nameEdit.addTextChangedListener(new AfterTextChangedWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (_shape != null) {
                    String name = _nameEdit.getText().toString();
                    _shape.setTitle(name);
                    _shape.refresh(MapView.getMapView()
                            .getMapEventDispatcher(), null, this.getClass());
                }
            }
        });

        // Save an instance of the remarks, so we know if they changed when the dropdown closes
        _prevRemarks = _shape.getMetaString("remarks", "");
        _remarksLayout.setText(_prevRemarks);

        // Update the R & B text
        PointMapItem device = ATAKUtilities.findSelf(_mapView);
        // It's possible that we don't have GPS and therefore don't have a controller point
        if (device != null) {
            _noGps.setVisibility(View.GONE);
            rabtable.setVisibility(View.VISIBLE);
            rabtable.update(device, _shape.getCenter().get());
        } else {
            _noGps.setVisibility(View.VISIBLE);
            rabtable.setVisibility(View.GONE);
        }

        // keep the calculations and processes in the detail page, but disable the 
        // view for 3.2 as per JS.   Maybe this becomes a preference.
        _noGps.setVisibility(View.GONE);
        rabtable.setVisibility(View.GONE);

        _centerButton.setText(_unitPrefs.formatPoint(_shape.getCenter(),
                true));

        double height = _shape.getHeight();
        Span unit = _unitPrefs.getAltitudeUnits();
        if (!Double.isNaN(height)) {
            _heightButton.setText(SpanUtilities.format(height, Span.METER,
                    unit));
        } else {
            _heightButton.setText("-- " + unit.getAbbrev());
        }

        //Listener for the height button
        _heightButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                //Make sure we stop coloring/deleting
                _onDoneDeleting();
                _onDoneColoring();
                _onHeightSelected();
            }
        });

        _thickSeek.setProgress((int) (_shape.getStrokeWeight() * 10) - 10);

        _thickSeek.setOnSeekBarChangeListener(
                new SimpleSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar,
                            int progress, boolean fromUser) {
                        double strokeWeight = 1 + (progress / 10d);
                        _shape.setStrokeWeight(strokeWeight);
                        _drawPrefs.setStrokeWeight(strokeWeight);
                    }
                });

        // Listener for the add lines button
        _addLines.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {

                _onDoneDeleting();
                _onDoneColoring();

                DropDownManager ddm = DropDownManager.getInstance();
                ddm.hidePane();
                //Send intent to start the telestration tool and drawing tools toolbar
                Intent intent = new Intent();
                intent.setAction(ToolbarBroadcastReceiver.OPEN_TOOLBAR);
                intent.putExtra("toolbar",
                        DrawingToolsToolbar.TOOLBAR_IDENTIFIER);
                AtakBroadcast.getInstance().sendBroadcast(intent);

                intent = new Intent();
                intent.setAction(ToolManagerBroadcastReceiver.BEGIN_TOOL);
                intent.putExtra("tool", TelestrationTool.TOOL_IDENTIFIER);
                intent.putExtra("uid", _shape.getUID());
                intent.putExtra("ignoreToolbar", false);
                intent.putExtra("adding", _shape.getUID());
                AtakBroadcast.getInstance().sendBroadcast(intent);

            }
        });

        //Listener for the delete button
        _deleteLines.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {

                //This seems really backward but hey... it works
                if (_deleteLines.isChecked()) {
                    _onDoneAdding();
                    _onDoneColoring();
                    _deleteLines.setText(R.string.done_deleting);
                    //if it is checked set checked?
                    _deleteLines.setChecked(true);
                    _onDeleteSelected();

                } else {
                    _onDoneDeleting();
                    _onDoneColoring();
                    _onDoneAdding();
                    _deleteLines.setText(R.string.delete_lines);
                    _deleteLines.setChecked(false);

                }
            }
        });

        //Not sure why we do this here
        _updateColorButtonDrawable();

        //Set visibility of things correctly
        _colorButton.setVisibility(GONE);
        _multiPolylineColor.setVisibility(VISIBLE);
        _deleteLines.setVisibility(VISIBLE);

        //On click listener for the little rectangle that holds the selected color
        _colorButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                _onColorSelected();
            }
        });

        //Listener for the "Change Color" button
        _multiPolylineColor.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {

                //Weird way to do this based on text but it works
                if (_multiPolylineColor.getText().equals(
                        _context.getString(R.string.donecoloring))) {

                    //Remove all of our custom listeners
                    _onDoneColoring();
                    _onDoneDeleting();
                    _onDoneAdding();
                    //Set the button to the proper states
                    _multiPolylineColor.setText(R.string.change_line_color);
                    _multiPolylineColor.setChecked(false);
                    _colorButton.setVisibility(GONE);
                } else {

                    //Make sure we don't have delete and color listeners at the same time
                    _onDoneDeleting();
                    _onDoneAdding();

                    //Set the correct states
                    _multiPolylineColor.setText(R.string.donecoloring);
                    _multiPolylineColor.setChecked(true);
                    _colorButton.setVisibility(VISIBLE);

                    //Call functions to prompt the user for a color and listen for map presses
                    _onMPColorSelected();
                    _onColorSelected();
                }
            }
        });

        //Send button listeners
        _sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                //make sure we remove our custom listeners
                _onDoneColoring();
                _onDoneDeleting();
                _onDoneAdding();
                //Call the send function
                _onDoneDeleting();
                _onDoneColoring();
                sendSelected(_shape.getUID());
            }
        });

        _attachmentButton = this
                .findViewById(R.id.cotInfoAttachmentsButton);

        if (attachmentManager == null)
            attachmentManager = new AttachmentManager(_mapView,
                    _attachmentButton);
        attachmentManager.setMapItem(_shape);
    }

    //Called when the user picks a color from the pallet,
    //updates the button the user sees with the appropriate color
    private void _updateColorButtonDrawable() {
        final ShapeDrawable color = super.updateColorButtonDrawable();
        color.getPaint().setColor(_color);

        post(new Runnable() {
            @Override
            public void run() {
                _colorButton.setImageDrawable(color);
            }
        });

    }

    // show a dialog view that allows the user to select buttons that have the
    // colors overlayed on them
    @Override
    protected void _onColorSelected(int color, String label) {
        _drawPrefs.setShapeColor(color);

        _color = color;
        _updateColorButtonDrawable();
    }

    //Called when the user presses the button to change line colors
    protected void _onMPColorSelected() {
        if (!_listeningToMap) {

            //Prompt the user so they know what is going on
            TextContainer.getTopInstance().displayPrompt(
                    _context.getString(R.string.multi_polyline_hint));

            //Keep old listeners and remove them
            _mapView.getMapEventDispatcher().pushListeners();
            _clearExtraListeners();
            //Add out own listener
            _mapView.getMapEventDispatcher().addMapEventListener(
                    MapEvent.ITEM_CLICK, colorListener);
            _listeningToMap = true;
        }
    }

    //Once the user selects done coloring
    protected void _onDoneColoring() {

        //Set some states
        _colorButton.setVisibility(GONE);
        if (_multiPolylineColor.isChecked()) {
            _multiPolylineColor.setChecked(false);
            _multiPolylineColor.setText(R.string.change_line_color);
        }

        //Remove our custom map listener
        _requestRemoveMapListener();
    }

    protected void _onDoneAdding() {

        Intent intent = new Intent();
        intent.setAction(ToolManagerBroadcastReceiver.END_TOOL);
        intent.putExtra("tool", TelestrationTool.TOOL_IDENTIFIER);
        AtakBroadcast.getInstance().sendBroadcast(intent);

        DropDownManager ddm = DropDownManager.getInstance();
        ddm.unHidePane();
    }

    /**
     * Listener for when the user presses the delete button
     * looks for a map press and process it
     */

    @Override
    public void onMapEvent(MapEvent event) {

        //Capture the event
        if (event.getType().equals(MapEvent.ITEM_CLICK)) {

            MapItem item = event.getItem();
            MapItem parent = ATAKUtilities.findAssocShape(item);
            if (item instanceof DrawingShape && parent == _shape) {

                //Remove the item that the user pressed
                _shape.removeLine((DrawingShape) item);
                if (_shape.isEmpty()) {
                    //The user deleted the last line, remove the entire group
                    _shape.removeFromGroup();
                    _onDoneDeleting();

                    //Close the dropdown if the last line is removed
                    Intent intent = new Intent();
                    intent.setAction(DropDownManager.CLOSE_DROPDOWN);
                    AtakBroadcast.getInstance().sendBroadcast(
                            intent);
                }
            }
        }

    }

    /**
     * Listener for when the user begins color changing mode
     * Listens for an item press and than changes that items color
     */
    final MapEventDispatcher.MapEventDispatchListener colorListener = new MapEventDispatcher.MapEventDispatchListener() {
        @Override
        public void onMapEvent(MapEvent event) {
            MapItem item = event.getItem();
            MapItem parent = ATAKUtilities.findAssocShape(item);
            if (item instanceof DrawingShape && parent == _shape) {
                DrawingShape ds = (DrawingShape) item;
                ds.setColor(_color);
            }
        }
    };

    /**
     * If the user selects the delete button, add our custom listener and prompt the user
     */
    protected void _onDeleteSelected() {
        if (!_listeningToMap) {

            //Prompt the user so they know what they need to do
            TextContainer.getTopInstance().displayPrompt(
                    "Tap individual lines to delete them");

            //Remember old listeners and clear them
            _mapView.getMapEventDispatcher().pushListeners();
            _clearExtraListeners();

            //Add our listener
            _mapView.getMapEventDispatcher()
                    .addMapEventListener(this);
            _listeningToMap = true;
        }
    }

    /**
     * Once the user is done deleting. Put all the listeners back to normal
     */
    protected void _onDoneDeleting() {

        //Set states of the button
        if (_deleteLines.isChecked()) {
            _deleteLines.setChecked(false);
            _deleteLines.setText(R.string.delete_lines);
        }

        //Remove our custom listener
        _requestRemoveMapListener();

    }

    /**
     * Function that clears the map of listeners so we can intercept them to change the color/delete
     */
    private void _clearExtraListeners() {
        _mapView.getMapEventDispatcher().clearListeners(
                MapEvent.ITEM_LONG_PRESS);
        _mapView.getMapEventDispatcher()
                .clearListeners(MapEvent.MAP_LONG_PRESS);
        _mapView.getMapEventDispatcher().clearListeners(
                MapEvent.ITEM_CLICK);
        _mapView.getMapEventDispatcher().clearListeners(
                MapEvent.MAP_CONFIRMED_CLICK);
        _mapView.getMapTouchController().skipDeconfliction(true);
    }

    /**
     * Function to remove our custom listeners
     */
    private void _requestRemoveMapListener() {
        if (_listeningToMap) {
            TextContainer.getTopInstance().closePrompt();
            _mapView.getMapEventDispatcher().removeMapEventListener(
                    MapEvent.ITEM_CLICK,
                    colorListener);
            _mapView.getMapEventDispatcher().removeMapEventListener(
                    MapEvent.ITEM_CLICK,
                    this);

            _mapView.getMapTouchController().skipDeconfliction(false);
            _mapView.getMapEventDispatcher().popListeners();
            _listeningToMap = false;
        }
    }

    @Override
    public void refresh() {
        attachmentManager.refresh();
    }

    /**
     * Function that is called when the user wants to change the height
     */
    @Override
    protected void _onHeightSelected() {

        //Stop out coloring and deleting
        _onDoneColoring();
        _onDoneDeleting();
        _onDoneAdding();

        //Let the user enter the height
        createHeightDialog(_shape, R.string.enter_shape_height, new Span[] {
                Span.METER, Span.YARD, Span.FOOT
        });
    }

    @Override
    protected void sendSelected(final String uid) {
        MapItem item = _mapView.getRootGroup().deepFindUID(uid);
        if (item != null)
            new DispatchMapItemTask(_mapView, item).execute();
    }
}
