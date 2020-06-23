
package com.atakmap.android.geofence.ui;

import android.content.Context;
import android.content.Intent;

import com.atakmap.android.geofence.component.GeoFenceReceiver;
import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.hierarchy.HierarchyListReceiver;
import com.atakmap.android.hierarchy.HierarchyListUserSelect;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.overlay.MapOverlay;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class HierarchyListUserGeoFence extends HierarchyListUserSelect {

    private static final String TAG = "HierarchyListUserGeoFence";

    public HierarchyListUserGeoFence() {
        super(TAG, 0L);
    }

    @Override
    public String getTitle() {
        return MapView.getMapView().getContext()
                .getString(R.string.select_items_to_monitor);
    }

    @Override
    public String getButtonText() {
        return MapView.getMapView().getContext().getString(R.string.ok);
    }

    @Override
    public ButtonMode getButtonMode() {
        return ButtonMode.VISIBLE_WHEN_SELECTED;
    }

    @Override
    public boolean processUserSelections(Context context,
            Set<HierarchyListItem> selected) {

        AtakBroadcast.getInstance().sendBroadcast(
                new Intent(HierarchyListReceiver.CLOSE_HIERARCHY));

        final ArrayList<String> uids = getMapItemUIDs(selected);

        //send the list back over to GeoFenceManager
        AtakBroadcast.getInstance().sendBroadcast(
                new Intent(GeoFenceReceiver.ITEMS_SELECTED)
                        .putExtra("monitorUid", _tag)
                        .putStringArrayListExtra("uids", uids));

        return false;
    }

    private ArrayList<String> getMapItemUIDs(
            Collection<HierarchyListItem> selected) {
        final ArrayList<String> toReturn = new ArrayList<>();

        //TODO any optimizations in here? e.g. omit group/overlay based on user selected MonitoredTypes?
        for (HierarchyListItem item : selected) {
            if (item == null) {
                Log.w(TAG, "Skipping empty item");
                continue;
            }

            //            Log.d(TAG, "Menu Item title: " + item.getTitle() + " of type: "
            //                    + item.getClass().getName());

            final Object userObject = item.getUserObject();
            if (userObject instanceof MapGroup) {
                final MapGroup group = (MapGroup) userObject;

                // process all items of the map group
                processMapGroup(group, toReturn);

                // process any supported sub map groups
                Collection<MapGroup> childGroups = group.getChildGroups();
                if (childGroups == null || childGroups.size() < 1)
                    continue;

                for (MapGroup childGroup : childGroups) {
                    if (childGroup == null) {
                        Log.w(TAG, "Unable to find map child group");
                        continue;
                    }

                    if (filterGroup(childGroup))
                        continue;

                    Log.d(TAG,
                            "Processing child group: "
                                    + childGroup.getFriendlyName());
                    processMapGroup(childGroup, toReturn);
                }

            } else if (userObject instanceof MapItem) {
                checkAndAdd(toReturn, ((MapItem) userObject).getUID());
            } else {
                // Pass overlay ID, and go ahead and grab each MapItem in the overlay
                List<HierarchyListItem> child = new ArrayList<>(
                        1);
                for (int i = 0; i < item.getChildCount(); i++) {
                    child.add(item.getChildAt(i));
                    //recurse
                    toReturn.addAll(this.getMapItemUIDs(child));
                    child.clear();
                }
            }
        } // end _MenuItem loop

        return toReturn;
    }

    private void checkAndAdd(List<String> toReturn, String uid) {
        if (FileSystemUtils.isEmpty(uid)) {
            Log.w(TAG, "Cannot add empty UID");
            return;
        }
        //Not in this case, this.mapItemUIDs refers to the list of map items that were found inside
        //(or outside) a geofence when monitoring began

        //if not list set, then just add UID
        if (FileSystemUtils.isEmpty(this.mapItemUIDs)) {
            //Log.d(TAG, "Adding UID (not list set)" + uid);
            toReturn.add(uid);
        } else {
            //list is set, see if this UID is included
            if (this.mapItemUIDs.contains(uid)) {
                //Log.d(TAG, "Adding UID (is in set)" + uid);
                toReturn.add(uid);
            }
        }
    }

    private void processMapGroup(final MapGroup group,
            final List<String> toCheck) {
        if (group == null)
            return;

        // special cases to avoid?
        group.forEachItem(new MapGroup.MapItemsCallback() {

            @Override
            public boolean onItemFunction(MapItem item) {
                if (item == null) {
                    Log.w(TAG, "Unable to find map item...");
                    return false;
                }

                checkAndAdd(toCheck, item.getUID());
                return false;
            }
        });
    }

    @Override
    protected boolean filterOverlay(MapOverlay overlay) {
        if (overlay.getIdentifier().equals("otheroverlays"))
            return true;
        return false;
    }
}
