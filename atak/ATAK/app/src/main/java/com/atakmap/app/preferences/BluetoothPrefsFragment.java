
package com.atakmap.app.preferences;

import android.content.Context;
import android.os.Bundle;

import com.atakmap.android.gui.PanEditTextPreference;
import com.atakmap.android.preference.AtakPreferenceFragment;
import com.atakmap.android.preference.PreferenceSearchIndex;
import com.atakmap.app.R;

public class BluetoothPrefsFragment extends AtakPreferenceFragment {

    private static final String TAG = "BluetoothPrefsFragment";

    public static java.util.List<PreferenceSearchIndex> index(Context context) {
        return index(context,
                BluetoothPrefsFragment.class,
                R.string.bluetoothPreferences,
                R.drawable.bluetooth);
    }

    public BluetoothPrefsFragment() {
        super(R.xml.bluetooth_preferences, R.string.bluetoothPreferences);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(getResourceID());
        ((PanEditTextPreference) findPreference(
                "atakBluetoothReconnectSeconds"))
                        .checkValidInteger();

    }

}
