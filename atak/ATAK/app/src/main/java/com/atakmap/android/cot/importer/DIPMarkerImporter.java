
package com.atakmap.android.cot.importer;

import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.app.R;

import java.util.Arrays;
import java.util.HashSet;

/**
 * Importer for Jumpmaster DIPs
 * Note: Although Jumpmaster is a plugin, users without the plugin loaded
 * should still be able to see DIPs on the map, so the importer is in core
 */
public class DIPMarkerImporter extends MarkerImporter {

    private final MapGroup _altGroup;

    public DIPMarkerImporter(MapView mapView) {
        super(mapView, "DIPs", new HashSet<>(Arrays.asList("b-m-p-j", "j-m")),
                true);
        _altGroup = _group.findMapGroup(_context.getString(
                R.string.alternate_dips));
    }

    @Override
    protected void addToGroup(MapItem item) {
        //if it is a simple DIP ignore it
        if (item.getType().contentEquals("b-m-p-j-basic"))
            return;
        if (item.getType().endsWith("-alt"))
            super.addToGroup(item, _altGroup);
        else {
            if (item instanceof Marker && item.getGroup() == null)
                ((Marker) item).setAlwaysShowText(true);
            super.addToGroup(item);
        }
    }

    @Override
    protected int getNotificationIcon(MapItem item) {
        if (item.getType().endsWith("-alt"))
            return R.drawable.alt_dip;
        return R.drawable.dip;
    }
}
