
package com.atakmap.app.preferences;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceManager;

import com.atakmap.android.gui.ColorPicker;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.preference.AtakPreferenceFragment;
import com.atakmap.android.preference.PreferenceSearchIndex;
import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;

/**
 * Preference fragment used to display the available preferences for
 * customizing th ATAk actionbar, the user can control the icon colors as well as
 * the action bar background see Bug: 6194
 */
public class CustomActionBarFragment extends AtakPreferenceFragment {

    private final String TAG = "CustomActionBarFragment";

    //custom string keys for storing the preference values
    public static final String ACTIONBAR_ICON_COLOR_KEY = "actionbar_icon_color_key";
    public static final String ACTIONBAR_BACKGROUND_COLOR_KEY = "actionbar_background_color_key";
    private Context context;

    public static java.util.List<PreferenceSearchIndex> index(Context context) {
        return index(context,
                CustomActionBarFragment.class,
                R.string.preferences_text131,
                R.drawable.customize_actionbar_pref_icon);
    }

    public CustomActionBarFragment() {
        super(R.xml.custom_actionbar_preferences, R.string.preferences_text131);
    }

    @Override
    public String getSubTitle() {
        return getSubTitle(getString(R.string.displayPreferences),
                getSummary());
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(getResourceID());

        context = getActivity();
        //the custom action bar icon colors defaults to white
        final Preference iconColors = findPreference(getActivity()
                .getResources().getString(
                        R.string.selected_actionbar_icon_color));
        iconColors
                .setOnPreferenceClickListener(
                        new Preference.OnPreferenceClickListener() {
                            @Override
                            public boolean onPreferenceClick(
                                    Preference preference) {
                                showColorPicker(ACTIONBAR_ICON_COLOR_KEY,
                                        "#FFFFFF");
                                return true;
                            }
                        });

        //the custom background color used for the ATAK background action bar defaults
        // to 70% transparent black

        final Preference backgroundColor = findPreference(getActivity()
                .getResources()
                .getString(R.string.selected_actionbar_background_color));
        backgroundColor
                .setOnPreferenceClickListener(
                        new Preference.OnPreferenceClickListener() {
                            @Override
                            public boolean onPreferenceClick(
                                    Preference preference) {
                                showColorPicker(ACTIONBAR_BACKGROUND_COLOR_KEY,
                                        "#000000");
                                return true;
                            }
                        });

        //defaults the values used for customizing ATAk actionbar and icons
        Preference defaultPref = findPreference(getActivity()
                .getResources().getString(R.string.default_actionbar_colors));
        defaultPref
                .setOnPreferenceClickListener(
                        new Preference.OnPreferenceClickListener() {
                            @Override
                            public boolean onPreferenceClick(
                                    Preference preference) {
                                AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(
                                        context);

                                // set title
                                alertDialogBuilder.setTitle(R.string.confirm);

                                // set dialog message
                                alertDialogBuilder
                                        .setMessage(
                                                R.string.preferences_text405)
                                        .setCancelable(false)
                                        .setPositiveButton(R.string.yes,
                                                new DialogInterface.OnClickListener() {
                                                    @Override
                                                    public void onClick(
                                                            DialogInterface dialog,
                                                            int id) {
                                                        resetActionBarPrefs(
                                                                ACTIONBAR_ICON_COLOR_KEY,
                                                                "#FFFFFF");
                                                        resetActionBarPrefs(
                                                                ACTIONBAR_BACKGROUND_COLOR_KEY,
                                                                "#000000");
                                                    }
                                                })
                                        .setNegativeButton(R.string.no, null);

                                // create alert dialog
                                AlertDialog alertDialog = alertDialogBuilder
                                        .create();

                                // show it
                                alertDialog.show();
                                return true;
                            }
                        });
    }

    /**
     * Displays a standard dialog extended class that informs user
     * that color selected is a darken tint and may not show well with certain action bar colors
     *
     * @param color in color to bind if user selected to use this color
     */
    private void showWarningDialog(final String color) {

        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(
                context);

        // set title
        alertDialogBuilder.setTitle(R.string.warning);

        // set dialog message
        alertDialogBuilder
                .setMessage(R.string.preferences_text406)
                .setCancelable(false)
                .setPositiveButton(R.string.preferences_text407,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int id) {
                                Log.d(TAG, "New Color " +
                                        "attached to "
                                        + ACTIONBAR_ICON_COLOR_KEY + " "
                                        + color);
                                PreferenceManager
                                        .getDefaultSharedPreferences(MapView
                                                .getMapView().getContext())
                                        .edit()
                                        .putString(ACTIONBAR_ICON_COLOR_KEY,
                                                color)
                                        .apply();
                            }
                        })
                .setNegativeButton(R.string.cancel, null);

        // create alert dialog
        AlertDialog alertDialog = alertDialogBuilder.create();

        // show it
        alertDialog.show();
    }

    /**
     * Tests the RGB color values of the int color
     * returns if the color is a light or dark color based on saturation
     *
     * @param color the color to test
     * @return true if the color is dark.
     */
    public boolean isColorDark(int color) {

        double darkness = 1 - (0.299 * Color.red(color) +
                0.587 * Color.green(color) + 0.114
                        * Color.blue(color))
                / 255;
        // It's a dark color
        return !(darkness < 0.5); // It's a light color
    }

    /**
     * Attaches and modifies the preference for the icon color / action bar background
     * used to reset back to default setting
     *
     * @param key          the key to reset.
     * @param defaultValue is the default value to reset things back to
     */
    private void resetActionBarPrefs(String key, String defaultValue) {
        PreferenceManager.getDefaultSharedPreferences(getActivity())
                .edit().putString(key, defaultValue).apply();
    }

    /**
     * shows a RGB color picker in a android alert dialog class
     * user can construct their own color by using the seek bars
     */
    private void showColorPicker(final String key, String defaultValue) {
        final ColorPicker picker = new ColorPicker(getActivity(),
                Color.parseColor(PreferenceManager.getDefaultSharedPreferences(
                        getActivity()).getString(
                                key, defaultValue)));
        AlertDialog.Builder b = new AlertDialog.Builder(getActivity())
                .setTitle(R.string.select_custom_color)
                .setMessage(R.string.preferences_text408)
                .setPositiveButton(R.string.ok,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                dialog.dismiss(); //dismiss dialog
                                attachNewColorValue(key,
                                        SelfMarkerCustomFragment
                                                .convertIntColorToString(picker
                                                        .getColor()));
                            }
                        })
                .setNegativeButton(R.string.cancel, null);
        b.setView(picker);
        final AlertDialog alert = b.create();
        alert.show();
    }

    /**
     * binds the new string color value to the @param key in
     * preferences
     *
     * @param key   the key to record the color value to
     * @param color the color value.
     */
    private void attachNewColorValue(String key, String color) {

        if (key.equals(ACTIONBAR_ICON_COLOR_KEY)) {
            if (isColorDark(Color.parseColor(color))) {
                showWarningDialog(color);
                return;
            }
        }

        Log.d(TAG, "New Color " +
                "attached to " + key + " " + color);
        SharedPreferences sp = PreferenceManager
                .getDefaultSharedPreferences(getActivity());
        sp.edit().putString(key, color).apply();
    }
}
