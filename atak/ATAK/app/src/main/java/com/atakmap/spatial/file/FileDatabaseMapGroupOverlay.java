
package com.atakmap.spatial.file;

import android.widget.BaseAdapter;

import com.atakmap.android.hierarchy.HierarchyListFilter;
import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.overlay.DefaultMapGroupOverlay;

public class FileDatabaseMapGroupOverlay extends DefaultMapGroupOverlay {

    private static final String TAG = "FileDatabaseMapGroupOverlay";
    private final FileDatabase database;
    private FileDatabaseMapGroupHierarchyListItem listModel;

    public FileDatabaseMapGroupOverlay(MapView mapView, MapGroup group,
            FileDatabase database) {
        super(mapView, group.getFriendlyName(), group, group.getMetaString(
                "iconUri", null), null);
        this.database = database;
    }

    @Override
    public HierarchyListItem getListModel(BaseAdapter callback, long actions,
            HierarchyListFilter filter) {
        if (!FileDatabaseMapGroupHierarchyListItem.addToObjList(this.rootGroup))
            return null;

        if (listModel == null || listModel.isDisposed())
            listModel = new FileDatabaseMapGroupHierarchyListItem(null,
                    this.mapView, this.rootGroup, this.filter, filter,
                    callback, database, true);

        return listModel;
    }
}
