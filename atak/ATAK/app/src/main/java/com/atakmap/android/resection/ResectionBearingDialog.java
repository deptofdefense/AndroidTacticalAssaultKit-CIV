
package com.atakmap.android.resection;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.android.util.AfterTextChangedWatcher;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.NorthReference;

import java.text.DecimalFormat;

/**
 * Fancy dialog for setting a bearing value
 * All bearing inputs and outputs are in degrees true north
 * This is tailored for the resection tool, but could potentially be used elsewhere
 */
public class ResectionBearingDialog implements View.OnClickListener,
        View.OnTouchListener {

    private static final String TAG = "ResectionBearingDialog";
    private static final DecimalFormat _df = LocaleUtil.getDecimalFormat("#.#");

    private final MapView _mapView;
    private final Context _context;
    private final SharedPreferences _prefs;

    private String _title;
    private GeoPoint _targetPoint;
    private double _oldBearing;
    private View _compassView, _bearingLine, _bearingCompass;
    private EditText _bearingTxt;
    private ImageButton _compassBtn, _addBtn, _subBtn;
    private ToggleButton _magToggle;
    private AlertDialog _dialog;
    private Callback _callback;
    private Object _tag;

    public ResectionBearingDialog(MapView mapView) {
        _mapView = mapView;
        _context = mapView.getContext();
        _prefs = PreferenceManager.getDefaultSharedPreferences(_context);
    }

    /**
     * Set the target point used for obtaining default bearing
     * If not set then the default bearing is the device's current heading
     * @param point Target point
     */
    public void setTargetPoint(GeoPoint point) {
        _targetPoint = point;
    }

    public GeoPoint getTargetPoint() {
        return _targetPoint;
    }

    /**
     * Set the title of the dialog
     * @param title Title to be displayed at the top of the dialog
     */
    public void setTitle(String title) {
        if (_dialog != null && _dialog.isShowing())
            _dialog.setTitle(title);
        _title = title;
    }

    /**
     * Get the current set bearing in true degrees
     * @return Bearing in true degrees
     */
    public double getTrueBearing() {
        GeoPoint point = getSelfPoint();
        double bearing = getBearing();
        if (point == null || !point.isValid() || Double.isNaN(bearing))
            return Double.NaN;
        if (_magToggle.isChecked())
            return ATAKUtilities.convertFromMagneticToTrue(point, bearing);
        return bearing;
    }

    /**
     * Set a tag associated with this dialog (for use in the callback)
     * @param tag Tag object
     */
    public void setTag(Object tag) {
        _tag = tag;
    }

    /**
     * Get dialog tag
     * @return Tag object
     */
    public Object getTag() {
        return _tag;
    }

    public interface Callback {
        void onBearingEntered(ResectionBearingDialog dialog,
                double oldBearingTrue, double newBearingTrue);
    }

    /**
     * Show the dialog
     * @param bearingTrue The initial bearing in degrees true north
     * @param showMag True to display the bearing in magnetic north
     * @param cb Callback to fire once user selects "OK"
     */
    public void show(double bearingTrue, boolean showMag, Callback cb) {
        _oldBearing = bearingTrue;
        _callback = cb;

        View v = LayoutInflater.from(_context).inflate(
                R.layout.resection_bearing_entry, _mapView, false);

        _compassView = v.findViewById(R.id.compassView);
        _bearingLine = v.findViewById(R.id.bearingLine);
        _bearingCompass = v.findViewById(R.id.bearingCompass);
        _bearingCompass.setOnTouchListener(this);

        _bearingTxt = v.findViewById(R.id.bearingText);
        _bearingTxt.addTextChangedListener(new AfterTextChangedWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (_dialog == null)
                    return;
                GeoPoint point = getSelfPoint();
                double bearing = getBearing();
                if (Double.isNaN(bearing))
                    bearing = 0;
                float declination = _magToggle.isChecked()
                        ? (float) ATAKUtilities
                                .getCurrentMagneticVariation(point)
                        : 0f;
                if (Double.isNaN(declination))
                    declination = 0;
                _compassView.setRotation(declination);
                _bearingLine.setRotation((float) bearing + declination);
            }
        });

        _compassBtn = v.findViewById(R.id.compassButton);
        _compassBtn.setOnClickListener(this);

        _magToggle = v.findViewById(R.id.magToggle);
        _magToggle.setChecked(showMag);
        _magToggle.setOnClickListener(this);

        _addBtn = v.findViewById(R.id.add);
        _addBtn.setOnClickListener(this);

        _subBtn = v.findViewById(R.id.subtract);
        _subBtn.setOnClickListener(this);

        AlertDialog.Builder b = new AlertDialog.Builder(_context);
        if (!FileSystemUtils.isEmpty(_title))
            b.setTitle(_title);
        b.setView(v);
        b.setPositiveButton(R.string.ok, null);
        b.setNegativeButton(R.string.cancel, null);
        _dialog = b.show();
        _dialog.getButton(DialogInterface.BUTTON_POSITIVE)
                .setOnClickListener(this);

        GeoPoint point = getSelfPoint();
        _bearingTxt.setText(_df.format(showMag ? ATAKUtilities
                .convertFromTrueToMagnetic(point, bearingTrue)
                : bearingTrue));
    }

    public void show(double bearingTrue, Callback cb) {
        NorthReference ref = NorthReference.MAGNETIC;
        try {
            int north = Integer.parseInt(_prefs.getString(
                    "rab_north_ref_pref", String.valueOf(ref.getValue())));
            ref = NorthReference.findFromValue(north);
        } catch (Exception ignored) {
        }
        show(bearingTrue, ref == NorthReference.MAGNETIC, cb);
    }

    public void show(boolean showMag, Callback cb) {
        show(getDefaultBearing(), showMag, cb);
    }

    public void show(Callback cb) {
        show(getDefaultBearing(), cb);
    }

    /**
     * Get the default bearing displayed to the user
     *
     * @return Default bearing in true degrees
     */
    protected double getDefaultBearing() {
        return _mapView.getMapData().getDouble("deviceAzimuth", 0.0);
    }

    @Override
    public void onClick(View v) {
        if (_dialog == null)
            return;

        // Set bearing to the default value
        if (v == _compassBtn) {
            double azi = getDefaultBearing();
            if (_magToggle.isChecked())
                azi = ATAKUtilities.convertFromTrueToMagnetic(
                        getSelfPoint(), azi);
            _bearingTxt.setText(_df.format(azi));
        }

        // Toggle magnetic/true north entry
        else if (v == _magToggle) {
            GeoPoint point = getSelfPoint();
            double b = getBearing();
            if (_magToggle.isChecked()) {
                // True -> Magnetic
                b = ATAKUtilities.convertFromTrueToMagnetic(point, b);
            } else {
                // Magnetic -> True
                b = ATAKUtilities.convertFromMagneticToTrue(point, b);
            }
            _bearingTxt.setText(_df.format(b));
        }

        // Add or subtract 5 degrees
        else if (v == _addBtn || v == _subBtn) {
            double b = getBearing();
            if (Double.isNaN(b))
                b = 0;
            b += (v == _addBtn ? 5 : -5);
            if (b >= 360)
                b -= 360;
            else if (b < 0)
                b += 360;
            _bearingTxt.setText(_df.format(b));
        }

        // Dialog "OK" button
        else {
            double newBearing = getTrueBearing();
            if (Double.isNaN(newBearing)) {
                Toast.makeText(_context, R.string.invalid_bearing2,
                        Toast.LENGTH_LONG).show();
                return;
            }
            if (_callback != null)
                _callback.onBearingEntered(this, _oldBearing, newBearing);
            _dialog.dismiss();
        }
    }

    private GeoPoint getSelfPoint() {
        GeoPoint point = _mapView.getCenterPoint().get();
        Marker self = _mapView.getSelfMarker();
        if (self != null && self.getGroup() != null)
            point = self.getPoint();
        return point;
    }

    private double getBearing() {
        if (_bearingTxt == null)
            return Double.NaN;
        try {
            return Double.parseDouble(_bearingTxt.getText().toString());
        } catch (Exception e) {
            return Double.NaN;
        }
    }

    /**
     * Used for dragging the bearing line
     */
    @Override
    public boolean onTouch(View v, MotionEvent event) {

        //get the angle from the middle
        double startX = v.getWidth() / 2f;
        double startY = v.getHeight() / 2f;
        double radius = Math.min(v.getWidth(), v.getHeight()) / 2d;

        double stopX = event.getX();//- this.getLeft();
        double stopY = event.getY();//- this.getLeft();

        // Ignore touch events outside circle
        if (Math.hypot(stopX - startX, stopY - startY) > radius)
            return false;

        GeoPoint point = getSelfPoint();
        double b = Math.toDegrees(Math.atan2(stopY - startY, stopX - startX))
                + 90;
        if (_magToggle.isChecked())
            b = ATAKUtilities.convertFromTrueToMagnetic(point, b);
        if (b < 0)
            b += 360;
        _bearingTxt.setText(_df.format(b));
        return true;
    }
}
