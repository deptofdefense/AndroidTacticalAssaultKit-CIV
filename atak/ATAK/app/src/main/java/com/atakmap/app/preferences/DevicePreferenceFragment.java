
package com.atakmap.app.preferences;

import android.content.Context;
import android.preference.PreferenceManager;
import android.text.InputFilter;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;

import com.atakmap.os.FileObserver;
import com.atakmap.android.gui.PanEditTextPreference;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.preference.AtakPreferenceFragment;
import com.atakmap.android.preference.PreferenceSearchIndex;
import com.atakmap.app.R;
import com.atakmap.app.ATAKDatabaseHelper;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;

import com.atakmap.android.cot.NetworkGPSPreferenceFragment;

import java.io.File;
import java.io.FilenameFilter;

public class DevicePreferenceFragment extends AtakPreferenceFragment {

    public static final String TAG = "DevicePreferenceFragment";

    private static DevicePreferenceFragment _instance;
    private CheckBoxPreference useWRCallsign;

    private ListPreference myTeamListPreference;

    private PreferenceCategory alternateContactCategory;
    private ListPreference saSipAddressAssignment;
    private PanEditTextPreference saSipAddress;
    private String _previousSaSipAddressAssignment = "";

    private FileChecker prefFileObserver;
    private FileChecker partialPrefFileObserver;
    private ListPreference loadPrefs;
    private ListPreference loadPartialPrefs;
    private final static String extension = ".pref";
    private PreferenceControl pc;
    private SharedPreferences.OnSharedPreferenceChangeListener spChanged;

    public synchronized static DevicePreferenceFragment getInstance() {
        if (_instance == null) {
            _instance = new DevicePreferenceFragment();
        }
        return _instance;
    }

    public DevicePreferenceFragment() {
        super(R.xml.device_preferences, R.string.devicePreferences);
    }

    public static java.util.List<PreferenceSearchIndex> index(Context context) {
        return index(context,
                DevicePreferenceFragment.class,
                R.string.devicePreferences,
                R.drawable.my_prefs_settings);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(getResourceID());

        pc = PreferenceControl.getInstance(getActivity());
        pc.connect();
        getActivity().setResult(Activity.RESULT_CANCELED, null);

        Preference lc = findPreference("locationCallsign");
        ((PanEditTextPreference) lc).setFilters(new InputFilter[] {
                new InputFilter.LengthFilter(40)
        }, false);

        Preference reportingSettings = findPreference("reportingSettings");
        reportingSettings
                .setOnPreferenceClickListener(
                        new Preference.OnPreferenceClickListener() {
                            @Override
                            public boolean onPreferenceClick(Preference pref) {
                                showScreen(new ReportingPreferenceFragment());
                                return true;
                            }
                        });

        Preference gpsSettings = findPreference("gpsSettings");
        gpsSettings
                .setOnPreferenceClickListener(
                        new Preference.OnPreferenceClickListener() {
                            @Override
                            public boolean onPreferenceClick(Preference pref) {
                                showScreen(new NetworkGPSPreferenceFragment());
                                return true;
                            }
                        });

        useWRCallsign = (CheckBoxPreference) findPreference(
                "locationUseWRCallsign");
        final SharedPreferences sp = PreferenceManager
                .getDefaultSharedPreferences(getActivity());
        if (!sp.getString("mockingOption", "WRGPS").equals("WRGPS")) { // If the mockingOPtion isn't
                                                                       // defined, then we assume
                                                                       // that screen hasn't been
                                                                       // visited so it goes to it's
                                                                       // default value
            useWRCallsign.setEnabled(false);
            useWRCallsign.setChecked(false);
        }
        spChanged = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(
                    SharedPreferences sharedPreferences, String key) {
                switch (key) {
                    case "locationTeam":
                    case "atakRoleType":
                    case "locationUnitType":
                    case "saSipAddressAssignment":
                    case "loadPrefs":
                    case "loadPartialPrefs":
                        String s = sharedPreferences.getString(key, null);
                        if (s != null) {
                            ListPreference attribute = (ListPreference) findPreference(
                                    key);
                            attribute.setValue(s);
                        }
                        break;
                }
            }
        };

        sp.registerOnSharedPreferenceChangeListener(spChanged);

