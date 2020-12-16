
package com.atakmap.android.data;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.preference.PreferenceManager;

import com.atakmap.android.dropdown.DropDownManager;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.missionpackage.MissionPackageMapComponent;
import com.atakmap.app.R;
import com.atakmap.app.preferences.PreferenceControl;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProvider;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.net.AtakAuthenticationDatabase;
import com.atakmap.net.AtakCertificateDatabase;

import java.io.File;

/**
 * Task to cleanup map and data content
 */
public class ClearContentTask extends AsyncTask<Void, Integer, Boolean> {
    private static final String TAG = "ClearContentTask";

    protected ProgressDialog _progressDialog;
    protected final Context _context;

    private final boolean _bClearMaps;
    private final boolean _bExitWhenDone;

    ClearContentTask(Context context, boolean bClearMaps,
            boolean bExitWhenDone) {
        _context = context;
        _bClearMaps = bClearMaps;
        _bExitWhenDone = bExitWhenDone;
    }

    @Override
    protected void onPreExecute() {
        if (_progressDialog == null) {
            // Before running code in background/worker thread
            _progressDialog = new ProgressDialog(_context);
            _progressDialog.setIcon(R.drawable.ic_menu_clear_content);
            _progressDialog.setTitle(_context
                    .getString(R.string.zeroize_processing_request));
            _progressDialog
                    .setMessage(_context
                            .getString(
                                    R.string.zeroize_please_wait_app_will_exit));
            _progressDialog.setIndeterminate(true);
            _progressDialog.setCancelable(false);
            _progressDialog.show();
        } else {
            // update title as progress dialog may have been passed off from another aysnc task
            _progressDialog
                    .setMessage(_context
                            .getString(
                                    R.string.zeroize_please_wait_app_will_exit));
        }
    }

    @Override
    protected Boolean doInBackground(Void... arg0) {
        Thread.currentThread().setName(TAG);

        // work to be performed by background thread
        Log.d(TAG, "Executing...");

        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(_context);
        prefs.edit().putBoolean("clearingContent", true).apply();

        //close dropdowns/tools
        AtakBroadcast.getInstance().sendBroadcast(new Intent(
                "com.atakmap.android.maps.toolbar.END_TOOL"));
        DropDownManager.getInstance().closeAllDropDowns();

        //
        //now notify components to clear their respective data
        //it is expected that these will be quick operations

        ClearContentRegistry.getInstance().clearContent(_bClearMaps);

        // Prevent errors during secure delete
        MissionPackageMapComponent mp = MissionPackageMapComponent
                .getInstance();
        if (mp != null)
            mp.getFileIO().disableFileWatching();

        //delete majority of files here on background thread rather then tying up UI
        //thread by having components delete large numbers of files
        //while processing ZEROIZE_CONFIRMED_ACTION intent
        DataMgmtReceiver.deleteDirs(new String[] {
                "grg", "attachments", "cert", "overlays",
                FileSystemUtils.EXPORT_DIRECTORY,
                FileSystemUtils.TOOL_DATA_DIRECTORY,
                FileSystemUtils.SUPPORT_DIRECTORY,
                FileSystemUtils.CONFIG_DIRECTORY
        }, true);

        // reset all prefs and stored credentials
        AtakAuthenticationDatabase.clear();
        AtakCertificateDatabase.clear();

        //Clear all pref groups
        Log.d(TAG, "Clearing preferences");
        for (String name : PreferenceControl
                .getInstance(_context).PreferenceGroups) {
            prefs = _context.getSharedPreferences(name,
                    Context.MODE_PRIVATE);

            if (prefs != null)
                prefs.edit().clear().apply();
        }

        setClearContent(
                PreferenceManager.getDefaultSharedPreferences(_context), true);

        final File databaseDir = FileSystemUtils.getItem("Databases");
        final File[] files = IOProviderFactory.listFiles(databaseDir);
        if (files != null) {
            for (File file : files) {
                if (IOProviderFactory.isFile(file)) {
                    final String name = file.getName();
                    // skip list for now
                    if (name.equals("files.sqlite3")
                            || name.equals("GRGs2.sqlite") ||
                            name.equals("layers3.sqlite")
                            || name.equals("GeoPackageImports.sqlite")) {

                        Log.d(TAG, "skipping: " + name);

                    } else {

                        Log.d(TAG, "purging: " + name);
                        IOProviderFactory.delete(file,
                                IOProvider.SECURE_DELETE);

                    }
                }
            }
        }

        //optionally delete maps
        //these are deleted last since they usually take the longest
        if (_bClearMaps) {
            DataMgmtReceiver.deleteDirs(new String[] {
                    "layers", "native", "mobac", "mrsid", "imagery", "pri",
                    "pfi", "imagecache"
            }, true);
            DataMgmtReceiver.deleteDirs(new String[] {
                    "DTED", "pfps",
            }, false);

            //TODO delete APASS data? query DB for those datasets prior to dumping the DB?
        }

        return true;
    }

    /**
     * Set flag indicating device is clearing content
     *
     * @param b set the state of the clearingContent flag
     */
    public static void setClearContent(SharedPreferences preferences,
            boolean b) {
        if (preferences == null)
            return;

        preferences.edit().putBoolean("clearingContent", b).apply();
    }

    /**
     * Used during shutdown to determine if the device is clearing content
     *
     * @param preferences a reference to shared preference
     * @return obtain the state of the clearingContent flag.
     */
    public static boolean isClearContent(final SharedPreferences preferences) {
        return preferences != null
                && preferences.getBoolean("clearingContent", false);

    }

    @Override
    protected void onCancelled(Boolean result) {
        AtakBroadcast.getInstance().sendBroadcast(
                new Intent("com.atakmap.app.quitapp")
                        .putExtra("FORCE_QUIT", true));
    }

    @Override
    protected void onPostExecute(Boolean result) {
        // work to be performed by UI thread after work is complete
        Log.d(TAG, "onPostExecute");

        // close the progress dialog
        if (_progressDialog != null) {
            _progressDialog.dismiss();
            _progressDialog = null;
        }

        if (_bExitWhenDone) {
            //now exit ATAK
            AtakBroadcast.getInstance().sendBroadcast(
                    new Intent("com.atakmap.app.QUITAPP")
                            .putExtra("FORCE_QUIT", true));
        }
    }
}
