
package com.atakmap.android.munitions;

import android.os.Bundle;

import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.toolbar.Tool;
import com.atakmap.coremap.log.Log;

/**
 * 
 * TODO: REMOVE - Completely unnecessary use of a tool
 */
@Deprecated
public class DangerCloseTool extends Tool {

    public static final String TAG = "DangerCloseTool";

    public static final String TOOL_NAME = "danger_close";
    private static DangerCloseTool _instance;

    DangerCloseTool(MapView mapView) {
        super(mapView, TOOL_NAME);
        _instance = this;
    }

    static public DangerCloseTool getInstance() {
        return _instance;
    }

    @Override
    public void dispose() {
        _instance = null;
    }

    @Override
    protected boolean onToolBegin(Bundle extras) {
        String weaponTarget = extras.getString("target");
        String weaponName = extras.getString("name");
        String categoryName = extras.getString("category");
        String description = extras.getString("description");
        int inner = extras.getInt("innerRange");
        int outer = extras.getInt("outerRange");
        boolean remove = extras.getBoolean("remove");
        String fromLine = extras.getString("fromLine");

        MapItem mi = _mapView.getMapItem(weaponTarget);

        if (mi == null) {
            Log.d(TAG, "weapon target missing: " + weaponTarget);
            return false;
        }
        if (!(mi instanceof PointMapItem)) {
            Log.d(TAG, "weapon target not a point: " + weaponTarget);
            return false;
        }

        final PointMapItem tar = (PointMapItem) mi;

        return createDangerClose(tar, weaponName, categoryName, description,
                inner, outer, remove, fromLine) && super.onToolBegin(extras);

    }

    @Deprecated
    public boolean createDangerClose(final PointMapItem tar,
            final String weaponName,
            final String categoryName, final String description,
            int inner, int outer, boolean remove, String fromLine) {
        return DangerCloseReceiver.getInstance().createDangerClose(
                tar, weaponName, categoryName, description, inner,
                outer, remove, fromLine, true);
    }

}