        alternateContactCategory = (PreferenceCategory) findPreference(
                "alternateContactCategory");
        saSipAddressAssignment = (ListPreference) findPreference(
                "saSipAddressAssignment");
        saSipAddress = (PanEditTextPreference) findPreference("saSipAddress");
        if (saSipAddressAssignment != null) {
            String value = saSipAddressAssignment.getValue();
            if (value == null) {
                value = getString(R.string.voip_assignment_disabled);
                saSipAddressAssignment.setValue(value);
            }
            changeVisibilityVoIP(value);

            saSipAddressAssignment
                    .setOnPreferenceChangeListener(
                            new Preference.OnPreferenceChangeListener() {

                                @Override
                                public boolean onPreferenceChange(
                                        Preference arg0,
                                        Object arg1) {
                                    changeVisibilityVoIP((String) arg1);
                                    return true;
                                }

                            });
        }

        Preference savePrefs = findPreference("savePrefs");
        savePrefs
                .setOnPreferenceChangeListener(
                        new Preference.OnPreferenceChangeListener() {
                            @Override
                            public boolean onPreferenceChange(
                                    Preference preference,
                                    Object newValue) {
                                Log.d(TAG, "saving the setting: " + newValue
                                        + extension);
                                pc.saveSettings(newValue + extension);
                                return true;
                            }
                        });

        loadPrefs = (ListPreference) findPreference("loadPrefs");
        loadPrefs
                .setOnPreferenceChangeListener(
                        new Preference.OnPreferenceChangeListener() {
                            @Override
                            public boolean onPreferenceChange(
                                    Preference preference,
                                    Object newValue) {
                                pc.loadSettings((String) newValue, true);
                                return true;
                            }
                        });

        loadPartialPrefs = (ListPreference) findPreference("loadPartialPrefs");
        loadPartialPrefs
                .setOnPreferenceChangeListener(
                        new Preference.OnPreferenceChangeListener() {
                            @Override
                            public boolean onPreferenceChange(
                                    Preference preference,
                                    Object newValue) {
                                String[] categorySet = null;
                                pc.loadPartialSettings(getActivity(),
                                        (String) newValue, true, categorySet);
                                return true;
                            }
                        });
        Preference encryptionPassphrase = findPreference(
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

        Preference readyClone = findPreference("prepareForClone");
        readyClone
                .setOnPreferenceClickListener(
                        new Preference.OnPreferenceClickListener() {
                            @Override
                            public boolean onPreferenceClick(
                                    Preference preference) {
                                AlertDialog.Builder alertBuilder = new AlertDialog.Builder(
                                        getActivity());
                                alertBuilder
                                        .setIcon(
                                                com.atakmap.android.util.ATAKConstants
                                                        .getIconId());
                                alertBuilder
                                        .setTitle(R.string.preferences_text461)
                                        .setMessage(
                                                R.string.preferences_text462)
                                        .setPositiveButton(R.string.ok,
                                                new DialogInterface.OnClickListener() {
                                                    @Override
                                                    public void onClick(
                                                            DialogInterface dialog,
                                                            int which) {
                                                        dialog.dismiss();
                                                        prepareForClone();
                                                    }
                                                })
                                        .setNegativeButton(R.string.cancel,
                                                null);

                                AlertDialog dialog = alertBuilder.create();
                                dialog.setCancelable(false);
                                dialog.show();

                                return true;
                            }
                        });

        /*
            catches team color pref change
         */
        myTeamListPreference = (ListPreference) findPreference("locationTeam");
        myTeamListPreference
                .setOnPreferenceChangeListener(
                        new Preference.OnPreferenceChangeListener() {
                            @Override
                            public boolean onPreferenceChange(
                                    Preference preference,
                                    Object newValue) {
                                //when preference changes launch the customselficon class to create a new icon
                                //and store on device
                                return true;
                            }
                        });
        // Attach a file observer to the preference file and watch for changes
        if (prefFileObserver != null) {
            prefFileObserver.stopWatching();
            prefFileObserver = null;
        }
        prefFileObserver = new FileChecker(PreferenceControl.DIRPATH);
        prefFileObserver.loadFiles();
        prefFileObserver.startWatching();

        // Attach a file observer to the preferences file and watch for changes
        if (partialPrefFileObserver != null) {
            partialPrefFileObserver.stopWatching();
            partialPrefFileObserver = null;
        }
        partialPrefFileObserver = new FileChecker(PreferenceControl.DIRPATH);
        partialPrefFileObserver.loadPartialFiles();
        partialPrefFileObserver.startWatching();
    }

