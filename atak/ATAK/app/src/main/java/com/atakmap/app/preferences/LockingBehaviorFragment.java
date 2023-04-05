
package com.atakmap.app.preferences;

import android.content.Context;
import android.os.Bundle;

import com.atakmap.android.preference.AtakPreferenceFragment;
import com.atakmap.android.preference.PreferenceSearchIndex;
import com.atakmap.app.R;

public class LockingBehaviorFragment extends AtakPreferenceFragment {

    public static java.util.List<PreferenceSearchIndex> index(Context context) {
        return index(context,
                ControlPrefsFragment.class,
                R.string.controlPreferences,
                R.drawable.ic_menu_settings);
    }

    public LockingBehaviorFragment() {
        super(R.xml.locking_preference, R.string.lockBehavior);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(getResourceID());
    }
}
