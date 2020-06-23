
package com.atakmap.android.layers.app;

import android.content.Context;
import android.os.Bundle;

import com.atakmap.android.preference.AtakPreferenceFragment;
import com.atakmap.android.preference.PreferenceSearchIndex;
import com.atakmap.app.R;

public class LayerPreferenceFragment extends AtakPreferenceFragment {

    public static java.util.List<PreferenceSearchIndex> index(Context context) {
        return index(context,
                LayerPreferenceFragment.class,
                R.string.imageryLayerPreferences,
                R.drawable.ic_menu_maps);
    }

    public LayerPreferenceFragment() {
        super(R.xml.layers_preferences, R.string.imageryLayerPreferences);
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
    }

}
