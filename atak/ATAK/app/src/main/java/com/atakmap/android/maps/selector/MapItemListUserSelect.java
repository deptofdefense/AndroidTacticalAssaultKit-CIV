
package com.atakmap.android.maps.selector;

import android.content.Context;

import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.hierarchy.HierarchyListUserSelect;
import com.atakmap.android.hierarchy.items.MapItemHierarchyListItem;
import com.atakmap.android.hierarchy.items.MapItemUser;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.selector.MapItemListOverlay.ListModel;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import androidx.annotation.NonNull;

/**
 * Overlay Manager select interface for {@link MapItemList}
 */
class MapItemListUserSelect extends HierarchyListUserSelect {

    private static final String TAG = "MapItemList";

    private final Context _context;
    private final MapItemList _list;
    private final ListModel _listModel;

    private boolean _closed;

    MapItemListUserSelect(@NonNull MapView mapView, @NonNull MapItemList list,
            @NonNull ListModel listModel) {
        super(list.title, 0L);
        _context = mapView.getContext();
        _list = list;
        _listModel = listModel;
    }

    @Override
    public String getTitle() {
        String title = _list.title;
        if (title == null)
            title = _context.getString(isMultiSelect()
                    ? R.string.select_items
                    : R.string.select_item);
        return title;
    }

    @Override
    public String getButtonText() {
        String text = _list.buttonText;
        if (text == null)
            text = _context.getString(R.string.done);
        return text;
    }

    @Override
    public ButtonMode getButtonMode() {
        return ButtonMode.VISIBLE_WHEN_SELECTED;
    }

    @Override
    public boolean isMultiSelect() {
        return _list.onSelect instanceof MapItemList.OnItemsSelected;
    }

    @Override
    public boolean accept(HierarchyListItem item) {
        return item == _listModel || item instanceof MapItemHierarchyListItem;
    }

    @Override
    public boolean acceptEntry(HierarchyListItem item) {
        return item == _listModel || FileSystemUtils.isEquals(
                item.getUID(), "HierarchyListSearchResults");
    }

    @Override
    public boolean processUserSelections(Context context,
            Set<HierarchyListItem> items) {

        // Retrieve map items from the OM list entries
        List<MapItem> mapItems = new ArrayList<>(items.size());
        for (HierarchyListItem item : items) {
            if (item instanceof MapItemUser) {
                MapItem mi = ((MapItemUser) item).getMapItem();
                if (mi != null)
                    mapItems.add(mi);
            }
        }

        // No items selected - fail
        if (mapItems.isEmpty()) {
            cancel(context);
            return true;
        }

        // Invoke callback with map item(s)
        final boolean accept;
        final MapItemList.SelectCallback callback = _list.onSelect;
        if (callback instanceof MapItemList.OnItemSelected)
            accept = ((MapItemList.OnItemSelected) callback)
                    .onItemSelected(mapItems.get(0));
        else if (callback instanceof MapItemList.OnItemsSelected)
            accept = ((MapItemList.OnItemsSelected) callback)
                    .onItemsSelected(mapItems);
        else {
            accept = true;
            Log.e(TAG, "Unsupported callback: " + callback);
        }

        // The selection has been accepted - close the drop-down
        if (accept)
            close();

        return true;
    }

    @Override
    public void cancel(Context context) {
        if (!_closed) {
            Log.d(TAG, "Cancelling selector");
            final MapItemList.OnCancel callback = _list.onCancel;
            if (callback != null)
                callback.onCancel();
            close();
        }
    }

    private void close() {
        _listModel.close();
        _closed = true;
    }
}
