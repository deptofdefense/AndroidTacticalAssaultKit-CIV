
package com.atakmap.android.video;

import android.content.Context;
import android.content.Intent;

import com.atakmap.android.dropdown.DropDown.OnStateListener;
import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.dropdown.DropDownManager;
import com.atakmap.android.hierarchy.HierarchyListReceiver;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;

import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;

public class VideoBrowserDropDownReceiver extends DropDownReceiver implements
        OnStateListener {

    public static final String TAG = "VideoBrowserDropDownReceiver";

    public static final String VIDEO_TOOL = "com.atakmap.android.video.VIDEO_TOOL";

    protected Context context;

    public static final String VIDEO_DIRNAME = FileSystemUtils.TOOL_DATA_DIRECTORY
            + File.separatorChar + "videos";
    public static final String SNAPSHOT_DIRNAME = FileSystemUtils.TOOL_DATA_DIRECTORY
            + File.separatorChar + "videosnaps";

    public static final String VIDEO_DIR = FileSystemUtils.getItem(
            VIDEO_DIRNAME).getPath();
    public static final String SNAPSHOT_DIR = FileSystemUtils.getItem(
            SNAPSHOT_DIRNAME).getPath();

    public VideoBrowserDropDownReceiver(MapView mapView, Context context) {
        super(mapView);
        this.context = context;
    }

    @Override
    public void disposeImpl() {
    }

    @Override
    public void onReceive(Context c, Intent intent) {
        // only in place for the time being until DropDown shutffling is implemented.
        if (vddr != null && !vddr.isClosed()) {

            boolean top = DropDownManager.getInstance().isTopDropDown(vddr);
            if (!top) {
                getMapView().post(new Runnable() {
                    public void run() {
                        Toast.makeText(context,
                                "Tool is open  underneath the current tool.",
                                Toast.LENGTH_SHORT).show();
                    }

                });
            } else if (!vddr.isVisible()) {
                vddr.open();
            }
            return;
        }
        // end of temporary

        final String action = intent.getAction();
        if (action == null)
            return;
        ArrayList<String> paths = new ArrayList<>();
        paths.add(context.getString(R.string.video));
        AtakBroadcast.getInstance().sendBroadcast(new Intent(
                HierarchyListReceiver.MANAGE_HIERARCHY)
                        .putStringArrayListExtra("list_item_paths", paths)
                        .putExtra("isRootList", true));
    }

    private VideoDropDownReceiver vddr;

    // temporary shim to replicate existing behavior
    void setVideoDropDownReceiver(final VideoDropDownReceiver vddr) {
        this.vddr = vddr;
    }

    @Override
    public void onDropDownSelectionRemoved() {
    }

    @Override
    public void onDropDownVisible(boolean v) {

    }

    @Override
    public void onDropDownSizeChanged(double width, double height) {
    }

    @Override
    public void onDropDownClose() {
    }
}
