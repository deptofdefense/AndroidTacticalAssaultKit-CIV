
package com.atakmap.android.gridlines;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceManager;

import com.atakmap.android.gui.ColorPicker;
import com.atakmap.android.preference.AtakPreferenceFragment;
import com.atakmap.android.preference.PreferenceSearchIndex;
import com.atakmap.app.R;

import java.util.List;

import gov.tak.platform.graphics.Color;

public class GridLinesPreferenceFragment extends AtakPreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    public static List<PreferenceSearchIndex> index(Context context) {
        return index(context,
                GridLinesPreferenceFragment.class,
                R.string.gridLinePreferences,
                R.drawable.ic_overlay_gridlines);
    }

    public GridLinesPreferenceFragment() {
        super(R.xml.gridlines_preferences, R.string.gridLinePreferences);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (newValue instanceof String) {
            if (newValue.equals("Custom")) {
                showCustomColorPicker(preference);
            } else {
                try {
                    Color.parseColor(((String) newValue));

                    preference.getSharedPreferences().edit()
                            .putString(preference.getKey() + "_value",
                                    ((String) newValue))
                            .apply();
                } catch (Exception ex) {
                    return false;
                }
            }
        }

        return true;
    }

    private void showCustomColorPicker(Preference preference) {
        final String currentSelection = preference.getSharedPreferences()
                .getString(preference.getKey(), "#ffffff");
        final String currentValue = preference.getSharedPreferences()
                .getString(preference.getKey() + "_value", "#ffffff");

        final ColorPicker picker = new ColorPicker(getActivity(),
                android.graphics.Color.parseColor(
                        PreferenceManager.getDefaultSharedPreferences(
                                getActivity()).getString(
                                        preference.getKey() + "_value",
                                        "#FFFFFF")));
        AlertDialog.Builder b = new AlertDialog.Builder(getActivity())
                .setTitle(R.string.select_custom_color)
                .setMessage(R.string.preferences_text408)
                .setPositiveButton(R.string.ok,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                dialog.dismiss(); //dismiss dialog
                                final int color = picker.getColor();

                                preference.getSharedPreferences()
                                        .edit()
                                        .putString(
                                                preference.getKey() + "_value",
                                                String.format("#%06X",
                                                        0xFFFFFF & color))
                                        .apply();
                            }
                        })
                .setNegativeButton(R.string.cancel,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                dialog.dismiss();
                                preference.getSharedPreferences()
                                        .edit()
                                        .putString(preference.getKey(),
                                                currentSelection)
                                        .putString(
                                                preference.getKey() + "_value",
                                                currentValue)
                                        .apply();
                                ((ListPreference) preference)
                                        .setValue(currentSelection);
                            }
                        });
        b.setView(picker);
        final AlertDialog alert = b.create();
        alert.show();
    }

    @Override
    public String getSubTitle() {
        return getSubTitle(getString(R.string.displayPreferences),
                getSummary());
    }

    @Override
    public void onCreate(Bundle savedInstanceBundle) {
        super.onCreate(savedInstanceBundle);
        addPreferencesFromResource(getResourceID());

        final Preference gridColor = findPreference("pref_grid_color");

        gridColor.setOnPreferenceChangeListener(this);
    }

}
