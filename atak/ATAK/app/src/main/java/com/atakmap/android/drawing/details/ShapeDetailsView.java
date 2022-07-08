
package com.atakmap.android.drawing.details;

import android.app.AlertDialog;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.SeekBar;

import com.atakmap.android.gui.ShapeColorButton;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.Shape;
import com.atakmap.android.util.SimpleSeekBarChangeListener;

import android.text.Editable;
import com.atakmap.android.util.AfterTextChangedWatcher;
import com.atakmap.android.drawing.mapItems.DrawingShape;
import com.atakmap.android.drawing.tools.ShapeEditTool;
import com.atakmap.android.gui.CoordDialogView;
import com.atakmap.android.gui.RangeAndBearingTableHandler;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.toolbar.ToolManagerBroadcastReceiver;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.app.R;
import com.atakmap.coremap.conversions.Area;
import com.atakmap.coremap.conversions.AreaUtilities;
import com.atakmap.coremap.conversions.CoordinateFormat;
import com.atakmap.coremap.conversions.Span;
import com.atakmap.coremap.conversions.SpanUtilities;
import com.atakmap.android.util.AttachmentManager;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.map.CameraController;

import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.Nullable;

public class ShapeDetailsView extends GenericDetailsView implements
        PointMapItem.OnPointChangedListener,
        Shape.OnPointsChangedListener,
        View.OnClickListener,
        CompoundButton.OnCheckedChangeListener {

    private static final String TAG = "ShapeDetailsView";

    private AttachmentManager attachmentManager;
    private ImageButton _attachmentButton;
    private ShapeColorButton _colorBtn;

    private View _sendExportView;
    private View _shapeEditView;

    private TextView _shapeAreaLabel;
    private Button _shapeAreaTF;

    private TextView _shapePerimeterLabel;
    private Button _shapePerimeterTF;

    private TextView _startPointLabel;
    private Button _startPointBtn;

    private TextView _endPointLabel;
    private Button _endPointBtn;

    private CheckBox _closedCB;
    private View _transView;

    private DrawingShape _shape;

    public ShapeDetailsView(Context context) {
        super(context);
    }

    public ShapeDetailsView(Context context, final AttributeSet inAtr) {
        super(context, inAtr);
    }

    @Override
    public boolean setItem(MapView mapView, MapItem shape) {
        if (!(shape instanceof DrawingShape))
            return false;
        unregisterListeners();
        super.setItem(mapView, shape);
        _shape = (DrawingShape) shape;
        registerListeners();
        _init();
        return true;
    }

    /**
     * Callback for when the Dropdown closes.
     * Updates the shape's meta data if something has changed.
     */
    @Override
    public void onClose() {
        super.onClose();
        unregisterListeners();

        attachmentManager.cleanup();

        Intent intent = new Intent();
        intent.setAction(ToolManagerBroadcastReceiver.END_TOOL);
        intent.putExtra("tool", ShapeEditTool.TOOL_IDENTIFIER);
        AtakBroadcast.getInstance().sendBroadcast(intent);
    }

    @Override
    protected String getEditTool() {
        return ShapeEditTool.TOOL_IDENTIFIER;
    }

    @Override
    protected void onEditToolBegin(Bundle extras) {
        _sendExportView.setVisibility(GONE);
        _shapeEditView.setVisibility(VISIBLE);
    }

    @Override
    protected void onEditToolEnd() {
        _sendExportView.setVisibility(VISIBLE);
        _shapeEditView.setVisibility(GONE);
        updateArea();
        updatePerimeterOrLength();
    }

    /**
     * *************************** PRIVATE METHODS ***************************
     */

    /**
     * Get the shape's center marker
     * @return Shape center marker or null if none
     */
    @Nullable
    private Marker getCenterMarker() {
        return _shape != null ? _shape.getMarker() : null;
    }

    /**
     * Register shape/point listeners
     */
    private void registerListeners() {
        if (_shape == null)
            return;
        Marker center = getCenterMarker();
        if (center != null)
            center.addOnPointChangedListener(this);
        _shape.addOnPointsChangedListener(this);
    }

    /**
     * Unregister shape/point listeners
     */
    private void unregisterListeners() {
        if (_shape == null)
            return;
        Marker center = getCenterMarker();
        if (center != null)
            center.removeOnPointChangedListener(this);
        _shape.removeOnPointsChangedListener(this);
    }

    private void _init() {
        _nameEdit = findViewById(R.id.drawingShapeNameEdit);
        _remarksLayout = findViewById(R.id.remarksLayout);
        _noGps = findViewById(R.id.drawingShapeRangeBearingNoGps);
        rabtable = new RangeAndBearingTableHandler(this);
        _centerButton = findViewById(R.id.drawingShapeCenterButton);
        _shapeAreaLabel = findViewById(R.id.drawingShapeAreaLabel);
        _shapeAreaTF = findViewById(R.id.shapeAreaText);

        _shapePerimeterLabel = findViewById(R.id.drawingShapePerimeterLabel);
        _shapePerimeterTF = findViewById(R.id.shapePerimeterText);

        _heightButton = findViewById(R.id.drawingShapeHeightButton);
        _colorBtn = findViewById(R.id.drawingShapeColorButton);
        setupShapeColorButton(_colorBtn, _shape);

        _startPointLabel = findViewById(R.id.startPointLabel);
        _startPointBtn = findViewById(R.id.startPointButton);

        _endPointLabel = findViewById(R.id.endPointLabel);
        _endPointBtn = findViewById(R.id.endPointButton);

        _closedCB = findViewById(R.id.closedCB);

        _transSeek = findViewById(R.id.drawingShapeTransparencySeek);
        _thickSeek = findViewById(R.id.drawingShapeStrokeSeek);
        _transView = findViewById(R.id.drawingShapeTransparencyView);
        _sendExportView = findViewById(R.id.drawingShapeSendExportView);
        _shapeEditView = findViewById(R.id.drawingShapeEditView);

        findViewById(R.id.drawingShapeSendButton).setOnClickListener(this);
        findViewById(R.id.drawingShapeUndoButton).setOnClickListener(this);
        findViewById(R.id.drawingShapeEditButton).setOnClickListener(this);
        findViewById(R.id.drawingShapeEndEditingButton)
                .setOnClickListener(this);

        _attachmentButton = this
                .findViewById(R.id.cotInfoAttachmentsButton);

        if (attachmentManager == null)
            attachmentManager = new AttachmentManager(_mapView,
                    _attachmentButton);
        attachmentManager.setMapItem(_shape);

        refresh();

        _nameEdit.addTextChangedListener(new AfterTextChangedWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (_shape != null)
                    _shape.setTitle(_nameEdit.getText().toString());
            }
        });

        _remarksLayout.addTextChangedListener(new AfterTextChangedWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (_shape != null)
                    _shape.setRemarks(_remarksLayout.getText());
            }
        });

        // Update the R & B text
        PointMapItem device = ATAKUtilities.findSelf(_mapView);
        // It's possible that we don't have GPS and therefore don't have a controller point
        if (device != null) {
            _noGps.setVisibility(GONE);
            rabtable.setVisibility(VISIBLE);
            rabtable.update(device, _shape.getCenter().get());
        } else {
            _noGps.setVisibility(VISIBLE);
            rabtable.setVisibility(GONE);
        }

        // keep the calculations and processes in the detail page, but disable the 
        // view for 3.2 as per JS.   Maybe this becomes a preference.
        _noGps.setVisibility(GONE);
        rabtable.setVisibility(GONE);

        _thickSeek
                .setOnSeekBarChangeListener(new SimpleSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar sb, int prog,
                            boolean user) {
                        if (user) {
                            double strokeWeight = 1 + (prog / 10d);
                            _shape.setStrokeWeight(strokeWeight);
                            _drawPrefs.setStrokeWeight(strokeWeight);
                        }
                    }
                });

        _centerButton.setOnClickListener(this);
        _startPointBtn.setOnClickListener(this);
        _endPointBtn.setOnClickListener(this);
        _transSeek
                .setOnSeekBarChangeListener(new SimpleSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar sb, int alpha,
                            boolean user) {
                        if (user) {
                            _shape.setFillAlpha(alpha);
                            _drawPrefs.setFillAlpha(alpha);
                        }
                    }
                });

        _heightButton.setOnClickListener(this);

        setupAreaButton(_shapeAreaTF, new Runnable() {
            public void run() {
                updateArea();
            }
        });

        setupLengthButton(_shapePerimeterTF, new Runnable() {
            public void run() {
                updatePerimeterOrLength();
            }
        });
    }

    @Override
    public void refresh() {
        if (_shape == null)
            return;

        _nameEdit.setText(_shape.getTitle());
        _remarksLayout.setText(_shape.getRemarks());

        boolean closed = _shape.isClosed();

        // Update the center point
        Marker centerMarker = getCenterMarker();
        GeoPointMetaData center = _shape.getCenter();
        if (centerMarker != null)
            center = centerMarker.getGeoPointMetaData();
        _centerButton.setText(_unitPrefs.formatPoint(center,
                centerMarker != null));
        _centerButton.setEnabled(closed);

        // Whether the shape is closed or not
        _closedCB.setVisibility(_shape.getNumPoints() > 2 ? VISIBLE : GONE);
        _closedCB.setOnCheckedChangeListener(null);
        _closedCB.setChecked(closed);
        _closedCB.setOnCheckedChangeListener(this);

        // Update the start and end points
        int openVis = closed ? GONE : VISIBLE;
        int closedVis = closed ? VISIBLE : GONE;
        GeoPointMetaData start = _shape.getPoint(0);
        GeoPointMetaData end = _shape.getPoint(_shape.getNumPoints() - 1);
        if (start != null)
            _startPointBtn.setText(_unitPrefs.formatPoint(start, false));
        if (end != null)
            _endPointBtn.setText(_unitPrefs.formatPoint(end, false));
        _startPointLabel.setVisibility(openVis);
        _startPointBtn.setVisibility(openVis);
        _endPointLabel.setVisibility(openVis);
        _endPointBtn.setVisibility(openVis);

        // Height
        double height = _shape.getHeight();
        Span unit = _unitPrefs.getAltitudeUnits();
        if (!Double.isNaN(height))
            _heightButton.setText(SpanUtilities.format(height, Span.METER,
                    unit));
        else
            _heightButton.setText("-- " + unit.getAbbrev());

        // Update shape stats
        updatePerimeterOrLength();
        updateArea();

        // Stroke weight
        _thickSeek.setProgress((int) (_shape.getStrokeWeight() * 10) - 10);

        // Alpha transparency
        int alpha = _shape.isClosed() ? Color.alpha(_shape.getFillColor())
                : _drawPrefs.getFillAlpha();
        _transSeek.setProgress(alpha);
        _transView.setVisibility(closedVis);

        // Attachments button
        attachmentManager.refresh();
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();

        // Send shape
        if (id == R.id.drawingShapeSendButton)
            sendSelected(_shape.getUID());

        // Edit shape
        else if (id == R.id.drawingShapeEditButton)
            startEditingMode();

        // Undo shape edit
        else if (id == R.id.drawingShapeUndoButton)
            undoToolEdit();

        // End shape editing
        else if (id == R.id.drawingShapeEndEditingButton)
            endEditingMode();

        // Edit height
        else if (v == _heightButton)
            _onHeightSelected();

        // Edit center point (closed shape only)
        else if (v == _centerButton)
            _onCenterSelected();

        // Edit start/end points (open shape only)
        else if (v == _startPointBtn || v == _endPointBtn)
            onPointSelected(v == _startPointBtn);
    }

    @Override
    public void onPointChanged(final PointMapItem item) {
        _mapView.post(new Runnable() {
            @Override
            public void run() {
                if (getCenterMarker() == item)
                    refresh();
            }
        });
    }

    @Override
    public void onPointsChanged(Shape s) {
        _mapView.post(new Runnable() {
            @Override
            public void run() {
                if (_shape == s)
                    refresh();
            }
        });
    }

    private void updatePerimeterOrLength() {
        final double range = _shape.getPerimeterOrLength();
        final int rangeSystem = _unitPrefs.getRangeSystem();
        if (_shape.isClosed())
            _shapePerimeterLabel.setText(R.string.perimeter);
        else
            _shapePerimeterLabel.setText(R.string.length);
        if (Double.isNaN(range)) {
            _shapePerimeterTF.setVisibility(GONE);
            _shapePerimeterLabel.setVisibility(GONE);
        } else {
            _shapePerimeterTF.setVisibility(VISIBLE);
            _shapePerimeterLabel.setVisibility(VISIBLE);
            _shapePerimeterTF.setText(
                    SpanUtilities.formatType(rangeSystem, range, Span.METER,
                            false));
        }
    }

    private void updateArea() {
        final int areaUnits = _unitPrefs.getAreaSystem();
        final double area = _shape.getArea();

        if (Double.isNaN(area)) {
            _shapeAreaTF.setVisibility(GONE);
            _shapeAreaLabel.setVisibility(GONE);
        } else {
            _shapeAreaLabel.setVisibility(VISIBLE);
            _shapeAreaTF.setVisibility(VISIBLE);
            _shapeAreaTF.setText("(" + AreaUtilities.formatArea(areaUnits, area,
                    Area.METER2) + ")");
        }
    }

    private void _onCenterSelected() {
        AlertDialog.Builder b = new AlertDialog.Builder(getContext());
        LayoutInflater inflater = LayoutInflater.from(getContext());
        final CoordDialogView coordView = (CoordDialogView) inflater.inflate(
                R.layout.draper_coord_dialog, null);
        b.setTitle("Enter Shape Center Point: ");
        b.setView(coordView);
        b.setPositiveButton(R.string.ok, null);
        b.setNegativeButton(R.string.cancel, null);
        coordView.setParameters(_shape.getAnchorItem().getGeoPointMetaData(),
                _mapView.getPoint(), _cFormat);
        // Overrides setPositive button onClick to keep the window open when the input is invalid.
        final AlertDialog locDialog = b.create();
        locDialog.show();
        locDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // On click get the geopoint and elevation double in ft
                        GeoPointMetaData p = coordView.getPoint();
                        CoordinateFormat cf = coordView.getCoordFormat();
                        CoordDialogView.Result result = coordView.getResult();
                        if (result == CoordDialogView.Result.INVALID)
                            return;
                        if (result == CoordDialogView.Result.VALID_UNCHANGED
                                && cf != _cFormat) {
                            // The coordinate format was changed but not the point itself
                            _centerButton.setText(coordView
                                    .getFormattedString());
                        }
                        _cFormat = cf;
                        if (result == CoordDialogView.Result.VALID_CHANGED) {
                            _shape.moveClosedSet(_shape.getCenter(), p);
                            if (_shape.getAnchorItem() != null)
                                _shape.getAnchorItem().setPoint(p);

                            _centerButton.setText(coordView
                                    .getFormattedString());

                            CameraController.Programmatic.panTo(
                                    dropDown.getMapView().getRenderer3(),
                                    _shape.getCenter().get(), true);
                        }
                        locDialog.dismiss();
                    }
                });
    }

    /**
     * Called when the start or end point button is selected
     * @param start True if start point, false if end point
     */
    private void onPointSelected(final boolean start) {
        final int index = start ? 0 : _shape.getNumPoints() - 1;
        GeoPointMetaData point = _shape.getPoint(index);
        if (point == null)
            return;

        AlertDialog.Builder b = new AlertDialog.Builder(getContext());
        LayoutInflater inflater = LayoutInflater.from(getContext());
        final CoordDialogView coordView = (CoordDialogView) inflater.inflate(
                R.layout.draper_coord_dialog, null);
        b.setTitle(start ? R.string.start_point : R.string.end_point);
        b.setView(coordView);
        b.setPositiveButton(R.string.ok, null);
        b.setNegativeButton(R.string.cancel, null);

        coordView.setParameters(point, _mapView.getPoint(), _cFormat);
        final AlertDialog d = b.create();
        d.show();
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        GeoPointMetaData p = coordView.getPoint();
                        CoordinateFormat cf = coordView.getCoordFormat();
                        CoordDialogView.Result result = coordView.getResult();
                        if (result == CoordDialogView.Result.INVALID)
                            return;
                        _cFormat = cf;
                        _shape.setPoint(index, p, true);
                        refresh();
                        d.dismiss();
                    }
                });
    }

    @Override
    protected void _onHeightSelected() {
        createHeightDialog(_shape, R.string.enter_shape_height, new Span[] {
                Span.METER, Span.YARD, Span.FOOT
        });
    }

    @Override
    protected void heightSelected(final double height, final Span u,
            final double h) {
        super.heightSelected(height, u, h);
        Marker center = getCenterMarker();
        if (center != null)
            center.setHeight(height);
    }

    @Override
    protected void sendSelected(final String uid) {
        if (attachmentManager != null)
            attachmentManager.send();
        else
            super.sendSelected(uid);
    }

    @Override
    public void onCheckedChanged(CompoundButton cb, boolean checked) {
        if (cb == _closedCB) {
            // Re-register listeners in case the center marker was added/removed
            unregisterListeners();
            _shape.setClosed(checked);
            _shape.setFilled(checked);
            registerListeners();

            // Make sure the fill color properly matches the stroke color
            if (checked && _shape.getFillColor() == Color.WHITE) {
                int color = _shape.getStrokeColor();
                int alpha = _transSeek.getProgress();
                _shape.setFillColor((alpha << 24) | (color & 0xFFFFFF));
            }

            setupShapeColorButton(_colorBtn, _shape);
            refresh();
        }
    }
}
