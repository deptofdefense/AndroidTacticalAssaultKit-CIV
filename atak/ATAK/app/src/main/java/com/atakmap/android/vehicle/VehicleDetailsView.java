
package com.atakmap.android.vehicle;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ShapeDrawable;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import com.atakmap.android.maps.MapItem;
import com.atakmap.android.util.SimpleItemSelectedListener;
import com.atakmap.android.util.SimpleSeekBarChangeListener;

import com.atakmap.android.drawing.details.GenericDetailsView;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.coremap.conversions.AngleUtilities;
import com.atakmap.coremap.conversions.CoordinateFormat;
import com.atakmap.coremap.conversions.Span;
import com.atakmap.android.gui.CoordDialogView;
import com.atakmap.android.gui.RangeAndBearingTableHandler;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.coremap.conversions.SpanUtilities;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.android.drawing.tools.ShapeEditTool;
import com.atakmap.android.toolbar.ToolManagerBroadcastReceiver;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.app.R;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.coremap.maps.coords.NorthReference;

import com.atakmap.android.util.AttachmentManager;
import android.widget.ImageButton;

import java.text.DecimalFormat;

import com.atakmap.coremap.locale.LocaleUtil;

public class VehicleDetailsView extends GenericDetailsView implements
        VehicleShape.AzimuthChangedListener,
        VehicleShape.PositionChangedListener, View.OnClickListener {

    private AttachmentManager attachmentManager;
    private ImageButton _attachmentButton;

    private Button _modelButton;
    private Spinner _modelSpinner;
    private Button _azimuthButton;
    private TextView _dimensions;
    private VehicleShape _shape;

    public VehicleDetailsView(Context context) {
        super(context);
    }

    public VehicleDetailsView(Context context, final AttributeSet inAtr) {
        super(context, inAtr);
    }

    @Override
    public boolean setItem(MapView mapView, MapItem item) {
        if (!(item instanceof VehicleShape))
            return false;
        super.setItem(mapView, item);
        _shape = (VehicleShape) item;
        _init();
        return true;
    }

    /**
     * Callback for when the Dropdown closes. Updates the circle's meta data if something has
     * changed.
     */
    @Override
    public void onClose() {
        attachmentManager.cleanup();

        Intent intent = new Intent();
        intent.setAction(ToolManagerBroadcastReceiver.END_TOOL);
        intent.putExtra("tool", ShapeEditTool.TOOL_IDENTIFIER);
        AtakBroadcast.getInstance().sendBroadcast(intent);

        _shape.removeAzimuthChangedListener(this);
        _shape.removePositionChangedListener(this);
    }

    /**
     * *************************** PRIVATE METHODS ***************************
     */

    private void _init() {
        _nameEdit = findViewById(R.id.nameEdit);
        _remarksLayout = findViewById(R.id.remarksLayout);
        _noGps = findViewById(R.id.vehicleRangeBearingNoGps);
        rabtable = new RangeAndBearingTableHandler(this);
        _centerButton = findViewById(R.id.vehicleCenterButton);
        _colorButton = findViewById(R.id.vehicleColorButton);
        ImageButton sendButton = findViewById(
                R.id.vehicleSendButton);
        _modelButton = findViewById(R.id.vehicleModel);
        _modelSpinner = findViewById(R.id.vehicleSpinner);
        _transSeek = findViewById(R.id.vehicleTransparencySeek);
        View transView = findViewById(R.id.vehicleTransparencyView);
        _dimensions = findViewById(R.id.vehicleDimensions);
        _azimuthButton = findViewById(R.id.vehicleAzimuth);

        _nameEdit.setText(_shape.getTitle());

        // Vehicle model
        _modelButton.setText(_shape.getMetaString("vehicle_model", ""));

        // Update views for azimuth and dimensions
        updateDisplay();

        // Save an instance of the remarks, so we know if they changed when the dropdown closes
        _remarksLayout.setText(_shape.getMetaString("remarks", ""));

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

        // Depending on whether or not the shape is closed, the UI will change
        // shouldn't be able to move the center of a shape, or to edit the transparency
        // because there is no fill color.
        if (!_shape.isClosed()) {
            transView.setVisibility(View.GONE);
            _centerButton.setEnabled(false);
        } else {

            _centerButton.setOnClickListener(this);

            _alpha = _shape.getFillColor(true) >>> 24;
            _transSeek.setProgress(_alpha);

            _transSeek
                    .setOnSeekBarChangeListener(
                            new SimpleSeekBarChangeListener() {
                                @Override
                                public void onProgressChanged(SeekBar seekBar,
                                        int progress, boolean fromUser) {
                                    if (fromUser) {
                                        int color = _shape.getFillColor(true);
                                        _alpha = progress;
                                        _shape.setFillColor(Color.argb(_alpha,
                                                Color.red(color),
                                                Color.green(color),
                                                Color.blue(color)));
                                        _drawPrefs.setFillAlpha(progress);
                                    }
                                }
                            });
        }

        _updateColorButtonDrawable();
        _colorButton.setOnClickListener(this);
        sendButton.setOnClickListener(this);
        _modelButton.setOnClickListener(this);
        _azimuthButton.setOnClickListener(this);
        _shape.addAzimuthChangedListener(this);
        _shape.addPositionChangedListener(this);

        _attachmentButton = this
                .findViewById(R.id.cotInfoAttachmentsButton);

        if (attachmentManager == null)
            attachmentManager = new AttachmentManager(_mapView,
                    _attachmentButton);
        attachmentManager.setMapItem(_shape);

    }

    @Override
    public void onClick(View v) {
        int i1 = v.getId();
        if (i1 == R.id.vehicleCenterButton) {
            AlertDialog.Builder b = new AlertDialog.Builder(getContext());
            final CoordDialogView coordView = (CoordDialogView) LayoutInflater
                    .from(getContext()).inflate(
                            R.layout.draper_coord_dialog, _mapView, false);
            b.setTitle(R.string.point_dropper_text6);
            b.setView(coordView);
            b.setPositiveButton(R.string.ok, null);
            b.setNegativeButton(R.string.cancel, null);
            coordView.setParameters(
                    _shape.getAnchorItem().getGeoPointMetaData(),
                    _mapView.getPoint(), _cFormat);

            final AlertDialog ad = b.create();
            ad.show();
            ad.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(
                    new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            // On click get the geopoint and elevation double in ft
                            GeoPointMetaData p = coordView.getPoint();
                            CoordinateFormat cf = coordView.getCoordFormat();
                            CoordDialogView.Result result = coordView
                                    .getResult();
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
                                GeoPointMetaData gp = _shape.getAnchorItem()
                                        .getGeoPointMetaData();

                                // update the altitude
                                gp = GeoPointMetaData.wrap(
                                        new GeoPoint(gp.get().getLatitude(),
                                                gp.get()
                                                        .getLongitude(),
                                                p.get().getAltitude(),
                                                gp.get().getCE(),
                                                gp.get().getLE()),
                                        gp.getGeopointSource(),
                                        p.getAltitudeSource());
                                _shape.getAnchorItem().setPoint(gp);

                                _centerButton.setText(coordView
                                        .getFormattedString());

                                ATAKUtilities.scaleToFit(_shape);
                            }
                            ad.dismiss();
                        }
                    });

            // Set vehicle direction
        } else if (i1 == R.id.vehicleAzimuth) {
            AlertDialog.Builder b;
            DecimalFormat decFormat = LocaleUtil.getDecimalFormat("#.##");
            final NorthReference northRef = getNorthReferencePref();
            final EditText input = new EditText(getContext());
            input.setInputType(InputType.TYPE_CLASS_NUMBER
                    | InputType.TYPE_NUMBER_FLAG_SIGNED
                    | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            input.setText(decFormat.format(AngleUtilities.roundDeg(
                    _shape.getAzimuth(northRef), 2)));
            b = new AlertDialog.Builder(getContext());
            b.setTitle(getContext().getString(R.string.enter)
                    + northRef.getName() + " "
                    + getContext().getString(R.string.azimuth));
            b.setView(input);
            b.setPositiveButton(R.string.ok,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int w) {
                            double deg;
                            try {
                                deg = Double.parseDouble(input.getText()
                                        .toString());
                            } catch (Exception e) {
                                deg = 0;
                            }
                            _shape.setAzimuth(deg, northRef);
                            _azimuthButton.setText(String.format(
                                    getResources().getString(R.string.deg),
                                    AngleUtilities.roundDeg(
                                            _shape.getAzimuth(northRef), 2),
                                    northRef.getAbbrev()));
                        }
                    });
            b.setNegativeButton(R.string.cancel, null);
            b.show();

            // Select vehicle model
        } else if (i1 == R.id.vehicleModel) {
            String[] blocks = VehicleBlock.getBlocks();
            final ArrayAdapter<String> adapter = new ArrayAdapter<>(
                    getContext(), R.layout.spinner_text_view_dark,
                    blocks);
            adapter.setDropDownViewResource(
                    android.R.layout.simple_spinner_dropdown_item);

            // Create model list
            String currModel = _shape.getVehicleModel();
            int currPos = 0;
            for (int i = 0; i < blocks.length; i++) {
                if (currModel.equals(blocks[i])) {
                    currPos = i;
                    break;
                }
            }
            _modelSpinner.setAdapter(adapter);
            _modelSpinner.setSelection(currPos);
            _modelSpinner.performClick();
            _modelSpinner.setOnItemSelectedListener(
                    new SimpleItemSelectedListener() {
                        @Override
                        public void onItemSelected(AdapterView<?> parent,
                                View view,
                                int position, long id) {
                            String oldModel = _shape.getVehicleModel();
                            String newModel = adapter.getItem(position);
                            if (newModel == null
                                    || oldModel.equals(newModel))
                                return;
                            _shape.setVehicleModel(newModel);
                            _shape.setTitle(_shape.getTitle()
                                    .replace(oldModel, newModel));
                            _modelButton.setText(newModel);
                            _nameEdit.setText(_nameEdit.getText()
                                    .toString()
                                    .replace(oldModel, newModel));
                            updateDisplay();
                        }

                    });

            // Set vehicle color
        } else if (i1 == R.id.vehicleColorButton) {
            _onColorSelected();

            // Send vehicle
        } else if (i1 == R.id.vehicleSendButton) {
            sendSelected(_shape.getUID());

        }
    }

    private void updateDisplay() {
        // Center
        _centerButton.setText(_unitPrefs.formatPoint(_shape.getCenter(), true));

        // Dimensions
        Span unit = _unitPrefs.getRangeUnits(1);
        double length = _shape.getMetaDouble("length", 0.0);
        double width = _shape.getMetaDouble("width", 0.0);
        double height = _shape.getHeight();
        if (Double.isNaN(height))
            height = 0.0;

        _dimensions.setText(String.format(getResources().getString(
                R.string.vehicle_dimensions),
                SpanUtilities.convert(length, Span.METER, unit),
                SpanUtilities.convert(width, Span.METER, unit),
                SpanUtilities.convert(height, Span.METER, unit),
                unit.getAbbrev()));

        // Azimuth - metadata is in TRUE north
        NorthReference northRef = getNorthReferencePref();
        _azimuthButton.setText(String.format(
                getResources().getString(R.string.deg),
                AngleUtilities.roundDeg(_shape.getAzimuth(northRef), 2),
                northRef.getAbbrev()));
    }

    private void _updateColorButtonDrawable() {
        final ShapeDrawable color = super.updateColorButtonDrawable();
        color.getPaint().setColor(_shape.getStrokeColor(true));

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
        _shape.setFillColor(Color.argb(_alpha, Color.red(color),
                Color.green(color), Color.blue(color)));
        _updateColorButtonDrawable();
    }

    @Override
    public void refresh() {
        attachmentManager.refresh();
    }

    @Override
    protected void _onHeightSelected() {
    }

    @Override
    protected void heightSelected(double height, Span u, double h) {
    }

    private NorthReference getNorthReferencePref() {
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(getContext());
        String northPref = prefs.getString("rab_north_ref_pref",
                String.valueOf(NorthReference.MAGNETIC.getValue()));
        return (northPref.equals("1") ? NorthReference.MAGNETIC
                : NorthReference.TRUE);
    }

    @Override
    public void onChange(VehicleShape shape, double trueDeg) {
        updateDisplay();
    }

    @Override
    public void onChange(VehicleShape shape, GeoPoint oldPos,
            GeoPoint newPos) {
        updateDisplay();
    }

    @Override
    protected void sendSelected(final String uid) {
        if (attachmentManager != null)
            attachmentManager.send();
        else
            super.sendSelected(uid);
    }

}
