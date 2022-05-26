
package com.atakmap.android.vehicle.model.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.ShapeDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.atakmap.android.drawing.details.GenericDetailsView;
import com.atakmap.android.drawing.tools.ShapeEditTool;
import com.atakmap.android.editableShapes.Rectangle;
import com.atakmap.android.gui.CoordDialogView;
import com.atakmap.android.gui.RangeAndBearingTableHandler;
import com.atakmap.android.gui.ThemedSpinner;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.maps.Shape;
import com.atakmap.android.rubbersheet.maps.AbstractSheet;
import com.atakmap.android.rubbersheet.maps.RubberModel;
import com.atakmap.android.toolbar.ToolManagerBroadcastReceiver;
import com.atakmap.android.user.ExpandableGridView;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.android.util.AttachmentManager;
import com.atakmap.android.util.SimpleItemSelectedListener;
import com.atakmap.android.util.SimpleSeekBarChangeListener;
import com.atakmap.android.vehicle.model.VehicleModel;
import com.atakmap.android.vehicle.model.VehicleModelCache;
import com.atakmap.android.vehicle.model.VehicleModelEditTool;
import com.atakmap.android.vehicle.model.VehicleModelInfo;
import com.atakmap.app.R;
import com.atakmap.coremap.conversions.AngleUtilities;
import com.atakmap.coremap.conversions.CoordinateFormat;
import com.atakmap.coremap.conversions.Span;
import com.atakmap.coremap.conversions.SpanUtilities;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.coremap.maps.coords.NorthReference;

import java.text.DecimalFormat;
import java.util.List;

/**
 * Details view for vehicle models
 */
