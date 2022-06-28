
package com.atakmap.android.util;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.app.R;
import com.atakmap.coremap.conversions.ConversionFactors;

/**
 * Class to convert speed based on user preferences.
 *
 *
 */

public class SpeedFormatter implements
        SharedPreferences.OnSharedPreferenceChangeListener {

    public static final String TAG = "SpeedFormatter";

    private static SpeedFormatter _instance;
    private int currentSpeedFormat;
    private final SharedPreferences _pref;

    public static final int MPH = 0; // miles per hour
    public static final int KMPH = 1; // kilometers per hour
    public static final int KTS = 2; // knots
    public static final int MPS = 3; // meters per second

    private final String[] speedArray;

    private SpeedFormatter() {

        _pref = PreferenceManager.getDefaultSharedPreferences(MapView
                .getMapView().getContext());
        _pref.registerOnSharedPreferenceChangeListener(this);

        String type = _pref.getString("speed_unit_pref", Integer.toString(MPH));

        try {
            currentSpeedFormat = Integer.parseInt(type);
        } catch (Exception e) {
            currentSpeedFormat = 0;
        }

        speedArray = MapView.getMapView().getContext().getResources()
                .getStringArray(R.array.speed_units_display);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sp, String key) {

        if (key == null)
            return;

        if (key.equals("speed_unit_pref")) {
            String type = sp.getString("speed_unit_pref", "0");
            try {
                currentSpeedFormat = Integer.parseInt(type);
            } catch (Exception e) {
                currentSpeedFormat = 0;
            }
        }
    }

    /**
     * Given a map item, determine the speed in the preferred speed designated by the speed unit preference.
     */
    public String getSpeedFormatted(MapItem item) {
        if (item == null) {
            return getSpeedFormatted(Double.NaN);
        } else if (item.getType().equals("self")) {
            return getSpeedFormatted(item.getMetaDouble("Speed", Double.NaN));
        } else if (item instanceof Marker) {
            double speed = ((Marker) item).getTrackSpeed();
            if (!Double.isNaN(speed))
                return getSpeedFormatted(speed);
            else {
                speed = item.getMetaDouble("est.speed", Double.NaN);
                long millis = item.getMetaLong("est.time", 0);

                String sText = getSpeedFormatted(speed);
                if (millis > 0)
                    sText = sText + " EST ";
                return sText;
            }
        } else {
            return getSpeedFormatted(item.getMetaDouble("Speed", Double.NaN));
        }
    }

    /**
     * Given a speed in m/s, converts to String based on appropriate speed units as selected by
     * user in preferences.
     *
     * @param speed in m/s
     * @return String formatted with converted speed and label.
     */
    public String getSpeedFormatted(final double speed) {
        return getSpeedFormatted(speed, currentSpeedFormat);
    }

    /**
     * Given a speed in m/s, converts to String based on appropriate speed units as passed in.
     *
     * @param speed in m/s
     * @param speed_unit 0=MPH, 1=KMPH, 2=KTS, 3=MPS
     * @return String formatted with converted speed and label.
     */
    public String getSpeedFormatted(final double speed, final int speed_unit) {
        double factor;
        String type;
        switch (speed_unit) {
            case MPH: //mph
                factor = ConversionFactors.METERS_PER_S_TO_MILES_PER_H;
                type = speedArray[MPH];
                break;
            case KMPH: //kmph
                factor = ConversionFactors.METERS_PER_S_TO_KILOMETERS_PER_H;
                type = speedArray[KMPH];
                break;
            case KTS: //Kts
                factor = ConversionFactors.METERS_PER_S_TO_KNOTS;
                type = speedArray[KTS];
                break;
            case MPS:
                factor = 1;
                type = speedArray[MPS];
                break;
            default:
                factor = ConversionFactors.METERS_PER_S_TO_MILES_PER_H;
                type = speedArray[0];
                break;
        }
        if (!Double.isNaN(speed)) {
            return (int) Math.round(factor * speed) + " " + type;
        }

        return "--- " + type;

    }

    public static SpeedFormatter getInstance() {
        if (_instance == null) {
            _instance = new SpeedFormatter();
        }
        return _instance;
    }

}
