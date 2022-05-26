
package com.atakmap.android.data;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;

import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.Switch;
import android.widget.Toast;

import com.atakmap.android.hierarchy.HierarchyListReceiver;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;
import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.map.opengl.GLMapSurface;
import com.atakmap.map.opengl.GLMapView;

import java.io.File;

/**
 * Support processing data mgmt requests, currently just Zeroize
 * 
 * 
 */
public class DataMgmtReceiver extends BroadcastReceiver {
    static public final String TAG = "DataMgmtReceiver";

    /**
     * intent action to initiate zeroize
     */
    final public static String CLEAR_CONTENT_ACTION = "com.atakmap.app.CLEAR_CONTENT";

    /**
     * intent action to process zeroize - see ClearContentRegistry
     * @deprecated
     */
    @DeprecatedApi(since = "4.5", removeAt = "4.8", forRemoval = true)
    final public static String ZEROIZE_CONFIRMED_ACTION = "com.atakmap.app.ZEROIZE_CONFIRMED";

    @DeprecatedApi(since = "4.5", removeAt = "4.8", forRemoval = true)
    final public static String ZEROIZE_CLEAR_MAPS = "com.atakmap.app.ZEROIZE_CLEAR_MAPS";

    private MapView _mapView;

    public DataMgmtReceiver(MapView mapView) {
        _mapView = mapView;

    }

    public void dispose() {
        _mapView = null;

    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (CLEAR_CONTENT_ACTION.equals(intent.getAction())) {
            confirmClearContent();
        }
    }

    private void confirmClearContent() {
        Log.d(TAG, "Confirming Clear Content");

        // build dialog with zeroize sequence view
        LayoutInflater inflater = LayoutInflater.from(_mapView.getContext());
        View view = inflater.inflate(R.layout.clear_content, null);
        final CheckBox clearMaps = view
                .findViewById(R.id.zeroizeClearMaps);
        clearMaps.setChecked(false);
        final Switch lock1 = view.findViewById(R.id.zeroizeSwitch1);
        final Switch lock2 = view.findViewById(R.id.zeroizeSwitch2);
        final Button zeroizeBtn = view
                .findViewById(R.id.zeroizeButton);
        zeroizeBtn.setVisibility(Button.GONE);

        AlertDialog.Builder adb = new AlertDialog.Builder(_mapView.getContext())
                .setIcon(R.drawable.nav_delete)
                .setTitle(R.string.clear_content)
                .setPositiveButton(R.string.select_items,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int whichButton) {
                                dialog.dismiss();
                                AtakBroadcast
                                        .getInstance()
                                        .sendBroadcast(
                                                new Intent(
                                                        HierarchyListReceiver.MANAGE_HIERARCHY)
                                                                .putExtra(
                                                                        "hier_mode_string",
                                                                        "1")
                                                                .putExtra(
                                                                        "hier_userselect_handler",
                                                                        "com.atakmap.android.hierarchy.HierarchyListUserDelete"));
                            }
                        })
                .setNegativeButton(R.string.cancel, null);
        final AlertDialog ad = adb.create();

        // make zeroize button visible once both switches are flipped
        OnCheckedChangeListener listener = new OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(CompoundButton buttonView,
                    boolean isChecked) {
                if (lock1.isChecked() && lock2.isChecked()) {
                    zeroizeBtn.setVisibility(Button.VISIBLE);
                } else {
                    zeroizeBtn.setVisibility(Button.GONE);
                }
            }
        };
        lock1.setOnCheckedChangeListener(listener);
        lock2.setOnCheckedChangeListener(listener);

        // on button press confirm switches and then zeroize
        zeroizeBtn.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                if (lock1.isChecked() && lock2.isChecked()) {
                    Log.d(TAG,
                            "Initiating clear content (ZEROIZE) sequence...");
                    ad.dismiss();

                    // stop rendering all data
                    final GLMapSurface glSurface = _mapView.getGLSurface();
                    glSurface.onPause();
                    final GLMapView glMapView = glSurface.getGLMapView();
                    glMapView.dispose();

                    // TODO support clearing UI data w/out having to restarting ATAK                    
                    // kick off background task to perform cleanup, and then shutdown ATAK
                    new ClearContentTask(_mapView.getContext(), clearMaps
                            .isChecked(), true).execute();
                } else {
                    Log.d(TAG, "both switches not flipped to zeroize");
                    Toast.makeText(_mapView.getContext(),
                            R.string.zeroize_flip_both_switches_to_clear_cache,
                            Toast.LENGTH_SHORT)
                            .show();
                }
            }
        });

        // now show the dialog
        ad.setView(view);
        ad.show();
    }

    public static void deleteFiles(String[] files) {
        if (files == null || files.length < 1)
            return;

        final String[] mountPoints = FileSystemUtils.findMountPoints();
        for (String file : files) {
            for (String mountPoint : mountPoints) {
                File toDelete = new File(mountPoint, file);
                if (IOProviderFactory.exists(toDelete)
                        && !IOProviderFactory.isDirectory(toDelete))
                    FileSystemUtils.deleteFile(toDelete);
            }
        }
    }

    public static void deleteDirs(String[] dirs, boolean bContentOnly) {
        if (dirs == null || dirs.length < 1)
            return;

        final String[] mountPoints = FileSystemUtils.findMountPoints();
        for (String dir : dirs) {
            for (String mountPoint : mountPoints) {
                File toDelete = new File(mountPoint, dir);
                if (IOProviderFactory.exists(toDelete)
                        && IOProviderFactory.isDirectory(toDelete))
                    FileSystemUtils.deleteDirectory(toDelete, bContentOnly);
            }
        }
    }
}
