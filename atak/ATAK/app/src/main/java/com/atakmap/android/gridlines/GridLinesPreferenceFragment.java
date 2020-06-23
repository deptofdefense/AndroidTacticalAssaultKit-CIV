
package com.atakmap.android.gridlines;

import android.content.Context;
import android.os.Bundle;

import com.atakmap.android.preference.AtakPreferenceFragment;
import com.atakmap.android.preference.PreferenceSearchIndex;
import com.atakmap.app.R;

import java.util.List;

public class GridLinesPreferenceFragment extends AtakPreferenceFragment {

    public static List<PreferenceSearchIndex> index(Context context) {
        return index(context,
                GridLinesPreferenceFragment.class,
                R.string.gridLinePreferences,
                R.drawable.ic_overlay_gridlines);
    }

    public GridLinesPreferenceFragment() {
        super(R.xml.gridlines_preferences, R.string.gridLinePreferences);
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
