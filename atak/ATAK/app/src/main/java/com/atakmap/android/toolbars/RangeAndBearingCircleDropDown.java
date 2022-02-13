
package com.atakmap.android.toolbars;

import com.atakmap.android.cotdetails.extras.ExtraDetailsLayout;
import com.atakmap.android.drawing.mapItems.DrawingCircle;
import com.atakmap.android.editableShapes.CircleEditTool;
import com.atakmap.android.hashtags.view.RemarksLayout;
import com.atakmap.android.maps.Shape;
import com.atakmap.android.preference.UnitPreferences;
import com.atakmap.android.toolbar.Tool;
import com.atakmap.android.toolbar.ToolListener;
import com.atakmap.android.toolbar.ToolManagerBroadcastReceiver;
import com.atakmap.coremap.conversions.CoordinateFormatUtilities;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.text.Editable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Spinner;
import android.view.WindowManager;

import com.atakmap.android.drawing.details.GenericDetailsView;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.util.SimpleItemSelectedListener;

import com.atakmap.android.contact.ContactPresenceDropdown;
import com.atakmap.android.dropdown.DropDown.OnStateListener;
import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.gui.ColorPalette;
import com.atakmap.android.gui.ColorPalette.OnColorSelectedListener;
import com.atakmap.android.gui.CoordDialogView;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapTouchController;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.android.util.AfterTextChangedWatcher;
import com.atakmap.app.R;
import com.atakmap.coremap.conversions.Span;
import com.atakmap.coremap.conversions.SpanUtilities;
import java.text.DecimalFormat;

import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.map.CameraController;

