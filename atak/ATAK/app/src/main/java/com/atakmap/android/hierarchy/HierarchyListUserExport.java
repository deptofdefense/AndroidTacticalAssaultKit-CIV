
package com.atakmap.android.hierarchy;

import android.content.Context;
import android.content.Intent;

import com.atakmap.android.hierarchy.action.Actions;
import com.atakmap.android.hierarchy.action.Export;
import com.atakmap.android.importexport.ExportFilters;
import com.atakmap.android.importexport.ExportFilters.BoundingBoxFilter;
import com.atakmap.android.importexport.ExportMarshal;
import com.atakmap.android.importexport.Exportable;
import com.atakmap.android.importexport.FormatNotSupportedException;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.overlay.MapOverlay;
import com.atakmap.android.user.FilterMapOverlay;
import com.atakmap.android.util.NotificationUtil;
import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoBounds;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Exports all items selected by user in the Hierarchy List
 * Optionally filter to a georegion. Note, this implementation requires an
 * ExportMarshal so it does meet the constructor requirement of a 
 * HierarchyListUserSelect to be instantiated via reflection
 * 
 * 
 */
class HierarchyListUserExport extends HierarchyListUserSelect {

    public static final String TAG = "HierarchyListUserExport";
    protected ExportMarshal marshal;

    /**
     * BBox filter allows us to filter out (from the UI) map
     * overlays/groups which have no matching exports. It is also used
     * at export time filter out non-matching exports. e.g. an overlay
     * which is displayed since some of its exports match, but others
     * do not 
     */
    private BoundingBoxFilter bboxFilter;

    HierarchyListUserExport(ExportMarshal marshal, GeoBounds bbox) {
        super("Export", Actions.ACTION_EXPORT);
        this.marshal = marshal;
        if (bbox != null) {
            bboxFilter = new ExportFilters.BoundingBoxFilter(bbox);
            this.marshal.addFilter(bboxFilter);
        }
    }

    @Override
    public String getTitle() {
        return MapView.getMapView().getContext()
                .getString(R.string.export_items);
    }

    @Override
    public String getButtonText() {
        return MapView.getMapView().getContext().getString(R.string.export);
    }

    @Override
    public ButtonMode getButtonMode() {
        return ButtonMode.VISIBLE_WHEN_SELECTED;
    }

    @Override
    public boolean processUserSelections(final Context context,
            final Set<HierarchyListItem> selected) {
        AtakBroadcast.getInstance().sendBroadcast(
                new Intent(HierarchyListReceiver.CLEAR_HIERARCHY));

        if (marshal == null) {
            Log.w(TAG, "No export marshal provided");
            return false;
        }

        if (selected.size() < 1) {
            Log.w(TAG, "No exportables selected " + marshal);
            NotificationUtil.getInstance().postNotification(
                    R.drawable.ic_network_error_notification_icon,
                    NotificationUtil.RED,
                    "Export Failed",
                    "No selected items support " + marshal.getContentType()
                            + " export",
                    "No selected items support " + marshal.getContentType()
                            + " export");
            return false;
        }

        Log.d(TAG, "Exporting: " + selected.size() + " items to " +
                marshal);

        List<Exportable> exports = new ArrayList<>();
        Exportable export;
        for (HierarchyListItem mi : selected) {
            export = mi.getAction(Export.class);
            if (export != null) {
                exports.add(export);
            } else {
                Log.w(TAG, "No export action for: " + mi.getClass().getName());
            }
        }

        if (exports.size() < 1) {
            Log.w(TAG, "No exportables selected (2) " + marshal.toString());
            NotificationUtil.getInstance().postNotification(
                    R.drawable.ic_network_error_notification_icon,
                    NotificationUtil.RED,
                    "Export Failed",
                    "No selected items support " + marshal.getContentType()
                            + " export",
                    "No selected items support " + marshal.getContentType()
                            + " export");
            return false;
        }

        //initiate the export
        try {
            marshal.execute(exports);
            return true;
        } catch (IOException | FormatNotSupportedException e) {
            Log.e(TAG,
                    "Failed to initiate export of type: "
                            + marshal.getContentType(),
                    e);
            NotificationUtil.getInstance().postNotification(
                    R.drawable.ic_network_error_notification_icon,
                    NotificationUtil.RED,
                    "Export Failed",
                    marshal.getContentType() + " export failed",
                    marshal.getContentType() + " export failed");
            return false;
        }
    }

