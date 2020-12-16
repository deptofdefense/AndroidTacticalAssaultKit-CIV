
package com.atakmap.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;

public class LinkLineReceiver extends BroadcastReceiver {

    private static final String TAG = "LinkLineReceiver";
    public static final String ACTION = "com.atakmap.app.LINK_LINE";

    private final MapGroup _searchGroup;
    private final MapGroup _linkGroup;
    private final MapView mapView;
    private final LinkLineHandler linkLineHandler;

    /**
     * Generates a Broadcast receiver that generates a link between a first and a second UID.
     * @param mapView the mapView to register / unregister item listeners for cases where there
     *                are deferred links arriving.
     * @param searchGroup the group to search for items to be linked to.
     * @param linkGroup the group which contains the associations/links.
     */
    public LinkLineReceiver(final MapView mapView, final MapGroup searchGroup,
            final MapGroup linkGroup) {
        _searchGroup = searchGroup;
        _linkGroup = linkGroup;
        this.mapView = mapView;
        linkLineHandler = new LinkLineHandler(
                this.mapView.getMapEventDispatcher(), _linkGroup);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Bundle extras = intent.getExtras();
        if (extras == null)
            return;

        String parentUid = extras.getString("firstUID");
        String childUid = extras.getString("secondUID");

        //MapItem parent = this.mapView.getRootGroup().deepFindUID(uid1);
        MapItem parentItem = null;
        MapItem childItem = null;
        if (parentUid != null) {
            parentItem = _searchGroup.deepFindItem("uid", parentUid);
        }
        if (childUid != null) {
            childItem = _searchGroup.deepFindItem("uid", childUid);
        }
        linkLineHandler.processLink(parentUid, parentItem, childUid, childItem);
    }

}
