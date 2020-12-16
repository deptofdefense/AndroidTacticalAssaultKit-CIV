
package com.atakmap.android.track.ui;

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

public class TrackHistoryPrefsFragment extends AtakPreferenceFragment {

    public static final String TAG = "TrackHistoryPrefsFragment";

    public TrackHistoryPrefsFragment() {
        super(R.xml.bread_preferences, R.string.trackHistoryPreferences);
    }

    @Override
    public String getSubTitle() {
        return getSubTitle(getString(R.string.toolPreferences),
                getSummary());
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.addPreferencesFromResource(getResourceID());

        final Preference track_history_default_color = findPreference(
                "track_history_default_color");
        track_history_default_color
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
                                b.setTitle(
                                        track_history_default_color.getTitle());
                                int color = Color.WHITE;
                                try {
                                    color = Integer.parseInt(_prefs.getString(
                                            "track_history_default_color",
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
                                        _prefs.edit().putString(
                                                "track_history_default_color",
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

        ((PanEditTextPreference) findPreference(
                "bread_track_timegap_threshold"))
                        .checkValidInteger();
        ((PanEditTextPreference) findPreference("bread_dist_threshold"))
                .checkValidInteger();
    }

}
