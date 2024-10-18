
package com.atakmap.app.preferences;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LightingColorFilter;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.view.View;

import com.atakmap.android.gui.ColorPicker;
import com.atakmap.android.icons.Icon2525cIconAdapter;
import com.atakmap.android.preference.AtakPreferenceFragment;
import com.atakmap.android.preference.PreferenceSearchIndex;
import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;

/**
 * Handles the preference fragment for the GPS icon customization
 * User can select from 3 states, default coloring, using the current team color, or setting a custom
 * color using a RGB color picker. when the team/custom color is selected a file is created
 * and used to pinpoint the file dimens are 32x32 same as default icon
 * default casing is always the default icon,
 */
public class SelfMarkerCustomFragment extends AtakPreferenceFragment {

    private static final String TAG = "SelfMarkerCustomFragment"; //debug tag

    private CheckBoxPreference defaultColor, teamColor, customColor;
    private int currentSetting;
    private Drawable self_icon; //used to scale the icons for the custom/team color pref
    private CreateBitmapIntoDrawable createBitmapIntoDrawable;
    private SharedPreferences sp;

    public static java.util.List<PreferenceSearchIndex> index(Context context) {
        return index(context,
                SelfMarkerCustomFragment.class,
                R.string.gpsPreferences,
                R.drawable.ic_menu_compass);
    }

    public SelfMarkerCustomFragment() {
        super(R.xml.self_marker_custom_pref_fragment, R.string.gpsPreferences);

    }

    @Override
    public String getSubTitle() {
        return getSubTitle(getString(R.string.displayPreferences),
                getSummary());
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        //background threads to create bitmaps for team color icon, custom color icon
        createBitmapIntoDrawable = new CreateBitmapIntoDrawable(getActivity(),
                customColor, self_icon);
        createBitmapIntoDrawable.execute();

        createBitmapIntoDrawable = new CreateBitmapIntoDrawable(getActivity(),
                teamColor, self_icon);
        createBitmapIntoDrawable.execute();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        self_icon = getActivity().getResources()
                .getDrawable(R.drawable.ic_self); //wrap drawable to get dimens from

        addPreferencesFromResource(getResourceID()); //attach pref XML file to this Preference

        //get pref instance
        getPreferenceManager();
        sp = PreferenceManager.getDefaultSharedPreferences(getActivity());

        //assign xml preferences to java variables
        defaultColor = (CheckBoxPreference) this
                .findPreference("default_gps_icon");
        teamColor = (CheckBoxPreference) this
                .findPreference("team_color_gps_icon");
        customColor = (CheckBoxPreference) this
                .findPreference("custom_color_gps_icon_pref");

        Preference changeColorPreference = this
                .findPreference("change_custom_gps_color");

        Preference changeOutlineColorPreference = this
                .findPreference("change_custom_gps_outline_color");

        //get the default or current states for preferences used
        currentSetting = sp.getInt("custom_gps_icon_setting", 0);

        //set listeners
        teamColor.setOnPreferenceClickListener(clickListener);
        defaultColor.setOnPreferenceClickListener(clickListener);
        customColor.setOnPreferenceClickListener(clickListener);
        changeColorPreference.setOnPreferenceClickListener(clickListener);
        changeOutlineColorPreference
                .setOnPreferenceClickListener(clickListener);

        setDefaultedSavedStates(); //set default states
    }

    /**
     * get the default sate currently 0 - default icon
     * 1- team color icon 2- custom color
     */
    private void setDefaultedSavedStates() {
        //set checked status based on current keys
        switch (currentSetting) {
            case 0:
                defaultColor.setChecked(true);
                break;
            case 1:
                teamColor.setChecked(true);
                break;
            case 2:
                customColor.setChecked(true);
                break;
            default:
                break;
        }
    }

    /**
     * because we are not using the default list pref we have to handle the clickable
     * states ourselves so if user selects one deactivate the other 2
     *
     * @param cbp the checkbox receiving the checked state
     */
    private void resetAllChecks(CheckBoxPreference cbp) {
        defaultColor.setChecked(false);
        teamColor.setChecked(false);
        customColor.setChecked(false);
        cbp.setChecked(true);
    }

