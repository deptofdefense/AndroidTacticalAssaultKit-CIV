
package com.atakmap.app.preferences;

import android.content.Context;
import android.os.Bundle;

import com.atakmap.android.preference.AtakPreferenceFragment;
import com.atakmap.android.preference.PreferenceSearchIndex;
import com.atakmap.app.R;

public class DexOptionsPreferenceFragment extends AtakPreferenceFragment {
    public DexOptionsPreferenceFragment() {
        super(R.xml.dex_options_prefs, R.string.dex_options);
    }

    public static java.util.List<PreferenceSearchIndex> index(Context context) {
        return index(context,
                DexOptionsPreferenceFragment.class,
                R.string.dex_options,
                R.drawable.ic_menu_network);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(getResourceID());
    }
}
