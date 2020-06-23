
package com.atakmap.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;

import com.atakmap.android.maps.Association;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.coremap.log.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LinkLineReceiver extends BroadcastReceiver {

    private static final String TAG = "LinkLineReceiver";
    public static final String ACTION = "com.atakmap.app.LINK_LINE";

    private final Map<String, Map<String, _Link>> _links = new HashMap<>();
    private final MapGroup _searchGroup;
    private final MapGroup _linkGroup;

    public LinkLineReceiver(MapGroup searchGroup, MapGroup linkGroup) {
        _searchGroup = searchGroup;
        _linkGroup = linkGroup;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Bundle extras = intent.getExtras();
        if (extras == null)
            return;

        String uid1 = extras.getString("firstUID");
        String uid2 = extras.getString("secondUID");

        if (uid1 != null && uid2 != null) {
            if (!_removeLink(uid1, uid2)) {
                _addLink(uid1, uid2);
            }
        }
    }

    private void _addLink(final String uid1, final String uid2) {
        final MapItem item1 = _searchGroup.deepFindItem("uid", uid1);
        final MapItem item2 = _searchGroup.deepFindItem("uid", uid2);

        if (item1 != null && item2 != null) {
            // Let the detail handler know the marker was found
            item2.setMetaBoolean("paired", true);
            Log.d(TAG, "Adding link to " + uid2 + " ("
                    + item2 + ")");
        } else
            Log.d(TAG, "Failed to pair items " + uid1 + " ("
                    + item1 + ") -> " + uid2
                    + " (" + item2 + ")");

        if (item1 instanceof PointMapItem && item2 instanceof PointMapItem) {
            final _Link link = new _Link();
            link.assoc = new Association((PointMapItem) item1,
                    (PointMapItem) item2, UUID.randomUUID().toString());

            link.assoc.setColor(Color.WHITE);
            link.assoc.setStrokeWeight(3d);
            _linkGroup.addItem(link.assoc);

            link.mapEventListener = new MapItem.OnGroupChangedListener() {
                @Override
                public void onItemAdded(MapItem item, MapGroup newParent) {
                }

                @Override
                public void onItemRemoved(MapItem item, MapGroup oldParent) {
                    _linkGroup.removeItem(link.assoc);
                    Log.d(TAG, "Removing group changed listener from "
                            + item1 + " and " + item2);
                    item1.removeOnGroupChangedListener(this);
                    item2.removeOnGroupChangedListener(this);
                    item2.setMetaBoolean("paired", false);
                }
            };
            item1.addOnGroupChangedListener(link.mapEventListener);
            item2.addOnGroupChangedListener(link.mapEventListener);
            Log.d(TAG, "Adding group changed listener to " + item1 + " and "
                    + item2);

            link.vizListener = new MapItem.OnVisibleChangedListener() {
                @Override
                public void onVisibleChanged(MapItem item) {
                    link.assoc.setVisible(
                            item1.getVisible() && item2.getVisible());
                }
            };
            item1.addOnVisibleChangedListener(link.vizListener);
            item2.addOnVisibleChangedListener(link.vizListener);

            Map<String, _Link> map = _links.get(uid1);
            if (map == null) {
                map = new HashMap<>();
                _links.put(uid1, map);
            }
            map.put(uid2, link);
        }
    }

    private boolean _removeLink(String uid1, String uid2) {
        _Link link = null;
        Map<String, _Link> map = _links.get(uid1);
        if (map != null) {
            link = map.remove(uid2);
            if (map.size() == 0) {
                _links.remove(uid2);
            }
        }

        if (link != null) {
            MapItem item1 = link.assoc.getFirstItem();
            MapItem item2 = link.assoc.getSecondItem();
            if (item1 != null && item2 != null) {
                item1.removeOnGroupChangedListener(link.mapEventListener);
                item1.removeOnVisibleChangedListener(link.vizListener);
                item2.removeOnGroupChangedListener(link.mapEventListener);
                item2.removeOnVisibleChangedListener(link.vizListener);
            }
            _linkGroup.removeItem(link.assoc);
        }

        return link != null;
    }

    private static class _Link {
        Association assoc;
        MapItem.OnGroupChangedListener mapEventListener;
        MapItem.OnVisibleChangedListener vizListener;
    }

}
