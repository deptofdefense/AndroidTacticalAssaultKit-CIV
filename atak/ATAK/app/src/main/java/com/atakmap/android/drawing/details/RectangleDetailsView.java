
package com.atakmap.android.drawing.details;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;

import android.text.Editable;

import com.atakmap.android.drawing.DrawingPreferences;
import com.atakmap.android.gui.ShapeColorButton;
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
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.map.CameraController;

import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import java.text.DecimalFormat;

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
    private Button _lengthButton;
    private Button _widthButton;
    private Button _areaTF;
    private Button _perimeterTF;
    private CheckBox _tacticalCB;

    private ImageButton _sendButton;
    private Rectangle _rect;
    private UnitPreferences _unitPrefs;
    private DrawingPreferences _drawPrefs;

    private static final DecimalFormat DEC_FMT_2 = LocaleUtil
            .getDecimalFormat("#.##");

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

                int areaUnits = _unitPrefs.getAreaSystem();

                _areaTF.setText(
                        AreaUtilities.formatArea(areaUnits, _rect.getArea(),
                                Area.METER2));

                int rangeSystem = _unitPrefs.getRangeSystem();
                double range = _rect.getPerimeterOrLength();

                _perimeterTF.setText(
                        SpanUtilities.formatType(rangeSystem, range, Span.METER,
                                false));

                updateLengthWidth();
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
        ShapeColorButton colorBtn = findViewById(R.id.drawingRectColorButton);
        setupShapeColorButton(colorBtn, _rect);

        _lengthButton = findViewById(R.id.lengthButton);
        _widthButton = findViewById(R.id.widthButton);

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
        _perimeterTF = this.findViewById(R.id.rectPerimeterText);

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

        double height = _rect.getHeight();
        Span unit = _unitPrefs.getAltitudeUnits();
        if (!Double.isNaN(height)) {
            _heightButton.setText(SpanUtilities.format(height, Span.METER,
                    unit));
        } else {
            _heightButton.setText("-- " + unit.getAbbrev());
        }

        updateLengthWidth();

        _lengthButton.setOnClickListener(this);
        _widthButton.setOnClickListener(this);
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
                    public void onProgressChanged(SeekBar seekBar, int alpha,
                            boolean fromUser) {
                        if (fromUser) {
                            _rect.setFillAlpha(alpha);
                            _drawPrefs.setFillAlpha(alpha);
                        }

                    }

                });

        _transSeek.setProgress(Color.alpha(_rect.getFillColor()));

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

        setupLengthButton(_perimeterTF, new Runnable() {
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

        // Edit rectangle length
        else if (v == _lengthButton) {
            showDialog(R.string.length, _rect.getLength(),
                    new DialogCallback() {
                        @Override
                        public void onEntered(double size) {
                            _rect.setLength(size);
                        }
                    });
        }

        // Edit rectangle width
        else if (v == _widthButton) {
            showDialog(R.string.width, _rect.getWidth(),
                    new DialogCallback() {
                        @Override
                        public void onEntered(double size) {
                            _rect.setWidth(size);
                        }
                    });
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton cb, boolean checked) {
        if (cb == _tacticalCB) {
            _rect.showTacticalOverlay(checked);
        }
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
                            CameraController.Programmatic.panTo(
                                    dropDown.getMapView().getRenderer3(),
                                    _rect.getCenter().get(), true);
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
    protected void sendSelected(final String uid) {
        if (attachmentManager != null)
            attachmentManager.send();
        else
            super.sendSelected(uid);
    }

    /**
     * Show a dialog for entering diameter/axis
     * @param titleId Title string resource ID
     * @param diameter Diameter in meters
     * @param cb Callback fired when setting radius
     */
    private void showDialog(@StringRes int titleId, double diameter,
            @NonNull
            final DialogCallback cb) {
        LayoutInflater inflater = LayoutInflater.from(getContext());
        final LinearLayout layout = (LinearLayout) inflater.inflate(
                R.layout.drawing_distance_input, _mapView, false);
        final EditText input = layout
                .findViewById(R.id.drawingDistanceInput);
        final Spinner units = layout
                .findViewById(R.id.drawingDistanceUnits);

        final ArrayAdapter<Span> adapter = new ArrayAdapter<>(getContext(),
                R.layout.spinner_text_view_dark, Span.values());
        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        units.setAdapter(adapter);

        Span span = _unitPrefs.getRangeUnits(diameter);
        int start = adapter.getPosition(span);
        units.setSelection(start);
        input.setText(DEC_FMT_2.format(SpanUtilities.convert(diameter,
                Span.METER, span)));
        input.selectAll();

        AlertDialog.Builder b = new AlertDialog.Builder(getContext());
        b.setMessage(titleId);
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
                new OnClickListener() {
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
                            Toast.makeText(getContext(),
                                    "number needs to be greater than 0",
                                    Toast.LENGTH_LONG).show();
                            return;
                        }
                        cb.onEntered(r);
                        _unitPrefs.setRangeSystem(u.getType());
                        refresh();
                        updateLengthWidth();
                        d.dismiss();
                    }
                });
        input.requestFocus();
    }

    /**
     * Interface used as a callback to the diameter entry dialog
     */
    private interface DialogCallback {
        /**
         * A size has been set using the dialog
         * @param size the size in meters
         */
        void onEntered(double size);
    }

    private void updateLengthWidth() {
        Span rangeUnit = _unitPrefs.getRangeUnits(_rect.getLength());
        _lengthButton
                .setText(SpanUtilities.format(_rect.getLength(), Span.METER,
                        rangeUnit));
        rangeUnit = _unitPrefs.getRangeUnits(_rect.getWidth());
        _widthButton.setText(SpanUtilities.format(_rect.getWidth(), Span.METER,
                rangeUnit));
    }
}