    private void prepareForClone() {
        final SharedPreferences sp = PreferenceManager
                .getDefaultSharedPreferences(getActivity());
        sp.edit().remove("bestDeviceUID").apply();
        sp.edit().remove("locationCallsign").apply();

        ATAKDatabaseHelper.removeDatabases();

        AlertDialog.Builder alertBuilder = new AlertDialog.Builder(
                getActivity());
        alertBuilder
                .setTitle(R.string.preferences_text463)
                .setMessage(
                        R.string.preferences_text464)
                .setPositiveButton(R.string.ok,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                dialog.dismiss();
                                Intent intent = new Intent();
                                intent.setAction("com.atakmap.app.QUITAPP");
                                intent.putExtra("FORCE_QUIT", true);
                                AtakBroadcast.getInstance().sendBroadcast(
                                        intent);
                            }
                        });

        AlertDialog dialog = alertBuilder.create();
        dialog.setCancelable(false);
        dialog.show();

    }

    @Override
    public void onDestroy() {
        if (prefFileObserver != null) {
            prefFileObserver.stopWatching();
        }
        prefFileObserver = null;

        if (partialPrefFileObserver != null) {
            partialPrefFileObserver.stopWatching();
        }
        partialPrefFileObserver = null;

        //pc.dispose();  //Do not want to destroy it here, just disconnect so we can access it later.  AS.
        pc.disconnect();
        final SharedPreferences sp = PreferenceManager
                .getDefaultSharedPreferences(getActivity());
        sp.unregisterOnSharedPreferenceChangeListener(spChanged);
        super.onDestroy();
    }

    public void setUseWRCallsign(boolean enabled) {
        if (useWRCallsign != null) {
            useWRCallsign.setEnabled(enabled);
            useWRCallsign.setChecked(false);
        }
    }

    private void changeVisibilityVoIP(String selection) {
        if (selection.equals(_previousSaSipAddressAssignment)) {
            return;
        }

        if (selection
                .equals(getString(R.string.voip_assignment_manual_entry))) {
            _previousSaSipAddressAssignment = selection;
            alternateContactCategory.addPreference(saSipAddress);
        } else {
            //manual entry is hidden for all other assignment methods
            _previousSaSipAddressAssignment = selection;
            alternateContactCategory
                    .removePreference(saSipAddress);
        }
    }

    private class FileChecker extends FileObserver {
        class ConfigFilter implements FilenameFilter {
            @Override
            public boolean accept(File dir, String filename) {
                return filename.endsWith(extension);
            }
        }

        FileChecker(String path) {
            super(path, FileObserver.CREATE | FileObserver.DELETE
                    | FileObserver.MOVED_FROM
                    | FileObserver.MOVED_TO); // We only care about modification events
        }

        @Override
        public void onEvent(int event, String path) {
            if (path != null && !path.contains("/")) {
                loadFiles();
                loadPartialFiles();
            }
        }

        void loadFiles() {
            File configDirectory = new File(PreferenceControl.DIRPATH);
            String[] files = IOProviderFactory.list(configDirectory,
                    new ConfigFilter());
            if (files == null || files.length == 0) {
                SpannableString msg = new SpannableString(
                        getString(R.string.preferences_text467));
                msg.setSpan(new ForegroundColorSpan(Color.BLACK), 0,
                        msg.length(), 0);
                // Not the best way to do it, but it works
                loadPrefs.setDialogMessage(msg);
            } else if (loadPrefs != null) {
                loadPrefs.setDialogMessage(null);
                loadPrefs.setEntries(files);
                loadPrefs.setEntryValues(files);
            }
        }

        void loadPartialFiles() {
            File configDirectory = new File(PreferenceControl.DIRPATH);
            String[] files = IOProviderFactory.list(configDirectory,
                    new ConfigFilter());
            if (files == null || files.length == 0) {
                SpannableString msg = new SpannableString(
                        getString(R.string.preferences_text467));
                msg.setSpan(new ForegroundColorSpan(Color.BLACK), 0,
                        msg.length(), 0);
                // Not the best way to do it, but it works
                loadPartialPrefs.setDialogMessage(msg);
            } else if (loadPartialPrefs != null) {
                loadPartialPrefs.setDialogMessage(null);
                loadPartialPrefs.setEntries(files);
                loadPartialPrefs.setEntryValues(files);
            }
        }
    }
}
