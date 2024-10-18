
package com.atakmap.android.hierarchy;

import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import com.atakmap.android.grg.GRGMapOverlay;
import com.atakmap.android.grg.ImageOverlay;
import com.atakmap.android.hierarchy.action.Export;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.missionpackage.export.MissionPackageExportWrapper;
import com.atakmap.android.missionpackage.ui.MissionPackageHierarchyListItem;
import com.atakmap.android.missionpackage.ui.MissionPackageListGroup;
import com.atakmap.android.missionpackage.ui.MissionPackageMapOverlay;
import com.atakmap.android.overlay.MapOverlay;
import com.atakmap.android.user.FilterMapOverlay;
import com.atakmap.android.util.AttachmentManager;
import com.atakmap.android.util.NotificationUtil;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.spatial.file.FileDatabaseMapGroupHierarchyListItem;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Allows user to select file(s). Upon completion, Sends intent FILE_SELECTED
 * Includes extras:
 *  provided "tag"
 *  filepaths
 *  file path(s)
 *
 *  Support single or multi-file select through use of intent, e.g.
 *
 *  Intent i = new Intent("com.atakmap.android.maps.MANAGE_HIERARCHY");
 *  i.putExtra("hier_userselect_handler", "com.atakmap.android.hierarchy.HierarchyListUserFileSelect");
 *  i.putExtra("hier_usertag", _contact.getId().toString());
 *  i.putExtra("hier_multiselect", false);
 *  AtakBroadcast.getInstance().sendBroadcast(i);
 */
public class HierarchyListUserFileSelect extends HierarchyListUserSelect {

    final static String TAG = "HierarchyListUserFileSelect";
    final static public String FILE_SELECTED = "com.atakmap.android.hierarchy.FILE_SELECTED";

    public HierarchyListUserFileSelect() {
        super(TAG, 0L);
    }

    @Override
    public String getTitle() {
        return MapView.getMapView().getContext()
                .getString(R.string.select_file);
    }

    @Override
    public String getButtonText() {
        return MapView.getMapView().getContext().getString(R.string.done);
    }

    @Override
    public ButtonMode getButtonMode() {
        return ButtonMode.VISIBLE_WHEN_SELECTED;
    }

    @Override
    public boolean processUserSelections(Context context,
            Set<HierarchyListItem> selected) {
        Log.d(TAG, "processUserSelections count: " + selected.size());

        if (FileSystemUtils.isEmpty(_tag)) {
            Log.w(TAG, "No action tag available");
            Toast.makeText(context, "Failed to process selections",
                    Toast.LENGTH_SHORT).show();
            finish();
            return false;
        }

        if (selected.size() < 1) {
            Log.w(TAG, "No files selected " + _tag);
            NotificationUtil.getInstance().postNotification(
                    R.drawable.ic_network_error_notification_icon,
                    NotificationUtil.RED,
                    "Export Failed",
                    "No selected items are supported files",
                    "No selected items are supported files");
            finish();
            return false;
        }

        Log.d(TAG, "Processing: " + selected.size() + " items for " + _tag);

        //pull out "file" path(s)
        ArrayList<String> filepaths = new ArrayList<>();
        for (HierarchyListItem item : selected) {
            Export ex;
            Object userObj;
            if (item instanceof GRGMapOverlay.GRGMapOverlayListItem
                    && item.getUserObject() instanceof ImageOverlay) {
                String file = ((ImageOverlay) item.getUserObject())
                        .getMetaString("file", null);
                if (!FileSystemUtils.isEmpty(file)) {
                    filepaths.add(file);
                } else {
                    Log.w(TAG,
                            "Failed to get filepath for GRG: "
                                    + item);
                }
            } else if ((ex = item.getAction(Export.class)) != null) {
                // Utilize mission package export wrapper for finding items
                try {
                    MissionPackageExportWrapper exWrapper = (MissionPackageExportWrapper) ex
                            .toObjectOf(
                                    MissionPackageExportWrapper.class, null);
                    if (exWrapper != null) {
                        for (String p : exWrapper.getFilepaths()) {
                            if (!FileSystemUtils.isEmpty(p))
                                filepaths.add(p);
                        }
                        for (String uid : exWrapper.getUIDs()) {
                            List<File> attachments = AttachmentManager
                                    .getAttachments(uid);

                            for (File attachment : attachments) {
                                if (FileSystemUtils.isFile(attachment)) {
                                    filepaths.add(attachment
                                            .getAbsolutePath());
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Failed to export: " + item, e);
                }
            } else if ((userObj = item.getUserObject()) != null
                    && userObj instanceof MissionPackageListGroup) {
                // Publish content from existing mission package
                MissionPackageListGroup manifest = (MissionPackageListGroup) userObj;
                filepaths.add(manifest.getManifest().getPath());
            }
        }

        if (filepaths.size() < 1) {
            Log.w(TAG, "No files selected (2) " + _tag);
            NotificationUtil.getInstance().postNotification(
                    R.drawable.ic_network_error_notification_icon,
                    NotificationUtil.RED,
                    "Export Failed",
                    "No selected items are supported files",
                    "No selected items are supported files");
            finish();
            return false;
        }

        finish();
        AtakBroadcast.getInstance().sendBroadcast(new Intent(FILE_SELECTED)
                .putExtra("tag", _tag)
                .putExtra("filepath", filepaths.get(0))
                .putExtra("filepaths", filepaths));
        return true;
    }

    private void finish() {
        AtakBroadcast.getInstance().sendBroadcast(
                new Intent(HierarchyListReceiver.CLOSE_HIERARCHY));
    }

    @Override
    public boolean acceptEntry(HierarchyListItem item) {
        Log.d(TAG, "acceptEntry: " + item.getClass() + ", " + item.getTitle());
        //        if(item instanceof FeatureSetHierarchyListItem)
        //            return false;

        if (item instanceof MissionPackageHierarchyListItem)
            return false;

        if (item instanceof FileDatabaseMapGroupHierarchyListItem) {
            FileDatabaseMapGroupHierarchyListItem fi = (FileDatabaseMapGroupHierarchyListItem) item;
            if (fi.getUserObject() != null
                    && fi.getUserObject() instanceof MapGroup) {
                MapGroup mg = (MapGroup) fi.getUserObject();
                //dont drill in past files, but parent is same ListItem class
                return FileSystemUtils
                        .isEmpty(mg.getMetaString("FILEPATH", null));
            }
        }

        return true;
    }

    @Override
    protected boolean filterListItemImpl(HierarchyListItem item) {
        //Log.w(TAG, "filterListItemImpl " + item.toString());
        return false;
    }

    @Override
    protected boolean filterOverlay(MapOverlay overlay) {
        String id = overlay.getIdentifier();
        if (FileSystemUtils.isEmpty(id))
            return true;
        if (!(overlay instanceof FilterMapOverlay)) {
            // Allow the following non-filter overlays
            return !(overlay instanceof MissionPackageMapOverlay)
                    && !(overlay instanceof GRGMapOverlay)
                    && !id.equals("fileoverlays");
        }

        return true;
    }
}
