
package com.atakmap.android.bloodhound.link;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import com.atakmap.android.ipc.DocumentedExtra;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.menu.MapMenuEventListener;
import com.atakmap.android.menu.MapMenuReceiver;
import com.atakmap.android.toolbars.RangeAndBearingMapItem;
import com.atakmap.coremap.filesystem.FileSystemUtils;

/**
 * Receiver for bloodhound link intents
 * NOTE: This class is unrelated to the bloodhound tool - this receiver
 * handles intents for the R&B bloodhound radial option
 * Consider merging functionality at some point
 */
public class BloodHoundLinkReceiver extends BroadcastReceiver implements
        MapMenuEventListener {

    public static final String TOGGLE_LINK = "com.atakmap.android.bloodhound.link.TOGGLE_LINK";

    private final MapView _mapView;
    private final BloodHoundLinkManager _manager;

    public BloodHoundLinkReceiver(MapView mv, BloodHoundLinkManager manager) {
        _mapView = mv;
        _manager = manager;

        DocumentedIntentFilter f = new DocumentedIntentFilter();
        f.addAction(TOGGLE_LINK, "Toggle bloodhound link for a R&B line",
                new DocumentedExtra[] {
                        new DocumentedExtra("uid",
                                "R&B line UID",
                                false, String.class),
                        new DocumentedExtra("toggle",
                                "True to toggle on, false to toggle off",
                                true, Boolean.class)
                });
        f.addAction(MapMenuReceiver.SHOW_MENU,
                "Listen for radial menu events so we can update the state");
        AtakBroadcast.getInstance().registerReceiver(this, f);
        MapMenuReceiver.getInstance().addEventListener(this);
    }

    public void dispose() {
        MapMenuReceiver.getInstance().removeEventListener(this);
        AtakBroadcast.getInstance().unregisterReceiver(this);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null)
            return;

        if (action.equals(TOGGLE_LINK)) {
            String uid = intent.getStringExtra("uid");
            if (FileSystemUtils.isEmpty(uid))
                return;

            boolean on = _manager.hasLink(uid);
            boolean toggleOn = intent.getBooleanExtra("toggle", !on);

            MapItem mi = _mapView.getRootGroup().deepFindUID(uid);
            if (toggleOn && mi instanceof RangeAndBearingMapItem)
                _manager.addLink((RangeAndBearingMapItem) mi);
            else
                _manager.removeLink(uid);
        }
    }

    @Override
    public boolean onShowMenu(MapItem item) {
        if (item instanceof RangeAndBearingMapItem)
            item.toggleMetaData("enable_hounding", _manager.canLink(item));
        return false;
    }

    @Override
    public void onHideMenu(MapItem item) {
    }
}
