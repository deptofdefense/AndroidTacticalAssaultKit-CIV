
package com.atakmap.android.image;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.widget.Toast;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.preference.PreferenceSearchIndex;
import com.atakmap.coremap.log.Log;

import com.atakmap.android.layers.ScanLayersService;
import com.atakmap.android.preference.AtakPreferenceFragment;
import com.atakmap.app.R;

public class MediaPreferenceFragment extends AtakPreferenceFragment {

    public static java.util.List<PreferenceSearchIndex> index(Context context) {
        return index(context,
                MediaPreferenceFragment.class,
                R.string.mediaPreferences,
                R.drawable.media_prefs_camera);
    }

    public MediaPreferenceFragment() {
        super(R.xml.media_preferences, R.string.mediaPreferences);
    }

    @Override
    public String getSubTitle() {
        return getSubTitle(getString(R.string.controlPreferences),
                getSummary());
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(getResourceID());

        Preference layerCacheReset = findPreference(
                "mediaLayerCacheForceRescan");
        if (layerCacheReset != null) {
            layerCacheReset
                    .setOnPreferenceClickListener(
                            new OnPreferenceClickListener() {
                                @Override
                                public boolean onPreferenceClick(
                                        Preference pref) {
                                    Toast.makeText(layerCacheReset.getContext(),
                                            "rescanning for new layers",
                                            Toast.LENGTH_SHORT).show();
                                    Log.d(TAG,
                                            "rescanning for new layers created after caching operation");
                                    Intent scanIntent = new Intent(
                                            ScanLayersService.START_SCAN_LAYER_ACTION);
                                    scanIntent.putExtra("forceReset", true);
                                    AtakBroadcast.getInstance()
                                            .sendBroadcast(scanIntent);

                                    return true;
                                }
                            });
        }
    }
}