    /**
     * asynctasks run in the class/instance they were created and called
     * make sure its not still running when this frag is gone
     */
    @Override
    public void onDestroy() {
        super.onDestroy();

        //bg thread cleanups just in case.....
        if (createBitmapIntoDrawable != null
                && createBitmapIntoDrawable
                        .getStatus() == AsyncTask.Status.RUNNING) {
            createBitmapIntoDrawable.cancel(true);
        }
    }

    /**
     * if the pref state has changed ie. user selected default (0)and the current state was
     * custom color(2) apply the new int
     *
     * @param i the int state to save in prefs
     */
    private void applyNewPreferenceSetting(int i) {
        sp.edit().putInt("custom_gps_icon_setting", i).apply();
    }

    /**
     * handles the click events when a user clicks a checkbox preference
     */
    private final Preference.OnPreferenceClickListener clickListener = new Preference.OnPreferenceClickListener() {
        @Override
        public boolean onPreferenceClick(Preference preference) {

            switch (preference.getKey()) {
                case "default_gps_icon":
                    resetAllChecks(defaultColor);
                    applyNewPreferenceSetting(0);
                    break;
                case "team_color_gps_icon":
                    resetAllChecks(teamColor);
                    applyNewPreferenceSetting(1);
                    break;
                case "custom_color_gps_icon_pref":
                    resetAllChecks(customColor);
                    applyNewPreferenceSetting(2);
                    break;
                case "change_custom_gps_color":
                    //show color pallet dialog
                    showColorPicker("custom_color_selected");
                    break;
                case "change_custom_gps_outline_color":
                    //show color pallet dialog
                    showColorPicker("custom_outline_color_selected");
                    break;
            }
            return true;
        }
    };

