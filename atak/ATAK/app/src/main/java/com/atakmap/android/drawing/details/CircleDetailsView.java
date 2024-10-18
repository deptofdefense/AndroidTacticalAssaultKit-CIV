
package com.atakmap.android.drawing.details;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Looper;
import android.text.Editable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Toast;
import android.view.Window;

import com.atakmap.android.drawing.mapItems.DrawingCircle;
import com.atakmap.android.gui.ShapeColorButton;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.Shape;
import com.atakmap.android.util.Circle;
import com.atakmap.android.util.SimpleSeekBarChangeListener;

import com.atakmap.android.util.AttachmentManager;
import com.atakmap.android.editableShapes.CircleEditTool;
import com.atakmap.android.gui.CoordDialogView;
import com.atakmap.android.gui.RangeAndBearingTableHandler;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.toolbar.ToolManagerBroadcastReceiver;
import com.atakmap.android.util.AfterTextChangedWatcher;
import com.atakmap.app.R;
import com.atakmap.coremap.conversions.Area;
import com.atakmap.coremap.conversions.AreaUtilities;
import com.atakmap.coremap.conversions.CoordinateFormat;
import com.atakmap.coremap.conversions.Span;
import com.atakmap.coremap.conversions.SpanUtilities;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.map.CameraController;

import java.text.DecimalFormat;

