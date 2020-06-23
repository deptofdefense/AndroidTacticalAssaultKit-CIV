
package com.atakmap.android.maps.conversion;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.log.Log;

public class UnitChangeReceiver extends BroadcastReceiver {

    public static final String TAG = "UnitChangeReceiver";

    public static final String DIST_ADJUST = "com.atakmap.android.maps.DIST_UNIT_ADJUST";
    public static final String ANGL_ADJUST = "com.atakmap.android.maps.ANGL_UNIT_ADJUST";

    private final MapView _mapView;
    private final SharedPreferences _prefs;

    public UnitChangeReceiver(MapView mapView) {
        _mapView = mapView;
        _prefs = PreferenceManager.getDefaultSharedPreferences(_mapView
                .getContext());
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.e(TAG, "Action: " + action);
        if (action.equals(ANGL_ADJUST)) {
            int index = intent.getIntExtra("index", -1);
            if (index != -1) {
                _prefs.edit()
                        .putString("rab_brg_units_pref", String.valueOf(index))
                        .apply();
            } else {
                int currentIndex = Integer.parseInt(_prefs.getString(
                        "rab_brg_units_pref", "0"));
                currentIndex++;
                _prefs.edit()
                        .putString("rab_brg_units_pref",
                                String.valueOf(currentIndex % 2))
                        .apply();
            }
        } else if (action.equals(DIST_ADJUST)) {
            int index = intent.getIntExtra("index", -1);
            if (index != -1) {
                _prefs.edit()
                        .putString("rab_rng_units_pref", String.valueOf(index))
                        .apply();
            } else {
                int currentIndex = Integer.parseInt(_prefs.getString(
                        "rab_rng_units_pref", "0"));
                currentIndex++;
                _prefs.edit()
                        .putString("rab_rng_units_pref",
                                String.valueOf(currentIndex % 3))
                        .apply();
            }
        }

    }

}