public class VehicleModelDetailsView extends GenericDetailsView implements
        Rectangle.OnPointsChangedListener, View.OnClickListener,
        CompoundButton.OnCheckedChangeListener,
        AbstractSheet.OnAlphaChangedListener,
        MapItem.OnMetadataChangedListener,
        RubberModel.OnChangedListener {

    private final Context _context;
    private final LayoutInflater _inf;
    private VehicleModel _vehicle;

    private AttachmentManager _attManager;
    private ImageView _modelIcon;
    private Button _modelButton;
    private Button _azimuthButton;
    private TextView _dimensions;
    private CheckBox _outlineCB;
    private View _optionsLayout, _editLayout;

    public VehicleModelDetailsView(Context context) {
        this(context, null);
    }

    public VehicleModelDetailsView(Context context, final AttributeSet inAtr) {
        super(context, inAtr);
        _context = context;
        _inf = LayoutInflater.from(context);
    }

    @Override
    public boolean setItem(MapView mapView, MapItem item) {
        if (!(item instanceof VehicleModel))
            return false;
        super.setItem(mapView, item);
        _vehicle = (VehicleModel) item;
        _init();
        return true;
    }

    /**
     * Callback for when the Dropdown closes. Updates the circle's meta data if something has
     * changed.
     */
    @Override
    public void onClose() {
        _attManager.cleanup();

        Intent intent = new Intent();
        intent.setAction(ToolManagerBroadcastReceiver.END_TOOL);
        intent.putExtra("tool", ShapeEditTool.TOOL_IDENTIFIER);
        AtakBroadcast.getInstance().sendBroadcast(intent);

        _vehicle.removeOnPointsChangedListener(this);
        _vehicle.removeChangeListener(this);
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
        _modelIcon = findViewById(R.id.vehicleIcon);
        _modelButton = findViewById(R.id.vehicleModel);
        _transSeek = findViewById(R.id.vehicleTransparencySeek);
        _dimensions = findViewById(R.id.vehicleDimensions);
        _azimuthButton = findViewById(R.id.vehicleAzimuth);
        _outlineCB = findViewById(R.id.vehicleOutline);
        _optionsLayout = findViewById(R.id.vehicleOptions);
        _editLayout = findViewById(R.id.editOptions);

        _nameEdit.setText(_vehicle.getTitle());

        // Vehicle model
        VehicleModelInfo info = _vehicle.getVehicleInfo();
        _modelButton.setText(info.name);
        _modelIcon.setImageDrawable(info.getIcon());

        _alpha = _vehicle.getAlpha();

        // Save an instance of the remarks, so we know if they changed when the dropdown closes
        _remarksLayout.setText(_vehicle.getRemarks());

        // Update the R & B text
        PointMapItem device = ATAKUtilities.findSelf(_mapView);
        // It's possible that we don't have GPS and therefore don't have a controller point
        if (device != null) {
            _noGps.setVisibility(View.GONE);
            rabtable.setVisibility(View.VISIBLE);
            rabtable.update(device, _vehicle.getCenterPoint());
        } else {
            _noGps.setVisibility(View.VISIBLE);
            rabtable.setVisibility(View.GONE);
        }

        // keep the calculations and processes in the detail page, but disable the 
        // view for 3.2 as per JS.   Maybe this becomes a preference.
        _noGps.setVisibility(View.GONE);
        rabtable.setVisibility(View.GONE);

        _centerButton.setOnClickListener(this);

        _transSeek
                .setOnSeekBarChangeListener(new SimpleSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress,
                            boolean fromUser) {
                        if (fromUser)
                            _vehicle.setAlpha(_alpha = progress);
                    }
                });

        _updateColorButtonDrawable();
        _colorButton.setOnClickListener(this);
        _modelButton.setOnClickListener(this);
        _azimuthButton.setOnClickListener(this);
        findViewById(R.id.sendButton).setOnClickListener(this);
        findViewById(R.id.editButton).setOnClickListener(this);
        findViewById(R.id.undoButton).setOnClickListener(this);
        findViewById(R.id.endEditButton).setOnClickListener(this);
        _outlineCB.setOnCheckedChangeListener(this);
        _vehicle.addOnPointsChangedListener(this);
        _vehicle.addOnAlphaChangedListener(this);
        _vehicle.addOnMetadataChangedListener("outline", this);
        _vehicle.addChangeListener(this);

        ImageButton attBtn = this.findViewById(R.id.attachmentsButton);
        if (_attManager == null)
            _attManager = new AttachmentManager(_mapView, attBtn);
        _attManager.setMapItem(_vehicle);
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();

        // Set center point
        if (id == R.id.vehicleCenterButton)
            promptCenterEntry();

        // Set vehicle heading
        else if (id == R.id.vehicleAzimuth)
            promptAzimuthEntry();

        // Select vehicle model
        else if (id == R.id.vehicleModel)
            promptSetModel();

        // Set vehicle color
        else if (id == R.id.vehicleColorButton)
            _onColorSelected();

        // Send vehicle
        else if (id == R.id.sendButton)
            sendSelected(_vehicle.getUID());

        // Edit mode
        else if (id == R.id.editButton)
            startEditingMode();

        // Undo edit
        else if (id == R.id.undoButton)
            undoToolEdit();

        // End editing mode
        else if (id == R.id.endEditButton)
            endEditingMode();
    }

    @Override
    public void onCheckedChanged(CompoundButton cb, boolean checked) {
        if (cb == _outlineCB && _vehicle.setShowOutline(checked)) {
            if (checked && _vehicle.getAlpha() == 255)
                _vehicle.setAlpha(0);
            else if (!checked && _vehicle.getAlpha() == 0)
                _vehicle.setAlpha(255);
        }
    }

    @Override
    protected String getEditTool() {
        return VehicleModelEditTool.TOOL_NAME;
    }

    @Override
    protected void onEditToolBegin(Bundle extras) {
        refresh();
    }

    @Override
    protected void onEditToolEnd() {
        refresh();
    }

    /**
     * Prompt to set vehicle center point
     */
    private void promptCenterEntry() {
        AlertDialog.Builder b = new AlertDialog.Builder(_context);
        final CoordDialogView coordView = (CoordDialogView) _inf.inflate(
                R.layout.draper_coord_dialog, _mapView, false);
        b.setTitle(R.string.point_dropper_text6);
        b.setView(coordView);
        b.setPositiveButton(R.string.ok, null);
        b.setNegativeButton(R.string.cancel, null);
        coordView.setParameters(_vehicle.getCenter(), _mapView.getPoint(),
                _cFormat);

        final AlertDialog d = b.create();
        d.show();
        Button btn = d.getButton(AlertDialog.BUTTON_POSITIVE);
        btn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                // On click get the geopoint and elevation double in ft
                GeoPointMetaData p = coordView.getPoint();
                CoordinateFormat cf = coordView.getCoordFormat();
                CoordDialogView.Result result = coordView.getResult();

                // Invalid input
                if (result == CoordDialogView.Result.INVALID)
                    return;

                if (result == CoordDialogView.Result.VALID_UNCHANGED
                        && cf != _cFormat) {
                    // The coordinate format was changed but not the point itself
                    _centerButton.setText(coordView.getFormattedString());
                }
                _cFormat = cf;

                if (result == CoordDialogView.Result.VALID_CHANGED) {
                    _vehicle.move(_vehicle.getCenter(), p, true);
                    _centerButton.setText(coordView.getFormattedString());
                    ATAKUtilities.scaleToFit(_vehicle);
                }
                d.dismiss();
            }
        });
    }

    /**
     * Prompt to set vehicle azimuth/heading
     */
    private void promptAzimuthEntry() {
        DecimalFormat decFormat = LocaleUtil.getDecimalFormat("#.##");
        final NorthReference northRef = _unitPrefs.getNorthReference();
        final EditText input = new EditText(_context);
        input.setInputType(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_SIGNED
                | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setText(decFormat.format(AngleUtilities.roundDeg(
                _vehicle.getAzimuth(northRef), 2)));
        AlertDialog.Builder b = new AlertDialog.Builder(_context);
        b.setTitle(_context.getString(R.string.enter)
                + northRef.getName() + " "
                + _context.getString(R.string.azimuth));
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
                _vehicle.setAzimuth(deg, northRef);
                double rounded = AngleUtilities.roundDeg(_vehicle.getAzimuth(
                        northRef), 2);
                _azimuthButton.setText(_context.getString(R.string.deg,
                        rounded, northRef.getAbbrev()));
            }
        });
        b.setNegativeButton(R.string.cancel, null);
        b.show();
    }

    /**
     * Prompt to set the model this vehicle is using
     * Also changes the title, if matching
     */
    private void promptSetModel() {
        View v = _inf.inflate(R.layout.vehicle_model_grid, _mapView, false);

        ThemedSpinner spinner = v.findViewById(R.id.model_spinner);
        ExpandableGridView grid = v.findViewById(R.id.model_grid);

        final VehicleModelGridAdapter gridAdapter = new VehicleModelGridAdapter(
                _mapView);
        grid.setAdapter(gridAdapter);

        final VehicleModelInfo model = _vehicle.getVehicleInfo();
        gridAdapter.setCategory(model.category);
        gridAdapter.setSelected(model);

        List<String> categories = VehicleModelCache.getInstance()
                .getCategories();
        final ArrayAdapter<String> catAdapter = new ArrayAdapter<>(
                _context, R.layout.spinner_text_view_dark);
        catAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        catAdapter.addAll(categories);
        spinner.setAdapter(catAdapter);
        spinner.setOnItemSelectedListener(new SimpleItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view,
                    int position, long id) {
                gridAdapter.setCategory(catAdapter.getItem(position));
            }
        });

        AlertDialog.Builder b = new AlertDialog.Builder(_context);
        b.setTitle(R.string.vehicle_model);
        b.setView(v);
        b.setPositiveButton(R.string.cancel, null);
        final AlertDialog d = b.show();

        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> p, View v, int pos, long i) {
                VehicleModelInfo newModel = gridAdapter.getItem(pos);
                if (newModel == null || model == newModel)
                    return;
                String newName = _nameEdit.getText().toString()
                        .replace(model.name, newModel.name);
                _vehicle.setVehicleInfo(newModel);
                _vehicle.setTitle(newName);
                _modelButton.setText(newModel.name);
                _modelIcon.setImageDrawable(newModel.getIcon());
                _nameEdit.setText(newName);
                refresh();
                d.dismiss();
            }
        });

        spinner.setSelection(categories.indexOf(model.category));
    }

    private static void setViewsEnabled(boolean enabled, View... views) {
        for (View v : views)
            v.setEnabled(enabled);
    }

    private void _updateColorButtonDrawable() {
        final ShapeDrawable color = super.updateColorButtonDrawable();
        color.getPaint().setColor(_vehicle.getStrokeColor());
        post(new Runnable() {
            @Override
            public void run() {
                _colorButton.setImageDrawable(color);
            }
        });
    }

    @Override
    protected void _onColorSelected(int color, String label) {
        _vehicle.setColor(color);
        _updateColorButtonDrawable();
    }

    @Override
    public void refresh() {
        // Center
        _centerButton
                .setText(_unitPrefs.formatPoint(_vehicle.getCenter(), true));

        // Dimensions
        Span unit = _unitPrefs.getRangeUnits(0);
        double[] dim = _vehicle.getModelDimensions(false);
        _dimensions.setText(String.format(getResources().getString(
                R.string.vehicle_dimensions),
                SpanUtilities.convert(dim[1], Span.METER, unit),
                SpanUtilities.convert(dim[0], Span.METER, unit),
                SpanUtilities.convert(dim[2], Span.METER, unit),
                unit.getAbbrev()));

        // Azimuth - metadata is in TRUE north
        NorthReference northRef = _unitPrefs.getNorthReference();
        _azimuthButton.setText(String.format(
                getResources().getString(R.string.deg),
                AngleUtilities.roundDeg(_vehicle.getAzimuth(northRef), 2),
                northRef.getAbbrev()));

        // Alpha
        _transSeek.setProgress(_alpha);

        // Vehicle outline
        _outlineCB.setChecked(_vehicle.showOutline());

        // Whether the vehicle is read-only - not to be confused with getEditable()
        boolean editable = _vehicle.hasMetaValue("editable");
        boolean inEditMode = editToolActive();
        setViewsEnabled(editable, _nameEdit, _centerButton, _azimuthButton,
                _modelButton, _colorButton, _transSeek, _outlineCB,
                _remarksLayout);
        _optionsLayout.setVisibility(editable && !inEditMode
                ? View.VISIBLE
                : View.GONE);
        _editLayout.setVisibility(editable && inEditMode
                ? View.VISIBLE
                : View.GONE);
        _attManager.refresh();
    }

    @Override
    protected void sendSelected(final String uid) {
        if (_attManager != null)
            _attManager.send();
        else
            super.sendSelected(uid);
    }

    @Override
    public void onPointsChanged(Shape s) {
        refresh();
    }

    @Override
    public void onAltitudeChanged(RubberModel model, double altitude,
            GeoPoint.AltitudeReference reference) {
        refresh();
    }

    @Override
    public void onMetadataChanged(MapItem item, String field) {
        refresh();
    }

    @Override
    public void onAlphaChanged(AbstractSheet sheet, int alpha) {
        if (_alpha != alpha)
            _transSeek.setProgress(alpha);
    }

    @Override
    public void onRotationChanged(RubberModel model, double[] rotation) {
    }
}