public class CircleDetailsView extends GenericDetailsView implements
        View.OnClickListener, AdapterView.OnItemSelectedListener,
        Shape.OnPointsChangedListener, DrawingCircle.OnRadiusChangedListener {

    public static final String TAG = "CircleDetailsView";

    private static final DecimalFormat DEC_FMT_2 = LocaleUtil
            .getDecimalFormat("#.##");

    private AttachmentManager attachmentManager;

    private LinearLayout _sendView;
    private LinearLayout _editView;
    private Button _areaTV;
    private Button _circumferenceTV;

    private int _previousPosition = 0;

    private Button _radiusButton;
    private Spinner _ringsSpin;
    private DrawingCircle _circle;

    /**
     * *************************** CONSTRUCTOR ***************************
     */

    public CircleDetailsView(Context context) {
        super(context);
    }

    public CircleDetailsView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * *************************** PUBLIC METHODS ***************************
     */

    @Override
    public boolean setItem(MapView mapView, MapItem circle) {
        if (_circle != null) {
            _circle.removeOnRadiusChangedListener(this);
            _circle.removeOnPointsChangedListener(this);
        }
        if (!(circle instanceof DrawingCircle))
            return false;
        super.setItem(mapView, circle);
        _circle = (DrawingCircle) circle;
        _circle.addOnPointsChangedListener(this);
        _circle.addOnRadiusChangedListener(this);
        _init();
        return true;
    }

    /**
     * Callback for when the Dropdown closes. Updates the circle's meta data if something has
     * changed.
     */
    @Override
    public void onClose() {
        super.onClose();
        if (_circle == null)
            return;

        attachmentManager.cleanup();
        // Update the name if the user changed it.
        String name = _nameEdit.getText().toString();
        if (!name.equals(_prevName))
            _circle.setTitle(name);

        // Update the remarks if the user changed them.
        String remarks = _remarksLayout.getText();
        if (!remarks.equals(_prevRemarks))
            _circle.setMetaString("remarks", remarks);

        attachmentManager.cleanup();

        Intent intent = new Intent();
        intent.setAction(ToolManagerBroadcastReceiver.END_TOOL);
        intent.putExtra("tool", CircleEditTool.TOOL_IDENTIFIER);
        AtakBroadcast.getInstance().sendBroadcast(intent);

        setItem(_mapView, null);
    }

    @Override
    protected String getEditTool() {
        return CircleEditTool.TOOL_IDENTIFIER;
    }

    @Override
    protected void onEditToolBegin(Bundle extras) {
        _sendView.setVisibility(GONE);
        _editView.setVisibility(VISIBLE);
    }

    @Override
    protected void onEditToolEnd() {
        _editView.setVisibility(GONE);
        _sendView.setVisibility(VISIBLE);
    }

    /**
     * *************************** PRIVATE METHODS ***************************
     */

    private void _init() {
        if (_circle == null)
            return;
        _nameEdit = findViewById(R.id.drawingCircleNameEdit);
        _remarksLayout = findViewById(R.id.remarksLayout);
        _noGps = findViewById(R.id.drawingCircleRangeBearingNoGps);
        rabtable = new RangeAndBearingTableHandler(this);
        ShapeColorButton colorBtn = findViewById(R.id.drawingCircleColorButton);
        setupShapeColorButton(colorBtn, _circle);
        _centerButton = findViewById(R.id.drawingCircleCenterButton);
        _radiusButton = findViewById(R.id.drawingCircleRadiusButton);
        _heightButton = findViewById(R.id.drawingCircleHeightButton);
        _ringsSpin = findViewById(R.id.drawingCircleRingsSpinner);
        _transSeek = findViewById(R.id.drawingCircleTransparencySeek);
        _thickSeek = findViewById(R.id.drawingShapeStrokeSeek);

        _sendView = findViewById(R.id.drawingCircleSendView);
        _editView = findViewById(R.id.drawingCircleEditView);

        findViewById(R.id.drawingCircleSendButton).setOnClickListener(this);
        findViewById(R.id.drawingCircleEditButton).setOnClickListener(this);
        findViewById(R.id.drawingCircleUndoButton).setOnClickListener(this);
        findViewById(R.id.drawingCircleEndEditingButton)
                .setOnClickListener(this);

        _areaTV = findViewById(R.id.circleAreaText);
        _circumferenceTV = findViewById(R.id.circumferenceText);

        ImageButton attButton = findViewById(
                R.id.cotInfoAttachmentsButton);

        if (attachmentManager == null)
            attachmentManager = new AttachmentManager(_mapView, attButton);
        attachmentManager.setMapItem(_circle);

        _thickSeek.setProgress((int) (_circle.getStrokeWeight() * 10) - 10);

        _thickSeek.setOnSeekBarChangeListener(
                new SimpleSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar,
                            int progress, boolean fromUser) {
                        double strokeWeight = (progress / 10d) + 1;
                        _circle.setStrokeWeight(strokeWeight);
                        _drawPrefs.setStrokeWeight(strokeWeight);
                    }
                });

        _transSeek.setOnSeekBarChangeListener(
                new SimpleSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar,
                            int alpha, boolean fromUser) {
                        if (!fromUser)
                            return;
                        _circle.setFillAlpha(alpha);
                        _drawPrefs.setFillAlpha(alpha);
                    }
                });

        // Save an instance of the name, so we know if it changed when the dropdown closes
        _prevName = _circle.getTitle();
        _nameEdit.setText(_prevName);
        _nameEdit.addTextChangedListener(new AfterTextChangedWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                _circle.setTitle(s.toString());
            }
        });

        // Save an instance of the remarks, so we know if they changed when the dropdown closes
        _prevRemarks = _circle.getMetaString("remarks", "");
        _remarksLayout.setText(_prevRemarks);
        _remarksLayout.addTextChangedListener(new AfterTextChangedWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                _circle.setMetaString("remarks", s.toString());
            }
        });

        _transSeek.setProgress(Color.alpha(_circle.getFillColor()));

        ArrayAdapter<Integer> adapter = new ArrayAdapter<>(getContext(),
                R.layout.spinner_text_view_dark, new Integer[] {
                        1, 2, 3, 4, 5, 6, 7, 8, 9, 10
                });
        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        _ringsSpin.setAdapter(adapter);
        _ringsSpin.setOnItemSelectedListener(this);

        _heightButton.setOnClickListener(this);
        _radiusButton.setOnClickListener(this);

        _centerButton.setOnClickListener(this);

        setupAreaButton(_areaTV, new Runnable() {
            public void run() {
                refresh();
            }
        });

        setupLengthButton(_circumferenceTV, new Runnable() {
            public void run() {
                refresh();
            }
        });
        refresh();
    }

    @Override
    public void onClick(View v) {
        if (_circle == null)
            return;

        int id = v.getId();

        // Send circle
        if (id == R.id.drawingCircleSendButton)
            sendSelected(_circle.getUID());

        // Edit circle
        else if (id == R.id.drawingCircleEditButton)
            startEditingMode();

        // Undo circle edits
        else if (id == R.id.drawingCircleUndoButton)
            undoToolEdit();

        // End circle edit mode
        else if (id == R.id.drawingCircleEndEditingButton)
            endEditingMode();

        // Edit center
        else if (v == _centerButton)
            _onCenterSelected(_mapView);

        // Edit radius
        else if (v == _radiusButton)
            _onRadiusSelected();

        // Edit height
        else if (v == _heightButton)
            _onHeightSelected();
    }

    @Override
    public void onItemSelected(AdapterView<?> adapter, View v, int pos,
            long id) {
        if (_circle == null)
            return;
        if (_previousPosition != -1 && _previousPosition != pos
                && _circle.getNumRings() == _circle.setNumRings(pos + 1))
            Toast.makeText(_mapView.getContext(), R.string.rings_error,
                    Toast.LENGTH_LONG).show();
        _previousPosition = pos;
    }

    @Override
    public void onNothingSelected(AdapterView<?> adapter) {
    }

    @Override
    public void refresh() {
        if (Thread.currentThread() != Looper.getMainLooper().getThread()) {
            post(new Runnable() {
                @Override
                public void run() {
                    refresh();
                }
            });
            return;
        }

        attachmentManager.refresh();

        // Update center button
        GeoPointMetaData center = _circle.getCenter();
        _centerButton.setText(_unitPrefs.formatPoint(center, true));

        // keep the calculations and processes in the detail page, but disable the
        // view for 3.2 as per JS.   Maybe this becomes a preference.
        _noGps.setVisibility(View.GONE);
        rabtable.setVisibility(View.GONE);

        // Radius
        _radiusButton.setText(SpanUtilities.formatType(
                _unitPrefs.getRangeSystem(), _circle.getRadius(),
                Span.METER));

        // Area
        // See ATAK-9517 - makes more sense to use the set radius units for area
        _areaTV.setText(AreaUtilities.formatArea(_unitPrefs.getAreaSystem(),
                _circle.getArea(),
                Area.METER2));

        int rangeSystem = _unitPrefs.getRangeSystem();
        double range = _circle.getPerimeterOrLength();
        _circumferenceTV.setText(
                SpanUtilities.formatType(rangeSystem, range, Span.METER,
                        false));

        // Height
        Span unit = _unitPrefs.getAltitudeUnits();
        String heightTxt = "-- " + unit.getAbbrev();
        double height = _circle.getHeight();
        if (!Double.isNaN(height))
            heightTxt = SpanUtilities.format(height, Span.METER, unit);
        _heightButton.setText(heightTxt);

        // Rings
        int position = _circle.getNumRings() - 1;
        _previousPosition = -1;
        _ringsSpin.setSelection(position);
        _previousPosition = position;

        _circle.refresh();
    }

    private void _onRadiusSelected() {
        LayoutInflater inflater = LayoutInflater.from(getContext());
        final LinearLayout layout = (LinearLayout) inflater.inflate(
                R.layout.drawing_distance_input, _mapView, false);
        final EditText input = layout
                .findViewById(R.id.drawingDistanceInput);
        final Spinner units = layout
                .findViewById(R.id.drawingDistanceUnits);

        // use and arrayadapter because it just uses the toString function for it's T objects
        final ArrayAdapter<Span> adapter = new ArrayAdapter<>(getContext(),
                R.layout.spinner_text_view_dark, Span.values());
        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        units.setAdapter(adapter);

        double radius = _circle.getRadius();
        Span radiusUnits = _unitPrefs.getRangeUnits(radius);
        int start = adapter.getPosition(radiusUnits);
        units.setSelection(start);
        input.setText(DEC_FMT_2.format(SpanUtilities.convert(radius,
                Span.METER, radiusUnits)));
        input.selectAll();

        AlertDialog.Builder b = new AlertDialog.Builder(getContext());
        b.setMessage(_mapView.getContext().getString(R.string.circle_radius));
        b.setView(layout);
        b.setPositiveButton(R.string.ok, null);
        b.setNegativeButton(R.string.cancel, null);
        final AlertDialog d = b.create();
        Window w = d.getWindow();
        if (w != null)
            w.setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        d.show();
        d.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        double r = 0;
                        try {
                            r = Double.parseDouble(input.getText().toString());
                        } catch (NumberFormatException e) {
                            Log.e(TAG, "error: ", e);
                        }
                        Span u = adapter.getItem(units
                                .getSelectedItemPosition());
                        if (u == null)
                            return;
                        r = SpanUtilities.convert(r, u, Span.METER);
                        if (r <= 0) {
                            Toast.makeText(_mapView.getContext(),
                                    R.string.circle_warning_min_radius,
                                    Toast.LENGTH_LONG).show();
                            return;
                        } else if (r > Circle.MAX_RADIUS) {
                            Toast.makeText(_mapView.getContext(),
                                    R.string.circle_warning_max_radius,
                                    Toast.LENGTH_LONG).show();
                            return;
                        }
                        if (_circle.setRadius(r)) {
                            _unitPrefs.setRangeSystem(u.getType());
                            refresh();
                            d.dismiss();
                        }
                    }
                });
        input.requestFocus();
    }

    private void _onCenterSelected(MapView mapView) {
        AlertDialog.Builder b = new AlertDialog.Builder(getContext());
        LayoutInflater inflater = LayoutInflater.from(getContext());
        // custom view that allows entry of geo via mgrs, dms, and decimal degrees
        final CoordDialogView cdv = (CoordDialogView) inflater.inflate(
                R.layout.draper_coord_dialog, null);
        b.setTitle(R.string.circle_center_point);
        b.setView(cdv);
        b.setPositiveButton(R.string.ok, null);
        b.setNegativeButton(R.string.cancel, null);
        cdv.setParameters(_circle.getCenter(), mapView.getPoint(), _cFormat);

        // Overrides setPositive button onClick to keep the window open when the input is invalid.
        final AlertDialog locDialog = b.create();
        locDialog.show();
        locDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // On click get the geopoint and elevation double in ft
                        GeoPointMetaData p = cdv.getPoint();
                        CoordinateFormat cf = cdv.getCoordFormat();
                        CoordDialogView.Result result = cdv.getResult();
                        if (result == CoordDialogView.Result.INVALID)
                            return;
                        if (result == CoordDialogView.Result.VALID_UNCHANGED
                                && cf != _cFormat) {
                            refresh();
                        }
                        _cFormat = cf;
                        if (result == CoordDialogView.Result.VALID_CHANGED) {
                            _circle.setCenterPoint(p);
                            refresh();
                            CameraController.Programmatic.panTo(
                                    _mapView.getRenderer3(), p.get(), true);
                        }
                        locDialog.dismiss();
                    }
                });
    }

    @Override
    protected void _onHeightSelected() {
        createHeightDialog(_circle, R.string.circle_height, new Span[] {
                Span.METER, Span.YARD, Span.FOOT
        });
    }

    @Override
    protected void heightSelected(double height, Span u, double h) {
        super.heightSelected(height, u, h);
        refresh();
    }

    @Override
    public void onRadiusChanged(DrawingCircle circle, double oldRadius) {
        refresh();
    }

    @Override
    public void onPointsChanged(final Shape shape) {
        refresh();
    }

    @Override
    protected void sendSelected(final String uid) {
        if (attachmentManager != null)
            attachmentManager.send();
        else
            super.sendSelected(uid);
    }

}
