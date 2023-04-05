
package com.atakmap.android.cotdetails;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RectShape;
import android.graphics.drawable.shapes.Shape;
import android.text.Editable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.atakmap.android.cotdetails.extras.ExtraDetailsLayout;
import com.atakmap.android.cotselector.CoTSelector;
import com.atakmap.android.drawing.details.GenericDetailsView;
import com.atakmap.android.gui.ColorPalette;
import com.atakmap.android.gui.ColorPalette.OnColorSelectedListener;
import com.atakmap.android.gui.CoordDialogView;
import com.atakmap.android.gui.RangeAndBearingTableHandler;
import com.atakmap.android.gui.RangeEntryDialog;
import com.atakmap.android.hashtags.view.RemarksLayout;
import com.atakmap.android.icons.Icon2525cTypeResolver;
import com.atakmap.android.icons.UserIcon;
import com.atakmap.android.image.ImageGalleryReceiver;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.preference.UnitPreferences;
import com.atakmap.android.user.TLEAccuracy;
import com.atakmap.android.user.TLECategory;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.android.util.AfterTextChangedWatcher;
import com.atakmap.android.util.AttachmentManager;
import com.atakmap.android.util.SimpleItemSelectedListener;
import com.atakmap.android.util.SpeedFormatter;
import com.atakmap.app.R;
import com.atakmap.coremap.conversions.Angle;
import com.atakmap.coremap.conversions.AngleUtilities;
import com.atakmap.coremap.conversions.CoordinateFormat;
import com.atakmap.coremap.conversions.Span;
import com.atakmap.coremap.conversions.SpanUtilities;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.ErrorCategory;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.coremap.maps.coords.NorthReference;
import com.atakmap.map.CameraController;

import java.util.Date;