    /**
     * shows a RGB color picker in a android alert dialog class
     * user can construct their own color by using the seek bars
     */
    private void showColorPicker(final String prefKey) {
        final ColorPicker picker = new ColorPicker(getActivity(),
                Color.parseColor(PreferenceManager.getDefaultSharedPreferences(
                        getActivity()).getString(
                                prefKey, "#FFFFFF")));
        AlertDialog.Builder b = new AlertDialog.Builder(getActivity())
                .setTitle(R.string.select_custom_color)
                .setMessage(R.string.preferences_text408)
                .setPositiveButton(R.string.ok,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                dialog.dismiss(); //dismiss dialog
                                setUpCustomColor(picker, prefKey);
                            }
                        })
                .setNegativeButton(R.string.cancel, null);
        b.setView(picker);
        final AlertDialog alert = b.create();
        alert.show();
    }

    /**
     * @param picker the dialog colorpicker class where the color was constructed and saved
     */
    private void setUpCustomColor(ColorPicker picker, final String prefKey) {
        //set new color for "custom_color_selected" pref
        PreferenceManager
                .getDefaultSharedPreferences(getActivity())
                .edit()
                .putString(prefKey,
                        convertIntColorToString(picker.getColor()))
                .apply();

        refreshIcon(); //wrap new pref icon and set

        //if the current setting was 2 , apply new color created and saved to gps icon
        if (PreferenceManager.getDefaultSharedPreferences(getActivity())
                .getInt("custom_gps_icon_setting", 0) == 2) {
            //color changed make sure to refresh the icon on the mapview.
            PreferenceManager.getDefaultSharedPreferences(getActivity()).edit()
                    .putInt("custom_gps_icon_setting", 0).apply();
            applyNewPreferenceSetting(2);
        }
    }

    /**
     * refreshes custom color pref icon when new color is
     * selected
     */
    private void refreshIcon() {
        createBitmapIntoDrawable = new CreateBitmapIntoDrawable(getActivity(),
                customColor, self_icon);
        createBitmapIntoDrawable.execute();
    }

    /**
     * @param color the int color reference
     * @return the hex String version of the int color
     */
    public static String convertIntColorToString(int color) {
        return String.format("#%06X", 0xFFFFFF & color);
    }

    /**
     * ASyncTask runs asynchronos with the UI thread
     * creates a drawable with a specified color used as the preference icon
     * the icon is scaled based on the devices resol/dimens to properly be seen
     * the constructor takes a checkbox pref object used to set the final bitmap created as its icon
     * PosExecute thread(UI thread) sets this bitmap to the preference icon
     */
    static class CreateBitmapIntoDrawable
            extends AsyncTask<Void, Void, Drawable> {

        private final CheckBoxPreference cbp;
        private final Activity activity;
        private final Drawable self_icon;

        CreateBitmapIntoDrawable(Activity activity,
                CheckBoxPreference checkBoxPreference, Drawable self_icon) {
            this.cbp = checkBoxPreference;
            this.activity = activity;
            this.self_icon = self_icon;
        }

        @Override
        protected void onPostExecute(Drawable drawable) {
            super.onPostExecute(drawable);
            if (drawable != null) {
                cbp.setIcon(drawable);
            }
        }

        /**
         * @param params nothing
         * @return a bitmap used to indicate to user what current color for each of the 3 gps icon colors
         * bitmaps are created from specific colors , indicative of the preference we are creating for:
         * bitmap is created / scaled and returned into onPostExecute to be attached to pref key
         */
        @Override
        protected Drawable doInBackground(Void... params) {
            Drawable drawable = null;
            Bitmap icon;
            if (activity == null)
                return null;

            final Resources resources = activity.getResources();
            if (resources == null)
                return null;

            if (cbp.getKey().equals("team_color_gps_icon")) {
                icon = BitmapFactory.decodeResource(resources,
                        R.drawable.self_color_team, getBitmapOptions());
                //team color icon creation
                if (icon != null) {
                    icon = changeBitmapColor(
                            icon,
                            Icon2525cIconAdapter.teamToColor(PreferenceManager
                                    .getDefaultSharedPreferences(
                                            activity)
                                    .getString("locationTeam", "Cyan")));
                    //scale the new bitmap to equal dimens of the default drawable
                    Bitmap scaled = Bitmap.createScaledBitmap(icon,
                            self_icon.getMinimumHeight() - 5,
                            self_icon.getMinimumWidth() - 5, true);
                    drawable = new BitmapDrawable(activity.getResources(),
                            scaled); //convert to drawable
                }
            } else {
                icon = BitmapFactory.decodeResource(resources,
                        R.drawable.self_color_custom, getBitmapOptions());
                if (icon != null) {
                    //custom color pref icon creation
                    Bitmap recolorIcon = changeBitmapColor(icon,
                            Color.parseColor(PreferenceManager
                                    .getDefaultSharedPreferences(activity)
                                    .getString("custom_color_selected",
                                            "#FFFFFF")));
                    if (recolorIcon != null) {
                        icon = recolorIcon;
                    } else {
                        Log.d(TAG, "error recoloring icon");
                    }

                    Bitmap scaled = Bitmap.createScaledBitmap(icon,
                            self_icon.getMinimumHeight() - 5,
                            self_icon.getMinimumWidth() - 5, true);
                    drawable = new BitmapDrawable(resources, scaled);
                }
            }
            return drawable;
        }

        /**
         * Pulls a bitmap in and recolors based on @color param
         *
         * @param sourceBitmap the original bitmap
         * @param color        the int color to change
         * @return Bitmap with new color
         */
        private Bitmap changeBitmapColor(Bitmap sourceBitmap, int color) {

            Bitmap resultBitmap = Bitmap.createBitmap(sourceBitmap, 0, 0,
                    sourceBitmap.getWidth() - 1, sourceBitmap.getHeight() - 1);
            Paint p = new Paint();
            ColorFilter filter = new LightingColorFilter(color, 1);
            p.setColorFilter(filter);
            Canvas canvas = new Canvas(resultBitmap);
            canvas.drawBitmap(resultBitmap, 0, 0, p);

            return resultBitmap;
        }

        /**
         * @return Bitmap options used on resized image
         */
        private BitmapFactory.Options getBitmapOptions() {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inScaled = false;
            return options;
        }
    }
}
