
package com.atakmap.app.preferences;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.view.LayoutInflater;
import android.widget.Toast;

import com.atakmap.android.gui.AlertDialogHelper;
import com.atakmap.android.gui.HintDialogHelper;
import com.atakmap.android.importfiles.ui.ImportManagerFileBrowser;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.preference.AtakPreferenceFragment;
import com.atakmap.android.preference.PreferenceSearchIndex;
import com.atakmap.android.update.AppMgmtActivity;
import com.atakmap.android.update.AppMgmtUtils;
import com.atakmap.android.update.FileSystemProductProvider;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.net.AtakCertificateDatabaseIFace;

import java.io.File;
import java.util.List;

public class AppMgmtPreferenceFragment extends AtakPreferenceFragment {

    public static final String TAG = "AppMgmtPreferenceFragment";

    public static final String PREF_ATAK_UPDATE_LOCAL_PATH = "atakUpdateLocalPathString";

    public static java.util.List<PreferenceSearchIndex> index(Context context) {
        return index(context,
                AppMgmtPreferenceFragment.class,
                R.string.app_mgmt_settings,
                R.drawable.ic_menu_plugins);
    }

    public AppMgmtPreferenceFragment() {
        super(R.xml.app_mgmt_preferences, R.string.app_mgmt_settings);
    }