public class CoTInfoView extends RelativeLayout
        implements PointMapItem.OnPointChangedListener,
        OnSharedPreferenceChangeListener {

    private static final String TAG = "CoTInfoView";

    private final Context context;

    private static final String QUICK_NAV_COT = "b-m-p-w-GOTO";
    private static final String WAYPOINT_COT = "b-m-p-w";
    private static final String INITIAL_POINT_COT = "b-m-p-c-ip";
    private static final String CONTACT_POINT_COT = "b-m-p-c-cp";
    private static final String OBSERVER_POINT_COT = "b-m-p-s-p-op";

    private final static String[] EDITABLE = {
            QUICK_NAV_COT, WAYPOINT_COT, INITIAL_POINT_COT, CONTACT_POINT_COT,
            OBSERVER_POINT_COT
    };

    private static final int HOSTILE_COLOR = Color.rgb(255, 128, 128);
    private static final int NEUTRAL_COLOR = Color.rgb(170, 255, 170);
    private static final int FRIENDLY_COLOR = Color.rgb(132, 223, 255);
    private static final int UNKNOWN_COLOR = Color.rgb(235, 235, 55);
    private static final int OTHER_COLOR = Color.rgb(185, 185, 185);

    /**
     * When the type set is changed, calls out to the statically supplied listener.   This could be done
     * using the extras block within CotInfoView, but will exist for the short term to support external
     * development.
     */
    public interface TypeSetListener {
        /**
         * provides a callback when the type is set so that the listener can possibly customize the 
         * specified GUI components.   This customization may clash with existing functionality so 
         * user beware.   There is also no guarantee that this will be called on the UI thread.
         */
        void typeSet(ImageView iconButton, Button cotButton,
                ImageButton modifierButton, PointMapItem marker);
    }

    protected static TypeSetListener tsListener;

    /**
     * Registration for the typeset listener required for an external development effort.    
     * @param l the TypeSetListener implementation.
     */
    public static void setTypeSetListener(final TypeSetListener l) {
        tsListener = l;
    }

    protected AttachmentManager attachmentManager;

    private boolean needsUpdate = false;

    private ImageView _iconButton;
    private EditText _nameEdit;
    private Button _coordButton;
    private View _noGps;
    private RangeAndBearingTableHandler rabtable;
    private Button _cotButton;
    private Button _tleButton;
    private Button _heightButton;
    private TextView _tleText;
    private TextView _derivedFrom;
    private ImageButton _colorButton;
    private LinearLayout _extendedCotInfo;
    protected TextView _lastSeenText;
    private TextView _batteryText;
    private TextView _speedText;
    private TextView _courseText;
    private TextView _addressText;
    private TextView _addressInfoText;
    private View _addressLayout;
    private TextView _authorText;
    private TextView _productionTimeText;
    private View cotAuthorLayout;
    private ImageView cotAuthorIconButton;
    private ImageView cotAuthorPanButton;
    protected RemarksLayout _remarksEdit;
    private TextView _summaryEdit;
    private TextView _summaryTitle;
    private ImageButton _sendButton;
    protected ImageButton _attachmentsButton;
    private ToggleButton _autoBroadcastButton;
    private ExtraDetailsLayout _extrasLayout;
    protected boolean _visible = false;

    private String _prevName;
    private String _prevRemarks;

    private String _cotType;
    private int _bgColor = OTHER_COLOR;

    private NorthReference _northReference;
    private CoordinateFormat _cFormat;

    private UnitPreferences _prefs;

    PointMapItem _marker;

    private CoTAutoBroadcaster cab;

    protected MapView mapView;

    // constant strings to use for meta info
    protected CoTSelector selector;

    @Override
    public void onSharedPreferenceChanged(
            final SharedPreferences sp,
            final String key) {

        if (key == null)
            return;

        switch (key) {
            case UnitPreferences.COORD_FMT:
                _cFormat = _prefs.getCoordinateFormat();
                break;
            case UnitPreferences.NORTH_REFERENCE:
                _northReference = _prefs.getNorthReference();
                break;
            case "hostileUpdateDelay":
                post(new Runnable() {
                    public void run() {
                        try {
                            if (_prefs.get("hostileUpdateDelay", "60")
                                    .equals("0")) {
                                _autoBroadcastButton.setVisibility(INVISIBLE);
                            } else {
                                _autoBroadcastButton.setVisibility(VISIBLE);
                            }
                        } catch (Exception ignored) {
                        }
                    }
                });
                break;
            default:
                return;
        }

        refreshMarker();
    }

    public CoTInfoView(Context context) {
        this(context, null);
    }

    public CoTInfoView(Context context, AttributeSet inAtr) {
        super(context, inAtr);
        this.context = context;
        //check this flag for easier editing in the visual layout
        if (!isInEditMode()) {
            _prefs = new UnitPreferences(MapView.getMapView());
            _cFormat = _prefs.getCoordinateFormat();
            _northReference = _prefs.getNorthReference();
        }
    }

    public int getBackgroundColor() {
        return _bgColor;
    }

    protected void setup() {
        GenericDetailsView.addEditTextPrompts(this);
        _iconButton = this.findViewById(R.id.cotInfoNameTitle);
        _nameEdit = this.findViewById(R.id.cotInfoNameEdit);
        _coordButton = this.findViewById(R.id.cotInfoCoordButton);
        ImageButton _panButton = this
                .findViewById(R.id.cotInfoPanButton);
        _noGps = this.findViewById(R.id.cotInfoRangeBearingNoGps);
        rabtable = new RangeAndBearingTableHandler(this);
        _cotButton = this.findViewById(R.id.cotInfoCotButton);
        _heightButton = this.findViewById(R.id.cotInfoHeightButton);
        _tleButton = this.findViewById(R.id.cotInfoTLE);
        _tleText = this.findViewById(R.id.cotInfoCAT);
        _derivedFrom = this.findViewById(R.id.cotInfoDerivedFrom);
        _colorButton = this.findViewById(R.id.cotInfoColorButton);
        _lastSeenText = this.findViewById(R.id.cotInfoLastSeenText);
        _batteryText = this
                .findViewById(R.id.cotInfoBatteryLevelText);
        _speedText = this.findViewById(R.id.cotInfoSpeedText);
        _courseText = this.findViewById(R.id.cotInfoCourseText);

        _addressLayout = this.findViewById(R.id.cotAddressLayout);
        _addressText = this.findViewById(R.id.cotInfoAddress);
        _addressInfoText = this
                .findViewById(R.id.cotInfoAddressInfo);

        _authorText = this.findViewById(R.id.cotInfoAuthor);
        _productionTimeText = this
                .findViewById(R.id.cotInfoProductionTime);
        cotAuthorLayout = this.findViewById(R.id.cotAuthorLayout);
        cotAuthorIconButton = this
                .findViewById(R.id.cotAuthorIconButton);
        cotAuthorPanButton = this
                .findViewById(R.id.cotAuthorPanButton);

        _remarksEdit = this.findViewById(R.id.remarksLayout);
        _summaryEdit = this.findViewById(R.id.cotInfoSummaryEdit);
        _summaryTitle = this.findViewById(R.id.cotInfoSummaryTitle);
        _extendedCotInfo = this
                .findViewById(R.id.extendedCotInfo);
        _sendButton = this.findViewById(R.id.cotInfoSendButton);
        _attachmentsButton = this
                .findViewById(R.id.cotInfoAttachmentsButton);

        attachmentManager = new AttachmentManager(mapView, _attachmentsButton);

        _autoBroadcastButton = this
                .findViewById(R.id.cotInfoBroadcastButton);
        _extrasLayout = findViewById(R.id.extrasLayout);

        _colorButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                _onColorSelected();
            }
        });

        _iconButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (_marker == null)
                    return;

                final String type = _marker.getType();

                // Check if this is an atom CoT type
                // e.g. a-f, a-h, a-u, a-n
                if (_marker == null || !type.startsWith("a-")) {
                    Toast.makeText(getContext(),
                            R.string.point_dropper_text14,
                            Toast.LENGTH_LONG).show();
                    return;
                }

                final String how = _marker.getMetaString("how", null);
                if (how != null && how.equals("m-g")) {
                    Toast.makeText(getContext(),
                            "unable to change, machine generated",
                            Toast.LENGTH_LONG).show();
                    return;
                }

                AlertDialog.Builder actionBuilder = new AlertDialog.Builder(
                        context);
                View affiliationOptionsView = LayoutInflater.from(context)
                        .inflate(R.layout.change_affiliation_opts,
                                null);
                actionBuilder.setView(affiliationOptionsView)
                        .setTitle(R.string.point_dropper_text13)
                        .setNegativeButton(R.string.cancel, null);
                final AlertDialog ad = actionBuilder.create();

                final RadioGroup group = affiliationOptionsView
                        .findViewById(R.id.select_affiliation_group);

                group.setOnCheckedChangeListener(
                        new RadioGroup.OnCheckedChangeListener() {
                            @Override
                            public void onCheckedChanged(RadioGroup arg0,
                                    int id) {
                                final int chkid = group
                                        .getCheckedRadioButtonId();
                                if (chkid == R.id.unknown_btn) {
                                    _marker.setType("a-u" + type.substring(3));
                                } else if (chkid == R.id.neutral_btn) {
                                    _marker.setType("a-n" + type.substring(3));
                                } else if (chkid == R.id.hostile_btn) {
                                    _marker.setType("a-h" + type.substring(3));
                                } else if (chkid == R.id.friendly_btn) {
                                    _marker.setType("a-f" + type.substring(3));
                                }
                                _marker.refresh(mapView.getMapEventDispatcher(),
                                        null,
                                        this.getClass());
                                _marker.persist(mapView.getMapEventDispatcher(),
                                        null,
                                        this.getClass());
                                _iconButton
                                        .setImageDrawable(getPointIcon(_marker,
                                                mapView));
                                ad.dismiss();
                            }
                        });
                ad.show();
            }
        });

        _nameEdit.addTextChangedListener(new AfterTextChangedWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (_marker != null)
                    _updateName();
            }
        });

        _panButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (_marker != null) {
                    GeoPoint gp = _marker.getPoint();
                    CameraController.Programmatic.panTo(
                            mapView.getRenderer3(), gp, false);
                }
            }
        });

        _coordButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (_marker == null)
                    return;
                AlertDialog.Builder b = new AlertDialog.Builder(getContext());
                LayoutInflater inflater = LayoutInflater.from(getContext());
                final CoordDialogView coordView = (CoordDialogView) inflater
                        .inflate(R.layout.draper_coord_dialog, null);
                b.setTitle(R.string.rb_coord_title);
                b.setView(coordView);
                b.setPositiveButton(R.string.ok, null);
                b.setNegativeButton(R.string.cancel, null);
                coordView.setParameters(_marker.getGeoPointMetaData(),
                        mapView.getPoint(),
                        _cFormat, !_marker.getMovable());

                // Overrides setPositive button onClick to keep the window open when the input is invalid.
                final AlertDialog locDialog = b.create();
                locDialog.setCancelable(false);
                locDialog.show();
                locDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                if (_marker == null) {
                                    locDialog.dismiss();
                                    return;
                                }
                                // On click get the geopoint and elevation double in ft
                                GeoPointMetaData p = coordView.getPoint();
                                boolean changedFormat = coordView
                                        .getCoordFormat() != _cFormat;

                                if (coordView
                                        .getCoordFormat() != CoordinateFormat.ADDRESS)
                                    _cFormat = coordView
                                            .getCoordFormat();

                                CoordDialogView.Result result = coordView
                                        .getResult();
                                if (result == CoordDialogView.Result.INVALID)
                                    return;
                                if (result == CoordDialogView.Result.VALID_UNCHANGED
                                        && changedFormat) {
                                    // The coordinate format was changed but not the point itself
                                    onPointChanged(_marker);
                                }
                                if (result == CoordDialogView.Result.VALID_CHANGED) {

                                    com.atakmap.android.drawing.details.GenericPointDetailsView
                                            .setAddress(coordView, _marker,
                                                    _nameEdit);
                                    _marker.setPoint(p);
                                    _marker.persist(mapView
                                            .getMapEventDispatcher(),
                                            null,
                                            this.getClass());
                                    CameraController.Programmatic.panTo(
                                            mapView.getRenderer3(),
                                            p.get(), false);

                                }
                                locDialog.dismiss();
                            }
                        });
            }
        });

        _cotButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Check if this is an atom CoT type
                // e.g. a-f, a-h, a-u, a-n
                if (_marker == null || !_cotType.startsWith("a-")) {
                    Toast.makeText(getContext(),
                            R.string.point_dropper_text14,
                            Toast.LENGTH_LONG).show();
                } else {
                    selector.show(_marker, null);
                }
            }
        });

        _heightButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (_marker == null)
                    return;
                double heightM = _marker.getHeight();
                new RangeEntryDialog(mapView).show(
                        R.string.enter_marker_height, heightM,
                        _prefs.getAltitudeUnits(),
                        new RangeEntryDialog.Callback() {
                            @Override
                            public void onSetValue(double valueM, Span unit) {
                                _marker.setHeight(valueM);
                                _marker.persist(
                                        mapView.getMapEventDispatcher(),
                                        null, this.getClass());
                                _heightButton
                                        .setText(valueM >= 0 ? SpanUtilities
                                                .format(
                                                        valueM, Span.METER,
                                                        unit, 2)
                                                : "-- " + unit.getAbbrev());
                            }
                        });
            }
        });

        _tleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (_marker != null) {
                    if (GeoPointMetaData.isPrecisionImageryDerived(
                            _marker.getGeoPointMetaData())) {

                        Toast.makeText(
                                getContext(),
                                "Point source was derived from Precise Imagery\nChanging the category is not allowed",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                }

                // Create view
                LayoutInflater inflater = LayoutInflater.from(context);
                final View tleSelection = inflater.inflate(
                        R.layout.tle_selection_input, null);
                final Spinner spinner = tleSelection
                        .findViewById(R.id.tle_spinner);
                final TextView value = tleSelection
                        .findViewById(R.id.tle_value);
                final RadioGroup rbs = tleSelection
                        .findViewById(R.id.tle_accuracy);

                // Set up values (TLE categories)
                final ArrayAdapter<TLECategory> adapter = new ArrayAdapter<>(
                        context,
                        android.R.layout.simple_spinner_item,
                        TLECategory.values());
                adapter.setDropDownViewResource(
                        android.R.layout.simple_spinner_dropdown_item);
                spinner.setAdapter(adapter);

                // Make default selection based on marker CE
                double ce = GeoPoint.UNKNOWN;
                if (_marker != null)
                    ce = _marker.getPoint().getCE();
                TLECategory tle = TLECategory.getCategory(ce);
                TLEAccuracy acc = tle.getAccuracy(ce);
                spinner.setSelection(tle.getValue());
                rbs.check(acc.getResource());
                value.setText(TLECategory.getCEString(ce));

                // Disable accuracy buttons if category is unknown
                for (TLEAccuracy t : TLEAccuracy.values())
                    rbs.findViewById(t.getResource())
                            .setEnabled(tle != TLECategory.UNKNOWN);

                // Radio buttons updated
                rbs.setOnCheckedChangeListener(
                        new RadioGroup.OnCheckedChangeListener() {
                            @Override
                            public void onCheckedChanged(RadioGroup group,
                                    int checkedId) {
                                TLECategory tle = adapter.getItem(
                                        spinner.getSelectedItemPosition());
                                TLEAccuracy acc = TLEAccuracy
                                        .fromResource(checkedId);
                                if (tle != null)
                                    value.setText(getResources().getString(
                                            R.string.loc_error, tle
                                                    .getCEString(acc)));
                            }
                        });

                // New list item selected
                spinner.setOnItemSelectedListener(
                        new SimpleItemSelectedListener() {
                            @Override
                            public void onItemSelected(AdapterView<?> parent,
                                    View view,
                                    int position, long id) {
                                if (view instanceof TextView)
                                    ((TextView) view).setTextColor(Color.WHITE);
                                TLECategory tle = adapter.getItem(position);
                                TLEAccuracy acc = TLEAccuracy.fromResource(
                                        rbs.getCheckedRadioButtonId());
                                if (tle != null) {

                                    value.setText(getResources().getString(
                                            R.string.loc_error, tle
                                                    .getCEString(acc)));
                                    for (TLEAccuracy t : TLEAccuracy.values())

                                        rbs.findViewById(t.getResource())
                                                .setEnabled(
                                                        tle != TLECategory.UNKNOWN);
                                }
                            }

                        });

                // Create dialog
                AlertDialog.Builder adb = new AlertDialog.Builder(context);
                adb.setCancelable(false);
                adb.setPositiveButton(R.string.ok,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int id) {
                                TLECategory tle = adapter.getItem(
                                        spinner.getSelectedItemPosition());
                                TLEAccuracy acc = TLEAccuracy.fromResource(
                                        rbs.getCheckedRadioButtonId());

                                double ce = GeoPoint.UNKNOWN;
                                if (tle != null)
                                    ce = tle.getCE(acc);

                                if (_marker != null) {
                                    // Update marker GeoPoint
                                    GeoPointMetaData mgp = _marker
                                            .getGeoPointMetaData();

                                    _marker.setPoint(
                                            GeoPointMetaData.wrap(
                                                    new GeoPoint(mgp.get()
                                                            .getLatitude(),
                                                            mgp.get()
                                                                    .getLongitude(),
                                                            mgp.get()
                                                                    .getAltitude(),
                                                            ce,
                                                            mgp.get().getLE()),
                                                    GeoPointMetaData.USER,
                                                    mgp.getAltitudeSource()));

                                    // Mark as hand-edited
                                    _marker.setMetaBoolean("ce_human_input",
                                            true);

                                    // Save location and metadata
                                    _marker.persist(
                                            mapView.getMapEventDispatcher(),
                                            null,
                                            this.getClass());

                                    // Update text view
                                    final GeoPoint point = _marker.getPoint();
                                    _tleButton.setText(TLECategory
                                            .getCEString(point));
                                    _tleText.setText(ErrorCategory.getCategory(
                                            point.getCE())
                                            .getName());

                                    _tleText.setTextColor(TLECategory
                                            .getCategory(
                                                    point.getCE())
                                            .getColor());
                                }
                                dialog.cancel();
                            }
                        });
                adb.setNegativeButton(R.string.cancel, null);
                adb.setView(tleSelection);
                AlertDialog alert = adb.create();
                alert.setTitle(R.string.nineline_text6);
                alert.show();
            }
        });

        _remarksEdit.addTextChangedListener(new AfterTextChangedWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                _updateRemarks();
            }
        });

        _attachmentsButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if (_marker != null)
                    AtakBroadcast.getInstance().sendBroadcast(
                            new Intent(ImageGalleryReceiver.VIEW_ATTACHMENTS)
                                    .putExtra("uid", _marker.getUID()));
            }
        });

        cab = CoTAutoBroadcaster.getInstance();

        _autoBroadcastButton
                .setOnCheckedChangeListener(
                        new CompoundButton.OnCheckedChangeListener() {
                            @Override
                            public void onCheckedChanged(
                                    CompoundButton compoundButton,
                                    boolean b) {
                                if (b) {
                                    //add to auto broadcast list
                                    if (_marker instanceof Marker) {
                                        Marker m = (Marker) _marker;
                                        cab.addMarker(m);
                                    }
                                } else {
                                    //remove from list
                                    if (_marker instanceof Marker) {
                                        Marker m = (Marker) _marker;
                                        if (cab.isBroadcast(m))
                                            cab.removeMarker(m);
                                    }
                                }
                            }
                        });

        selector = new CoTSelector(mapView);
    }

    private void _updateColorButtonDrawable() {
        Shape rect = new RectShape();
        rect.resize(35, 35);
        ShapeDrawable color = new ShapeDrawable();
        color.setBounds(0, 0, 35, 35);
        color.setIntrinsicHeight(35);
        color.setIntrinsicWidth(35);
        color.setShape(rect);
        color.getPaint().setColor(_marker.getMetaInteger("color", Color.WHITE));
        _colorButton.setBackgroundResource(R.drawable.atak_button);
        _colorButton.setImageDrawable(color);
    }

    protected void _onColorSelected() {
        if (_marker == null)
            return;
        AlertDialog.Builder b = new AlertDialog.Builder(context)
                .setTitle(R.string.point_dropper_text15);
        ColorPalette palette = new ColorPalette(context,
                _marker.getMetaInteger("color", Color.BLACK));
        b.setView(palette);
        final AlertDialog alert = b.create();
        OnColorSelectedListener l = new OnColorSelectedListener() {
            @Override
            public void onColorSelected(int color, String label) {
                alert.cancel();
                _marker.setMetaInteger("color", color);
                _marker.refresh(mapView.getMapEventDispatcher(), null,
                        this.getClass());
                needsUpdate = true;
                _updateColorButtonDrawable();
            }
        };
        palette.setOnColorSelectedListener(l);
        alert.show();
    }

    public void setOnSendClickListener(OnClickListener listener) {
        if (_sendButton == null) {
            _sendButton = this
                    .findViewById(R.id.cotInfoSendButton);
        }
        _sendButton.setOnClickListener(listener);
    }

    public synchronized void initialize(MapView mapView) {
        if (this.mapView == null) {
            this.mapView = mapView;
            setup();
        }
    }

    public boolean setMarker(final PointMapItem pmi) {

        if (_marker != null)
            cleanup(true);

        if (pmi != null) {
            _marker = pmi;
            _marker.setMetaBoolean("focused", true);
            _marker.addOnPointChangedListener(this);
            attachmentManager.setMapItem(_marker);
            _prefs.registerListener(this);
            _updatePreferences();

            refreshMarker();

            needsUpdate = false;
            return true;
        }
        needsUpdate = false;
        Log.w(TAG, "marker is null");
        return false;
    }

    public void hideAttachmentOption(boolean hide) {
        if (hide)
            _attachmentsButton.setVisibility(View.INVISIBLE);
        else
            _attachmentsButton.setVisibility(View.VISIBLE);

    }

    public void updateDeviceLocation(final PointMapItem device) {
        // It's possible that we don't have GPS and therefore don't have a controller point
        rabtable.update(device, _marker);

        this.post(new Runnable() {
            @Override
            public void run() {
                if (device != null) {
                    _noGps.setVisibility(View.GONE);
                    rabtable.setVisibility(View.VISIBLE);
                } else {
                    _noGps.setVisibility(View.VISIBLE);
                    rabtable.setVisibility(View.GONE);
                }
            }
        });
    }

    protected void _updateRemarks() {
        if (!_visible || _marker == null)
            return;
        String remarks = _remarksEdit.getText().trim();
        if (!remarks.equals(_prevRemarks)) {
            _marker.setRemarks(remarks);
            needsUpdate = true;
            _prevRemarks = remarks;
        }
    }

    private void _updateSummary() {
        if (_marker != null) {
            String summary = _marker.getMetaString("summary", "");
            if (FileSystemUtils.isEmpty(summary)) {
                _summaryEdit.setText("");
                _summaryEdit.setVisibility(GONE);
                _summaryTitle.setVisibility(GONE);
            } else {
                _summaryEdit.setText(summary);
                _summaryEdit.setVisibility(VISIBLE);
                _summaryTitle.setVisibility(VISIBLE);
            }
        }
    }

    private void setEditable(boolean b) {
        _iconButton.setEnabled(b);
        _nameEdit.setEnabled(b);
        _coordButton.setEnabled(b);
        _noGps.setEnabled(b);
        rabtable.setEnabled(b);
        _cotButton.setEnabled(b);
        _heightButton.setEnabled(b);
        _lastSeenText.setEnabled(b);
        _batteryText.setEnabled(b);
        _tleButton.setEnabled(b);

        // allow remarks to be edited
        // _remarksLayout.setEnabled(b);
        if (!b)
            _bgColor = Color.rgb(255, 255, 255);
    }

    /**
     * Update the visual aspects of the current CoTInfoView, namely the # of attachments, 
     * image count and the background color.
     */
    public void updateVisual() {
        if (_marker != null) {
            String type = _marker.getType();
            setType(type);
            updateAttachmentsButton();
        }
    }

    /**
     * Drop down visibility changed
     * @param v True if visible
     */
    public void onVisible(boolean v) {
        if (v) {
            updateVisual();
            setMarker(_marker);
        } else {
            clearFocus();
            InputMethodManager inputManager = (InputMethodManager) getContext()
                    .getSystemService(Context.INPUT_METHOD_SERVICE);
            if (inputManager != null)
                inputManager.hideSoftInputFromWindow(getWindowToken(), 0);
        }
        _visible = v;
    }

    /**
     * Back button pressed (to close drop-down)
     */
    public void onBackButtonPressed() {
        // Call cleanup here so popping the back stack doesn't begin
        // triggering last-second changes to the marker (see bug 3430)
        cleanup(true);
    }

    private void updateAttachmentsButton() {
        attachmentManager.refresh();
    }

    protected void _updatePreferences() {
        _cFormat = _prefs.getCoordinateFormat();

        if (_cFormat == null)
            _cFormat = CoordinateFormat.MGRS;
    }

    protected void _updateName() {
        if (!_visible)
            return;

        String name = _nameEdit.getText().toString().trim();

        //Log.d(TAG, "update name called: " + name + " where the previous name was " + _prevName);

        // do not allow the callsign or the title to be set to the UID value
        if (!name.equals(_prevName) && _marker != null
                && !name.equals(_marker.getUID())) {
            // If the name changed then update it
            _marker.setMetaString("callsign", name);
            if (_marker instanceof Marker) {
                _marker.setTitle(name);
            }
            _prevName = name;
            needsUpdate = true;
        }
    }

    static public Drawable getPointIcon(PointMapItem pmi, MapView mapView) {
        // Set Track Icon
        Drawable drawable = null;
        String type = pmi.getType();

        final Context mContext = mapView.getContext();

        if (type.startsWith("shape_marker")) {
            drawable = mContext.getResources().getDrawable(
                    android.R.drawable.ic_menu_edit);
        } else if (type.equals("u-rb-a")) {
            drawable = mContext.getResources().getDrawable(
                    R.drawable.pairing_line_white);
        } else {
            Bitmap bitmap = ATAKUtilities.getIconBitmap(pmi);
            if (bitmap != null)
                drawable = new BitmapDrawable(mContext.getResources(),
                        bitmap);
        }

        // Make sure the drawables are 32x32
        if (drawable != null) {
            Bitmap b = ((BitmapDrawable) drawable).getBitmap();
            if (b != null) {
                b = Bitmap.createScaledBitmap(b, 64, 64, false);
                drawable = new BitmapDrawable(mContext.getResources(), b);
            }
        }
        return drawable;

    }

    public void setType(String type) {
        if (_marker == null)
            return;

        if (!_cotType.equals(type))
            needsUpdate = true;

        _cotType = type;

        final Drawable dr = getPointIcon(_marker, mapView);
        // so the previous marker icon does not show through when there is an error
        _iconButton.setImageDrawable(dr);

        if (_marker instanceof Marker) {
            _iconButton.setColorFilter(_marker.getIconColor(),
                    PorterDuff.Mode.MULTIPLY);
        } else {
            _iconButton.setColorFilter(Color.WHITE,
                    PorterDuff.Mode.MULTIPLY);
        }

        String targ = getTypeLabel(getContext(), type);
        _cotButton.setText(targ);

        // Update the background color if the cot type changes
        if (_cotType != null) {
            if (_cotType.startsWith("a-f")) {
                _bgColor = FRIENDLY_COLOR;
            } else if (_cotType.startsWith("a-n")) {
                _bgColor = NEUTRAL_COLOR;
            } else if (_cotType.startsWith("a-u")) {
                _bgColor = UNKNOWN_COLOR;
            } else if (_cotType.startsWith("a-h") || _cotType.startsWith("a-j")
                    || _cotType.startsWith("a-k")) {
                _bgColor = HOSTILE_COLOR;
            } else { // color for points that don't have an affiliation; route checkpoints, dips,
                     // etc
                _bgColor = OTHER_COLOR;
            }
        }
        attachmentManager.refresh();

        try {
            if (tsListener != null) {
                tsListener.typeSet(_iconButton, _cotButton,
                        findViewById(R.id.cotInfoModifierButton),
                        _marker);
            }
        } catch (Exception e) {
            Log.e(TAG, "error calling: " + tsListener, e);
        }
    }

    public static String getTypeLabel(Context context, String type) {
        if (FileSystemUtils.isEmpty(type))
            return "";

        // get the CoT name from some library
        String _25b25bName = Icon2525cTypeResolver.mil2525cFromCotType(type);

        String targ = context.getString(R.string.not_recognized);
        boolean includeType = true;
        if (_25b25bName.length() > 3) {
            targ = Icon2525cTypeResolver.getHumanName(type, context);
            includeType = targ.equals(context.getString(
                    R.string.not_recognized));
        } else if (type.equals(QUICK_NAV_COT) || type.equals(WAYPOINT_COT))
            targ = context.getString(R.string.waypoint);
        else if (type.equals(INITIAL_POINT_COT))
            targ = context.getString(R.string.initial_point);
        else if (type.equals(CONTACT_POINT_COT))
            targ = context.getString(R.string.contact_point);
        else if (type.equals(OBSERVER_POINT_COT))
            targ = context.getString(R.string.observer_point);

        if (includeType) { // if we couldn't figure out what it is, add the type to the screen
            targ += " [" + type + "]";
        }

        return targ;
    }

    public void cleanup(final boolean attemptUpdate) {

        attachmentManager.cleanup();

        //Log.d(TAG, "Cleanup attempt update? " + attemptUpdate
        // + ", needs update? " + needsUpdate);

        _updateSummary();

        if (_marker != null) {
            _marker.removeMetaData("focused");
            if (needsUpdate && attemptUpdate)
                _marker.persist(mapView.getMapEventDispatcher(), null,
                        this.getClass());
            _marker.removeOnPointChangedListener(this);
        }

        _prefs.unregisterListener(this);

        _marker = null;
    }

    @Override
    public void onPointChanged(final PointMapItem item) {

        if (item != _marker) {
            item.removeOnPointChangedListener(this);
        } else {
            postRefreshMarker();
        }
    }

    void postRefreshMarker() {
        post(new Runnable() {
            @Override
            public void run() {
                refreshMarker();
            }
        });
    }

    protected void refreshMarker() {
        if (_marker == null)
            return;
        _coordButton.setText(_prefs.formatPoint(
                _marker.getGeoPointMetaData(), true));
        updateDeviceLocation(ATAKUtilities.findSelf(mapView));

        // Retrieve all of the needed info from the marker.
        _cotType = _marker.getType();
        final String remarks = _marker.getRemarks();

        final String name = ATAKUtilities.getDisplayName(_marker);

        // mark the previous name as the current name so that changes to the name
        // can be accurately determined for calling persist.
        _prevName = name;

        boolean enabled = false;
        if (_cotType != null) {
            enabled = true;
            setType(_cotType);

            // except for KML
            if (_cotType.equals(MapItem.EMPTY_TYPE))
                enabled = false;
        } else {
            setType("unknown");
        }

        _attachmentsButton.setEnabled(enabled);
        _sendButton.setEnabled(enabled);

        _nameEdit.setText(name);
        _remarksEdit.setText(remarks);

        final GeoPointMetaData point = _marker.getGeoPointMetaData();

        // Update CE button text
        _tleButton.setText(TLECategory.getCEString(point.get()));
        _tleText.setText(ErrorCategory.getCategory(
                point.get().getCE()).getName());
        _tleText.setTextColor(TLECategory.getCategory(
                point.get().getCE()).getColor());
        // Disable button if marker doesn't belong to our device
        String parentUID = _marker.getMetaString("parent_uid", null);
        _tleButton.setEnabled(parentUID == null || parentUID.equals(
                MapView.getDeviceUid()));

        double height = _marker.getHeight();
        Span heightUnit = _prefs.getAltitudeUnits();
        _heightButton.setText(!Double.isNaN(height) ? SpanUtilities.format(
                height, Span.METER, heightUnit, 2)
                : "-- " + heightUnit.getAbbrev());

        final String pointSource = point.getGeopointSource();

        if (GeoPointMetaData.isPrecisionImageryDerived(point)) {

            _derivedFrom.setVisibility(View.VISIBLE);

            StringBuilder txt = new StringBuilder();
            txt.append(context.getString(R.string.derived_from));
            txt.append(pointSource);

            String n = (String) point
                    .getMetaData(GeoPointMetaData.PRECISE_IMAGE_FILE);
            if (n != null) {
                txt.append("\n ");
                final int lastPathSepIdx = n.lastIndexOf('/');
                if (lastPathSepIdx >= 0)
                    n = n.substring(lastPathSepIdx + 1);
                txt.append(n);
            }
            double x = (Double) (point
                    .getMetaData(GeoPointMetaData.PRECISE_IMAGE_FILE_X));
            double y = (Double) (point
                    .getMetaData(GeoPointMetaData.PRECISE_IMAGE_FILE_Y));
            txt.append("\n Image X: ");
            txt.append(
                    String.format(LocaleUtil.getCurrent(), "%.2f", x));
            txt.append(" Y: ");
            txt.append(
                    String.format(LocaleUtil.getCurrent(), "%.2f", y));

            _derivedFrom.setText(txt.toString());
        } else {
            StringBuilder txt = new StringBuilder();
            txt.append(context.getString(R.string.derived_from));
            txt.append(pointSource);
            _derivedFrom.setText(txt.toString());
        }

        // Metadata print out (debug)
        /*Map<String, Object> metadata = new HashMap<String, Object>();
        _marker.getMetaData(metadata);
        for(String key : metadata.keySet())
            Log.w(TAG, key + ": " + metadata.get(key));*/

        long lastSeenTime = _marker.getMetaLong("lastUpdateTime", 0);
        if (lastSeenTime > 0) {
            _lastSeenText.setText(String.format(LocaleUtil.getCurrent(),
                    "%s%s",
                    getContext().getString(R.string.last_report), new Date(
                            lastSeenTime)));
        } else {
            _lastSeenText.setText(R.string.point_dropper_text16);
        }

        long battery = _marker.getMetaLong("battery", -1);

        try {
            if (battery > -1 && battery < 101) {
                _batteryText.setVisibility(View.VISIBLE);
                _batteryText.setText("" + (int) battery + "%");
            } else {
                _batteryText.setVisibility(View.GONE);
                _batteryText.setText("");
            }
        } catch (Exception e) {
            Log.d(TAG, "error battery level reported: " + battery, e);
            _batteryText.setVisibility(View.GONE);
            _batteryText.setText("");

        }

        cotAuthorLayout.setVisibility(View.VISIBLE);
        cotAuthorLayout.setOnClickListener(null);
        cotAuthorPanButton.setVisibility(View.GONE);
        cotAuthorIconButton.setVisibility(View.GONE);
        if (_marker instanceof Marker) {
            Marker m = (Marker) _marker;

            refreshAuthorLayout(mapView, m, _authorText, _productionTimeText,
                    cotAuthorLayout, cotAuthorIconButton, cotAuthorPanButton);

            if (m.getMetaString("parent_uid", null) != null &&
                    !m.getMetaString("parent_uid", "").equals(
                            mapView.getSelfMarker().getUID())) {
                _autoBroadcastButton.setVisibility(INVISIBLE);
            } else {
                _autoBroadcastButton.setVisibility(VISIBLE);
                _autoBroadcastButton.setChecked(cab.isBroadcast(m));
            }

            // for bh
            if (_prefs.get("hostileUpdateDelay", "60").equals("0")) {
                _autoBroadcastButton.setVisibility(INVISIBLE);
            }

            _speedText.setVisibility(View.VISIBLE);
            _speedText
                    .setText(SpeedFormatter.getInstance()
                            .getSpeedFormatted(m));

            _courseText.setVisibility(View.VISIBLE);

            String orientationString = "---" + Angle.DEGREE_SYMBOL;
            double orientation = m.getTrackHeading();

            String additional = "";
            if (Double.isNaN(orientation)) {
                orientation = m.getMetaDouble("est.course", Double.NaN);
                additional = " EST";
            }

            if (!Double.isNaN(orientation)) {
                String unit = "T";
                if (_northReference != NorthReference.TRUE) {
                    orientation = ATAKUtilities.convertFromTrueToMagnetic(
                            m.getPoint(), orientation);
                    unit = "M";
                }
                orientationString = AngleUtilities.format(orientation)
                        + unit;
            }

            _courseText.setText(orientationString + additional);

        } else {
            _speedText.setVisibility(View.GONE);
            _courseText.setVisibility(View.GONE);
        }

        com.atakmap.android.drawing.details.GenericPointDetailsView
                .controlAddressUI(_marker, _addressText, _addressInfoText,
                        _addressLayout);

        hideAttachmentOption(_marker.getType().startsWith("b-m-p-j"));

        _updateSummary();
        updateAttachmentsButton();

        String iconsetPath = _marker.getMetaString(UserIcon.IconsetPath,
                null);
        if (!UserIcon.IsValidIconsetPath(iconsetPath, context)) {
            _colorButton.setVisibility(View.GONE);
        } else {
            _colorButton.setVisibility(View.VISIBLE);
            _updateColorButtonDrawable();
        }

        if (!_marker.getMetaBoolean("editable", false)) {
            Log.d(TAG, "marker is readonly: " + name);
            setEditable(false);
        } else {
            setEditable(isEditable(_marker));
        }

        //Log.d(TAG, "found: " + ((ViewGroup)_extendedCotInfo).getChildCount());
        for (int i = 0; i < _extendedCotInfo.getChildCount(); ++i) {
            View nextChild = _extendedCotInfo.getChildAt(i);
            //Log.d(TAG, "found: " + nextChild);
            //Log.d(TAG, "found: " + nextChild.getClass());
            if (nextChild instanceof ExtendedInfoView) {
                try {
                    ((ExtendedInfoView) nextChild).setMarker(_marker);
                } catch (Exception e) {
                    Log.e(TAG, "error occurred setting the marker on: "
                            + nextChild);
                }
            }
        }

        _extrasLayout.setItem(_marker);
    }

    private static boolean isEditable(MapItem item) {
        final String type = item.getType();

        boolean retval = item.getType().startsWith("a-");
        if (retval)
            return true;

        for (String editabletype : EDITABLE)
            if (type.equals(editabletype))
                return true;

        return false;
    }

    /**
     * Refresh the author layout for the marker that has been passed in.
     * @param m the marker
     * @param cotAuthorLayout the layout
     * @param cotAuthorIconButton the author icon button
     * @param cotAuthorPanButton the pan to button.
     */
    public static void refreshAuthorLayout(final MapView mapView,
            final Marker m, final TextView _authorText,
            final TextView _productionTimeText, final View cotAuthorLayout,
            final ImageView cotAuthorIconButton,
            final ImageView cotAuthorPanButton) {

        // if the marker is null for whatever reason, just exit out
        if (m == null)
            return;

        final String author_uid = m.getMetaString("parent_uid", null);
        String author_name = null;
        if (author_uid != null) {
            final MapItem author_item = mapView.getMapItem(author_uid);
            if (author_item != null) {
                author_name = author_item.getMetaString("callsign",
                        null);

                if (author_item instanceof Marker) {
                    final Marker author_marker = (Marker) author_item;
                    ATAKUtilities.SetUserIcon(mapView, cotAuthorIconButton,
                            author_marker);
                    cotAuthorPanButton.setVisibility(View.VISIBLE);

                    cotAuthorLayout
                            .setOnClickListener(new OnClickListener() {
                                @Override
                                public void onClick(View view) {
                                    if (author_marker != null) {
                                        GeoPoint gp = author_marker
                                                .getPoint();
                                        CameraController.Programmatic.panTo(
                                                mapView.getRenderer3(), gp,
                                                false);
                                    }
                                }
                            });
                }

            }
        }
        // fallback in case the marker is not on the map
        if (author_name == null)
            author_name = m.getMetaString("parent_callsign", null);

        if (author_name != null) {
            _authorText.setVisibility(VISIBLE);
            _authorText.setText(author_name);
        } else {
            _authorText.setVisibility(GONE);
            _authorText.setText("");
            cotAuthorLayout.setOnClickListener(null);
        }

        String productionTime = m.getMetaString("production_time", null);
        if (productionTime != null) {
            _productionTimeText.setText(productionTime);
            _productionTimeText.setVisibility(VISIBLE);
        } else {
            _productionTimeText.setVisibility(GONE);
        }
    }
}
