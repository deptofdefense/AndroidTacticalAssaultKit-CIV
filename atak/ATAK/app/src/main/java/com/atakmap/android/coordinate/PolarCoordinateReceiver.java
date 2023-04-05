
package com.atakmap.android.coordinate;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import android.widget.RadioGroup;

import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.toolbars.RangeAndBearingEndpoint;
import com.atakmap.android.toolbars.RangeAndBearingMapItem;
import com.atakmap.android.user.PlacePointTool;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.app.R;
import com.atakmap.coremap.conversions.Angle;
import com.atakmap.coremap.conversions.AngleUtilities;
import com.atakmap.coremap.conversions.ConversionFactors;
import com.atakmap.coremap.conversions.Span;
import com.atakmap.coremap.conversions.SpanUtilities;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.conversion.EGM96;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.coremap.maps.coords.NorthReference;

import java.text.DecimalFormat;

import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.map.CameraController;

import java.util.UUID;

public class PolarCoordinateReceiver extends BroadcastReceiver implements
        AdapterView.OnItemSelectedListener {

    public static final String TAG = "PolarCoordinateReceiver";

    private final MapView _mapView;
    private final SharedPreferences _prefs;

    private final Context _context;
    private MapGroup _rabGroup;
    private PointMapItem _relativeMarker;
    private PointMapItem _destMarker;
    private View _relativeMarkerButton;
    private ImageView _relativeMarkerIcon;
    private TextView _relativeMarkerName;
    private RadioGroup _affiliationGroup;

    private EditText _bearingText;
    private Spinner _bearingUnits;
    private Spinner _bearingAlignment;
    private EditText _rangeText;
    private Spinner _rangeUnits;
    private Spinner _zoomMethod;
    private Spinner _polarAction;
    private EditText _inclinationText;

    private int _rangeUnitsVal, _bearingUnitsVal, _bearingAlignVal;

    /**
     * Spinner index list
     */
    private static final int NORTH_REFERENCE_TRUE = 0;
    private static final int NORTH_REFERENCE_MAGNETIC = 1;
    private static final int NORTH_REFERENCE_GRID = 2;
    private static final int BEARING_UNITS_DEGREES = 0;
    private static final int BEARING_UNITS_MILS = 1;

    // XXX - Why not use the corresponding Span values???
    private static final int RANGE_UNITS_METER = 0;
    private static final int RANGE_UNITS_KILOMETER = 1;
    private static final int RANGE_UNITS_FEET = 2;
    private static final int RANGE_UNITS_MILE = 3;
    private static final int RANGE_UNITS_NAUTICAL_MILE = 4;

    //TODO any concerns about data/precision loss with use of these?
    protected static final DecimalFormat NO_DEC_FORMAT = LocaleUtil
            .getDecimalFormat("0");
    protected static final DecimalFormat TWO_DEC_FORMAT = LocaleUtil
            .getDecimalFormat("0.00");
    protected static final DecimalFormat FOUR_DEC_FORMAT = LocaleUtil
            .getDecimalFormat("0.0000");

    private String force;
    final AlertDialog ad;

    public PolarCoordinateReceiver(MapView mapView) {
        _mapView = mapView;
        _context = _mapView.getContext();
        _prefs = PreferenceManager.getDefaultSharedPreferences(mapView
                .getContext());
        View view = getView();
        AlertDialog.Builder builder = new AlertDialog.Builder(_context);
        builder.setView(view);
        builder.setCancelable(false);
        builder.setPositiveButton(R.string.goto_entry_text1, _okListener);
        builder.setNegativeButton(R.string.cancel, _cancelListener);
        builder.setTitle(_mapView.getResources().getString(
                R.string.goto_entry_title));
        ad = builder.create();
    }

    @Override
    public void onReceive(final Context context, Intent intent) {
        force = null;
        _destMarker = null;
        _relativeMarker = null;

        //TODO error checking e.g. valid range changes in future ATAK release that may change polar_array.xml?
        _polarAction.setSelection(_prefs.getInt("coord.polar.action", 0));
        _zoomMethod.setSelection(_prefs.getInt("coord.polar.zoom.method", 0));
        _rangeUnits.setSelection(_rangeUnitsVal = _prefs.getInt(
                "coord.polar.range.units", 0));
        _bearingUnits.setSelection(_bearingUnitsVal = _prefs.getInt(
                "coord.polar.bearing.units", 0));
        _bearingAlignment.setSelection(_bearingAlignVal = _prefs.getInt(
                "coord.polar.bearingAlignment.units", 0));
        _inclinationText.setText("0");

        Bundle extras = intent.getExtras();
        if (extras != null) {
            String uid = extras.getString("UID");
            if (uid != null) {
                MapItem mi = _mapView.getRootGroup().deepFindUID(uid);
                if (mi instanceof PointMapItem) {
                    Log.d(TAG, "polar plot Marker: " + uid);
                    _relativeMarker = (PointMapItem) mi;
                    setRelativeButton(_relativeMarker);
                    _polarAction.setVisibility(View.VISIBLE);

                    //reset UI
                    _bearingText.setText("");
                    _rangeText.setText("");
                    _inclinationText.setText("0");
                } else if (mi instanceof RangeAndBearingMapItem) {
                    //pull out source marker
                    Log.d(TAG,
                            "edit polar plot RangeAndBearingMapItem: " + uid);
                    RangeAndBearingMapItem rbmi = (RangeAndBearingMapItem) mi;
                    String p1uid = rbmi.getPoint1UID();
                    MapItem p1mi = _mapView.getRootGroup().deepFindUID(p1uid);
                    if (p1mi instanceof PointMapItem) {
                        _relativeMarker = (PointMapItem) p1mi;
                        setRelativeButton(_relativeMarker);
                    }

                    //pull out dest marker
                    String p2uid = rbmi.getPoint2UID();
                    MapItem p2mi = _mapView.getRootGroup().deepFindUID(p2uid);
                    if (p2mi instanceof PointMapItem) {
                        _destMarker = (PointMapItem) p2mi;
                        _polarAction.setVisibility(View.INVISIBLE);

                        if (_relativeMarker == null
                                || _relativeMarker.getPoint() == null
                                || !_relativeMarker.getPoint().isValid() ||
                                _destMarker == null
                                || _destMarker.getPoint() == null
                                || !_destMarker.getPoint().isValid()) {
                            Log.w(TAG, "Invalid polar points");
                            Toast.makeText(
                                    _context,
                                    _mapView.getResources().getString(
                                            R.string.rb_input_tip1),
                                    Toast.LENGTH_LONG).show();
                            return;
                        }

                        if (!_destMarker.getMovable()) {
                            Log.w(TAG, "Destination not movable: "
                                    + _destMarker.getUID());
                            Toast.makeText(
                                    _context,
                                    _mapView.getResources().getString(
                                            R.string.rb_input_tip2),
                                    Toast.LENGTH_LONG).show();
                            return;
                        }

                        double curBearingT = _relativeMarker.getPoint()
                                .bearingTo(_destMarker.getPoint());
                        double curRangeM = _relativeMarker.getPoint()
                                .distanceTo(_destMarker.getPoint());

                        Span s = Span.METER;
                        int rangeUnits = rbmi.getRangeUnits();
                        if (rangeUnits == Span.METRIC && curRangeM >= 1000) {
                            s = Span.KILOMETER;
                        } else if (rangeUnits == Span.ENGLISH) {
                            if (SpanUtilities.convert(curRangeM, Span.METER,
                                    Span.MILE) >= 1)
                                s = Span.MILE;
                            else
                                s = Span.FOOT;
                        } else if (rangeUnits == Span.NM) {
                            s = Span.NAUTICALMILE;
                        }
                        _rangeUnits.setSelection(
                                _rangeUnitsVal = getRangePosition(s));
                        _bearingUnits.setSelection(_bearingUnitsVal = rbmi
                                .getBearingUnits().getValue());
                        _bearingAlignment.setSelection(_bearingAlignVal = rbmi
                                .getNorthReference().getValue());

                        //convert values to selected units
                        double range = SpanUtilities.convert(curRangeM,
                                Span.METER, s);

                        // using format is a problem because it truncates the number instead of 
                        // rounding.
                        if (s == Span.METER || s == Span.FOOT)
                            _rangeText.setText(NO_DEC_FORMAT.format(range));
                        else
                            _rangeText.setText(FOUR_DEC_FORMAT.format(range));

                        // convert bearing utilizes the _rangeText for GRID, make sure it is set 
                        // previously.

                        double bearing = convertBearing(
                                curBearingT, rbmi.getBearingUnits().getValue(),
                                _bearingAlignment.getSelectedItemPosition());

                        //init UI
                        _bearingText.setText(TWO_DEC_FORMAT.format(bearing));

                        final double slantRange = GeoCalculations
                                .slantDistanceTo(
                                        _relativeMarker.getPoint(),
                                        _destMarker.getPoint());
                        if (!Double.isNaN(slantRange)) {
                            double offset = -1;
                            if (EGM96
                                    .getHAE(_relativeMarker.getPoint()) <= EGM96
                                            .getHAE(_destMarker.getPoint())) {
                                offset = 1;
                            }

                            double depAngle = offset
                                    * ((Math.acos(curRangeM / slantRange) *
                                            ConversionFactors.DEGREES_TO_RADIANS));

                            if (!Double.isNaN(depAngle)) {
                                _inclinationText.setText(TWO_DEC_FORMAT
                                        .format(depAngle));
                            }
                        }
                    }
                } else {
                    Log.w(TAG, "Unsupported polar source item: " + uid);
                    return;
                }
            }
            force = extras.getString("force");
        }

        if (isEditMode()) {
            ad.setTitle(_mapView.getResources().getString(
                    R.string.rb_editor_title));
            _zoomMethod.setVisibility(View.INVISIBLE);
        } else {
            ad.setTitle(_mapView.getResources().getString(
                    R.string.goto_entry_title));
            _zoomMethod.setVisibility(View.VISIBLE);
        }

        ad.show();

        _bearingText.requestFocus();
        setPolarAction(_polarAction.getSelectedItemPosition());
    }

    private Span getSpan(int position) {
        if (position <= RANGE_UNITS_METER)
            return Span.METER;
        else if (position == RANGE_UNITS_KILOMETER)
            return Span.KILOMETER;
        else if (position == RANGE_UNITS_FEET)
            return Span.FOOT;
        else if (position == RANGE_UNITS_MILE)
            return Span.MILE;
        else if (position == RANGE_UNITS_NAUTICAL_MILE)
            return Span.NAUTICALMILE;
        else {
            Log.w(TAG, "Error position: " + position);
            return Span.METER;
        }
    }

    private int getRangePosition(Span span) {
        switch (span) {
            case KILOMETER:
                return RANGE_UNITS_KILOMETER;
            case FOOT:
            case YARD:
                return RANGE_UNITS_FEET;
            case MILE:
                return RANGE_UNITS_MILE;
            case NAUTICALMILE:
                return RANGE_UNITS_NAUTICAL_MILE;
            case METER:
                return RANGE_UNITS_METER;
        }
        return RANGE_UNITS_METER;
    }

    public final DialogInterface.OnClickListener _okListener = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            if (_relativeMarker != null) {
                try {
                    double curRangeM = getRange(
                            getTextValue(_rangeText),
                            _rangeUnits.getSelectedItemPosition());

                    double curBearingT = getBearing(
                            getTextValue(_bearingText),
                            _bearingUnits.getSelectedItemPosition(),
                            _bearingAlignment.getSelectedItemPosition());

                    double inclination = getTextValue(_inclinationText);

                    // must be expressed in meters
                    GeoPoint point = GeoCalculations.pointAtDistance(
                            _relativeMarker.getPoint(), curBearingT, curRangeM,
                            inclination);
                    placePoint(GeoPointMetaData.wrap(point));
                } catch (NumberFormatException e) {
                    Log.e(TAG, "error: ", e);
                    Toast.makeText(
                            _context,
                            _mapView.getResources().getString(
                                    R.string.rb_input_tip3),
                            Toast.LENGTH_LONG).show();
                }
            }
        }
    };

    public final DialogInterface.OnClickListener _cancelListener = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            dialog.cancel();
        }
    };

    public View getView() {
        LayoutInflater inflater = LayoutInflater.from(_context);
        View view = inflater.inflate(R.layout.polar_coord_entry, null);
        _relativeMarkerButton = view.findViewById(R.id.relativeMarkerButton);
        _relativeMarkerIcon = view.findViewById(
                R.id.relativeMarkerIcon);
        _relativeMarkerName = view.findViewById(
                R.id.relativeMarkerName);
        _bearingText = view.findViewById(R.id.polarBearing);
        _bearingUnits = view.findViewById(R.id.polarBearingUnits);
        _bearingAlignment = view
                .findViewById(R.id.polarBearingAlignment);
        _rangeText = view.findViewById(R.id.polarRange);
        _rangeUnits = view.findViewById(R.id.polarRangeUnits);
        _zoomMethod = view.findViewById(R.id.polarZoom);
        _polarAction = view.findViewById(R.id.polarAction);
        _inclinationText = view.findViewById(R.id.polarInclination);

        _relativeMarkerButton.setOnClickListener(_relativeMarkerButtonListener);

        _polarAction.setOnItemSelectedListener(this);
        _rangeUnits.setOnItemSelectedListener(this);
        _bearingUnits.setOnItemSelectedListener(this);
        _bearingAlignment.setOnItemSelectedListener(this);

        _affiliationGroup = view
                .findViewById(R.id.affiliationGroup);
        _affiliationGroup
                .check(_prefs.getInt("affiliation_group_coordview", 0));

        _affiliationGroup.setOnCheckedChangeListener(
                new RadioGroup.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(RadioGroup group,
                            int checkedId) {
                        _prefs.edit().putInt("affiliation_group_coordview",
                                checkedId).apply();

                    }
                });

        return view;
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view,
            int position, long id) {
        String preferenceKey = null;
        if (parent == _polarAction) {
            setPolarAction(position);
        }

        // Convert range to new units
        else if (parent == _rangeUnits) {
            preferenceKey = "coord.polar.range.units";
            try {
                double rangeM = getRange(getTextValue(_rangeText),
                        _rangeUnitsVal);
                Span s = getSpan(position);
                double newRange = SpanUtilities.convert(rangeM, Span.METER, s);
                if (s == Span.METER || s == Span.FOOT)
                    _rangeText.setText(NO_DEC_FORMAT.format(newRange));
                else
                    _rangeText.setText(FOUR_DEC_FORMAT.format(newRange));
            } catch (Exception ignore) {
            }
            _rangeUnitsVal = position;
        }

        // Convert bearing to new units
        else if (parent == _bearingUnits) {
            preferenceKey = "coord.polar.bearing.units";
            try {
                double bearingDeg = getTextValue(_bearingText);
                if (_bearingUnitsVal == BEARING_UNITS_MILS)
                    bearingDeg = AngleUtilities.convert(bearingDeg,
                            Angle.MIL, Angle.DEGREE);
                double newBearing = AngleUtilities.convert(bearingDeg,
                        Angle.DEGREE, getBearingUnit());
                _bearingText.setText(TWO_DEC_FORMAT.format(newBearing));
            } catch (Exception ignore) {
            }
            _bearingUnitsVal = position;
        }

        // Convert bearing to new reference
        else if (parent == _bearingAlignment) {
            preferenceKey = "coord.polar.bearingAlignment.units";
            try {
                double bearingDeg = getTextValue(_bearingText);
                if (_bearingUnitsVal == BEARING_UNITS_MILS)
                    bearingDeg = AngleUtilities.convert(bearingDeg,
                            Angle.MIL, Angle.DEGREE);

                // correct back to true north
                if (_bearingAlignVal == NORTH_REFERENCE_MAGNETIC) {
                    bearingDeg = ATAKUtilities.convertFromMagneticToTrue(
                            _relativeMarker.getPoint(), bearingDeg);
                } else if (_bearingAlignVal == NORTH_REFERENCE_GRID) {
                    double rangeM = getRange(getTextValue(_rangeText),
                            _rangeUnitsVal);
                    double d = ATAKUtilities.computeGridConvergence(
                            _relativeMarker.getPoint(), bearingDeg, rangeM);
                    bearingDeg = bearingDeg + d;
                }

                // now correct to the appropriate new value

                if (position == NORTH_REFERENCE_MAGNETIC) {
                    bearingDeg = ATAKUtilities.convertFromTrueToMagnetic(
                            _relativeMarker.getPoint(), bearingDeg);
                } else if (position == NORTH_REFERENCE_GRID) {
                    double rangeM = getRange(getTextValue(_rangeText),
                            _rangeUnitsVal);
                    double d = ATAKUtilities.computeGridConvergence(
                            _relativeMarker.getPoint(), bearingDeg, rangeM);
                    bearingDeg = bearingDeg - d;
                }
                bearingDeg = AngleUtilities.wrapDeg(bearingDeg);

                double newBearing = AngleUtilities.convert(bearingDeg,
                        Angle.DEGREE, getBearingUnit());

                _bearingText.setText(TWO_DEC_FORMAT.format(newBearing));
            } catch (Exception ignore) {
            }
            _bearingAlignVal = position;
        }
        if (preferenceKey != null)
            _prefs.edit().putInt(preferenceKey, position).apply();
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
    }

    private void setPolarAction(int position) {
        if (ad == null)
            return;

        if (isEditMode()) {
            ad.getButton(DialogInterface.BUTTON_POSITIVE).setText(
                    _mapView.getResources().getString(R.string.rb_editor_move));
        } else if (position <= 0) {
            ad.getButton(DialogInterface.BUTTON_POSITIVE)
                    .setText(
                            _mapView.getResources().getString(
                                    R.string.goto_entry_text1));
        } else if (position == 1) {
            ad.getButton(DialogInterface.BUTTON_POSITIVE).setText(
                    _mapView.getResources().getString(
                            R.string.goto_entry_text3));
        } else {
            ad.getButton(DialogInterface.BUTTON_POSITIVE).setText(
                    _mapView.getResources()
                            .getString(R.string.goto_entry_text2));
        }

        if (isEditMode()) {
            _affiliationGroup.setVisibility(View.GONE);
        } else if (position == 1) {
            _affiliationGroup.setVisibility(View.INVISIBLE);
        } else {
            _affiliationGroup.setVisibility(View.VISIBLE);
        }
    }

    private boolean isEditMode() {
        return _destMarker != null;
    }

    private final View.OnClickListener _relativeMarkerButtonListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Toast.makeText(_context,
                    _mapView.getResources().getString(R.string.rb_input_tip4),
                    Toast.LENGTH_LONG)
                    .show();
        }
    };

    public void setRelativeButton(PointMapItem marker) {
        _relativeMarkerName.setText(ATAKUtilities
                .getDisplayName(marker));
        MapItem mi = marker;
        if (mi.hasMetaValue("shapeUID"))
            mi = ATAKUtilities.findAssocShape(mi);
        ATAKUtilities.SetIcon(_context, _relativeMarkerIcon, mi);
    }

    /**
     * @param value Value input by user
     * @param unitIndex Index of unit selection
     * @return Input value specified in meters
     */

    private double getRange(double value, int unitIndex) {
        double range = value;
        switch (unitIndex) {
            case RANGE_UNITS_KILOMETER:
                range *= ConversionFactors.KM_TO_METERS;
                break;
            case RANGE_UNITS_FEET:
                range *= ConversionFactors.FEET_TO_METERS;
                break;
            case RANGE_UNITS_MILE:
                range *= ConversionFactors.MILES_TO_METERS;
                break;
            case RANGE_UNITS_NAUTICAL_MILE:
                range *= ConversionFactors.NM_TO_METERS;
                break;
        }
        return range;
    }

    /**
     * @param value Value input by user
     * @param unitIndex Index of unit selection
     * @param northIndex Index of north orientation (true or magnetic)
     * @return Input value specified in degrees from True north
     */
    private double getBearing(final double value, int unitIndex,
            int northIndex) {
        double bearing = value;
        if (unitIndex == BEARING_UNITS_MILS) {
            bearing *= ConversionFactors.MRADIANS_TO_DEGREES;
        }
        switch (northIndex) {
            case NORTH_REFERENCE_MAGNETIC:
                bearing = ATAKUtilities.convertFromMagneticToTrue(
                        _relativeMarker.getPoint(),
                        bearing);
                break;
            case NORTH_REFERENCE_GRID:
                double rangeM = getRange(getTextValue(_rangeText),
                        _rangeUnitsVal);
                double d = ATAKUtilities.computeGridConvergence(
                        _relativeMarker.getPoint(), bearing, rangeM);
                bearing = bearing + d;
                break;
        }
        return bearing;
    }

    /**
     *
     * @param bearingT bearing as Deg True North
     * @param unitIndex  bearing units
     * @param northIndex NORTH_REFERENCE_MAGNETIC or NORTH_REFERENCE_TRUE
     * @return Deg True converted to specified units
     */
    private double convertBearing(double bearingT, int unitIndex,
            int northIndex) {
        double bearing = bearingT;

        // mag conversion always goes first
        switch (northIndex) {
            case NORTH_REFERENCE_MAGNETIC:
                bearing = ATAKUtilities.convertFromTrueToMagnetic(
                        _relativeMarker.getPoint(),
                        bearing);
                break;
            case NORTH_REFERENCE_GRID:
                double rangeM = getRange(getTextValue(_rangeText),
                        _rangeUnitsVal);
                double d = ATAKUtilities.computeGridConvergence(
                        _relativeMarker.getPoint(), bearing, rangeM);
                bearing = bearing - d;
                break;
        }
        bearing = AngleUtilities.wrapDeg(bearing);
        // then use mils - otherwise mag conversion angry...
        if (unitIndex == BEARING_UNITS_MILS) {
            bearing *= ConversionFactors.DEGREES_TO_MRADIANS;
        }
        return bearing;
    }

    /**
     * @param editText EditText containing double value
     * @return Contents of editText as double if possible
     */
    private double getTextValue(EditText editText) {
        try {
            Number n = FOUR_DEC_FORMAT.parse(editText.getText().toString());
            if (n != null)
                return n.doubleValue();
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse double: " + editText.getText());
        }
        return 0;
    }

    private RangeAndBearingMapItem createRB(final PointMapItem start,
            final PointMapItem end) {

        RangeAndBearingMapItem rb = RangeAndBearingMapItem
                .createOrUpdateRABLine(start.getUID() + "" + end.getUID(),
                        start, end, true);
        rb.setBearingUnits(getBearingUnit());
        rb.setNorthReference(getNorthReference());
        try {
            rb.setRangeUnits(getRangeUnit());
        } catch (IllegalStateException ise) {
            Log.e(TAG,
                    "error setting the range units for a range and bearing arrow: "
                            + rb.getUID(),
                    ise);
        }

        if (getGroup() != null) {
            getGroup().addItem(rb);
        }

        rb.persist(MapView.getMapView().getMapEventDispatcher(),
                null, PolarCoordinateReceiver.class);
        return rb;
    }

    private RangeAndBearingMapItem createRB(final PointMapItem start,
            final GeoPointMetaData end) {

        //wrap marker for the dest point
        RangeAndBearingEndpoint dest = new RangeAndBearingEndpoint(end,
                UUID.randomUUID().toString());
        dest.setMetaString("menu", "menus/rab_endpoint_menu.xml");
        if (getGroup() != null) {
            getGroup().addItem(dest);
        }

        return createRB(start, dest);
    }

    private MapGroup getGroup() {
        if (_rabGroup == null)
            _rabGroup = _mapView.getRootGroup().findMapGroup("Range & Bearing");

        return _rabGroup;
    }

    private NorthReference getNorthReference() {
        int pos = _bearingAlignment.getSelectedItemPosition();
        if (pos == 0)
            return NorthReference.TRUE;
        else if (pos == 1)
            return NorthReference.MAGNETIC;
        else
            return NorthReference.GRID;
    }

    private Angle getBearingUnit() {
        return _bearingUnits.getSelectedItemPosition() <= 0 ? Angle.DEGREE
                : Angle.MIL;
    }

    private int getRangeUnit() {
        if (_rangeUnits.getSelectedItemPosition() <= 1) {
            return Span.METRIC;
        } else if (_rangeUnits.getSelectedItemPosition() <= 3) {
            return Span.ENGLISH;
        } else {
            return Span.NM;
        }
    }

    public String getDropPointType() {
        final int id = _affiliationGroup.getCheckedRadioButtonId();
        if (id == R.id.coordDialogDropUnknown)
            return "a-u-G";
        else if (id == R.id.coordDialogDropNeutral)
            return "a-n-G";
        else if (id == R.id.coordDialogDropHostile)
            return "a-h-G";
        else if (id == R.id.coordDialogDropFriendly)
            return "a-f-G";
        else if (id == R.id.coordDialogDropGeneric)
            return "b-m-p-s-m";
        else
            return _prefs.getString("lastCoTTypeSet", "a-h-G");
    }

    /**
     * Create and send intent to place marker in world.
     * 
     * @param point Location of point to be placed
     */
    private void placePoint(GeoPointMetaData point) {
        if (_relativeMarker == null) {
            Log.w(TAG, "No relative marker");
            return;
        }

        if (isEditMode()) {
            Log.d(TAG, "Moving dest point: " + _destMarker.getUID());
            //move existing point
            _destMarker.setPoint(point);
            _destMarker.persist(MapView.getMapView().getMapEventDispatcher(),
                    null, PolarCoordinateReceiver.class);
            return;
        }

        Marker marker;

        int polarAction = _polarAction.getSelectedItemPosition();
        _prefs.edit().putInt("coord.polar.action", polarAction).apply();
        if (polarAction == 1) {
            //Create R&B line
            RangeAndBearingMapItem rabmi = createRB(_relativeMarker, point);
            if (rabmi == null)
                Log.d(TAG, "error placing a range and bearing map item");
        } else {
            //place point, and "both"
            marker = new PlacePointTool.MarkerCreator(point)
                    .setType(
                            (force != null) ? force
                                    : getDropPointType())
                    .showCotDetails(false)
                    .placePoint();

            String title = marker.getTitle();

            if (!title.endsWith(".polar"))
                title = title + ".polar";

            marker.setMetaString("callsign", title);
            marker.setTitle(title);

            //tether new point by a pairing line
            if (polarAction == 2) {
                //Create point tethered to line
                createRB(_relativeMarker, marker);
            }
        }

        int zoomMethod = _zoomMethod.getSelectedItemPosition();
        _prefs.edit().putInt("coord.polar.zoom.method", zoomMethod).apply();
        if (zoomMethod <= 0) {
            //slew to
            CameraController.Programmatic.panTo(
                    _mapView.getRenderer3(),
                    point.get(), true);
        } else {
            //auto zoom
            ATAKUtilities.scaleToFit(_mapView, new GeoPoint[] {
                    point.get(), _relativeMarker.getPoint()
            }, _mapView.getWidth(), _mapView.getHeight());
        }
    }
}
