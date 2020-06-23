
package com.atakmap.app.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.atakmap.android.preference.PreferenceSearchIndex;
import com.atakmap.android.tools.ActionBarReceiver;
import com.atakmap.android.preference.AtakPreferenceFragment;
import com.atakmap.app.R;

/**
 *
 * The fragmennt used to display the inner customactionbarpreference and and imageview to
 * preview the newly changed icon and action bar background
 */

public class ActionBarPreferences extends AtakPreferenceFragment implements
        SharedPreferences.OnSharedPreferenceChangeListener {

    private ImageView placeholderImage;
    private SharedPreferences prefs;
    private CustomActionBarFragment customActionBarFragment;

    public static java.util.List<PreferenceSearchIndex> index(Context context) {
        return index(context,
                ActionBarPreferences.class,
                null,
                R.string.preferences_text72,
                R.string.displayPreferences,
                R.drawable.customize_actionbar_pref_icon,
                "action", "tool", "toolbar");
    }

    public ActionBarPreferences() {
        super(-1, R.string.preferences_text72);

    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Context context = getActivity();
        prefs = PreferenceManager.getDefaultSharedPreferences(context
                .getApplicationContext());
        prefs.registerOnSharedPreferenceChangeListener(this);

        customActionBarFragment = new CustomActionBarFragment();
    }

    @Override
    public void onDestroy() {
        prefs.unregisterOnSharedPreferenceChangeListener(this);
        super.onDestroy();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        return inflater.inflate(R.layout.custom_action_bar_pref_layout, null,
                true);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        /*
        originally had a nested fragment but as android guidelines suggest
        "Note: You cannot inflate a layout into a fragment when that layout includes a <fragment>.
        Nested fragments are only supported when added to a fragment dynamically." per Android fragment guidelines
        so adding programmatically instead
         */

        //attach the inflated customactionbar
        getActivity().getFragmentManager().beginTransaction()
                .replace(R.id.prefFragContain, customActionBarFragment)
                .commit();

        //bind xml variables
        this.placeholderImage = view
                .findViewById(R.id.placeholderImageView);

        applyNewBackground();
        applyNewIconColors();
    }

    public void applyNewIconColors() {
        int newColor = ActionBarReceiver.getUserIconColor();
        placeholderImage.setColorFilter(new PorterDuffColorFilter(newColor,
                PorterDuff.Mode.SRC_IN));
    }

    private void applyNewBackground() {
        placeholderImage.setBackgroundColor(ActionBarReceiver.getInstance()
                .getActionBarColor());
    }

    /**
     * Called when a shared preference is changed, added, or removed. This
     * may be called even if a preference is set to its existing value.
     * <p>
     * <p>This callback will be run on your main thread.
     *
     * @param sharedPreferences The {@link SharedPreferences} that received
     *                          the change.
     * @param key               The key of the preference that was changed, added, or
     */
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
            String key) {
        if (key.equals(CustomActionBarFragment.ACTIONBAR_ICON_COLOR_KEY)) {
            applyNewIconColors();
        } else if (key
                .equals(CustomActionBarFragment.ACTIONBAR_BACKGROUND_COLOR_KEY)) {
            applyNewBackground();
        }
    }
}
