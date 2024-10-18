
package com.atakmap.app.preferences;

import android.content.Context;
import android.os.Bundle;

import com.atakmap.android.preference.AtakPreferenceFragment;
import com.atakmap.android.preference.PreferenceSearchIndex;
import com.atakmap.app.R;

public class ThreeDRenderingFragment extends AtakPreferenceFragment {
    public ThreeDRenderingFragment() {
        super(R.xml.three_d_rendering, R.string.rendering_3d_options);
    }

    public static java.util.List<PreferenceSearchIndex> index(Context context) {
        return index(context,
                ThreeDRenderingFragment.class,
                R.string.rendering_3d_options,
                R.drawable.ic_menu_network);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(getResourceID());
    }
}
