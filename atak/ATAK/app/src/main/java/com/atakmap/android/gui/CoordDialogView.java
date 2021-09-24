
package com.atakmap.android.gui;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.util.AttributeSet;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TabHost;
import android.widget.TabHost.OnTabChangeListener;
import android.widget.TabHost.TabSpec;
import android.widget.TabWidget;
import android.widget.TextView;
import android.widget.Toast;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.user.geocode.GeocodeManager;
import com.atakmap.android.user.geocode.GeocodingTask;
import com.atakmap.android.user.geocode.ReverseGeocodingTask;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.android.util.AfterTextChangedWatcher;
import com.atakmap.android.util.AltitudeUtilities;
import com.atakmap.app.R;
import com.atakmap.coremap.conversions.CoordinateFormat;
import com.atakmap.coremap.conversions.CoordinateFormatUtilities;
import com.atakmap.coremap.conversions.Span;
import com.atakmap.coremap.conversions.SpanUtilities;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.conversion.EGM96;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoPoint;

import java.text.DecimalFormat;

import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.map.elevation.ElevationManager;

/**
 * 
 */
public class CoordDialogView extends LinearLayout implements
        View.OnClickListener, OnTabChangeListener {
    // Use a tab change listener to update the other coordinate formats from the previous one
    public static final String TAG = "CoordDialogView";

    public static final int MGRS_TAB = 0;
    public static final int DD_TAB = 1;
    public static final int DM_TAB = 2;
    public static final int DMS_TAB = 3;
    public static final int UTM_TAB = 4;
    public static final int ADDRESS_TAB = 5;

    private GeoPointMetaData _mapCenter;

    private TabHost _host;
    private Result _result = Result.INVALID;

    private String _currElevMSL = "";
    private GeoPointMetaData _currPoint;
    private GeoPointMetaData _dtedPoint;
    private String[] _currDD = new String[] {
            "", ""
    };
    private String[] _currAddress = new String[] {
            "", ""
    };
    private String[] _currMGRS = new String[] {
            "", "", "", ""
    };
    private String[] _currUTM = new String[] {
            "", "", "", ""
    };
    private String[] _currDM = new String[] {
            "", "", "", ""
    };
    private String[] _currDMS = new String[] {
            "", "", "", "", "", ""
    };
    private CoordinateFormat _currFormat = CoordinateFormat.MGRS;

    private SharedPreferences _prefs;

    private EditText _elevText;

    private CheckBox _dropAddressChk;
    private RadioGroup _affiliationGroup;
    private TextView _licenseTv;
    private Button _mgrsButton;

    private EditText addressET; //Address Edit Text Field
    private EditText _mgrsZone, _mgrsSquare, _mgrsEast, _mgrsNorth, _mgrsRaw;
    private EditText _utmZone, _utmEast, _utmNorth, _utmRaw;
    private boolean watch = true; // only used to make surce setting the 
                                  // mgrsRaw does not cycle back around
    private boolean watchUTM = true; // only used to make surce setting the 
                                     // utmRaw does not cycle back around
    private EditText _ddLat, _ddLon;
    private EditText _dmsLatD, _dmsLatM, _dmsLatS, _dmsLonD, _dmsLonM,
            _dmsLonS;
    private EditText _dmLatD, _dmLatM, _dmLonD, _dmLonM;

    private static final DecimalFormat NO_DEC_FORMAT = LocaleUtil
            .getDecimalFormat("##0");
    private static final DecimalFormat ONE_DEC_FORMAT = LocaleUtil
            .getDecimalFormat(
                    "##0.0");
    private static final DecimalFormat TWO_DEC_FORMAT = LocaleUtil
            .getDecimalFormat(
                    "##0.00");

    private String altSource = GeoPointMetaData.UNKNOWN;

    private boolean isADDRtabDisabled;

    protected String humanAddress;
    protected GeoPointMetaData humanAddressPoint;

    private final Context context;
    private Button _dtedButton;
    private Button _clearButton;
    private ImageButton _copyButton;

    public CoordDialogView(final Context context) {
        super(context);
        this.context = context;
    }

    public CoordDialogView(final Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
    }

    public void setParameters(GeoPointMetaData point,
            GeoPointMetaData mapCenter,
            CoordinateFormat format) {
        setParameters(point, mapCenter, format, false);
    }

    public void setParameters(GeoPointMetaData point,
            GeoPointMetaData mapCenter,
            CoordinateFormat format, boolean bReadOnly) {
        _setPoint(point);
        if (point != null) {
            _setElev();
        }
        _mapCenter = mapCenter;
        _setFormat(format);
        syncRawMGRS(true);
        syncRawUTM(true);
        setEditable(!bReadOnly);
    }

    private void setEditable(boolean bEditable) {
        setEditable(_elevText, bEditable);
        setEditable(_clearButton, bEditable);
        // No reason to disable the copy button in read-only mode (ATAK-9380)
        setEditable(_copyButton, true);
        setEditable(_dtedButton, bEditable);
        setEditable(_mgrsButton, bEditable);
        setEditable(_dmLonM, bEditable);
        setEditable(_dmLonD, bEditable);
        setEditable(_dmLatM, bEditable);
        setEditable(_dmLatD, bEditable);
        setEditable(_dmsLonS, bEditable);
        setEditable(_dmsLonM, bEditable);
        setEditable(_dmsLonD, bEditable);
        setEditable(_dmsLatS, bEditable);
        setEditable(_dmsLatM, bEditable);
        setEditable(_dmsLatD, bEditable);
        setEditable(_ddLon, bEditable);
        setEditable(_ddLat, bEditable);
        setEditable(_mgrsRaw, bEditable);
        setEditable(_mgrsNorth, bEditable);
        setEditable(_mgrsEast, bEditable);
        setEditable(_mgrsSquare, bEditable);
        setEditable(_mgrsZone, bEditable);
        setEditable(_utmZone, bEditable);
        setEditable(_utmEast, bEditable);
        setEditable(_utmNorth, bEditable);
        setEditable(_utmRaw, bEditable);
        setEditable(addressET, bEditable);
        setEditable(_dropAddressChk, bEditable);
    }

    private void setEditable(View v, boolean editable) {
        if (v != null) {
            v.setEnabled(editable);
            if (v instanceof EditText)
                ((EditText) v).setTextColor(editable ? 0xFFFFFFFF : 0xFFCCCCCC);
        }
    }

    public void setParameters(GeoPointMetaData point,
            GeoPointMetaData mapCenter) {
        setParameters(point, mapCenter, CoordinateFormat.MGRS);
    }

    public Result getResult() {
        return _result;
    }

    public GeoPointMetaData getPoint() {
        return _getGeoPoint();
    }

    public CoordinateFormat getCoordFormat() {
        return _currFormat;
    }

    /**
     * @return Point, or null if unrecognized format.
     */
    private GeoPointMetaData _getPoint() {
        GeoPointMetaData point = null;
        switch (_currFormat) {
            case MGRS:
                point = _getMGRS();
                break;
            case DD:
                point = _getDD();
                break;
            case DM:
                point = _getDM();
                break;
            case DMS:
                point = _getDMS();
                break;
            case UTM:
                point = _getUTM();
                break;
            case ADDRESS:
                point = _getAddress();
                break;
            default:
                break;

        }
        if (point != null && point.get().isValid()) {
            if (_result == Result.INVALID)
                _result = Result.VALID_CHANGED;
        } else
            _result = Result.INVALID;
        return point;
    }

    // perform basic validation of text fields
    private void _validatePoint() {
        GeoPointMetaData point = null;
        switch (_currFormat) {
            case MGRS:
                point = _getMGRSForConvert();
                break;
            case DD:
                point = _getDDForConvert();
                break;
            case DM:
                point = _getDMForConvert();
                break;
            case DMS:
                point = _getDMSForConvert();
                break;
            case UTM:
                point = _getUTMForConvert();
                break;
            case ADDRESS:
                point = _getAddressForConvert();
                break;
        }
        if (point != null) {
            _result = Result.VALID_CHANGED;
        }
    }

    private GeoPointMetaData _getGeoPoint() {
        double altHAE = GeoPoint.UNKNOWN;

        GeoPointMetaData point = _getPoint();
        if (point == null)
            return null; //Return null if point failed

        _validatePoint();

        String elev = "";
        try {
            elev = _elevText.getText().toString().trim();
            if (!elev.isEmpty()) {
                altHAE = EGM96.getHAE(point.get().getLatitude(),
                        point.get().getLongitude(),
                        SpanUtilities.convert(
                                elev, Span.FOOT,
                                Span.METER));
            }
        } catch (NumberFormatException e) {
            Log.e("CoordDialogView", "NFE in _getGeoPoint()");
        }

        if (!_currElevMSL.equals(elev) && _result == Result.VALID_UNCHANGED
                || _result == Result.VALID_CHANGED) {
            _result = Result.VALID_CHANGED;

            if (this.altSource.equals(GeoPointMetaData.UNKNOWN)
                    || _dtedPoint == null
                    || !_dtedPoint.equals(point)) {
                // If it changed, and it wasn't dted, then it was the user
                // Or the user modified the point, which means we shouldn't
                // consider the altitude source valid anymore
                altSource = GeoPointMetaData.USER;
            }

        }
        GeoPointMetaData ret = GeoPointMetaData.wrap(new GeoPoint(
                point.get().getLatitude(),
                point.get().getLongitude(), altHAE))
                .setAltitudeSource(altSource);
        _currPoint = ret;
        return ret;
    }

    /**
     * @return returns the formatted string given the current tab used
     */
    public String getFormattedString() {
        if (_currPoint != null) {
            final String p = CoordinateFormatUtilities.formatToString(
                    _currPoint.get(), _currFormat);
            final String a = AltitudeUtilities.format(_currPoint);

            return p + "\n" + a;
        } else {
            return "error";
        }

    }

    private int typeToTab(CoordinateFormat cf) {
        if (cf == CoordinateFormat.ADDRESS) {
            return 5;
        } else if (cf == CoordinateFormat.UTM) {
            return 4;
        } else {
            return cf.getValue();
        }

    }

    private void _setFormat(CoordinateFormat format) {
        Log.d(TAG,
                "_setFormat: " + format.toString() + ", " + format.getValue());
        _currFormat = format;
        if (_host != null) {
            _host.setCurrentTab(typeToTab(format));
        }

        // The Tab listener isn't getting called, so force the MGRS grid button to be invisible
        // here.
        if (_currFormat == CoordinateFormat.ADDRESS) {
            _mgrsButton.setVisibility(View.GONE);
        }

        updateAddressCB();
    }

    private void _setPoint(GeoPointMetaData p) {
        _currPoint = p;
        _updatePoint();
    }

    private String altVal = null;

    private boolean _pullFromDTED() {
        boolean ret = false;
        GeoPointMetaData point = _getPoint();
        if (point != null) {
            // pull the elevation and make sure it is in MSL
            GeoPointMetaData altHAE = ElevationManager
                    .getElevationMetadata(point.get());
            double alt = EGM96.getMSL(altHAE.get());
            if (altHAE.get().isAltitudeValid()) {
                altVal = _formatElevation(SpanUtilities.convert(alt,
                        Span.METER, Span.FOOT));
                _elevText.setText(altVal);
                altSource = altHAE.getAltitudeSource();
                _dtedPoint = point;
                ret = true;
            } else {
                Toast.makeText(
                        getContext(),
                        context.getResources().getString(
                                R.string.goto_input_tip1),
                        Toast.LENGTH_LONG).show();
            }
        }
        return ret;
    }

    /**
     * Check if the coordinate has been changed
     * @param coord New input coordinate
     * @param currCoord Original input coordinate
     * @return True if changed
     */
    private boolean pointChanged(String[] coord, String[] currCoord) {
        if (FileSystemUtils.isEmpty(coord) &&
                FileSystemUtils.isEmpty(currCoord))
            return true;
        if (_currPoint != null && !FileSystemUtils.isEmpty(coord)
                && !FileSystemUtils.isEmpty(currCoord)) {
            if (coord.length != currCoord.length)
                return true;
            // Check if the MGRS input has been changed
            // If not then use the same input point to avoid unexpected
            // point modifications (ATAK-8585)
            boolean changed = false;
            for (int i = 0; i < currCoord.length && i < coord.length; i++) {
                if (!FileSystemUtils.isEquals(currCoord[i], coord[i])) {
                    changed = true;
                    break;
                }
            }
            return changed;
        }
        return true;
    }

    private GeoPointMetaData _getMGRS() {
        try {
            String[] coord = new String[] {
                    _mgrsZone.getText().toString()
                            .toUpperCase(LocaleUtil.getCurrent()),
                    _mgrsSquare.getText().toString()
                            .toUpperCase(LocaleUtil.getCurrent()),
                    _mgrsEast.getText().toString(),
                    _mgrsNorth.getText().toString()
            };
            if (!pointChanged(coord, _currMGRS))
                return _currPoint;
            return GeoPointMetaData
                    .wrap(CoordinateFormatUtilities.convert(coord,
                            CoordinateFormat.MGRS));
        } catch (IllegalArgumentException e) {
            Log.e(TAG, context.getString(R.string.goto_input_tip2), e);
            Toast.makeText(getContext(),
                    context.getString(R.string.goto_input_tip2),
                    Toast.LENGTH_LONG).show();
        }
        return null;
    }

    private GeoPointMetaData _getUTM() {
        try {
            String[] coord = new String[] {
                    _utmZone.getText().toString()
                            .toUpperCase(LocaleUtil.getCurrent()),
                    _utmEast.getText().toString(),
                    _utmNorth.getText().toString()
            };
            if (!pointChanged(coord, _currUTM))
                return _currPoint;
            return GeoPointMetaData
                    .wrap(CoordinateFormatUtilities.convert(coord,
                            CoordinateFormat.UTM));
        } catch (IllegalArgumentException e) {
            Log.e(TAG, context.getString(R.string.goto_input_tip2), e);
            Toast.makeText(getContext(),
                    context.getString(R.string.goto_input_tip2),
                    Toast.LENGTH_LONG).show();
        }
        return null;
    }

    private GeoPointMetaData _getMGRSForConvert() {
        String zone = _mgrsZone.getText().toString().trim()
                .toUpperCase(LocaleUtil.getCurrent());
        String square = _mgrsSquare.getText().toString().trim()
                .toUpperCase(LocaleUtil.getCurrent());
        String east = _mgrsEast.getText().toString().trim();
        String north = _mgrsNorth.getText().toString().trim();
        // Check if any of the edit texts are empty
        if (zone.isEmpty() || square.isEmpty() || east.isEmpty()
                || north.isEmpty()) {
            // They haven't finished entering in so don't try to convert, and don't throw
            // a toast to say the coordinate entry is invalid
            return null;
        }
        // The user didn't change the text since it was set so don't update
        if (zone.equals(_currMGRS[0]) && square.equals(_currMGRS[1])
                && east.equals(_currMGRS[2]) && north.equals(_currMGRS[3])) {
            return null;
        }
        GeoPointMetaData retval = _getMGRS();
        _currMGRS = new String[] {
                zone, square, east, north
        };
        return retval;
    }

    private GeoPointMetaData _getUTMForConvert() {
        String zone = _utmZone.getText().toString().trim()
                .toUpperCase(LocaleUtil.getCurrent());
        String east = _utmEast.getText().toString().trim();
        String north = _utmNorth.getText().toString().trim();
        // Check if any of the edit texts are empty
        if (zone.isEmpty() || east.isEmpty()
                || north.isEmpty()) {
            // They haven't finished entering in so don't try to convert, and don't throw
            // a toast to say the coordinate entry is invalid
            return null;
        }
        // The user didn't change the text since it was set so don't update
        if (zone.equals(_currUTM[0])
                && east.equals(_currUTM[1]) && north.equals(_currUTM[2])) {
            return null;
        }
        GeoPointMetaData retval = _getUTM();
        _currMGRS = new String[] {
                zone, east, north
        };
        return retval;
    }

    private void _setMGRS(GeoPointMetaData p) {
        String[] mgrs;
        if (p != null)
            mgrs = CoordinateFormatUtilities.formatToStrings(p.get(),
                    CoordinateFormat.MGRS);
        else
            mgrs = new String[] {
                    "", "", "", ""
            };

        _mgrsZone.setText(mgrs[0]);
        _mgrsSquare.setText(mgrs[1]);
        _mgrsEast.setText(mgrs[2]);
        _mgrsNorth.setText(mgrs[3]);
        _currMGRS = mgrs;
        syncRawMGRS(false);

    }

    private void _setUTM(GeoPointMetaData p) {
        String[] utm;
        if (p != null)
            utm = CoordinateFormatUtilities.formatToStrings(p.get(),
                    CoordinateFormat.UTM);
        else
            utm = new String[] {
                    "", "", ""
            };

        _utmZone.setText(utm[0]);
        _utmEast.setText(utm[1]);
        _utmNorth.setText(utm[2]);
        _currUTM = utm;
        syncRawUTM(false);

    }

    private GeoPointMetaData _getDD() {
        try {
            String[] coord = new String[] {
                    _ddLat.getText().toString(), _ddLon.getText().toString()
            };
            if (!pointChanged(coord, _currDD))
                return _currPoint;
            return GeoPointMetaData.wrap(CoordinateFormatUtilities
                    .convert(coord, CoordinateFormat.DD));
        } catch (IllegalArgumentException e) {
            Log.e(TAG, context.getString(R.string.goto_input_tip3), e);
            Toast.makeText(getContext(),
                    context.getString(R.string.goto_input_tip3),
                    Toast.LENGTH_LONG).show();
            return null;
        }
    }

    private GeoPointMetaData _getAddress() {
        try {
            String[] coord = new String[] {
                    _ddLat.getText().toString(), _ddLon.getText().toString()
            };
            return GeoPointMetaData.wrap(CoordinateFormatUtilities
                    .convert(coord, CoordinateFormat.ADDRESS));
        } catch (IllegalArgumentException e) {
            Log.e(TAG, context.getString(R.string.goto_input_tip4), e);
            Toast.makeText(getContext(),
                    context.getString(R.string.goto_input_tip4),
                    Toast.LENGTH_LONG).show();
            return null;
        }
    }

    private GeoPointMetaData _getDDForConvert() {
        String lon = _ddLon.getText().toString().trim();
        String lat = _ddLat.getText().toString().trim();
        // Check if any of the edit texts are empty
        if (lon.isEmpty() || lat.isEmpty()) {
            // They haven't finished entering in so don't try to convert, and don't throw
            // a toast to say the coordinate entry is invalid
            return null;
        }
        // The user didn't change the text since it was set so don't update
        if (lat.equals(_currDD[0]) && lon.equals(_currDD[1])) {
            return null;
        }

        GeoPointMetaData retval = _getDD();
        _currDD = new String[] {
                lat, lon
        };
        return retval;
    }

    private GeoPointMetaData _getAddressForConvert() {
        String lon = _ddLon.getText().toString().trim();
        String lat = _ddLat.getText().toString().trim();
        // Check if any of the edit texts are empty
        if (lon.isEmpty() || lat.isEmpty()) {
            // They haven't finished entering in so don't try to convert, and don't throw
            // a toast to say the coordinate entry is invalid
            return null;
        }
        // The user didn't change the text since it was set so don't update
        if (lat.equals(_currAddress[0]) && lon.equals(_currAddress[1])) {
            return null;
        }
        GeoPointMetaData retval = _getAddress();
        _currAddress = new String[] {
                lat, lon
        };
        return retval;
    }

    private void _setDD(GeoPointMetaData p) {
        String[] dd;
        if (p != null) {
            dd = CoordinateFormatUtilities.formatToStrings(p.get(),
                    CoordinateFormat.DD);
        } else {
            dd = new String[] {
                    "", ""
            };
        }

        _ddLat.setText(dd[0]);
        _ddLon.setText(dd[1]);
        _currDD = dd;
    }

    private void _setAddress(GeoPointMetaData p) {
    }

    private GeoPointMetaData _getDM() {
        try {
            String[] coord = new String[] {
                    _dmLatD.getText().toString(), _dmLatM.getText().toString(),
                    _dmLonD.getText().toString(), _dmLonM.getText().toString()
            };
            if (!pointChanged(coord, _currDM))
                return _currPoint;
            return GeoPointMetaData.wrap(CoordinateFormatUtilities
                    .convert(coord, CoordinateFormat.DM));
        } catch (IllegalArgumentException e) {
            Log.e(TAG, context.getString(R.string.goto_input_tip5), e);
            Toast.makeText(getContext(),
                    context.getString(R.string.goto_input_tip5),
                    Toast.LENGTH_LONG).show();
            return null;
        }
    }

    private GeoPointMetaData _getDMForConvert() {

        String latD = _dmLatD.getText().toString().trim();
        String latM = _dmLatM.getText().toString().trim();
        String lonD = _dmLonD.getText().toString().trim();
        String lonM = _dmLonM.getText().toString().trim();
        // Check if any of the edit texts are empty
        if (latD.isEmpty() || latM.isEmpty() || lonD.isEmpty()
                || lonM.isEmpty()) {
            // They haven't finished entering in so don't try to convert, and don't throw
            // a toast to say the coordinate entry is invalid
            return null;
        }
        // The user didn't change the text since it was set so don't update
        if (latD.equals(_currDM[0]) && latM.equals(_currDM[1])
                && lonD.equals(_currDM[2]) && lonM.equals(_currDM[3])) {
            return null;
        }

        GeoPointMetaData retval = _getDM();
        _currDM = new String[] {
                latD, latM, lonD, lonM
        };
        return retval;
    }

    private void _setDM(GeoPointMetaData p) {
        String[] dm;
        if (p != null) {
            dm = CoordinateFormatUtilities.formatToStrings(p.get(),
                    CoordinateFormat.DM);
        } else {
            dm = new String[] {
                    "", "", "", ""
            };
        }

        _dmLatD.setText(dm[0]);
        _dmLatM.setText(dm[1]);
        _dmLonD.setText(dm[2]);
        _dmLonM.setText(dm[3]);
        _currDM = dm;
    }

    private GeoPointMetaData _getDMS() {
        try {
            String[] coord = new String[] {
                    _dmsLatD.getText().toString(),
                    _dmsLatM.getText().toString(),
                    _dmsLatS.getText().toString(),
                    _dmsLonD.getText().toString(),
                    _dmsLonM.getText().toString(),
                    _dmsLonS.getText().toString()
            };
            if (!pointChanged(coord, _currDMS))
                return _currPoint;
            return GeoPointMetaData
                    .wrap(CoordinateFormatUtilities.convert(coord,
                            CoordinateFormat.DMS));
        } catch (IllegalArgumentException e) {
            Log.e(TAG, context.getString(R.string.goto_input_tip6), e);
            Toast.makeText(getContext(),
                    context.getString(R.string.goto_input_tip6),
                    Toast.LENGTH_LONG).show();
            return null;
        }
    }

    private GeoPointMetaData _getDMSForConvert() {
        String latD = _dmsLatD.getText().toString().trim();
        String latM = _dmsLatM.getText().toString().trim();
        String latS = _dmsLatS.getText().toString().trim();
        String lonD = _dmsLonD.getText().toString().trim();
        String lonM = _dmsLonM.getText().toString().trim();
        String lonS = _dmsLonS.getText().toString().trim();

        // Check if any of the edit texts are empty
        if (latD.isEmpty() || latM.isEmpty() || latS.isEmpty()
                || lonD.isEmpty() || lonM.isEmpty() || lonS.isEmpty()) {
            // They haven't finished entering in so don't try to convert, and don't throw
            // a toast to say the coordinate entry is invalid
            return null;
        }
        if (latD.equals(_currDMS[0]) && latM.equals(_currDMS[1])
                && latS.equals(_currDMS[2])
                && lonD.equals(_currDMS[3]) && lonM.equals(_currDMS[4])
                && lonS.equals(_currDMS[5])) {
            return null;
        }

        GeoPointMetaData retval = _getDMS();
        _currDMS = new String[] {
                latD, latM, latS, lonD, lonM, lonS
        };
        return retval;
    }

    private void _setDMS(GeoPointMetaData p) {
        String[] dms;
        if (p != null) {
            dms = CoordinateFormatUtilities.formatToStrings(p.get(),
                    CoordinateFormat.DMS);
        } else {
            dms = new String[] {
                    "", "", "", "", "", ""
            };
        }
        _dmsLatD.setText(dms[0]);
        _dmsLatM.setText(dms[1]);
        _dmsLatS.setText(dms[2]);
        _dmsLonD.setText(dms[3]);
        _dmsLonM.setText(dms[4]);
        _dmsLonS.setText(dms[5]);
        _currDMS = dms;

    }

    @Override
    protected void onFinishInflate() {

        super.onFinishInflate();
        _prefs = PreferenceManager.getDefaultSharedPreferences(getContext());

        _dropAddressChk = findViewById(R.id.coordDialogAddress);

        _affiliationGroup = findViewById(R.id.affiliationGroup);
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

        _licenseTv = findViewById(R.id.license);

        _licenseTv.setText(getAddressLookupSource());

        if (!isInEditMode()) { // The editor can't handle tabs apparently;
            _host = findViewById(R.id.coordDialogTabHost);
            _host.setup();
            TabSpec _mgrsSpec = _host
                    .newTabSpec(CoordinateFormat.MGRS.getDisplayName());
            _mgrsSpec.setIndicator(CoordinateFormat.MGRS.getDisplayName());
            _mgrsSpec.setContent(R.id.coordDialogMGRSView);
            TabSpec _ddSpec = _host.newTabSpec(CoordinateFormat.DD
                    .getDisplayName());
            _ddSpec.setIndicator(CoordinateFormat.DD.getDisplayName());
            _ddSpec.setContent(R.id.coordDialogDDView);
            TabSpec _dmSpec = _host.newTabSpec(CoordinateFormat.DM
                    .getDisplayName());
            _dmSpec.setIndicator(CoordinateFormat.DM.getDisplayName());
            _dmSpec.setContent(R.id.coordDialogDMView);
            TabSpec _dmsSpec = _host.newTabSpec(CoordinateFormat.DMS
                    .getDisplayName());
            _dmsSpec.setIndicator(CoordinateFormat.DMS.getDisplayName());
            _dmsSpec.setContent(R.id.coordDialogDMSView);
            TabSpec _utmSpec = _host
                    .newTabSpec(CoordinateFormat.UTM.getDisplayName());
            _utmSpec.setIndicator(CoordinateFormat.UTM.getDisplayName());
            _utmSpec.setContent(R.id.coordDialogUTMView);

            TabSpec addyToLatLongTab = _host
                    .newTabSpec(CoordinateFormat.ADDRESS
                            .getDisplayName());
            addyToLatLongTab.setIndicator(CoordinateFormat.ADDRESS
                    .getDisplayName());
            addyToLatLongTab.setContent(R.id.addyToLatLongTab);

            _host.addTab(_mgrsSpec);
            _host.addTab(_ddSpec);
            _host.addTab(_dmSpec);
            _host.addTab(_dmsSpec);
            _host.addTab(_utmSpec);
            _host.addTab(addyToLatLongTab);

            TabWidget tw = _host.getTabWidget();
            for (int i = 0; i < tw.getChildCount(); i++) {
                View v = tw.getChildAt(i);
                v.setPadding(36, 0, 36, 0);
                //TextView title = v.findViewById(android.R.id.title);
                // shb: do not use raw pixels - pixel density has increased
                // determine the conversion from raw pixel size to scale independent size.
                //final float sizeSP = TypedValue.applyDimension(
                //        TypedValue.COMPLEX_UNIT_SP, 4,
                //        context.getResources().getDisplayMetrics());

                //title.setTextSize(sizeSP);
            }

            checkADDRtab();

            View atv = tw.getChildTabViewAt(ADDRESS_TAB);
            if (atv != null) {
                if ((_prefs.getBoolean("enableGeocoder", true))) {
                    atv.setVisibility(VISIBLE);
                } else {
                    atv.setVisibility(GONE);
                }
            }
            _host.setCurrentTab(_currFormat.getValue());
            Log.d(TAG, "onFinishInflate: " + _currFormat.toString() + ", "
                    + _currFormat.getValue());

            post(new Runnable() {

                @Override
                public void run() {
                    // If we don't set the size in code, the hints will expand
                    // it even though they're invisible!
                    // Don't think there's a way to set it strictly to parent's
                    // size in XML? fit_parent won't contradict the size widgets
                    // want to be
                    findViewById(R.id.coordDialogDMSView)
                            .getLayoutParams().width = ((View) findViewById(
                                    R.id.coordDialogDMSView).getParent())
                                            .getMeasuredWidth();

                    // Hack to set tab bar's height because apparently on 7'' tab with two-line tab
                    // labels it doesn't automatically...?
                    // findViewById(android.R.id.tabs).getLayoutParams().height =
                    // _ddTabView.getMeasuredHeight();
                }
            });
        }

        _host.getTabWidget().getChildAt(ADDRESS_TAB).setOnClickListener(this);
        _host.setOnTabChangedListener(this);

        _elevText = findViewById(R.id.coordDialogElevationText);
        _elevText.addTextChangedListener(new AfterTextChangedWatcher() {
            @Override
            public void afterTextChanged(Editable arg0) {
                try {
                    String txt = _elevText.getText().toString().trim();
                    if (!FileSystemUtils.isEquals(txt, altVal)) {
                        if (altVal != null)
                            altSource = GeoPointMetaData.USER;
                        altVal = txt;
                        _dtedPoint = null;
                    }
                } catch (NumberFormatException e) {
                    // Do nothing
                }
            }
        });
        _utmZone = findViewById(R.id.coordDialogUTMGridText);
        _utmEast = findViewById(R.id.coordDialogUTMEastingText);
        _utmNorth = findViewById(R.id.coordDialogUTMNorthingText);

        _mgrsZone = findViewById(R.id.coordDialogMGRSGridText);
        _mgrsSquare = findViewById(R.id.coordDialogMGRSSquareText);
        _mgrsEast = findViewById(R.id.coordDialogMGRSEastingText);
        _mgrsNorth = findViewById(R.id.coordDialogMGRSNorthingText);

        findViewById(R.id.swap).setOnClickListener(this);
        findViewById(R.id.swapUTM).setOnClickListener(this);

        _mgrsRaw = findViewById(R.id.rawMGRS);
        _mgrsRaw.addTextChangedListener(new AfterTextChangedWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                try {
                    // see the sync method
                    if (!watch)
                        return;

                    GeoPoint gp = CoordinateFormatUtilities.convert(
                            s.toString(),
                            CoordinateFormat.MGRS);
                    if (gp != null) {
                        _setPoint(GeoPointMetaData.wrap(gp));
                        _result = Result.VALID_CHANGED;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "error occurred", e);
                }
            }
        });

        _utmRaw = findViewById(R.id.rawUTM);
        _utmRaw.addTextChangedListener(new AfterTextChangedWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                try {
                    // see the sync method
                    if (!watchUTM)
                        return;

                    GeoPoint gp = CoordinateFormatUtilities.convert(
                            s.toString(),
                            CoordinateFormat.UTM);
                    if (gp != null) {
                        _setPoint(GeoPointMetaData.wrap(gp));
                        _result = Result.VALID_CHANGED;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "error occurred", e);
                }
            }
        });

        _ddLat = findViewById(R.id.coordDialogDDLatText);
        _ddLon = findViewById(R.id.coordDialogDDLonText);
        addressET = findViewById(R.id.addressET);
        _dmsLatD = findViewById(R.id.coordDialogDMSLatDegText);
        _dmsLatM = findViewById(R.id.coordDialogDMSLatMinText);
        _dmsLatS = findViewById(R.id.coordDialogDMSLatSecText);
        _dmsLonD = findViewById(R.id.coordDialogDMSLonDegText);
        _dmsLonM = findViewById(R.id.coordDialogDMSLonMinText);
        _dmsLonS = findViewById(R.id.coordDialogDMSLonSecText);
        _dmLatD = findViewById(R.id.coordDialogDMLatDegText);
        _dmLatM = findViewById(R.id.coordDialogDMLatMinText);
        _dmLonD = findViewById(R.id.coordDialogDMLonDegText);
        _dmLonM = findViewById(R.id.coordDialogDMLonMinText);

        _mgrsButton = findViewById(R.id.coordDialogMGRSGridButton);
        _mgrsButton.setOnClickListener(this);

        _mgrsZone.addTextChangedListener(new AfterTextChangedWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (s.length() == 3) {
                    _mgrsSquare.requestFocus();
                    if (_mgrsSquare.getText().length() > 0)
                        _mgrsSquare.setSelection(0, _mgrsSquare.getText()
                                .length());
                }
            }
        });
        _mgrsSquare.addTextChangedListener(new AfterTextChangedWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (s.length() == 2) {
                    _mgrsEast.requestFocus();
                    if (_mgrsEast.getText().length() > 0)
                        _mgrsEast.setSelection(0, _mgrsEast.getText().length());
                }
            }
        });

        _mgrsEast.addTextChangedListener(new AfterTextChangedWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (s.length() == 5) {
                    _mgrsNorth.requestFocus();
                    if (_mgrsNorth.getText().length() > 0)
                        _mgrsNorth.setSelection(0, _mgrsNorth.getText()
                                .length());
                }
            }
        });
        // The Tab listener isn't getting called, so force the MGRS grid button to be invisible
        // here.
        if (_currFormat != CoordinateFormat.MGRS) {
            _mgrsButton.setVisibility(View.INVISIBLE);

        }
        _dtedButton = findViewById(R.id.coordDialogDtedButton);
        _dtedButton.setOnClickListener(this);
        _updateElev();
        _updatePoint();

        findViewById(R.id.button_convert_address).setOnClickListener(this);

        _clearButton = findViewById(R.id.clearButton);
        _clearButton.setOnClickListener(this);

        _copyButton = findViewById(R.id.copyButton);
        _copyButton.setOnClickListener(this);

        enableFormattedMGRS(_prefs.getBoolean("coordview.formattedMGRS", true));
        enableFormattedUTM(_prefs.getBoolean("coordview.formattedUTM", true));
    }

    @Override
    public void onTabChanged(String tabId) {
        hideKeyboard();
        GeoPointMetaData point = null;
        // the previous format used
        switch (_currFormat) {
            case MGRS:
                // if the tab used to be mgrs then get rid of the mgrs button
                //_mgrsButton.setVisibility(View.INVISIBLE);
                point = _getMGRSForConvert();
                if (point != null) {
                    _setDD(point);
                    _setDM(point);
                    _setDMS(point);
                    _setUTM(point);
                    _setAddress(point);

                }
                break;
            case DD:
                point = _getDDForConvert();
                if (point != null) {
                    _setMGRS(point);
                    _setDM(point);
                    _setDMS(point);
                    _setUTM(point);
                    _setAddress(point);
                    syncRawMGRS(false);
                    syncRawUTM(false);

                }
                break;
            case DM:
                point = _getDMForConvert();
                if (point != null) {
                    _setMGRS(point);
                    _setDD(point);
                    _setDMS(point);
                    _setUTM(point);
                    _setAddress(point);
                    syncRawMGRS(false);
                    syncRawUTM(false);

                }
                break;
            case DMS:
                point = _getDMSForConvert();
                if (point != null) {
                    _setMGRS(point);
                    _setDM(point);
                    _setDD(point);
                    _setUTM(point);
                    _setAddress(point);
                    syncRawMGRS(false);
                    syncRawUTM(false);

                }
                break;
            case ADDRESS:
                point = _getAddressForConvert();
                if (point != null) {
                    _setMGRS(point);
                    _setDM(point);
                    _setDD(point);
                    _setDMS(point);
                    _setUTM(point);
                    syncRawMGRS(false);
                    syncRawUTM(false);

                }
                break;
            case UTM:
                point = _getAddressForConvert();
                if (point != null) {
                    _setMGRS(point);
                    _setDM(point);
                    _setDD(point);
                    _setDMS(point);
                    _setAddress(point);
                }
                break;

        }

        // the coordinate has changed since last run, need to wipe out the
        // address.   Need to check based on distance because the address point has
        // a different level of precision then what is stored when doing display
        // readback for the other conversion screens.   Accuracy of the other conversion
        // screens within a meter.
        //
        if (point != null && humanAddressPoint != null) {
            if (humanAddressPoint.get().distanceTo(point.get()) > 1) {
                Log.d(TAG,
                        "address lookup point: " + humanAddressPoint
                                + ", but point has changed: " + point
                                + ", distance: "
                                + humanAddressPoint.get()
                                        .distanceTo(point.get())
                                + " clearing");
                _setAddressParams("", null);
            }
        }

        // If the point is not null then the value represent by this view was changed
        if (point != null) {
            _result = Result.VALID_CHANGED;
            _currPoint = point;
        }

        _currFormat = CoordinateFormat.find(tabId);
        // If the tab is now MGRS, show the mgrs button
        if (_currFormat != CoordinateFormat.ADDRESS) {
            _mgrsButton.setVisibility(View.VISIBLE);
        } else {
            _mgrsButton.setVisibility(View.INVISIBLE);
        }

        if (_currFormat == CoordinateFormat.ADDRESS) {
            if (_currPoint != null && humanAddressPoint != null) {
                if (humanAddressPoint.get().distanceTo(_currPoint.get()) > 1) {
                    Log.d(TAG, "reverse lookup based on currentPoint "
                            + _currPoint);
                    handleSearchButton();
                }
            } else if (_currPoint != null) {
                Log.d(TAG, "reverse lookup based on currentPoint "
                        + _currPoint);
                handleSearchButton();
            } else {
                Log.d(TAG, "no reverse lookup");
            }
        }
        updateAddressCB();
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();

        // Swap MGRS
        if (id == R.id.swap)
            enableFormattedMGRS(_mgrsRaw.getVisibility() != View.GONE);

        // Swap UTM
        else if (id == R.id.swapUTM)
            enableFormattedUTM(_utmRaw.getVisibility() != View.GONE);

        // Pull from DTED
        else if (v == _dtedButton)
            _pullFromDTED();

        // Clear coordinate
        else if (v == _clearButton)
            clear();

        // Find address
        else if (id == R.id.button_convert_address) {
            if (isNetworkAvailable()) {
                handleSearchButton();
            } else {
                Toast.makeText(
                        getContext(),
                        context.getString(R.string.goto_input_tip8),
                        Toast.LENGTH_SHORT).show();

            }
            post(new Runnable() {
                @Override
                public void run() {
                    hideKeyboard();
                }
            });
        }

        // Auto fill
        else if (v == _mgrsButton) {
            switch (_currFormat) {
                case MGRS: {
                    String[] c = CoordinateFormatUtilities.formatToStrings(
                            _mapCenter.get(),
                            CoordinateFormat.MGRS);
                    _mgrsZone.setText(c[0]);
                    _mgrsSquare.setText(c[1]);
                    syncRawMGRS(true);
                    showKeyboard();
                    break;
                }
                case DD: {
                    String[] c = CoordinateFormatUtilities.formatToStrings(
                            _mapCenter.get(),
                            CoordinateFormat.DMS);
                    _ddLon.setText(c[3]);
                    _ddLon.setSelection(_ddLon.getText().length());

                    _ddLat.setText(c[0]);
                    _ddLat.setSelection(_ddLat.getText().length());
                    showKeyboard();
                    break;
                }
                case DM: {
                    String[] c = CoordinateFormatUtilities.formatToStrings(
                            _mapCenter.get(),
                            CoordinateFormat.DMS);
                    _dmLonD.setText(c[3]);
                    _dmLatD.setText(c[0]);
                    _dmLatM.requestFocus();
                    showKeyboard();
                    break;
                }
                case DMS: {
                    String[] c = CoordinateFormatUtilities.formatToStrings(
                            _mapCenter.get(),
                            CoordinateFormat.DMS);
                    _dmsLonD.setText(c[3]);
                    _dmsLatD.setText(c[0]);
                    _dmsLatM.requestFocus();
                    showKeyboard();
                    break;
                }
                case UTM: {
                    String[] c = CoordinateFormatUtilities.formatToStrings(
                            _mapCenter.get(),
                            CoordinateFormat.UTM);
                    _utmZone.setText(c[0]);
                    _utmEast.requestFocus();
                    syncRawUTM(true);
                    showKeyboard();
                    break;
                }
                case ADDRESS: {
                    break;
                }
                default:
                    break;

            }
        }

        // Copy address to clipboard
        else if (v == _copyButton) {
            int current = _host.getCurrentTab();
            String text = null;
            switch (current) {
                case MGRS_TAB: {
                    text = String.format(
                            "%s%s%s%s",
                            _mgrsZone.getText().toString()
                                    .toUpperCase(LocaleUtil.getCurrent()),
                            _mgrsSquare.getText().toString()
                                    .toUpperCase(LocaleUtil.getCurrent()),
                            _mgrsEast.getText().toString(),
                            _mgrsNorth.getText().toString());
                }
                    break;
                case DD_TAB: {
                    text = String.format("%s%s, %s%S",
                            _ddLat.getText().toString(), "\u00B0",
                            _ddLon.getText().toString(), "\u00B0");
                }
                    break;
                case DM_TAB: {
                    text = String.format("%s%s %s%s, %s%s %s%s",
                            _dmLatD.getText().toString(), "\u00B0", _dmLatM
                                    .getText().toString(),
                            "'",
                            _dmLonD.getText().toString(), "\u00B0", _dmLonM
                                    .getText().toString(),
                            "'");
                }
                    break;
                case DMS_TAB: {
                    text = String.format("%s%s %s%s %s%s, %s%s %s%s %s%s",
                            _dmsLatD.getText().toString(), "\u00B0",
                            _dmsLatM.getText().toString(), "'",
                            _dmsLatS.getText().toString(), "''",
                            _dmsLonD.getText().toString(), "\u00B0",
                            _dmsLonM.getText().toString(), "'",
                            _dmsLonS.getText().toString(), "''");
                }
                    break;
                case UTM_TAB: {
                    text = String.format("%s%s%s",
                            _utmZone.getText().toString()
                                    .toUpperCase(LocaleUtil.getCurrent()),
                            _utmEast.getText().toString(),
                            _utmNorth.getText().toString());
                }
                    break;
                case ADDRESS_TAB: {
                    text = addressET.getText().toString();
                }
                    break;
            }

            if (!FileSystemUtils.isEmpty(text)) {
                String elev = _elevText.getText().toString().trim();
                if (!elev.isEmpty()) {
                    text += " " + elev + "ft MSL";
                }

                Log.d(TAG, "Copy location to clipboard: " + text);
                ATAKUtilities.copyClipboard("location", text, true);
            } else {
                Log.w(TAG, "Failed to copy location to clipboard");
            }
        }

        // Address tab
        else if (v == _host.getTabWidget().getChildAt(ADDRESS_TAB)) {
            hideKeyboard();

            checkADDRtab();
            if (isADDRtabDisabled) {
                Toast.makeText(
                        getContext(),
                        context.getString(R.string.goto_input_tip7),
                        Toast.LENGTH_SHORT).show();
                if (_currFormat == CoordinateFormat.ADDRESS)
                    _host.setCurrentTab(0);

            } else {
                _host.setCurrentTab(ADDRESS_TAB);
            }
        }
    }

    private void syncRawUTM(boolean force) {

        if (_utmRaw.getVisibility() != View.GONE && !force)
            return;
        try {
            watchUTM = false;
            _utmRaw.setText(
                    _utmZone.getText().toString()
                            .toUpperCase(LocaleUtil.getCurrent())
                            +
                            _utmEast.getText().toString() +
                            _utmNorth.getText().toString());
            _utmRaw.setSelection(_utmRaw.getText().length());
            watchUTM = true;
        } catch (Exception e) {
            Log.e(TAG, " caught exception ", e);
        }

    }

    private void syncRawMGRS(boolean force) {
        if (_mgrsRaw.getVisibility() != View.GONE && !force)
            return;
        try {
            watch = false;
            _mgrsRaw.setText(
                    _mgrsZone.getText().toString()
                            .toUpperCase(LocaleUtil.getCurrent())
                            +
                            _mgrsSquare.getText().toString()
                                    .toUpperCase(LocaleUtil.getCurrent())
                            +
                            _mgrsEast.getText().toString() +
                            _mgrsNorth.getText().toString());
            _mgrsRaw.setSelection(_mgrsRaw.getText().length());
            watch = true;
        } catch (Exception e) {
            Log.e(TAG, " caught exception ", e);
        }
    }

    private void enableFormattedMGRS(boolean enable) {
        _prefs.edit().putBoolean("coordview.formattedMGRS", enable).apply();
        if (enable) {
            _mgrsZone.setVisibility(View.VISIBLE);
            _mgrsSquare.setVisibility(View.VISIBLE);
            _mgrsEast.setVisibility(View.VISIBLE);
            _mgrsNorth.setVisibility(View.VISIBLE);
            _mgrsRaw.setVisibility(View.GONE);
        } else {
            syncRawMGRS(true);
            _mgrsZone.setVisibility(View.GONE);
            _mgrsSquare.setVisibility(View.GONE);
            _mgrsEast.setVisibility(View.GONE);
            _mgrsNorth.setVisibility(View.GONE);
            _mgrsRaw.setVisibility(View.VISIBLE);
        }
    }

    private void enableFormattedUTM(boolean enable) {
        _prefs.edit().putBoolean("coordview.formattedUTM", enable).apply();
        if (enable) {
            _utmZone.setVisibility(View.VISIBLE);
            _utmEast.setVisibility(View.VISIBLE);
            _utmNorth.setVisibility(View.VISIBLE);
            _utmRaw.setVisibility(View.GONE);
        } else {
            syncRawUTM(true);
            _utmZone.setVisibility(View.GONE);
            _utmEast.setVisibility(View.GONE);
            _utmNorth.setVisibility(View.GONE);
            _utmRaw.setVisibility(View.VISIBLE);
        }
    }

    protected void checkADDRtab() {

        isADDRtabDisabled = !isNetworkAvailable();
        //_host.getTabWidget().getChildTabViewAt(5).setEnabled(!isADDRtabDisabled);
        if (isADDRtabDisabled)
            ((TextView) _host.getTabWidget().getChildAt(5)
                    .findViewById(android.R.id.title))
                            .setTextColor(Color.DKGRAY);
        else
            ((TextView) _host.getTabWidget().getChildAt(5)
                    .findViewById(android.R.id.title))
                            .setTextColor(Color.WHITE);
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager
                .getActiveNetworkInfo();
        return !(activeNetworkInfo == null || !activeNetworkInfo.isConnected());
    }

    /*
     * Geocoding of the departure or destination address
     */
    public void handleSearchButton() {
        String locationAddress = addressET.getText().toString();
        if (locationAddress.equals("")) {
            if (_currPoint != null) {
                final ProgressDialog pd = ProgressDialog.show(context,
                        context.getString(R.string.goto_dialog1),
                        context.getString(R.string.goto_dialog2), true,
                        false);
                final ReverseGeocodingTask rgt = new ReverseGeocodingTask(
                        _currPoint.get(),
                        getContext());

                rgt.setOnResultListener(
                        new ReverseGeocodingTask.ResultListener() {
                            @Override
                            public void onResult() {
                                GeoPointMetaData point = GeoPointMetaData
                                        .wrap(rgt.getPoint());
                                String addr = rgt.getHumanAddress();
                                if (!FileSystemUtils.isEmpty(addr))
                                    _setAddressParams(addr, point);
                                else
                                    _setAddressParams("", null);
                                pd.dismiss();
                            }
                        });
                rgt.execute();
            } else {
                Toast.makeText(getContext(),
                        context.getString(R.string.goto_input_tip9),
                        Toast.LENGTH_SHORT).show();
            }
            return;
        }

        final ProgressDialog pd = ProgressDialog.show(context,
                context.getString(R.string.goto_dialog1),
                locationAddress, true,
                false);

        MapView view = MapView.getMapView();
        GeoBounds gb = view.getBounds();
        final GeocodingTask gt = new GeocodingTask(getContext(),
                gb.getSouth(), gb.getWest(), gb.getNorth(), gb.getEast());

        gt.setOnResultListener(new GeocodingTask.ResultListener() {
            @Override
            public void onResult() {
                final String address = gt.getHumanAddress();
                if (address != null && address.length() > 0) {
                    final GeoPoint gp = gt.getPoint();
                    if (gp != null) {
                        final GeoPointMetaData result = GeoPointMetaData
                                .wrap(gp);
                        _setPoint(result);
                        _setAddressParams(address, result);
                    }
                } else {
                    _setAddressParams("", null);
                }
                pd.dismiss();

            }
        });
        gt.execute(locationAddress);
    }

    private void _setAddressParams(final String address,
            final GeoPointMetaData point) {
        humanAddress = address;
        humanAddressPoint = point;
        post(new Runnable() {
            @Override
            public void run() {
                updateAddressCB();
            }
        });
    }

    private void hideKeyboard() {
        // Check if no view has focus:
        View view = this.getFocusedChild();
        if (view != null) {
            view.clearFocus();
            InputMethodManager inputManager = (InputMethodManager) getContext()
                    .getSystemService(Context.INPUT_METHOD_SERVICE);
            inputManager.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private void showKeyboard() {
        // Check if no view has focus:
        InputMethodManager inputManager = (InputMethodManager) getContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        inputManager.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, 0);
    }

    private void _updatePoint() {
        if (Thread.currentThread() != Looper.getMainLooper().getThread()) {
            post(new Runnable() {
                @Override
                public void run() {
                    _updatePoint();
                }
            });
            return;
        }
        if (_currPoint != null && _dmLonM != null) {
            _setMGRS(_currPoint);
            _setDD(_currPoint);
            _setDMS(_currPoint);
            _setDM(_currPoint);
            _setUTM(_currPoint);
            _setAddress(_currPoint);
            _result = Result.VALID_UNCHANGED;
            altSource = _currPoint.getAltitudeSource();
        }
    }

    public void clear() {
        _currPoint = null;
        _setMGRS(null);
        _setDD(null);
        _setDMS(null);
        _setDM(null);
        _setUTM(null);
        _setAddress(null);
        _elevText.setText("");
        addressET.setText("");
        _setAddressParams("", null);
        syncRawMGRS(true);
        syncRawUTM(true);
    }

    private void _setElev() {
        if (_currPoint != null && _currPoint.get().isAltitudeValid()) {
            double e = SpanUtilities.convert(
                    EGM96.getMSL(_currPoint.get()),
                    Span.METER, Span.FOOT);
            _currElevMSL = _formatElevation(e);
            _updateElev();
        }
    }

    private void _updateElev() {
        if (!_currElevMSL.equals("" + GeoPointMetaData.UNKNOWN)) {
            _elevText.setText(_currElevMSL);
        }
    }

    private static String _formatElevation(double e) {
        if (Math.abs(e) < 100) {
            return TWO_DEC_FORMAT.format(e);
        } else if (Math.abs(e) < 1000) {
            return ONE_DEC_FORMAT.format(e);
        } else {
            return NO_DEC_FORMAT.format(e);
        }
    }

    public boolean isAddressPointChecked() {
        return _dropAddressChk != null
                && _dropAddressChk.getVisibility() == CheckBox.VISIBLE
                && _dropAddressChk.isChecked();
    }

    /**
     * Update the visibility of the "Add Address" checkbox
     */
    private void updateAddressCB() {
        if (_dropAddressChk == null || addressET == null)
            return;

        // Don't show anything if we're not on the address tab
        if (_currFormat != CoordinateFormat.ADDRESS) {
            _dropAddressChk.setVisibility(View.GONE);
            _licenseTv.setVisibility(View.GONE);
            return;
        }

        // Address needs to be valid
        _licenseTv.setVisibility(View.VISIBLE);
        if (!FileSystemUtils.isEmpty(humanAddress)
                && humanAddressPoint != null) {
            addressET.setText(humanAddress);
            _dropAddressChk.setVisibility(View.VISIBLE);
        } else {
            addressET.setText("");
            _dropAddressChk.setVisibility(View.GONE);
        }
    }

    public String getHumanAddress() {
        return humanAddress;
    }

    /**
     * Returns the address lookup source, or null if no address lookup source 
     * is selected.
     */
    public String getAddressLookupSource() {
        GeocodeManager.Geocoder curr = GeocodeManager.getInstance(context)
                .getSelectedGeocoder();
        return curr.getTitle();
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
        else if (id == R.id.coordDialogDropNothing)
            return null;
        else
            return null;
    }

    public enum Result {
        VALID_CHANGED,
        VALID_UNCHANGED,
        INVALID
    }

}
