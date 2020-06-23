
package com.atakmap.android.overlay;

import android.widget.BaseAdapter;

import com.atakmap.android.hierarchy.HierarchyListFilter;
import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.hierarchy.items.MapGroupHierarchyListItem;
import com.atakmap.android.maps.DeepMapItemQuery;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

public class DefaultMapGroupOverlay extends AbstractMapOverlay2 {

    public static final String TAG = "DefaultMapGroupOverlay";

    protected final static MapGroup.MapItemsCallback HAS_DESCENDANT_ITEMS = new MapGroup.MapItemsCallback() {
        @Override
        public boolean onItemFunction(MapItem item) {
            return item.getMetaBoolean("addToObjList", true);
        }
    };

    protected final MapView mapView;
    protected final String identifier;
    protected final String name;
    protected final String iconUri;
    protected final MapGroup rootGroup;
    protected final MapGroup.MapItemsCallback filter;
    protected MapGroupHierarchyListItem currentList;

    /**
     * Create a default map group overlay with the friendly name as the name of the overlay.
     * The icon must be set as part of the group bundle with the key iconUri.
     */
    public DefaultMapGroupOverlay(MapView mapView, MapGroup group) {
        this(mapView, group.getFriendlyName(), group, group.getMetaString(
                "iconUri", null), null);
    }

    /**
     * Construct a default map group overlay with the friendly name as the name of the overlay with 
     * a given icon described by iconUri.
     */
    public DefaultMapGroupOverlay(MapView mapView, MapGroup group,
            String iconUri) {
        this(mapView, group.getFriendlyName(), group, iconUri, null);
    }

    public DefaultMapGroupOverlay(MapView mapView, MapGroup group,
            MapGroup.MapItemsCallback filter) {
        this(mapView, group.getFriendlyName(), group, group.getMetaString(
                "iconUri", null), filter);
    }

    protected DefaultMapGroupOverlay(MapView mapView, String id,
            MapGroup group, String iconUri,
            MapGroup.MapItemsCallback filter) {
        this.mapView = mapView;
        this.identifier = id;
        this.rootGroup = group;
        if (this.rootGroup != null) {
            this.name = this.rootGroup.getFriendlyName();
        } else {
            this.name = "null";
            Log.w(TAG, "Root group is null");
        }
        this.iconUri = iconUri;
        this.filter = filter;

        //if overlay has icon, but group does not, then set it
        if (!FileSystemUtils.isEmpty(this.iconUri)
                && this.rootGroup != null
                &&
                FileSystemUtils.isEmpty(this.rootGroup.getMetaString("iconUri",
                        null))) {
            this.rootGroup.setMetaString("iconUri", this.iconUri);
        }
    }

    @Override
    public String getIdentifier() {
        return this.identifier;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public MapGroup getRootGroup() {
        return this.rootGroup;
    }

    @Override
    public DeepMapItemQuery getQueryFunction() {
        return null;
    }

    @Override
    public HierarchyListItem getListModel(BaseAdapter callback, long actions,
            HierarchyListFilter filter) {
        if (!MapGroupHierarchyListItem.addToObjList(this.rootGroup))
            return null;

        if (this.currentList == null || this.currentList.isDisposed())
            this.currentList = new MapGroupHierarchyListItem(null,
                    this.mapView, this.rootGroup, this.filter, filter,
                    callback);
        else
            this.currentList.refresh(callback, filter);

        return this.currentList;
    }
}
