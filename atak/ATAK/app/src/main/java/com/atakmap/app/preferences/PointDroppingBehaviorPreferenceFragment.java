
package com.atakmap.app.preferences;

import android.content.Context;
import android.os.Bundle;

import com.atakmap.android.preference.AtakPreferenceFragment;
import com.atakmap.android.preference.PreferenceSearchIndex;
import com.atakmap.app.R;

public class PointDroppingBehaviorPreferenceFragment
        extends AtakPreferenceFragment {
    public PointDroppingBehaviorPreferenceFragment() {
        super(R.xml.point_drop_behavior, R.string.pointDroppingBehavior);
    }

    public static java.util.List<PreferenceSearchIndex> index(Context context) {
        return index(context,
                PointDroppingBehaviorPreferenceFragment.class,
                R.string.pointDroppingBehavior,
                R.drawable.ic_menu_network);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(getResourceID());
    }
}
