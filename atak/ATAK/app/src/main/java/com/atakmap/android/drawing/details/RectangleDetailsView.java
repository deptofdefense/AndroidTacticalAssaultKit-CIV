
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
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;

import android.text.Editable;

import com.atakmap.android.drawing.DrawingPreferences;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.preference.UnitPreferences;
import com.atakmap.android.util.AfterTextChangedWatcher;
import com.atakmap.android.util.SimpleSeekBarChangeListener;
import com.atakmap.android.drawing.tools.DrawingRectangleEditTool;
import com.atakmap.android.editableShapes.Rectangle;
import com.atakmap.android.gui.CoordDialogView;
import com.atakmap.android.gui.RangeAndBearingTableHandler;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.maps.Shape;
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

import android.widget.ImageButton;

public class RectangleDetailsView extends GenericDetailsView implements
        View.OnClickListener, CompoundButton.OnCheckedChangeListener,
        Rectangle.OnPointsChangedListener,
        PointMapItem.OnPointChangedListener {

    public static final String TAG = "RectangleDetailsView";
    private AttachmentManager attachmentManager;
    private ImageButton _attachmentButton;
    private LinearLayout _sendView;
    private LinearLayout _editView;
    private Button _editButton;
    private Button _undoButton;
    private Button _endButton;
    private Button _areaTF;
    private CheckBox _tacticalCB;

    private ImageButton _sendButton;
    private Rectangle _rect;
    private UnitPreferences _unitPrefs;
    private DrawingPreferences _drawPrefs;

    @Override
    public void onPointChanged(PointMapItem item) {
        if (_rect == null || item != _rect.getAnchorItem()) {
            item.removeOnPointChangedListener(this);
            return;
        }
        updateStats();
    }

    @Override
    public void onPointsChanged(Shape shape) {
        if (shape != _rect) {
            shape.removeOnPointsChangedListener(this);
            return;
        }
        updateStats();
    }

    private void updateStats() {
        _mapView.post(new Runnable() {
            @Override
            public void run() {
                if (_rect == null)
                    return;
                _centerButton.setText(_unitPrefs.formatPoint(
                        _rect.getCenter(), true));

                double rectL = _rect.getLength();
                double rectW = _rect.getWidth();

                int areaUnits = _unitPrefs.getAreaSystem();

                int spanUnits = (areaUnits == Area.AC) ? Span.ENGLISH
                        : areaUnits;
                final String l = SpanUtilities.formatType(spanUnits,
                        rectL, Span.METER);
                final String w = SpanUtilities.formatType(spanUnits,
                        rectW, Span.METER);
                _areaTF.setText("(" + l + " x " + w + ")\n" +
                        AreaUtilities.formatArea(areaUnits, rectW * rectL,
                                Area.METER2));
            }
        });
    }

    /**
     * *************************** CONSTRUCTOR ***************************
     */

    public RectangleDetailsView(Context context) {
        super(context);
    }

    public RectangleDetailsView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * *************************** PUBLIC METHODS ***************************
     */

    @Override
    public boolean setItem(MapView mapView, MapItem rectangle) {
        if (!(rectangle instanceof Rectangle))
            return false;
        super.setItem(mapView, rectangle);
        _rect = (Rectangle) rectangle;
        _rect.addOnPointsChangedListener(this);
        PointMapItem item = _rect.getAnchorItem();
        if (item != null)
            item.addOnPointChangedListener(this);
        _init();
        return true;
    }

    /**
     * Listener for when the Dropdown closes. Updates the rectangle's meta data if something has
     * changed.
     */
    @Override
    public void onClose() {
        super.onClose();

        _rect.removeOnPointsChangedListener(this);
        PointMapItem item = _rect.getAnchorItem();
        if (item != null)
            item.removeOnPointChangedListener(this);

        // Update name if the user changed it.
        String name = _nameEdit.getText().toString();
        if (!name.equals(_prevName))
            _rect.setTitle(name);
        // Update remarks if the user changed them.
        String remarks = _remarksLayout.getText();
        if (!remarks.equals(_prevRemarks)) {
            _rect.setMetaString("remarks", remarks);
        }

        attachmentManager.cleanup();

        Intent intent = new Intent();
        intent.setAction(ToolManagerBroadcastReceiver.END_TOOL);
        intent.putExtra("tool", DrawingRectangleEditTool.TOOL_IDENTIFIER);
        AtakBroadcast.getInstance().sendBroadcast(intent);
    }

    @Override
    protected String getEditTool() {
        return DrawingRectangleEditTool.TOOL_IDENTIFIER;
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
        _unitPrefs = new UnitPreferences(_mapView);
        _drawPrefs = new DrawingPreferences(_mapView);
        _tacticalCB = findViewById(R.id.tacticalOverlayCB);
        _nameEdit = this.findViewById(R.id.drawingRectNameEdit);
        _remarksLayout = this.findViewById(R.id.remarksLayout);
        _centerButton = this
                .findViewById(R.id.drawingRectCenterButton);
        _colorButton = this
                .findViewById(R.id.drawingRectColorButton);
        _heightButton = this
                .findViewById(R.id.drawingRectHeightButton);
        _sendButton = this
                .findViewById(R.id.drawingRectSendButton);
        _transSeek = this
                .findViewById(R.id.drawingRectTransparencySeek);
        _thickSeek = this
                .findViewById(R.id.drawingShapeStrokeSeek);
        _noGps = this.findViewById(R.id.drawingRectRangeBearingNoGps);
        rabtable = new RangeAndBearingTableHandler(this);

        _sendView = this.findViewById(R.id.drawingRectSendView);
        _editView = this.findViewById(R.id.drawingRectEditView);
        _editButton = this.findViewById(R.id.drawingRectEditButton);
        _undoButton = this.findViewById(R.id.drawingRectUndoButton);
        _endButton = this
                .findViewById(R.id.drawingRectEndEditingButton);

        _areaTF = this.findViewById(R.id.rectAreaText);

        _tacticalCB.setChecked(_rect.showTacticalOverlay());
        _tacticalCB.setOnCheckedChangeListener(this);

        _editButton.setOnClickListener(this);
        _endButton.setOnClickListener(this);
        _undoButton.setOnClickListener(this);

        // Save an instance of the name, so we know if it changed when the dropdown closes
        _prevName = _rect.getTitle();
        _nameEdit.setText(_prevName);

        _nameEdit.addTextChangedListener(new AfterTextChangedWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (_rect != null) {
                    String name = _nameEdit.getText().toString();
                    _rect.setTitle(name);
                    _rect.refresh(MapView.getMapView()
                            .getMapEventDispatcher(), null, this.getClass());
                }
            }
        });

        // Save an instance of the remarks, so we know if they changed when the dropdown closes
        _prevRemarks = _rect.getMetaString("remarks", "");
        _remarksLayout.setText(_prevRemarks);

        _remarksLayout.addTextChangedListener(new AfterTextChangedWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (_rect != null) {
                    String remarks = _remarksLayout.getText();
                    if (!remarks.equals(_prevRemarks))
                        _rect.setMetaString("remarks", remarks);
                }
            }
        });

        // Update the R & B text
        PointMapItem device = ATAKUtilities.findSelf(_mapView);
        // It's possible that we don't have GPS and therefore don't have a controller point
        if (device != null) {
            _noGps.setVisibility(View.GONE);

            rabtable.update(device, _rect.getCenter().get());

            rabtable.setVisibility(View.VISIBLE);
        } else {
            _noGps.setVisibility(View.VISIBLE);
            rabtable.setVisibility(View.GONE);
        }

        // keep the calculations and processes in the detail page, but disable the 
        // view for 3.2 as per JS.   Maybe this becomes a preference.
        _noGps.setVisibility(View.GONE);
        rabtable.setVisibility(View.GONE);

        _centerButton.setOnClickListener(this);

        _updateColorButtonDrawable();

        _colorButton.setOnClickListener(this);

        double height = _rect.getHeight();
        Span unit = getUnitSpan(_rect);
        if (!Double.isNaN(height)) {
            _heightButton.setText(SpanUtilities.format(height, Span.METER,
                    unit));
        } else {
            _heightButton.setText("-- " + unit.getAbbrev());
        }

        _heightButton.setOnClickListener(this);
        _sendButton.setOnClickListener(this);

        _thickSeek.setOnSeekBarChangeListener(
                new SimpleSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar,
                            int progress, boolean fromUser) {
                        double strokeWeight = 1 + (progress / 10d);
                        _rect.setStrokeWeight(strokeWeight);
                        _drawPrefs.setStrokeWeight(strokeWeight);
                    }
                });

        _transSeek
                .setOnSeekBarChangeListener(new SimpleSeekBarChangeListener() {

                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress,
                            boolean fromUser) {
                        if (fromUser) {
                            int color = _rect.getFillColor();
                            _alpha = progress;
                            _rect.setFillColor(Color.argb(_alpha,
                                    Color.red(color),
                                    Color.green(color),
                                    Color.blue(color)));
                            _drawPrefs.setFillAlpha(_alpha);
                        }

                    }

                });

        _alpha = _rect.getFillColor() >>> 24;
        _transSeek.setProgress(_alpha);

        _thickSeek.setProgress((int) (_rect.getStrokeWeight() * 10) - 10);

        _attachmentButton = this
                .findViewById(R.id.cotInfoAttachmentsButton);

        if (attachmentManager == null)
            attachmentManager = new AttachmentManager(_mapView,
                    _attachmentButton);
        attachmentManager.setMapItem(_rect);

        setupAreaButton(_areaTF, new Runnable() {
            public void run() {
                updateStats();
            }
        });

        onPointsChanged(_rect);

    }

    @Override
    public void onClick(View v) {
        if (v == _centerButton)
            _onCenterSelected();
        else if (v == _colorButton)
            _onColorSelected();
        else if (v == _heightButton)
            _onHeightSelected();
        else if (v == _sendButton)
            sendSelected(_rect.getUID());
        else if (v == _editButton)
            startEditingMode();
        else if (v == _endButton)
            endEditingMode();
        else if (v == _undoButton)
            undoToolEdit();
    }

    @Override
    public void onCheckedChanged(CompoundButton cb, boolean checked) {
        if (cb == _tacticalCB) {
            _rect.showTacticalOverlay(checked);
        }
    }

    private void _updateColorButtonDrawable() {
        final ShapeDrawable color = super.updateColorButtonDrawable();
        color.getPaint().setColor(_rect.getStrokeColor());

        post(new Runnable() {
            @Override
            public void run() {
                _colorButton.setImageDrawable(color);
            }
        });

    }

    @Override
    protected void _onColorSelected(int color, String label) {
        _drawPrefs.setShapeColor(color);
        _rect.setColor(Color.argb(_alpha, Color.red(color),
                Color.green(color), Color.blue(color)), true);
        if (_rect.getAnchorItem() != null)
            _rect.getAnchorItem().refresh(_mapView
                    .getMapEventDispatcher(), null, getClass());
        _updateColorButtonDrawable();
    }

    private void _onCenterSelected() {
        AlertDialog.Builder b = new AlertDialog.Builder(getContext());
        LayoutInflater inflater = LayoutInflater.from(getContext());
        // custom view that allows entry of geo via mgrs, dms, dm, and decimal degrees
        final CoordDialogView coordView = (CoordDialogView) inflater.inflate(
                R.layout.draper_coord_dialog, null);
        b.setTitle("Enter Rectangle Center Point: ");
        b.setView(coordView);
        b.setPositiveButton(R.string.ok, null);
        b.setNegativeButton(R.string.cancel, null);
        GeoPointMetaData g = _rect.getCenter();
        coordView.setParameters(g, _mapView.getPoint(), _cFormat);
        // Overrides setPositive button onClick to keep the window open when the input is invalid.
        final AlertDialog locDialog = b.create();
        locDialog.show();
        locDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
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
                        if (result == CoordDialogView.Result.VALID_CHANGED) {// set the point if it
                            // has changed
                            _rect.move(_rect.getCenter(), p, true);
                            _centerButton.setText(coordView
                                    .getFormattedString());

                            dropDown.getMapView()
                                    .getMapController()
                                    .panTo(_rect.getCenter().get(), true);
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
        createHeightDialog(_rect, R.string.enter_rectangle_height, new Span[] {
                Span.METER, Span.YARD, Span.FOOT
        });
    }

    @Override
    protected void heightSelected(double height, Span u, double h) {
        // This is always saved as a string in feet for some reason
        _rect.setHeight(height);
        _rect.setMetaInteger("height_unit", u.getValue());
        _heightButton.setText(SpanUtilities.format(h, u, 2));
    }

    @Override
    protected void sendSelected(final String uid) {
        if (attachmentManager != null)
            attachmentManager.send();
        else
            super.sendSelected(uid);
    }

}
