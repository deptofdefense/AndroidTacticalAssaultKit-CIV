
package com.atakmap.android.vehicle.overhead;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ShapeDrawable;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import com.atakmap.android.maps.MapItem;
import com.atakmap.android.util.SimpleItemSelectedListener;
import com.atakmap.android.util.SimpleSeekBarChangeListener;

import com.atakmap.android.drawing.details.GenericDetailsView;
import com.atakmap.android.gui.CoordDialogView;
import com.atakmap.android.gui.RangeAndBearingTableHandler;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.app.R;
import com.atakmap.coremap.conversions.AngleUtilities;
import com.atakmap.coremap.conversions.CoordinateFormat;
import com.atakmap.coremap.conversions.Span;
import com.atakmap.coremap.conversions.SpanUtilities;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.coremap.maps.coords.NorthReference;

import com.atakmap.android.util.AttachmentManager;
import android.widget.ImageButton;

import java.text.DecimalFormat;

import com.atakmap.coremap.locale.LocaleUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class OverheadDetailsView extends GenericDetailsView
        implements OverheadMarker.OnChangedListener, View.OnClickListener {

    private AttachmentManager attachmentManager;
    private ImageButton _attachmentButton;

    private Button _modelButton;
    private ImageView _modelIcon;
    private Spinner _modelSpinner;
    private Button _azimuthButton;
    private TextView _dimensions;
    private OverheadMarker _marker;

    public OverheadDetailsView(Context context) {
        super(context);
    }

    public OverheadDetailsView(Context context, final AttributeSet inAtr) {
        super(context, inAtr);
    }

    @Override
    public boolean setItem(MapView mapView, MapItem item) {
        if (!(item instanceof OverheadMarker))
            return false;
        super.setItem(mapView, item);
        _marker = (OverheadMarker) item;
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
        attachmentManager.cleanup();
        _marker.removeOnChangedListener(this);
    }

    @Override
    public void onChanged(OverheadMarker marker) {
        updateDisplay();
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
            coordView.setParameters(_marker.getGeoPointMetaData(),
                    _mapView.getPoint(), _cFormat);

            final AlertDialog ad = b.create();
            ad.show();
            ad.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(
                    new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            // On click get the geopoint and elevation double in ft
                            GeoPointMetaData p = coordView.getPoint();
                            CoordinateFormat cf = coordView
                                    .getCoordFormat();
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
                                _marker.setPoint(p);

                                _centerButton.setText(coordView
                                        .getFormattedString());

                                dropDown.getMapView()
                                        .getMapController()
                                        .panTo(p.get(), true);
                            }
                            ad.dismiss();
                        }
                    });

            // Set vehicle color
        } else if (i1 == R.id.vehicleColorButton) {
            _onColorSelected();

            // Select vehicle model
        } else if (i1 == R.id.vehicleModel) {
            List<OverheadImage> images = new ArrayList<>(
                    OverheadParser.getImages().values());
            Collections.sort(images, OverheadImage.NAME_COMPARATOR);
            final OverheadAdapter adapter = new OverheadAdapter(
                    _mapView.getContext(), images);

            // Create model list
            String currModel = _marker.getImage().name;
            int currPos = 0;
            for (int i = 0; i < images.size(); i++) {
                if (currModel.equals(images.get(i).name)) {
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

                            OverheadImage img = (OverheadImage) adapter
                                    .getItem(position);
                            if (img == null)
                                return;
                            String oldModel = _marker.getImage().name;
                            String newModel = img.name;
                            if (newModel == null
                                    || oldModel.equals(newModel))
                                return;
                            _marker.setImage(OverheadParser
                                    .getImageByName(newModel));
                            _nameEdit.setText(_nameEdit.getText()
                                    .toString()
                                    .replace(oldModel, newModel));
                            updateDisplay();
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
                    _marker.getAzimuth(northRef), 2)));
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
                                deg = Double
                                        .parseDouble(input.getText()
                                                .toString());
                            } catch (Exception e) {
                                deg = 0;
                            }
                            _marker.setAzimuth(deg, northRef);
                            _azimuthButton.setText(String.format(
                                    getResources().getString(R.string.deg),
                                    AngleUtilities.roundDeg(
                                            _marker.getAzimuth(northRef), 2),
                                    northRef.getAbbrev()));
                        }
                    });
            b.setNegativeButton(R.string.cancel, null);
            b.show();

            // Send button
        } else if (i1 == R.id.vehicleSendButton) {
            sendSelected(_marker.getUID());

        }
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
        _modelIcon = findViewById(R.id.vehicleIcon);
        _modelSpinner = findViewById(R.id.vehicleSpinner);
        _transSeek = findViewById(R.id.vehicleTransparencySeek);
        _dimensions = findViewById(R.id.vehicleDimensions);
        _azimuthButton = findViewById(R.id.vehicleAzimuth);

        // Save an instance of the name, so we know if it changed when the dropdown closes
        _nameEdit.setText(_marker.getTitle());

        // Update views for azimuth and dimensions
        updateDisplay();

        // Save an instance of the remarks, so we know if they changed when the dropdown closes
        _remarksLayout.setText(_marker.getMetaString("remarks", ""));

        // Update the R & B text
        PointMapItem device = ATAKUtilities.findSelf(_mapView);
        // It's possible that we don't have GPS and therefore don't have a controller point
        if (device != null) {
            _noGps.setVisibility(View.GONE);
            rabtable.setVisibility(View.VISIBLE);
            rabtable.update(device, _marker.getPoint());
        } else {
            _noGps.setVisibility(View.VISIBLE);
            rabtable.setVisibility(View.GONE);
        }

        // keep the calculations and processes in the detail page, but disable the
        // view for 3.2 as per JS.   Maybe this becomes a preference.
        _noGps.setVisibility(View.GONE);
        rabtable.setVisibility(View.GONE);

        _centerButton.setOnClickListener(this);

        _alpha = _marker.getColor() >>> 24;
        _transSeek.setProgress(_alpha);
        _transSeek.setOnSeekBarChangeListener(
                new SimpleSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar,
                            int progress, boolean fromUser) {
                        if (fromUser) {
                            int color = _marker.getColor();
                            _alpha = progress;
                            _marker.setColor(Color.argb(_alpha,
                                    Color.red(color),
                                    Color.green(color),
                                    Color.blue(color)));
                        }
                    }
                });
        _updateColorButtonDrawable();
        _colorButton.setOnClickListener(this);
        _modelButton.setOnClickListener(this);
        _azimuthButton.setOnClickListener(this);
        _marker.addOnChangedListener(this);
        sendButton.setOnClickListener(this);

        _attachmentButton = this
                .findViewById(R.id.cotInfoAttachmentsButton);

        if (attachmentManager == null)
            attachmentManager = new AttachmentManager(_mapView,
                    _attachmentButton);
        attachmentManager.setMapItem(_marker);

    }

    private void updateDisplay() {
        // Center
        _centerButton.setText(_unitPrefs.formatPoint(_marker.getPoint(), true));

        // Dimensions
        Span unit = Span.FOOT;
        double length = _marker.getImage().length;
        double width = _marker.getImage().width;
        double height = _marker.getImage().height;

        double lconv = SpanUtilities.convert(length, Span.METER, unit);
        double wconv = SpanUtilities.convert(width, Span.METER, unit);
        double hconv = SpanUtilities.convert(height, Span.METER, unit);

        if (Double.compare(hconv, 0.0) == 0) {
            _dimensions.setText(getContext().getString(
                    R.string.vehicle_dimensions_lw, lconv, wconv,
                    unit.getAbbrev()));
        } else {
            _dimensions.setText(getContext().getString(
                    R.string.vehicle_dimensions, lconv, wconv, hconv,
                    unit.getAbbrev()));
        }

        // Azimuth - metadata is in TRUE north
        NorthReference northRef = getNorthReferencePref();
        double azimuth = _marker.getAzimuth(northRef);
        _azimuthButton.setText(String.format(
                getResources().getString(R.string.deg),
                AngleUtilities.roundDeg(azimuth, 2),
                northRef.getAbbrev()));

        OverheadImage img = _marker.getImage();
        _modelButton.setText(img.name);
        _modelIcon.setImageResource(img.resId);
    }

    private void _updateColorButtonDrawable() {
        final ShapeDrawable color = super.updateColorButtonDrawable();
        color.getPaint().setColor(_marker.getColor());

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
        _marker.setColor(color);
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

    private static class OverheadAdapter extends BaseAdapter {

        private final Context _ctx;
        private final List<OverheadImage> _images;
        private final LayoutInflater _inflater;

        OverheadAdapter(Context ctx, List<OverheadImage> images) {
            _ctx = ctx;
            _images = images;
            _inflater = LayoutInflater.from(_ctx);
        }

        private static class ViewHolder {
            ImageView icon;
            TextView model;
        }

        @Override
        public int getCount() {
            return _images.size();
        }

        @Override
        public Object getItem(int position) {
            return _images.get(position);
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(int position, View row, ViewGroup parent) {
            ViewHolder holder;
            if (row == null) {
                row = _inflater.inflate(R.layout.overhead_marker_dropdown_item,
                        parent, false);
                holder = new ViewHolder();
                holder.icon = row.findViewById(R.id.vehicle_icon);
                holder.model = row.findViewById(R.id.vehicle_model);
                row.setTag(holder);
            } else
                holder = (ViewHolder) row.getTag();

            OverheadImage img = _images.get(position);
            if (img == null)
                return _inflater.inflate(R.layout.empty, parent, false);

            holder.icon.setImageResource(img.resId);
            holder.model.setText(img.name);

            return row;
        }
    }

    @Override
    protected void sendSelected(final String uid) {
        if (attachmentManager != null)
            attachmentManager.send();
        else
            super.sendSelected(uid);
    }
}
