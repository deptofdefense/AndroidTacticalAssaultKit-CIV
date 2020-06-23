
package com.atakmap.android.maps;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class MapGroupDeleteReceiver extends BroadcastReceiver {

    private final MapGroup root;

    public MapGroupDeleteReceiver(MapView mapView) {
        this.root = mapView.getRootGroup();
    }

    @Override
    public void onReceive(Context arg0, Intent arg1) {
        if (!arg1.hasExtra("serialId"))
            return;
        final long serialId = arg1.getLongExtra("serialId", -1L);
        MapGroup toRemove = MapGroup.deepFindGroupBySerialIdBreadthFirst(
                this.root, serialId);
        if (toRemove == null)
            return;
        toRemove.getParentGroup().removeGroup(toRemove);
    }
}
