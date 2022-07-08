
package com.atakmap.android.maps.selector;

import android.content.Context;
import android.content.Intent;
import android.widget.BaseAdapter;

import com.atakmap.android.hierarchy.HierarchyListAdapter;
import com.atakmap.android.hierarchy.HierarchyListFilter;
import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.hierarchy.HierarchyListReceiver;
import com.atakmap.android.hierarchy.HierarchyListStateListener;
import com.atakmap.android.hierarchy.HierarchySelectHandler;
import com.atakmap.android.hierarchy.action.Search;
import com.atakmap.android.hierarchy.items.AbstractHierarchyListItem2;
import com.atakmap.android.hierarchy.items.MapItemHierarchyListItem;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.DeepMapItemQuery;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.overlay.AbstractMapOverlay2;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.log.Log;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import androidx.annotation.NonNull;
import gov.tak.api.util.Disposable;

/**
 * Overlay Manager back-end used to display a {@link MapItemList}
 */
class MapItemListOverlay extends AbstractMapOverlay2 implements Disposable {

    private static final String TAG = "MapItemList";
    static final String ID = "MapItemListOverlay";

    private final MapView _mapView;
    private final Context _context;

    private ListModel _listModel;

    MapItemListOverlay(MapView mapView) {
        _mapView = mapView;
        _context = mapView.getContext();
    }

    @Override
    public void dispose() {
        HierarchySelectHandler.unregister(getClass());
    }

    /**
     * Show the given map item list in a drop-down
     * @param list List to display
     */
    void show(@NonNull MapItemList list) {

        // OM list model for displaying the list
        _listModel = new ListModel(list);

        // Open list in OM
        ArrayList<String> paths = new ArrayList<>();
        paths.add(ID);
        AtakBroadcast.getInstance().sendBroadcast(new Intent(
                HierarchyListReceiver.MANAGE_HIERARCHY)
                        .putStringArrayListExtra("list_item_paths", paths)
                        .putExtra("hier_userselect_handler",
                                getClass().getName())
                        .putExtra("isRootList", true));
    }

    @Override
    public String getIdentifier() {
        return ID;
    }

    @Override
    public String getName() {
        return ID;
    }

    @Override
    public MapGroup getRootGroup() {
        return null;
    }

    @Override
    public DeepMapItemQuery getQueryFunction() {
        return null;
    }

    @Override
    public HierarchyListItem getListModel(BaseAdapter om, long capabilities,
            HierarchyListFilter filter) {
        final ListModel lm = _listModel;
        if (lm != null)
            lm.syncRefresh(om, filter);
        return lm;
    }

    class ListModel extends AbstractHierarchyListItem2 implements
            HierarchyListStateListener, Search {

        private final MapItemList _list;
        private final MapItemListUserSelect _selector;

        private ListModel(@NonNull MapItemList list) {
            _list = list;
            _selector = new MapItemListUserSelect(_mapView, list, this);
            HierarchySelectHandler.register(MapItemListOverlay.class,
                    _selector);
            this.asyncRefresh = true;
        }

        /**
         * Close Overlay Manager when the list is dismissed, but only if this
         * select handler and list is active
         */
        void close() {
            HierarchySelectHandler.unregister(MapItemListOverlay.class,
                    _selector);
            final ListModel curList = _listModel;
            final BaseAdapter adapter = this.listener;
            if (curList == this && adapter instanceof HierarchyListAdapter) {
                final HierarchyListAdapter om = (HierarchyListAdapter) adapter;
                if (om.getCurrentList(true) == this) {
                    Log.d(TAG, "Closing overlay manager");
                    AtakBroadcast.getInstance().sendBroadcast(new Intent(
                            HierarchyListReceiver.CLOSE_HIERARCHY));
                }
            }
        }

        @Override
        public String getTitle() {
            return _list.title;
        }

        @Override
        public String getUID() {
            return ID;
        }

        @Override
        public int getDescendantCount() {
            return 0;
        }

        @Override
        public Object getUserObject() {
            return _list;
        }

        @Override
        public boolean hideIfEmpty() {
            return true;
        }

        @Override
        protected void refreshImpl() {
            List<HierarchyListItem> items = new ArrayList<>(_list.items.size());

            for (MapItem mi : _list.items) {
                HierarchyListItem item = new MapItemHierarchyListItem(
                        _mapView, mi);
                if (this.filter.accept(item))
                    items.add(item);
            }

            sortItems(items);
            updateChildren(items);
        }

        @Override
        public boolean onOpenList(HierarchyListAdapter adapter) {
            return false;
        }

        @Override
        public boolean onCloseList(HierarchyListAdapter adapter,
                boolean forceClose) {
            _selector.cancel(_context);
            return false;
        }

        @Override
        public void onListVisible(HierarchyListAdapter adapter,
                boolean visible) {
        }

        @Override
        public boolean onBackButton(HierarchyListAdapter adapter,
                boolean deviceBack) {
            return false;
        }

        @Override
        public Set<HierarchyListItem> find(String terms) {
            Set<HierarchyListItem> ret = new HashSet<>();
            terms = terms.toLowerCase(LocaleUtil.getCurrent());
            List<HierarchyListItem> children = getChildren();
            for (HierarchyListItem item : children) {
                String title = item.getTitle();
                if (title != null && title.toLowerCase(LocaleUtil.getCurrent())
                        .contains(terms))
                    ret.add(item);
            }
            return ret;
        }
    }
}
