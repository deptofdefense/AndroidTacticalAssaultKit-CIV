
package com.atakmap.app.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.atakmap.android.navigation.views.NavView;
import com.atakmap.android.navigation.views.buttons.NavButtonDrawable;
import com.atakmap.android.preference.AtakPreferences;
import com.atakmap.android.preference.PreferenceSearchIndex;
import com.atakmap.android.tools.ActionBarReceiver;
import com.atakmap.android.preference.AtakPreferenceFragment;
import com.atakmap.app.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 *
 * The fragmennt used to display the inner customactionbarpreference and and imageview to
 * preview the newly changed icon and action bar background
 */

public class ActionBarPreferences extends AtakPreferenceFragment implements
        SharedPreferences.OnSharedPreferenceChangeListener {

    private AtakPreferences prefs;
    private CustomActionBarFragment customActionBarFragment;
    private LinearLayout buttonsLayout;
    private final List<ImageView> buttons = new ArrayList<>();

    public static List<PreferenceSearchIndex> index(Context context) {
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
        prefs = new AtakPreferences(context.getApplicationContext());
        prefs.registerListener(this);

        customActionBarFragment = new CustomActionBarFragment();
    }

    @Override
    public void onDestroy() {
        prefs.unregisterListener(this);
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
        buttons.clear();
        buttonsLayout = view.findViewById(R.id.preview_buttons);
        for (int i = 0; i < buttonsLayout.getChildCount(); i++) {
            View v = buttonsLayout.getChildAt(i);
            if (v instanceof ImageView) {
                ImageView btn = (ImageView) v;
                btn.setImageDrawable(new NavButtonDrawable(getActivity(),
                        btn.getDrawable()));
                buttons.add(btn);
            }
        }

        if (prefs.get(NavView.PREF_NAV_ORIENTATION_RIGHT, true))
            reverseButtons();

        applyNewBackground();
        applyNewIconColors();
    }

    public void applyNewIconColors() {
        int newColor = ActionBarReceiver.getUserIconColor();
        for (ImageView btn : buttons) {
            NavButtonDrawable dr = (NavButtonDrawable) btn.getDrawable();
            dr.setColor(newColor);
        }
    }

    private void applyNewBackground() {
        int newColor = ActionBarReceiver.getInstance().getActionBarColor();
        for (ImageView btn : buttons) {
            NavButtonDrawable dr = (NavButtonDrawable) btn.getDrawable();
            dr.setShadowColor(newColor);
        }
    }

    /**
     * Reverse the display order of the buttons to signify toolbar side
     */
    private void reverseButtons() {
        Collections.reverse(buttons);
        buttonsLayout.removeAllViews();
        for (ImageView btn : buttons)
            buttonsLayout.addView(btn);
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

        if (key == null)
            return;

        switch (key) {
            case CustomActionBarFragment.ACTIONBAR_ICON_COLOR_KEY:
                applyNewIconColors();
                break;
            case CustomActionBarFragment.ACTIONBAR_BACKGROUND_COLOR_KEY:
                applyNewBackground();
                break;
            case NavView.PREF_NAV_ORIENTATION_RIGHT:
                reverseButtons();
                break;
        }
    }
}
