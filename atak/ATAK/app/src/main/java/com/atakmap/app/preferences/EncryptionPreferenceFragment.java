
package com.atakmap.app.preferences;

import android.os.Bundle;
import android.preference.Preference;

import com.atakmap.android.preference.AtakPreferenceFragment;
import com.atakmap.app.R;

public class EncryptionPreferenceFragment extends AtakPreferenceFragment {
    public EncryptionPreferenceFragment() {
        super(R.xml.encrypt_preference, R.string.encryption_passphrase_title);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(getResourceID());
        Preference encryptionPassphrase = (Preference) findPreference(
                "encryptionPassphrase");
        encryptionPassphrase.setOnPreferenceClickListener(
                new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(
                            Preference preference) {
                        com.atakmap.app.ATAKDatabaseHelper
                                .changeKey(getActivity());
                        return true;
                    }
                });
    }
}
