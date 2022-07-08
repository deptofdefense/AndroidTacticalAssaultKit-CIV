
package com.atakmap.app.preferences;

import android.content.Context;
import android.os.Bundle;

import com.atakmap.android.preference.AtakPreferenceFragment;
import com.atakmap.android.preference.PreferenceSearchIndex;
import com.atakmap.app.R;

public class UserTouchPreferenceFragment extends AtakPreferenceFragment {
    public UserTouchPreferenceFragment() {
        super(R.xml.user_touch_prefs, R.string.usertouchPreferences);
    }

    public static java.util.List<PreferenceSearchIndex> index(Context context) {
        return index(context,
                UserTouchPreferenceFragment.class,
                R.string.usertouchPreferences,
                R.drawable.ic_menu_network);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(getResourceID());
    }
}