    @Override
    public String getSubTitle() {
        return getSubTitle(getString(R.string.app_mgmt_text1),
                getSummary());
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(getResourceID());

        CheckBoxPreference appMgmtEnableUpdateServer = (CheckBoxPreference) findPreference(
                "appMgmtEnableUpdateServer");
        appMgmtEnableUpdateServer
                .setOnPreferenceChangeListener(
                        new Preference.OnPreferenceChangeListener() {
                            @Override
                            public boolean onPreferenceChange(
                                    Preference preference,
                                    Object newValue) {
                                if (newValue instanceof Boolean) {
                                    Boolean bChecked = (Boolean) newValue;
                                    if (bChecked) {
                                        String updateUrl = getPreferenceManager()
                                                .getSharedPreferences()
                                                .getString(
                                                        "atakUpdateServerUrl",
                                                        getString(
                                                                R.string.atakUpdateServerUrlDefault));

                                        if (FileSystemUtils
                                                .isEmpty(updateUrl)) {
                                            android.widget.Toast.makeText(
                                                    getActivity(),
                                                    "Please set the sync server url.",
                                                    android.widget.Toast.LENGTH_SHORT)
                                                    .show();
                                            return true;
                                        }
                                        new AlertDialog.Builder(getActivity())
                                                .setIcon(
                                                        com.atakmap.android.util.ATAKConstants
                                                                .getIconId())
                                                .setTitle("Sync Updates")
                                                .setMessage(
                                                        "Would you like to sync with "
                                                                + getPreferenceManager()
                                                                        .getSharedPreferences()
                                                                        .getString(
                                                                                "atakUpdateServerUrl",
                                                                                getString(
                                                                                        R.string.atakUpdateServerUrlDefault))
                                                                + "?")
                                                .setPositiveButton(
                                                        "Sync",
                                                        new DialogInterface.OnClickListener() {
                                                            @Override
                                                            public void onClick(
                                                                    DialogInterface dialog,
                                                                    int which) {
                                                                Log.d(TAG,
                                                                        "User has enabled remote sync");

                                                                //lets sync now
                                                                AppMgmtPreferenceFragment.this
                                                                        .getActivity()
                                                                        .finish();

                                                                //send intent to sync, giving the pref state a chance to update
                                                                AtakBroadcast
                                                                        .getInstance()
                                                                        .sendBroadcast(
                                                                                new Intent(
                                                                                        AppMgmtActivity.SYNC));

                                                            }
                                                        })
                                                .setNegativeButton("Not Now",
                                                        null)
                                                .show();
                                    } else {
                                        //update server was disabled, lets re-sync now
                                        Log.d(TAG,
                                                "User has disabled remote sync");

                                        //close this pref UI so we can see sync dialog on previous activity
                                        AppMgmtPreferenceFragment.this
                                                .getActivity()
                                                .finish();

                                        AtakBroadcast
                                                .getInstance()
                                                .sendBroadcast(
                                                        new Intent(
                                                                AppMgmtActivity.SYNC));
                                    }
                                }

                                return true;
                            }
                        });

        Preference atakUpdateServerUrl = findPreference("atakUpdateServerUrl");
        atakUpdateServerUrl
                .setOnPreferenceChangeListener(
                        new Preference.OnPreferenceChangeListener() {
                            @Override
                            public boolean onPreferenceChange(
                                    Preference preference,
                                    final Object newValue) {

                                if (newValue == null
                                        || FileSystemUtils
                                                .isEmpty((String) newValue)) {
                                    Log.w(TAG, "URL is empty");
                                    new AlertDialog.Builder(getActivity())
                                            .setIcon(
                                                    R.drawable.importmgr_status_yellow)
                                            .setTitle("Invalid entry")
                                            .setMessage(
                                                    "Please enter a valid URL")
                                            .setPositiveButton(R.string.ok,
                                                    null)
                                            .show();
                                    return false;
                                }

                                final String url = (String) newValue;
                                if (FileSystemUtils
                                        .isEquals(
                                                url,
                                                getPreferenceManager()
                                                        .getSharedPreferences()
                                                        .getString(
                                                                "atakUpdateServerUrl",
                                                                getString(
                                                                        R.string.atakUpdateServerUrlDefault)))) {
                                    Log.d(TAG, "URL has not changed");
                                    return false;
                                }

                                Log.d(TAG, "User has updated remove server");

                                //close this pref UI so we can see sync dialog on previous activity
                                AppMgmtPreferenceFragment.this.getActivity()
                                        .finish();

                                MapView.getMapView().post(new Runnable() {
                                    @Override
                                    public void run() {
                                        //send intent to sync, giving the pref state a chance to update
                                        AtakBroadcast.getInstance()
                                                .sendBroadcast(
                                                        new Intent(
                                                                AppMgmtActivity.SYNC)
                                                                        .putExtra(
                                                                                "url",
                                                                                url));
                                    }
                                });

                                return true;
                            }
                        });

        Preference atakUpdateLocalPath = findPreference("atakUpdateLocalPath");
        atakUpdateLocalPath.setOnPreferenceClickListener(
                new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        promptDir();
                        return false;
                    }
                });

        final Preference caLocation = findPreference("updateServerCaLocation");
        caLocation.setOnPreferenceClickListener(
                new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        NetworkConnectionPreferenceFragment.getCertFile(
                                getActivity(),
                                getString(R.string.preferences_text412),
                                AtakCertificateDatabaseIFace.TYPE_UPDATE_SERVER_TRUST_STORE_CA,
                                false, null);
                        return false;
                    }
                });
    }

    private void promptDir() {

        final Activity context = AppMgmtPreferenceFragment.this.getActivity();
        LayoutInflater inflater = LayoutInflater.from(context);
        final ImportManagerFileBrowser importView = (ImportManagerFileBrowser) inflater
                .inflate(R.layout.import_manager_file_browser, null);

        final String _lastDirectory = getPreferenceManager()
                .getSharedPreferences().getString(PREF_ATAK_UPDATE_LOCAL_PATH,
                        FileSystemUtils.getItem(
                                FileSystemProductProvider.LOCAL_REPO_PATH)
                                .getAbsolutePath());

        importView.setTitle(R.string.select_directory_to_import);
        importView.setStartDirectory(_lastDirectory);
        importView.setUseProvider(false);
        importView.allowAnyExtenstionType();
        AlertDialog.Builder b = new AlertDialog.Builder(context);
        b.setView(importView);
        b.setNegativeButton(R.string.cancel, null);
        b.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // User has selected items and touched OK. Import the data.
                List<File> selectedFiles = importView.getSelectedFiles();

                if (selectedFiles.size() == 0) {
                    Toast.makeText(context,
                            R.string.no_import_directory,
                            Toast.LENGTH_SHORT).show();
                } else if (selectedFiles.size() > 1) {
                    Toast.makeText(context,
                            R.string.multiple_import_directory,
                            Toast.LENGTH_SHORT).show();
                } else {
                    setApkDir(context, selectedFiles.get(0));
                }
            }
        });
        final AlertDialog alert = b.create();

        // This also tells the importView to handle the back button presses
        // that the user provides to the alert dialog.
        importView.setAlertDialog(alert);

        // Show the dialog
        alert.show();

        AlertDialogHelper.adjustWidth(alert, .90);

        HintDialogHelper.showHint(getActivity(),
                getString(R.string.apk_directory_no_apks_hint_title),
                getString(R.string.apk_directory_no_apks_hint),
                "hint.atakUpdateLocalPath");
    }

    private void setApkDir(final Activity context, final File selected) {
        if (!FileSystemUtils.isFile(selected)
                || !IOProviderFactory.isDirectory(selected)) {
            Toast.makeText(context,
                    R.string.no_import_directory,
                    Toast.LENGTH_SHORT).show();
            return;
        }

        File[] list = IOProviderFactory.listFiles(selected,
                AppMgmtUtils.APK_FilenameFilter);
        if (list == null || list.length < 1) {
            Log.w(TAG, "setApkDir no APKs: " + selected.getAbsolutePath());
            new AlertDialog.Builder(getActivity())
                    .setIcon(
                            com.atakmap.android.util.ATAKConstants.getIconId())
                    .setTitle(getString(R.string.confirm_directory))
                    .setMessage(getString(R.string.apk_directory_no_apks))
                    .setPositiveButton(R.string.ok,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(
                                        DialogInterface dialogInterface,
                                        int i) {
                                    setApkDir2(selected);
                                }
                            })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        } else {
            setApkDir2(selected);
        }
    }

    private void setApkDir2(File selected) {
        // Store the currently displayed directory
        getPreferenceManager().getSharedPreferences()
                .edit()
                .putString(PREF_ATAK_UPDATE_LOCAL_PATH,
                        selected.getAbsolutePath())
                .apply();

        //remove old INF to be rebuilt from new custom dir
        File customInf = FileSystemUtils
                .getItem(FileSystemProductProvider.LOCAL_REPO_INDEX);
        if (FileSystemUtils.isFile(customInf)) {
            FileSystemUtils.delete(customInf);
        }

        //remove old INFZ to be rebuilt from new custom dir
        File customInfz = FileSystemUtils
                .getItem(FileSystemProductProvider.LOCAL_REPOZ_INDEX);
        if (FileSystemUtils.isFile(customInfz)) {
            FileSystemUtils.delete(customInfz);
        }

        Log.d(TAG, "Setting APK Dir: " + selected.getAbsolutePath());

        final Activity activity = getActivity();
        if (activity == null) {
            AtakBroadcast.getInstance()
                    .sendBroadcast(new Intent(AppMgmtActivity.SYNC));
            return;
        }

        new AlertDialog.Builder(getActivity())
                .setIcon(
                        com.atakmap.android.util.ATAKConstants.getIconId())
                .setTitle("Sync Updates")
                .setMessage(
                        "Would you like to sync "
                                + getPreferenceManager()
                                        .getSharedPreferences()
                                        .getString(
                                                PREF_ATAK_UPDATE_LOCAL_PATH, "")
                                + "?")
                .setPositiveButton(
                        "Sync",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(
                                    DialogInterface dialog,
                                    int which) {
                                Log.d(TAG,
                                        "User has enabled local dir sync");

                                //lets sync now
                                AppMgmtPreferenceFragment.this
                                        .getActivity()
                                        .finish();

                                AtakBroadcast.getInstance().sendBroadcast(
                                        new Intent(AppMgmtActivity.SYNC));
                            }
                        })
                .setNegativeButton("Not Now",
                        null)
                .show();
    }
}