    /**
     * Delegate filtering to the ExportMarshal. This controls which items
     * are listed in the Overlay Manager UI list
     */
    @Override
    protected boolean filterOverlay(MapOverlay overlay) {
        //String overId = overlay.getIdentifier();
        //Log.d(TAG, "filterOverlay " + overId);

        if (this.marshal != null && marshal.filterOverlay(overlay))
            return true;

        if (bboxFilter == null)
            return false;

        if (marshal != null && overlay instanceof FilterMapOverlay) {
            FilterMapOverlay list = (FilterMapOverlay) overlay;
            int count = list.getDescendantCount(this.marshal.getFilters(),
                    false);
            //Log.d(TAG, "FilterMapOverlay " + overId + " count=" + count);
            return count < 1;
        }

        return false;
    }

    /**
     * Delegate filtering to the ExportMarshal
     */
    @Override
    protected boolean filterGroup(MapGroup group) {
        //String grpName = group.getFriendlyName();
        //Log.d(TAG, "filterGroup " + grpName);

        return this.marshal != null && marshal.filterGroup(group) ||
                bboxFilter != null && !bboxFilter.isInBBox(group);

    }

    /**
     * Delegate filtering to the ExportMarshal
     */
    @Override
    protected boolean filterItem(MapItem item) {
        //Log.d(TAG, "filterItem " + item.getMetaString("title", "<none>"));

        return this.marshal != null && marshal.filterItem(item) ||
                bboxFilter != null && !bboxFilter.isInBBox(item);

    }

    @Override
    protected boolean filterListItemImpl(HierarchyListItem item) {
        //Log.d(TAG, "filterListItemImpl " + item.getClass().getName());

        if (this.marshal != null)
            return marshal.filterListItemImpl(item);
        else
            return super.filterListItemImpl(item);
    }

    @Override
    public int getChildCount(HierarchyListItem item) {
        if (this.bboxFilter == null)
            return item.getChildCount();

        final Object userObject = item.getUserObject();
        if (userObject == null) {
            //Log.w(TAG, "getChildCount userObject null: " + item.getChildCount());
            return item.getChildCount();
        }

        int childCount;
        if (userObject instanceof MapItem) {
            childCount = item.getChildCount();
        } else if (userObject instanceof MapGroup) {
            MapGroup mg = (MapGroup) userObject;
            childCount = getChildCount(mg, false);
        } else if (userObject instanceof FilterMapOverlay) {
            //childCount =  ((FilterMapOverlay) userObject).filteredCount(marshal.getFilters());
            childCount = item.getChildCount();
        } else {
            //Log.w(TAG, "Unsupported user object: " + userObject.getClass().getName());
            childCount = item.getChildCount();
        }

        //Log.d(TAG, "getChildCount userObject " + userObject.getClass().getName() + ": "
        //        + item.getTitle() + " child: " + childCount);
        return childCount;
    }

    private int getChildCount(MapGroup group, boolean deep) {
        if (group == null)
            return 0;

        int count = 0;
        for (MapItem item : group.getItems()) {
            if (item != null) {
                if (this.bboxFilter.isInBBox(item))
                    count++;
            }
        }

        Collection<MapGroup> childGroups = group.getChildGroups();
        if (deep) {
            for (MapGroup childGroup : childGroups) {
                count += getChildCount(childGroup, true);
            }
        }

        count += childGroups.size();

        return count;
    }

    @Override
    public int getDescendantCount(HierarchyListItem item) {
        if (this.bboxFilter == null)
            return item.getDescendantCount();

        final Object userObject = item.getUserObject();
        if (userObject instanceof MapItem)
            return item.getDescendantCount();
        else if (userObject instanceof MapGroup) {
            MapGroup mg = (MapGroup) userObject;
            return getChildCount(mg, true);
        } else if (userObject instanceof FilterMapOverlay)
            return ((FilterMapOverlay) userObject).getDescendantCount(marshal
                    .getFilters(), true);
        else
            return item.getDescendantCount();
    }
}