public class RangeAndBearingCircleDropDown extends DropDownReceiver implements
        OnStateListener, View.OnClickListener,
        DrawingCircle.OnRadiusChangedListener, Shape.OnPointsChangedListener,
        MapItem.OnGroupChangedListener, ToolListener {

    public static final String TAG = "RangeAndBearingCircleDropDown";

    protected static final Span[] unitsArray = new Span[] {
            Span.METER, Span.KILOMETER, Span.NAUTICALMILE, Span.FOOT, Span.MILE
    };
    protected final DecimalFormat _one = LocaleUtil.getDecimalFormat("0.0");
    protected final DecimalFormat _two = LocaleUtil.getDecimalFormat("0.00");

    protected final MapView _mapView;
    protected final Context _context;
    protected final UnitPreferences _prefs;
    protected final ViewGroup _root;
    protected RangeCircle _rabCircle;

    protected EditText _nameEditText;
    protected RemarksLayout _remarksEditText;

    private Button _radiusButton;
    protected ImageButton _colorButton;
    private ImageButton _sendButton;
    private Button _ringsPlusButton;
    private Button _ringsMinusButton;

    private LinearLayout _centerPointButton;
    private LinearLayout _radiusPointButton;

    private ImageView _centerPointIcon;
    private ImageView _radiusPointIcon;

    private TextView _centerPointLabel;
    private TextView _radiusPointLabel;
    protected Spinner unitsSpinner;
    protected UnitsArrayAdapter unitsAdapter;
    private TextView _ringsText;

    private View _buttonLayout, _editLayout;
    private Button _editButton, _undoButton, _endButton;
    protected ExtraDetailsLayout _extrasLayout;

    public RangeAndBearingCircleDropDown(MapView mapView) {

        super(mapView);

        _mapView = mapView;
        _context = mapView.getContext();

        _prefs = new UnitPreferences(mapView);

        _root = createRootView(mapView);

        initializeWidgets();

        ToolManagerBroadcastReceiver.getInstance().registerListener(this);
    }

    protected ViewGroup createRootView(MapView mapView) {
        return (ViewGroup) LayoutInflater.from(_context)
                .inflate(R.layout.rab_circle_details, mapView, false);
    }

    @Override
    public void disposeImpl() {
        ToolManagerBroadcastReceiver.getInstance().unregisterListener(this);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String uid = intent.getStringExtra("uid");
        String shapeUID = intent.getStringExtra("shapeUID");
        boolean edit = intent.hasExtra("edit");

        MapItem item = _mapView.getRootGroup().deepFindUID(uid);
        if (!(item instanceof RangeCircle))
            item = _mapView.getRootGroup().deepFindUID(shapeUID);
        if (!(item instanceof RangeCircle))
            return;

        openCircle((RangeCircle) item);
        if (edit)
            startEdit();
    }

    protected void openCircle(final RangeCircle circle) {
        if (!isClosed()) {
            closeDropDown();
            _mapView.post(new Runnable() {
                @Override
                public void run() {
                    openCircle(circle);
                }
            });
            return;
        }
        _rabCircle = circle;
        if (_rabCircle != null) {
            showDropDown(_root, THREE_EIGHTHS_WIDTH, FULL_HEIGHT,
                    FULL_WIDTH,
                    HALF_HEIGHT, this);
            setSelected(_rabCircle, "");
            refresh();
            _rabCircle.addOnRadiusChangedListener(this);
            _rabCircle.addOnPointsChangedListener(this);
            _rabCircle.addOnGroupChangedListener(this);
        }
    }

    @Override
    public void onRadiusChanged(DrawingCircle circle, double oldRadius) {
        refresh();
    }

    protected void initializeWidgets() {
        GenericDetailsView.addEditTextPrompts(_root);
        _nameEditText = _root.findViewById(R.id.nameEditText);
        _nameEditText.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        _nameEditText.addTextChangedListener(new AfterTextChangedWatcher() {
            String lastString = "";

            @Override
            synchronized public void afterTextChanged(Editable s) {
                String newString = s.toString();
                if (newString.equals(lastString)) {
                    return;
                }
                if (_rabCircle != null) {
                    lastString = newString;
                    _rabCircle.setTitle(newString);
                }
            }
        });

        _remarksEditText = _root.findViewById(R.id.remarksLayout);
        _remarksEditText.addTextChangedListener(new AfterTextChangedWatcher() {
            @Override
            public void afterTextChanged(Editable e) {
                if (_rabCircle != null) {
                    _rabCircle.setMetaString("remarks",
                            _remarksEditText.getText());
                }
            }

        });

        unitsSpinner = _root.findViewById(R.id.radiusUnitsSpinner);

        unitsAdapter = new UnitsArrayAdapter(_context,
                R.layout.spinner_text_view, unitsArray);
        unitsAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        unitsSpinner.setAdapter(unitsAdapter);

        unitsSpinner.setOnItemSelectedListener(
                new SimpleItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent,
                            View view, int position, long id) {
                        Span selectedUnits = unitsAdapter.getItem(position);
                        if (selectedUnits == null)
                            return;

                        // Convert the radius to the new units selection
                        double radius = _rabCircle.getRadius();
                        Span currentUnits = _prefs.getRangeUnits(radius);
                        radius = SpanUtilities.convert(radius, Span.METER,
                                currentUnits);
                        radius = SpanUtilities.convert(radius, selectedUnits,
                                Span.METER);

                        // Update preferences and circle
                        _prefs.setRangeSystem(selectedUnits.getType());
                        _rabCircle.setRadius(radius);
                    }
                });

        _colorButton = _root.findViewById(R.id.colorButton);
        _colorButton.setOnClickListener(this);

        _ringsPlusButton = _root.findViewById(R.id.ringsPlusButton);
        _ringsPlusButton.setOnClickListener(this);

        _ringsMinusButton = _root.findViewById(R.id.ringsMinusButton);
        _ringsMinusButton.setOnClickListener(this);

        _centerPointButton = _root.findViewById(
                R.id.centerPointButton);
        _centerPointButton.setOnClickListener(this);

        _radiusPointButton = _root.findViewById(
                R.id.radiusPointButton);
        _radiusPointButton.setOnClickListener(this);

        _centerPointIcon = _root.findViewById(
                R.id.centerPointIcon);
        _radiusPointIcon = _root.findViewById(
                R.id.radiusPointIcon);

        _centerPointLabel = _root.findViewById(
                R.id.centerPointLabel);
        _radiusPointLabel = _root.findViewById(
                R.id.radiusPointLabel);
        _ringsText = _root.findViewById(R.id.ringsText);

        _radiusButton = _root.findViewById(R.id.radiusButton);
        _radiusButton.setOnClickListener(this);

        _buttonLayout = _root.findViewById(R.id.buttonLayout);
        _sendButton = _root.findViewById(R.id.sendButton);
        _sendButton.setOnClickListener(this);
        _editButton = _root.findViewById(R.id.editButton);
        _editButton.setOnClickListener(this);

        _editLayout = _root.findViewById(R.id.editLayout);
        _undoButton = _root.findViewById(R.id.undoButton);
        _undoButton.setOnClickListener(this);
        _endButton = _root.findViewById(R.id.endButton);
        _endButton.setOnClickListener(this);

        _extrasLayout = _root.findViewById(R.id.extrasLayout);
    }

    @Override
    public void onClick(View v) {

        CircleEditTool active = getEditTool();

        // Set circle color
        if (v == _colorButton)
            displayColorSelectDialog();

        // Pan to radius marker
        else if (v == _radiusPointButton)
            MapTouchController.goTo(_rabCircle.getRadiusMarker(), true);

        // Add rings
        else if (v == _ringsPlusButton)
            addRings();

        // Subtract rings
        else if (v == _ringsMinusButton)
            subtractRings();

        // Pan to center point
        else if (v == _centerPointButton) {
            if (!_rabCircle.isCenterShapeMarker())
                MapTouchController.goTo(_rabCircle.getCenterMarker(), true);
            else
                displayCoordinateDialog(_rabCircle.getCenter());
        }

        // Set radius
        else if (v == _radiusButton) {
            // Open dialog to set the duration for the collection
            AlertDialog.Builder b = new AlertDialog.Builder(_context);

            final Span span = (Span) unitsSpinner.getSelectedItem();
            final EditText input = new EditText(_context);
            input.setInputType(InputType.TYPE_CLASS_NUMBER
                    | InputType.TYPE_NUMBER_FLAG_DECIMAL);

            input.setText(_radiusButton.getText());
            input.setSelection(input.getText().length());

            b.setMessage(_context.getString(R.string.rb_circle_dialog)
                    + span.getPlural() + ":");
            b.setView(input);
            b.setPositiveButton(R.string.ok,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int w) {
                            try {
                                String i = input.getText().toString();
                                if (FileSystemUtils.isEmpty(i)) {
                                    Toast.makeText(_context,
                                            "invalid radius, no value",
                                            Toast.LENGTH_SHORT).show();
                                    return;
                                }

                                double radius = Double.parseDouble(i);
                                double rMeters = SpanUtilities.convert(radius,
                                        span,
                                        Span.METER);

                                if (radius > 0.0) {
                                    _rabCircle.setRadius(rMeters);
                                } else {
                                    Toast.makeText(_context,
                                            R.string.rb_circle_tip2,
                                            Toast.LENGTH_LONG).show();
                                }
                            } catch (NumberFormatException nfe) {
                                Toast.makeText(_context, "invalid radius",
                                        Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
            b.setNegativeButton(R.string.cancel, null);
            AlertDialog d = b.create();
            if (d.getWindow() != null)
                d.getWindow().setSoftInputMode(
                        WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
            d.show();
            input.requestFocus();
        }

        // Send circle
        else if (v == _sendButton) {
            // Make sure the object is shared since the user hit "Send".
            _rabCircle.setMetaBoolean("shared", true);

            AtakBroadcast.getInstance().sendBroadcast(new Intent(
                    ContactPresenceDropdown.SEND_LIST)
                            .putExtra("targetUID", _rabCircle.getUID()));
        }

        // Edit button
        else if (v == _editButton)
            startEdit();

        if (active != null) {
            if (v == _undoButton)
                active.undo();
            else if (v == _endButton)
                active.requestEndTool();
        }
    }

    protected void startEdit() {
        Bundle extras = new Bundle();
        extras.putString("uid", _rabCircle.getUID());
        ToolManagerBroadcastReceiver.getInstance().startTool(
                CircleEditTool.TOOL_IDENTIFIER, extras);
    }

    @Override
    public void onToolBegin(Tool tool, Bundle extras) {
        refresh();
    }

    @Override
    public void onToolEnded(Tool tool) {
        refresh();
    }

    protected CircleEditTool getEditTool() {
        Tool active = ToolManagerBroadcastReceiver.getInstance()
                .getActiveTool();
        return active instanceof CircleEditTool ? (CircleEditTool) active
                : null;
    }

    protected void refresh() {
        _mapView.post(new Runnable() {
            @Override
            public void run() {
                if (_rabCircle == null)
                    return;

                populateLocationWidgets();
                _nameEditText.setText(_rabCircle.getTitle());
                final double d = _rabCircle.getRadius();
                final Span currSpan = _prefs.getRangeUnits(d);

                double val = SpanUtilities.convert(d, Span.METER, currSpan);

                if (currSpan == Span.METER || currSpan == Span.FOOT) {
                    _radiusButton.setText("" + Math.round(val));
                } else {
                    if (val < 100) {
                        _radiusButton.setText(_two.format(val));
                    } else {
                        _radiusButton.setText(_one.format(val));
                    }
                }
                unitsSpinner.setSelection(unitsAdapter.getPosition(currSpan));

                _remarksEditText.setText(_rabCircle.getMetaString(
                        "remarks", ""));
                updateColorButton(_rabCircle.getStrokeColor());

                if (getEditTool() != null) {
                    _buttonLayout.setVisibility(View.GONE);
                    _editLayout.setVisibility(View.VISIBLE);
                } else {
                    _buttonLayout.setVisibility(View.VISIBLE);
                    _editLayout.setVisibility(View.GONE);
                }
                _extrasLayout.setItem(_rabCircle);
            }
        });

    }

    // TODO: The separation is more stateful-widgets and non-stateful widgets.
    protected void populateLocationWidgets() {
        _mapView.post(new Runnable() {
            @Override
            public void run() {
                if (_rabCircle == null)
                    return;

                GeoPoint center = _rabCircle.getCenterPoint();
                Marker centerMarker = _rabCircle.getCenterMarker();
                if (centerMarker != null && !_rabCircle.isCenterShapeMarker()) {
                    _centerPointLabel.setText(centerMarker.getTitle());
                    _centerPointIcon.setImageBitmap(ATAKUtilities
                            .getIconBitmap(centerMarker));
                } else {
                    _centerPointLabel.setText(CoordinateFormatUtilities
                            .formatToString(center,
                                    _prefs.getCoordinateFormat()));
                    _centerPointIcon.setImageBitmap(null);
                }

                Marker radiusMarker = _rabCircle.getRadiusMarker();
                if (radiusMarker != null) {
                    _radiusPointLabel.setText(radiusMarker.getTitle());
                    _radiusPointIcon.setImageBitmap(ATAKUtilities
                            .getIconBitmap(radiusMarker));
                    _radiusPointButton.setEnabled(true);
                    _radiusPointLabel.setEnabled(true);
                } else {
                    _radiusPointLabel.setText(R.string.no_marker_set);
                    _radiusPointIcon.setImageBitmap(null);
                    _radiusPointButton.setEnabled(false);
                    _radiusPointLabel.setEnabled(false);
                }

                int rings = _rabCircle.getNumRings();
                _ringsText.setText((rings < 10 ? "0" : "") + rings);
            }
        });

    }

    private void addRings() {

        _rabCircle.setNumRings(_rabCircle.getNumRings() + 1);
        populateLocationWidgets();

    }

    private void subtractRings() {

        _rabCircle.setNumRings(_rabCircle.getNumRings() - 1);
        populateLocationWidgets();

    }

    private void displayColorSelectDialog() {
        AlertDialog.Builder b = new AlertDialog.Builder(_context);
        b.setTitle(R.string.rb_color_dialog);
        ColorPalette palette = new ColorPalette(_context,
                _rabCircle.getStrokeColor());
        b.setView(palette);
        final AlertDialog alert = b.create();

        OnColorSelectedListener l = new OnColorSelectedListener() {
            @Override
            public void onColorSelected(int color, String label) {
                updateColorButton(color);
                _rabCircle.setStrokeColor(color);
                alert.dismiss();
            }
        };

        palette.setOnColorSelectedListener(l);
        alert.show();
    }

    protected void updateColorButton(final int color) {
        int newColor = Color.argb(255, Color.red(color), Color.green(color),
                Color.blue(color));
        if (newColor != _rabCircle.getStrokeColor()) {
            _rabCircle.setColor(Color.argb(0, Color.red(color),
                    Color.green(color), Color.blue(color)));
            PointMapItem anchor = _rabCircle.getAnchorItem();
            if (anchor != null && !ATAKUtilities.isSelf(_mapView, anchor))
                anchor.refresh(_mapView.getMapEventDispatcher(),
                        null, getClass());
        }
        _root.post(new Runnable() {
            @Override
            public void run() {
                _colorButton.setColorFilter(color, PorterDuff.Mode.MULTIPLY);
            }
        });
    }

    protected void displayCoordinateDialog(GeoPointMetaData centerPoint) {

        AlertDialog.Builder b = new AlertDialog.Builder(_context);
        LayoutInflater inflater = LayoutInflater.from(_context);

        final CoordDialogView coordView = (CoordDialogView) inflater.inflate(
                R.layout.draper_coord_dialog, _mapView, false);

        b.setTitle(_context.getString(R.string.rb_coord_title));
        b.setView(coordView);
        b.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface d, int w) {
                GeoPointMetaData gp = coordView.getPoint();
                if (gp == null || !gp.get().isValid())
                    return;
                _prefs.setCoordinateFormat(coordView.getCoordFormat());
                _rabCircle.setCenterPoint(gp);
                populateLocationWidgets();

                CameraController.Programmatic.panTo(
                        _mapView.getRenderer3(), gp.get(), false);
            }
        });
        b.setNegativeButton(R.string.cancel, null);
        coordView.setParameters(centerPoint, _mapView.getPoint(),
                _prefs.getCoordinateFormat());
        b.show();
    }

    @Override
    public void onPointsChanged(Shape shape) {
        if (_rabCircle != null)
            populateLocationWidgets();
    }

    @Override
    public void onItemAdded(MapItem item, MapGroup group) {
    }

    @Override
    public void onItemRemoved(MapItem item, MapGroup group) {
        if (_rabCircle != null)
            refresh();
    }

    @Override
    public void onDropDownSelectionRemoved() {
    }

    @Override
    public void onDropDownClose() {
        _rabCircle.setMetaString("remarks", _remarksEditText.getText());
        _rabCircle.setTitle(_nameEditText.getText().toString());
        _rabCircle.persist(_mapView.getMapEventDispatcher(), null, getClass());
        _rabCircle.removeOnRadiusChangedListener(this);
        _rabCircle.removeOnPointsChangedListener(this);
        _rabCircle.removeOnGroupChangedListener(this);
        _rabCircle = null;
    }

    @Override
    public void onDropDownSizeChanged(double width, double height) {
    }

    @Override
    public void onDropDownVisible(boolean v) {

    }
}
