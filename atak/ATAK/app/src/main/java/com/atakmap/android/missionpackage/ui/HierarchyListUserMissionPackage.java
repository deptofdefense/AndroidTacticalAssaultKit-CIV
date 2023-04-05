
package com.atakmap.android.missionpackage.ui;

import android.content.Context;
import android.content.Intent;

import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.hierarchy.HierarchyListReceiver;
import com.atakmap.android.hierarchy.HierarchyListUserSelect;
import com.atakmap.android.hierarchy.action.Actions;
import com.atakmap.android.hierarchy.action.Export;
import com.atakmap.android.importexport.Exportable;
import com.atakmap.android.importexport.FormatNotSupportedException;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.missionpackage.export.MissionPackageExportMarshal;
import com.atakmap.android.missionpackage.export.MissionPackageExportWrapper;
import com.atakmap.android.overlay.MapOverlay;
import com.atakmap.android.util.NotificationUtil;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Gathers all items selected by user in the Hierarchy List so they may be
 * included in a Mission Package File Transfer. This UI is used directly by the
 * MPT dropdown. Also see <code>MissionPackageExportMarshal</code>
 * 
 * 
 */
public class HierarchyListUserMissionPackage extends HierarchyListUserSelect {

    private static final String TAG = "HierarchyListUserMissionPackage";

    /**
     * Delegate filtering to the export marshal implementation for consistency
     */
    private final MissionPackageExportMarshal marshal;

    public HierarchyListUserMissionPackage() {
        super(TAG, Actions.ACTION_EXPORT);
        marshal = new MissionPackageExportMarshal(MapView.getMapView()
                .getContext());
        marshal.setMissionPackageUID(_tag);
    }

    @Override
    public void setTag(String tag) {
        super.setTag(tag);
        marshal.setMissionPackageUID(_tag);
    }

    @Override
    public String getTitle() {
        return MapView.getMapView().getContext()
                .getString(R.string.select_items);
    }

    @Override
    public String getButtonText() {
        return MapView.getMapView().getContext().getString(R.string.done);
    }

    @Override
    public ButtonMode getButtonMode() {
        return ButtonMode.ALWAYS_VISIBLE;
    }

    @Override
    public boolean processUserSelections(final Context context,
            final Set<HierarchyListItem> selected) {
        Log.d(TAG, "processUserSelections count: " + selected.size());

        if (selected.size() < 1) {
            Log.w(TAG, "No exportables selected " + marshal.toString());
            NotificationUtil.getInstance().postNotification(
                    R.drawable.ic_network_error_notification_icon,
                    NotificationUtil.RED,
                    context.getString(R.string.mission_package_update_failed),
                    context.getString(
                            R.string.mission_package_no_items_support_export,
                            marshal.getContentType()),
                    context.getString(
                            R.string.mission_package_no_items_support_export,
                            marshal.getContentType()));
            finish();
            return false;
        }

        Log.d(TAG, "Exporting: " + selected.size() + " items to " +
                marshal.toString());

        // Resolve MP exports now while OM filter is still active
        List<Exportable> exports = new ArrayList<>();
        Exportable export;
        for (HierarchyListItem mi : selected) {
            export = mi.getAction(Export.class);
            if (export != null && export.isSupported(
                    MissionPackageExportWrapper.class)) {
                try {
                    // Need to put in a new wrapper to satisfy the export marshal
                    export = (Exportable) export.toObjectOf(
                            MissionPackageExportWrapper.class,
                            marshal.getFilters());
                } catch (Exception e) {
                    export = null;
                }
                if (export != null)
                    exports.add(export);
            } else {
                Log.w(TAG, "No export action for: " + mi.getClass().getName());
            }
        }

        if (FileSystemUtils.isEmpty(exports)) {
            Log.w(TAG, "No exportables selected (2) " + marshal);
            NotificationUtil.getInstance().postNotification(
                    R.drawable.ic_network_error_notification_icon,
                    NotificationUtil.RED,
                    context.getString(R.string.mission_package_update_failed),
                    context.getString(
                            R.string.mission_package_no_items_support_export,
                            marshal.getContentType()),
                    context.getString(
                            R.string.mission_package_no_items_support_export,
                            marshal.getContentType()));
            finish();
            return false;
        }

        //initiate the export
        try {
            marshal.execute(exports);
            finish();
            return true;
        } catch (IOException | FormatNotSupportedException e) {
            Log.e(TAG,
                    "Failed to initiate export of type: "
                            + marshal.getContentType(),
                    e);
            NotificationUtil.getInstance().postNotification(
                    R.drawable.ic_network_error_notification_icon,
                    NotificationUtil.RED,
                    context.getString(R.string.export_failed),
                    context.getString(R.string.mission_package_export_failed,
                            marshal.getContentType()),
                    context.getString(R.string.mission_package_export_failed,
                            marshal.getContentType()));
        }
        finish();
        return false;
    }

    private void finish() {
        AtakBroadcast.getInstance().sendBroadcast(new Intent(
                HierarchyListReceiver.CLEAR_HIERARCHY));
        MissionPackageMapOverlay.navigateTo(_tag);
    }

    @Override
    protected boolean filterOverlay(MapOverlay overlay) {
        if (this.marshal != null)
            return this.marshal.filterOverlay(overlay);

        Log.w(TAG, "Unable to filter overlay");
        return true;
    }

    @Override
    protected boolean filterGroup(MapGroup group) {
        if (this.marshal != null)
            return this.marshal.filterGroup(group);

        Log.w(TAG, "Unable to filter group");
        return true;
    }

    @Override
    protected boolean filterItem(MapItem item) {
        if (this.marshal != null)
            return this.marshal.filterItem(item);

        Log.w(TAG, "Unable to filter item");
        return true;
    }

    @Override
    protected boolean filterListItemImpl(HierarchyListItem item) {
        if (this.marshal != null)
            return this.marshal.filterListItemImpl(item);

        Log.w(TAG, "Unable to filter item");
        return true;
    }

    @Override
    public boolean acceptEntry(HierarchyListItem list) {
        if (this.marshal != null)
            return this.marshal.acceptEntry(list);

        Log.w(TAG, "Unable to filter list entry");
        return true;
    }
}
