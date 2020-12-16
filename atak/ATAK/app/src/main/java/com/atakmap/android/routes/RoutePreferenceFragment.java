
package com.atakmap.android.routes;

import android.os.Bundle;

import com.atakmap.android.gui.PanEditTextPreference;
import com.atakmap.android.preference.AtakPreferenceFragment;
import com.atakmap.app.R;

import android.app.AlertDialog;
import android.graphics.Color;
import com.atakmap.android.gui.ColorPalette;
import com.atakmap.android.gui.ColorPalette.OnColorSelectedListener;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.preference.Preference;
import com.atakmap.coremap.log.Log;

public class RoutePreferenceFragment extends AtakPreferenceFragment {

    public static final String TAG = "RoutePreferenceFragment";

    public RoutePreferenceFragment() {
        super(R.xml.route_preferences, R.string.routePreferences);
    }

    @Override
    public String getSubTitle() {
        return getSubTitle(getString(R.string.toolPreferences),
                getSummary());
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        super.addPreferencesFromResource(getResourceID());

        final Preference defaultRouteColor = findPreference(
                "defaultRouteColor");
        defaultRouteColor
                .setOnPreferenceClickListener(
                        new Preference.OnPreferenceClickListener() {
                            @Override
                            public boolean onPreferenceClick(
                                    Preference preference) {

                                final SharedPreferences _prefs = PreferenceManager
                                        .getDefaultSharedPreferences(
                                                getActivity());

                                AlertDialog.Builder b = new AlertDialog.Builder(
                                        getActivity());
                                b.setTitle(defaultRouteColor.getTitle());
                                int color = Color.WHITE;
                                try {
                                    color = Integer.parseInt(_prefs.getString(
                                            "defaultRouteColor",
                                            Integer.toString(Color.WHITE)));
                                } catch (Exception e) {
                                    Log.d(TAG,
                                            "error occurred getting preference");
                                }
                                ColorPalette palette = new ColorPalette(
                                        getActivity(), color);
                                b.setView(palette);
                                final AlertDialog alert = b.create();
                                OnColorSelectedListener l = new OnColorSelectedListener() {
                                    @Override
                                    public void onColorSelected(int color,
                                            String label) {
                                        _prefs.edit()
                                                .putString("defaultRouteColor",
                                                        Integer.toString(color))
                                                .apply();
                                        alert.dismiss();
                                    }
                                };
                                palette.setOnColorSelectedListener(l);
                                alert.show();
                                return true;
                            }
                        });

        ((PanEditTextPreference) findPreference("waypointBubble.Walking"))
                .checkValidInteger();
        ((PanEditTextPreference) findPreference("waypointBubble.Driving"))
                .checkValidInteger();
        ((PanEditTextPreference) findPreference("waypointBubble.Flying"))
                .checkValidInteger();
        ((PanEditTextPreference) findPreference("waypointBubble.Swimming"))
                .checkValidInteger();
        ((PanEditTextPreference) findPreference("waypointBubble.Watercraft"))
                .checkValidInteger();
    }

}
