
package com.atakmap.android.drawing.details;

import android.app.AlertDialog;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ShapeDrawable;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.Marker;
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

public class ShapeDetailsView extends GenericDetailsView implements
        PointMapItem.OnPointChangedListener, View.OnClickListener {

    private static final String TAG = "ShapeDetailsView";

    private AttachmentManager attachmentManager;
    private ImageButton _attachmentButton;

    private View _sendExportView;
    private View _shapeEditView;

    private TextView _shapeAreaLabel;
    private Button _shapeAreaTF;

    private TextView _shapePerimeterLabel;
    private Button _shapePerimeterTF;

    private DrawingShape _shape;
    private Marker _center;

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
        super.setItem(mapView, shape);
        _shape = (DrawingShape) shape;
        _init();
        return true;
    }

    /**
     * Callback for when the Dropdown closes.
     * Updates the shapes's meta data if something has changed.
     */
    @Override
    public void onClose() {

        super.onClose();
        if (_center != null)
            _center.removeOnPointChangedListener(this);

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
        _colorButton = findViewById(R.id.drawingShapeColorButton);
        /*
        *************************** PRIVATE FIELDS ***************************
        */
        _transSeek = findViewById(R.id.drawingShapeTransparencySeek);
        _thickSeek = findViewById(R.id.drawingShapeStrokeSeek);
        View _transView = findViewById(R.id.drawingShapeTransparencyView);
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

        _remarksLayout.addTextChangedListener(new AfterTextChangedWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (_shape != null) {
                    String remarks = _remarksLayout.getText();
                    if (!remarks.equals(_prevRemarks))
                        _shape.setMetaString("remarks", remarks);
                }
            }
        });

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

        _thickSeek.setProgress((int) (_shape.getStrokeWeight() * 10) - 10);

        _thickSeek
                .setOnSeekBarChangeListener(
                        new SimpleSeekBarChangeListener() {
                            @Override
                            public void onProgressChanged(SeekBar seekBar,
                                    int progress, boolean fromUser) {
                                double strokeWeight = 1 + (progress / 10d);
                                _shape.setStrokeWeight(strokeWeight);
                                _drawPrefs.setStrokeWeight(strokeWeight);
                            }
                        });

        // Depending on whether or not the shape is closed, the UI will change
        // shouldn't be able to move the center of a shape, or to edit the transparency
        // because there is no fill color.
        if (!_shape.isClosed()) {
            _transView.setVisibility(View.GONE);
            _centerButton.setEnabled(false);
            _shapeAreaLabel.setVisibility(View.GONE);
            _shapeAreaTF.setVisibility(View.GONE);
            updatePerimeterOrLength();
            _center = null;
        } else {
            _center = _shape.getMarker();
            _center.addOnPointChangedListener(this);
            _centerButton.setOnClickListener(this);

            _alpha = _shape.getFillColor() >>> 24;
            _transSeek.setProgress(_alpha);

            _transSeek
                    .setOnSeekBarChangeListener(
                            new SimpleSeekBarChangeListener() {
                                @Override
                                public void onProgressChanged(SeekBar seekBar,
                                        int progress, boolean fromUser) {
                                    if (fromUser) {
                                        int color = _shape.getFillColor();
                                        _alpha = progress;
                                        _shape.setFillColor(Color.argb(_alpha,
                                                Color.red(color),
                                                Color.green(color),
                                                Color.blue(color)));
                                        _drawPrefs.setFillAlpha(_alpha);
                                    }
                                }
                            });
            _shapeAreaLabel.setVisibility(View.VISIBLE);
            _shapeAreaTF.setVisibility(View.VISIBLE);
            updateArea();
            updatePerimeterOrLength();
        }
        onPointChanged(_center);

        double height = _shape.getHeight();
        Span unit = _unitPrefs.getAltitudeUnits();
        if (!Double.isNaN(height)) {
            _heightButton.setText(SpanUtilities.format(height, Span.METER,
                    unit));
        } else {
            _heightButton.setText("-- " + unit.getAbbrev());
        }
        _heightButton.setOnClickListener(this);
        _updateColorButtonDrawable();
        _colorButton.setOnClickListener(this);

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

        // Edit color
        else if (v == _colorButton)
            _onColorSelected();

        // Edit height
        else if (v == _heightButton)
            _onHeightSelected();

        // Edit center point (closed shape only)
        else if (v == _centerButton)
            _onCenterSelected();
    }

    @Override
    public void onPointChanged(final PointMapItem item) {
        _mapView.post(new Runnable() {
            @Override
            public void run() {
                if (_center == item) {
                    GeoPointMetaData center = _shape.getCenter();
                    PointMapItem anchor = _shape.getAnchorItem();
                    if (anchor != null)
                        center = anchor.getGeoPointMetaData();
                    _centerButton.setText(_unitPrefs.formatPoint(center,
                            anchor != null));
                }
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
            _shapePerimeterTF.setVisibility(View.GONE);
            _shapePerimeterLabel.setVisibility(View.GONE);
        } else {
            _shapePerimeterTF.setVisibility(View.VISIBLE);
            _shapePerimeterLabel.setVisibility(View.VISIBLE);
            _shapePerimeterTF.setText(
                    SpanUtilities.formatType(rangeSystem, range, Span.METER,
                            false));
        }
    }

    private void updateArea() {
        final int areaUnits = _unitPrefs.getAreaSystem();
        final double area = _shape.getArea();

        if (Double.isNaN(area)) {
            _shapeAreaTF.setVisibility(View.GONE);
            _shapeAreaLabel.setVisibility(View.GONE);
        } else {
            _shapeAreaLabel.setVisibility(View.VISIBLE);
            _shapeAreaTF.setVisibility(View.VISIBLE);
            _shapeAreaTF.setText("(" + AreaUtilities.formatArea(areaUnits, area,
                    Area.METER2) + ")");
        }
    }

    private void _updateColorButtonDrawable() {
        final ShapeDrawable color = super.updateColorButtonDrawable();
        color.getPaint().setColor(_shape.getStrokeColor());

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
        _shape.setColor(color);
        _drawPrefs.setShapeColor(color);
        _shape.setFillColor(Color.argb(_alpha, Color.red(color),
                Color.green(color), Color.blue(color)));
        if (_shape.getShapeMarker() != null)
            _shape.getShapeMarker().refresh(_mapView
                    .getMapEventDispatcher(), null, getClass());
        _updateColorButtonDrawable();
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

    @Override
    public void refresh() {
        attachmentManager.refresh();
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
        if (_center != null)
            _center.setHeight(height);
    }

    @Override
    protected void sendSelected(final String uid) {
        if (attachmentManager != null)
            attachmentManager.send();
        else
            super.sendSelected(uid);
    }

}
