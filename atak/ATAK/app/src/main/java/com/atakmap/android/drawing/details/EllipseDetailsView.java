
package com.atakmap.android.drawing.details;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Toast;

import com.atakmap.android.drawing.mapItems.DrawingEllipse;
import com.atakmap.android.drawing.tools.DrawingEllipseEditTool;
import com.atakmap.android.gui.CoordDialogView;
import com.atakmap.android.gui.RangeAndBearingTableHandler;
import com.atakmap.android.gui.ShapeColorButton;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Shape;
import com.atakmap.android.toolbar.ToolManagerBroadcastReceiver;
import com.atakmap.android.util.AfterTextChangedWatcher;
import com.atakmap.android.util.AttachmentManager;
import com.atakmap.android.util.Circle;
import com.atakmap.android.util.SimpleSeekBarChangeListener;
import com.atakmap.app.R;
import com.atakmap.coremap.conversions.AngleUtilities;
import com.atakmap.coremap.conversions.Area;
import com.atakmap.coremap.conversions.AreaUtilities;
import com.atakmap.coremap.conversions.CoordinateFormat;
import com.atakmap.coremap.conversions.Span;
import com.atakmap.coremap.conversions.SpanUtilities;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.coremap.maps.coords.NorthReference;
import com.atakmap.map.CameraController;

import java.text.DecimalFormat;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

/**
 * Default details view used for {@link DrawingEllipse}
 */
