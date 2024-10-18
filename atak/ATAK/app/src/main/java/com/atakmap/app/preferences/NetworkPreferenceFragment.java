
package com.atakmap.app.preferences;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;

import com.atakmap.android.cot.CotMapComponent;
import com.atakmap.android.preference.AtakPreferenceFragment;
import com.atakmap.android.preference.PreferenceSearchIndex;
import com.atakmap.app.R;
import com.atakmap.comms.app.CotStreamListActivity;
import com.atakmap.coremap.log.Log;

public class NetworkPreferenceFragment extends AtakPreferenceFragment implements
        OnPreferenceClickListener {

    public static java.util.List<PreferenceSearchIndex> index(Context context) {
        return index(context,
                NetworkPreferenceFragment.class,
                R.string.networkPreferences,
                R.drawable.ic_menu_network);
    }

    public NetworkPreferenceFragment() {
        super(R.xml.network_preferences, R.string.networkPreferences);
    }

    @Override
    public boolean onPreferenceClick(Preference pref) {
        String key = pref.getKey();
        switch (key) {
            case "networkSettings":
                showScreen(new NetworkConnectionPreferenceFragment());
                break;
            case "tadiljSettings":
                showScreen(new TadilJPreferenceFragment());
                break;
            case "serverConnections":
                if (CotMapComponent.hasServer()) {
                    startActivity(new Intent(getActivity(),
                            CotStreamListActivity.class));
                } else {
                    showScreen(new PromptNetworkPreferenceFragment());
                }
                break;
        }

        return true;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(getResourceID());

        Preference networkSettings = findPreference("networkSettings");
        networkSettings
                .setOnPreferenceClickListener(this);

        Preference tadiljSettings = findPreference("tadiljSettings");
        tadiljSettings.setOnPreferenceClickListener(this);

        Preference serverConnections = findPreference("serverConnections");
        serverConnections.setOnPreferenceClickListener(this);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "destroy the preference activity");
        super.onDestroy();
    }
}
