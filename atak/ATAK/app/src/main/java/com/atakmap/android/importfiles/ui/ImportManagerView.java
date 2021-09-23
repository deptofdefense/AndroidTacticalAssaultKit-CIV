
package com.atakmap.android.importfiles.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;

import com.atakmap.android.data.URIContentManager;
import com.atakmap.android.data.URIContentProvider;
import com.atakmap.android.data.URIHelper;
import com.atakmap.android.gui.AlertDialogHelper;
import com.atakmap.android.gui.TileButtonDialog;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.Toast;

import com.atakmap.android.importfiles.resource.RemoteResource;
import com.atakmap.android.importfiles.task.ImportFileTask;
import com.atakmap.android.importfiles.task.ImportFilesTask;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.app.ATAKActivity;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import java.io.File;
import java.util.List;

/**
 * Controls the import manager dialog
 * 
 * 
 */
public class ImportManagerView extends BroadcastReceiver implements
        URIContentProvider.Callback {

    private static final String TAG = "ImportManagerView";

    public static final String FILENAME = "import_links.xml";
    public static final String XML_FOLDER = FileSystemUtils.TOOL_DATA_DIRECTORY
            + File.separator + "import";
    public static final String XML_FILEPATH = XML_FOLDER + File.separator
            + FILENAME;

    private static final int FILE_SELECT_CODE = 5560; //arbitrary

    private final MapView _mapView;
    private final Context _context;
    private SharedPreferences defaultPrefs;

    public ImportManagerView(MapView mapView) {
        _mapView = mapView;
        _context = mapView.getContext();
        defaultPrefs = PreferenceManager.getDefaultSharedPreferences(
                _mapView.getContext());

        DocumentedIntentFilter filter = new DocumentedIntentFilter();
        filter.addAction(ATAKActivity.ACTIVITY_FINISHED);
        AtakBroadcast.getInstance().registerReceiver(this,
                filter);
    }

    public void dispose() {
        defaultPrefs = null;
        if (_mapView != null)
            AtakBroadcast.getInstance().unregisterReceiver(
                    this);

    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Bundle extras = intent.getExtras();
        if (extras == null) {
            Log.d(TAG, "No extras");
            return;
        }

        final int requestCode = extras.getInt("requestCode");
        final int resultCode = extras.getInt("resultCode");
        final Intent data = extras.getParcelable("data");

        Log.d(TAG, "Got Activity Result: "
                + (resultCode == Activity.RESULT_OK ? "OK" : "ERROR"));
        if (requestCode != FILE_SELECT_CODE
                || resultCode != Activity.RESULT_OK) {
            Log.d(TAG, "Skipping result: " + resultCode);
            return;
        }

        if (data == null || data.getData() == null) {
            Log.w(TAG, "Skipping result, no data: " + resultCode);
            return;
        }

        Uri dataUri = data.getData();

        //use import manager...
        Log.d(TAG, "Importing: " + dataUri);
        ImportFileTask importTask = new ImportFileTask(_mapView
                .getContext(), null);
        importTask.addFlag(ImportFileTask.FlagValidateExt
                | ImportFileTask.FlagPromptOverwrite
                | ImportFileTask.FlagPromptOnMultipleMatch
                | ImportFileTask.FlagShowNotificationsDuringImport);
        importTask.execute(dataUri.toString());
    }

    void showDialog() {
        TileButtonDialog d = new TileButtonDialog(_mapView);
        d.setIcon(R.drawable.ic_menu_import_file);
        d.addButton(R.drawable.sdcard, R.string.local_sd);
        d.addButton(R.drawable.ic_gallery, R.string.gallery_title);
        d.addButton(R.drawable.ic_kml, R.string.kml_networklink);
        d.addButton(R.drawable.ic_menu_network_connections, R.string.http_url);

        final List<URIContentProvider> providers = URIContentManager
                .getInstance().getProviders("Import Manager");
        for (URIContentProvider provider : providers)
            d.addButton(provider.getIcon(), provider.getName());

        d.addButton(R.drawable.ic_android_display_settings,
                R.string.choose_app);

        d.show(R.string.select_import_type, true);
        d.setOnClickListener(new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

                // Default options
                switch (which) {
                    case 0: //Local SD
                        importLocalFile();
                        break;
                    case 1: //Gallery import
                        try {
                            ((Activity) _context).startActivityForResult(
                                    new Intent(Intent.ACTION_GET_CONTENT)
                                            .setType("image/*"),
                                    FILE_SELECT_CODE);
                        } catch (Exception e) {
                            Log.w(TAG, "Failed to create gallery intent", e);
                            Toast.makeText(_context, R.string.install_gallery,
                                    Toast.LENGTH_LONG).show();
                        }
                        break;
                    case 2: //KML NetworkLink
                        new AddEditResource(_mapView).add(
                                RemoteResource.Type.KML);
                        break;
                    case 3: //HTTP URL
                        new AddEditResource(_mapView).add(
                                RemoteResource.Type.OTHER);
                        break;
                }

                // Content providers
                if (which >= 4 && which - 4 < providers.size()) {
                    URIContentProvider provider = providers.get(which - 4);
                    provider.addContent("Import Manager", new Bundle(),
                            ImportManagerView.this);
                }

                // Choose App
                else if (which >= 4 + providers.size()) {
                    try {
                        //use ATAKActivity to get result from file picker
                        //e.g. via ES File Explorer
                        Intent agc = new Intent();
                        agc.setType("file/*");
                        agc.setAction(Intent.ACTION_GET_CONTENT);
                        agc.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,
                                true);
                        ((Activity) _mapView.getContext())
                                .startActivityForResult(
                                        agc,
                                        FILE_SELECT_CODE);
                    } catch (Exception e) {
                        Log.w(TAG,
                                "Failed to ACTION_GET_CONTENT",
                                e);
                        Toast.makeText(
                                _mapView.getContext(),
                                R.string.install_browser,
                                Toast.LENGTH_LONG)
                                .show();
                    }
                }
            }
        });
    }

    @Override
    public void onAddContent(URIContentProvider p, List<String> uris) {
        for (String uri : uris) {
            File f = URIHelper.getFile(uri);
            if (!FileSystemUtils.isFile(f))
                continue;

            // Kick off import task
            ImportFileTask importTask = new ImportFileTask(_context, null);
            importTask.addFlag(ImportFileTask.FlagImportInPlace
                    | ImportFileTask.FlagValidateExt
                    | ImportFileTask.FlagPromptOverwrite
                    | ImportFileTask.FlagPromptOnMultipleMatch
                    | ImportFileTask.FlagShowNotificationsDuringImport
                    | ImportFileTask.FlagZoomToFile);
            importTask.execute(f.getAbsolutePath());
        }
    }

    private void importLocalFile() {
        final ImportManagerFileBrowser importView = ImportManagerFileBrowser
                .inflate(_mapView);
        importView.setTitle(R.string.select_files_to_import);
        importView.setStartDirectory(
                ATAKUtilities.getStartDirectory(_mapView.getContext()));
        importView.setExtensionTypes(ImportFilesTask.getSupportedExtensions());
        AlertDialog.Builder b = new AlertDialog.Builder(_mapView.getContext());
        b.setView(importView);
        b.setNegativeButton(R.string.cancel, null);
        b.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // User has selected items and touched OK. Import the data.
                List<File> selectedFiles = importView.getSelectedFiles();

                if (selectedFiles.size() == 0) {
                    Toast.makeText(_mapView.getContext(),
                            R.string.no_import_files,
                            Toast.LENGTH_SHORT).show();
                } else {
                    // Store the currently displayed directory so we can open it
                    // again the next time this dialog is opened.
                    defaultPrefs.edit().putString("lastDirectory",
                            importView.getCurrentPath())
                            .apply();

                    // Iterate over all of the selected files and begin an import task.
                    for (File file : selectedFiles) {
                        Log.d(TAG, "Importing file: " + file.getAbsolutePath());

                        ImportFileTask importTask = new ImportFileTask(_mapView
                                .getContext(), null);
                        importTask
                                .addFlag(ImportFileTask.FlagImportInPlace
                                        | ImportFileTask.FlagValidateExt
                                        | ImportFileTask.FlagPromptOverwrite
                                        | ImportFileTask.FlagPromptOnMultipleMatch
                                        | ImportFileTask.FlagShowNotificationsDuringImport
                                        | ImportFileTask.FlagZoomToFile);
                        importTask.execute(file.getAbsolutePath());
                    }
                }
            }
        });
        final AlertDialog alert = b.create();

        // This also tells the importView to handle the back button presses
        // that the user provides to the alert dialog.
        importView.setAlertDialog(alert);

        // Show the dialog
        alert.show();

        AlertDialogHelper.adjustWidth(alert, 0.90d);
    }
}