public class EllipseDetailsView extends GenericDetailsView implements
        View.OnClickListener, Shape.OnPointsChangedListener {

    public static final String TAG = "EllipseDetailsView";

    private static final DecimalFormat DEC_FMT_2 = LocaleUtil
            .getDecimalFormat("#.##");

    private final Context _context;

    private AttachmentManager attachmentManager;

    private LinearLayout _sendView;
    private LinearLayout _editView;
    private Button _areaTV;
    private Button _circumferenceTV;

    private Button _lengthButton;
    private Button _widthButton;
    private Button _headingButton;

    private DrawingEllipse _ellipse;

    /**
     * *************************** CONSTRUCTOR ***************************
     */

    public EllipseDetailsView(Context context) {
        this(context, null);
    }

    public EllipseDetailsView(Context context, AttributeSet attrs) {
        super(context, attrs);
        _context = context;
    }

    /**
     * *************************** PUBLIC METHODS ***************************
     */

    @Override
    public boolean setItem(MapView mapView, MapItem circle) {
        if (_ellipse != null)
            _ellipse.removeOnPointsChangedListener(this);
        if (!(circle instanceof DrawingEllipse))
            return false;
        super.setItem(mapView, circle);
        _ellipse = (DrawingEllipse) circle;
        _ellipse.addOnPointsChangedListener(this);
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
        if (_ellipse == null)
            return;

        attachmentManager.cleanup();
        // Update the name if the user changed it.
        String name = _nameEdit.getText().toString();
        if (!name.equals(_prevName))
            _ellipse.setTitle(name);

        // Update the remarks if the user changed them.
        String remarks = _remarksLayout.getText();
        if (!remarks.equals(_prevRemarks))
            _ellipse.setMetaString("remarks", remarks);

        attachmentManager.cleanup();

        Intent intent = new Intent();
        intent.setAction(ToolManagerBroadcastReceiver.END_TOOL);
        intent.putExtra("tool", DrawingEllipseEditTool.TOOL_IDENTIFIER);
        AtakBroadcast.getInstance().sendBroadcast(intent);

        setItem(_mapView, null);
    }

    @Override
    protected String getEditTool() {
        return DrawingEllipseEditTool.TOOL_IDENTIFIER;
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
        if (_ellipse == null)
            return;
        _nameEdit = findViewById(R.id.nameEdit);
        _remarksLayout = findViewById(R.id.remarksLayout);
        _noGps = findViewById(R.id.rabNoGps);
        rabtable = new RangeAndBearingTableHandler(this);
        ShapeColorButton colorBtn = findViewById(R.id.colorButton);
        setupShapeColorButton(colorBtn, _ellipse);
        _centerButton = findViewById(R.id.centerButton);
        _lengthButton = findViewById(R.id.lengthButton);
        _widthButton = findViewById(R.id.widthButton);
        _headingButton = findViewById(R.id.headingButton);
        _heightButton = findViewById(R.id.heightButton);
        _transSeek = findViewById(R.id.transparencySeek);
        _thickSeek = findViewById(R.id.strokeSeek);

        _sendView = findViewById(R.id.sendView);
        _editView = findViewById(R.id.editView);

        findViewById(R.id.sendButton).setOnClickListener(this);
        findViewById(R.id.editButton).setOnClickListener(this);
        findViewById(R.id.undoButton).setOnClickListener(this);
        findViewById(R.id.endButton).setOnClickListener(this);

        _areaTV = findViewById(R.id.areaText);
        _circumferenceTV = findViewById(R.id.circumferenceText);

        ImageButton attButton = findViewById(R.id.attachmentsButton);

        if (attachmentManager == null)
            attachmentManager = new AttachmentManager(_mapView, attButton);
        attachmentManager.setMapItem(_ellipse);

        _thickSeek.setProgress((int) (_ellipse.getStrokeWeight() * 10) - 10);

        _thickSeek.setOnSeekBarChangeListener(
                new SimpleSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar,
                            int progress, boolean fromUser) {
                        double strokeWeight = (progress / 10d) + 1;
                        _ellipse.setStrokeWeight(strokeWeight);
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
                        _ellipse.setFillAlpha(alpha);
                        _drawPrefs.setFillAlpha(alpha);
                    }
                });

        // Save an instance of the name, so we know if it changed when the dropdown closes
        _prevName = _ellipse.getTitle();
        _nameEdit.setText(_prevName);
        _nameEdit.addTextChangedListener(new AfterTextChangedWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                _ellipse.setTitle(s.toString());
            }
        });

        // Save an instance of the remarks, so we know if they changed when the dropdown closes
        _prevRemarks = _ellipse.getMetaString("remarks", "");
        _remarksLayout.setText(_prevRemarks);
        _remarksLayout.addTextChangedListener(new AfterTextChangedWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                _ellipse.setMetaString("remarks", s.toString());
            }
        });

        _transSeek.setProgress(Color.alpha(_ellipse.getFillColor()));

        _heightButton.setOnClickListener(this);
        _lengthButton.setOnClickListener(this);
        _widthButton.setOnClickListener(this);
        _headingButton.setOnClickListener(this);

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
        if (_ellipse == null)
            return;

        int id = v.getId();

        // Send circle
        if (id == R.id.sendButton)
            sendSelected(_ellipse.getUID());

        // Edit circle
        else if (id == R.id.editButton)
            startEditingMode();

        // Undo circle edits
        else if (id == R.id.undoButton)
            undoToolEdit();

        // End circle edit mode
        else if (id == R.id.endButton)
            endEditingMode();

        // Edit center
        else if (v == _centerButton)
            onCenterSelected();

        // Edit minor radius
        else if (v == _lengthButton) {
            showDiameterDialog(R.string.length, _ellipse.getLength(),
                    new DiameterDialogCallback() {
                        @Override
                        public void onDiameterEntered(double diameter) {
                            _ellipse.setLength(diameter);
                        }
                    });
        }

        // Edit major radius
        else if (v == _widthButton) {
            showDiameterDialog(R.string.width, _ellipse.getWidth(),
                    new DiameterDialogCallback() {
                        @Override
                        public void onDiameterEntered(double diameter) {
                            _ellipse.setWidth(diameter);
                        }
                    });
        }

        // Edit heading
        else if (v == _headingButton)
            onHeadingSelected();

        // Edit height
        else if (v == _heightButton)
            _onHeightSelected();
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
        GeoPointMetaData center = _ellipse.getCenter();
        _centerButton.setText(_unitPrefs.formatPoint(center, true));

        // keep the calculations and processes in the detail page, but disable the
        // view for 3.2 as per JS.   Maybe this becomes a preference.
        _noGps.setVisibility(View.GONE);
        rabtable.setVisibility(View.GONE);

        // Height
        Span unit = _unitPrefs.getAltitudeUnits();
        String heightTxt = "-- " + unit.getAbbrev();
        double height = _ellipse.getHeight();
        if (!Double.isNaN(height))
            heightTxt = SpanUtilities.format(height, Span.METER, unit);
        _heightButton.setText(heightTxt);

        // Diameter
        _lengthButton.setText(SpanUtilities.formatType(
                _unitPrefs.getRangeSystem(), _ellipse.getLength(),
                Span.METER));
        _widthButton.setText(SpanUtilities.formatType(
                _unitPrefs.getRangeSystem(), _ellipse.getWidth(),
                Span.METER));
        _headingButton.setText(NorthReference.format(_ellipse.getAngle(),
                _ellipse.getCenterPoint(), _ellipse.getLength(),
                _unitPrefs.getBearingUnits(),
                _unitPrefs.getNorthReference(), 0));

        // Area
        // See ATAK-9517 - makes more sense to use the set radius units for area
        _areaTV.setText(AreaUtilities.formatArea(_unitPrefs.getAreaSystem(),
                _ellipse.getArea(),
                Area.METER2));

        int rangeSystem = _unitPrefs.getRangeSystem();
        double range = _ellipse.getPerimeterOrLength();
        _circumferenceTV.setText(
                SpanUtilities.formatType(rangeSystem, range, Span.METER,
                        false));

        _ellipse.refresh();
    }

    /**
     * Show a dialog for entering diameter/axis
     * @param titleId Title string resource ID
     * @param diameter Diameter in meters
     * @param cb Callback fired when setting radius
     */
    private void showDiameterDialog(@StringRes int titleId, double diameter,
            @NonNull
            final DiameterDialogCallback cb) {
        LayoutInflater inflater = LayoutInflater.from(_context);
        final LinearLayout layout = (LinearLayout) inflater.inflate(
                R.layout.drawing_distance_input, _mapView, false);
        final EditText input = layout
                .findViewById(R.id.drawingDistanceInput);
        final Spinner units = layout
                .findViewById(R.id.drawingDistanceUnits);

        final ArrayAdapter<Span> adapter = new ArrayAdapter<>(_context,
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

        AlertDialog.Builder b = new AlertDialog.Builder(_context);
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
                            Toast.makeText(_context,
                                    R.string.circle_warning_min_radius,
                                    Toast.LENGTH_LONG).show();
                            return;
                        } else if (r > Circle.MAX_RADIUS) {
                            Toast.makeText(_context,
                                    R.string.circle_warning_max_radius,
                                    Toast.LENGTH_LONG).show();
                            return;
                        }
                        cb.onDiameterEntered(r);
                        _unitPrefs.setRangeSystem(u.getType());
                        refresh();
                        d.dismiss();
                    }
                });
        input.requestFocus();
    }

    private void onCenterSelected() {
        AlertDialog.Builder b = new AlertDialog.Builder(_context);
        LayoutInflater inflater = LayoutInflater.from(_context);
        // custom view that allows entry of geo via mgrs, dms, and decimal degrees
        final CoordDialogView cdv = (CoordDialogView) inflater.inflate(
                R.layout.draper_coord_dialog, null);
        b.setTitle(R.string.center_location);
        b.setView(cdv);
        b.setPositiveButton(R.string.ok, null);
        b.setNegativeButton(R.string.cancel, null);
        cdv.setParameters(_ellipse.getCenter(), _mapView.getPoint(), _cFormat);

        // Overrides setPositive button onClick to keep the window open when the input is invalid.
        final AlertDialog locDialog = b.create();
        locDialog.show();
        locDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(
                new OnClickListener() {
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
                            _ellipse.setCenterPoint(p);
                            refresh();
                            CameraController.Programmatic.panTo(
                                    _mapView.getRenderer3(), p.get(), true);
                        }
                        locDialog.dismiss();
                    }
                });
    }

    /**
     * Prompt to set the heading of the ellipse
     */
    private void onHeadingSelected() {
        DecimalFormat decFormat = LocaleUtil.getDecimalFormat("#.##");
        final NorthReference northRef = _unitPrefs.getNorthReference();

        final GeoPoint center = _ellipse.getCenterPoint();
        final double length = _ellipse.getLength();
        double deg = _ellipse.getAngle();
        deg = NorthReference.convert(deg, center, length,
                NorthReference.TRUE, northRef);

        final EditText input = new EditText(_context);
        input.setInputType(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_SIGNED
                | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setText(decFormat.format(AngleUtilities.roundDeg(deg, 2)));
        AlertDialog.Builder b = new AlertDialog.Builder(_context);
        b.setTitle(_context.getString(R.string.enter)
                + northRef.getName() + " "
                + _context.getString(R.string.heading));
        b.setView(input);
        b.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface d, int w) {
                double deg;
                try {
                    deg = Double.parseDouble(input.getText()
                            .toString());
                } catch (Exception e) {
                    deg = 0;
                }

                deg = NorthReference.convert(deg, center, length, northRef,
                        NorthReference.TRUE);

                _ellipse.setAngle(deg);
                refresh();
            }
        });
        b.setNegativeButton(R.string.cancel, null);
        b.show();
    }

    @Override
    protected void _onHeightSelected() {
        createHeightDialog(_ellipse, R.string.circle_height, new Span[] {
                Span.METER, Span.YARD, Span.FOOT
        });
    }

    @Override
    protected void heightSelected(double height, Span u, double h) {
        super.heightSelected(height, u, h);
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

    /**
     * Interface used as a callback to the diameter entry dialog
     */
    private interface DiameterDialogCallback {

        /**
         * Diameter has been set using the dialog
         * @param diameter Diameter in meters
         */
        void onDiameterEntered(double diameter);
    }
}
