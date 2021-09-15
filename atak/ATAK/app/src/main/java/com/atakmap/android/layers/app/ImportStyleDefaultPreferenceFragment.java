
package com.atakmap.android.layers.app;

import android.content.Context;
import android.os.Bundle;

import com.atakmap.android.preference.AtakPreferenceFragment;
import com.atakmap.android.preference.PreferenceSearchIndex;
import com.atakmap.app.R;

import android.app.AlertDialog;
import android.graphics.Color;
import com.atakmap.android.gui.ColorPalette;
import com.atakmap.android.gui.ColorPalette.OnColorSelectedListener;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.preference.Preference;

import com.atakmap.coremap.log.Log;

/**
 * 
 */
public class ImportStyleDefaultPreferenceFragment extends
        AtakPreferenceFragment {

    public static final String TAG = "ImportStyleDefaultPreferenceFragment";

    public static java.util.List<PreferenceSearchIndex> index(Context context) {
        return index(context,
                ImportStyleDefaultPreferenceFragment.class,
                R.string.importStylePreferences,
                R.drawable.ic_menu_import_file);
    }

    public ImportStyleDefaultPreferenceFragment() {
        super(R.xml.importstyle_preferences, R.string.importStylePreferences);
    }

    @Override
    public void onCreate(Bundle savedInstanceBundle) {
        super.onCreate(savedInstanceBundle);

        addPreferencesFromResource(getResourceID());

        final Preference pref_overlay_style_outline_color = findPreference(
                "pref_overlay_style_outline_color");
        pref_overlay_style_outline_color
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
                                b.setTitle(pref_overlay_style_outline_color
                                        .getTitle());

                                int color = Color.WHITE;
                                try {
                                    color = Integer.parseInt(_prefs.getString(
                                            "pref_overlay_style_outline_color",
                                            Integer.toString(Color.WHITE)));
                                } catch (Exception e) {
                                    Log.d(TAG, "invalid preference type");
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
                                                "pref_overlay_style_outline_color",
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

    }

    @Override
    public String getSubTitle() {
        return getSubTitle(getString(R.string.displayPreferences),
                getSummary());
    }
}
