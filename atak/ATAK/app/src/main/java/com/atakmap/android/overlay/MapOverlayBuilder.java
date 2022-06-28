
package com.atakmap.android.overlay;

import android.widget.BaseAdapter;

import com.atakmap.android.hierarchy.HierarchyListFilter;
import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.hierarchy.HierarchyListItem2;
import com.atakmap.android.maps.DeepMapItemQuery;
import com.atakmap.android.maps.MapGroup;

import java.util.HashMap;
import java.util.Map;

/**
 * Convenience mechanism for constructing unspecialized {@link MapOverlay2} instances.
 */
public final class MapOverlayBuilder {
    private String _identifier;
    private String _name;
    private MapGroup _rootGroup;
    private DeepMapItemQuery _query;
    private Map<Long, HierarchyListItem> _item = new HashMap<>();
    private MapOverlay2 _impl = new Impl();

    public MapOverlayBuilder() {
    }

    public MapOverlayBuilder setIdentifier(String identifier) {
        _identifier = identifier;
        return this;
    }

    public MapOverlayBuilder setName(String name) {
        _name = name;
        return this;
    }

    public MapOverlayBuilder setRootGroup(MapGroup group) {
        _rootGroup = group;
        return this;
    }

    public MapOverlayBuilder setQueryFunction(DeepMapItemQuery query) {
        _query = query;
        return this;
    }

    public MapOverlayBuilder setListItem(HierarchyListItem item) {
        return setListItem(-1L, item);
    }

    public MapOverlayBuilder setListItem(long filter, HierarchyListItem item) {
        _item.put(filter, item);
        return this;
    }

    public MapOverlay2 build() {
        return _impl;
    }

    private class Impl implements MapOverlay2 {

        @Override
        public String getIdentifier() {
            return _identifier;
        }

        @Override
        public String getName() {
            return _name;
        }

        @Override
        public MapGroup getRootGroup() {
            return _rootGroup;
        }

        @Override
        public DeepMapItemQuery getQueryFunction() {
            return _query;
        }

        @Override
        public HierarchyListItem getListModel(BaseAdapter adapter,
                long capabilities, HierarchyListItem.Sort preferredSort) {
            final HierarchyListItem item = getItem(capabilities);
            if (item == null)
                return null;
            if (preferredSort != null)
                item.refresh(preferredSort);
            return item;
        }

        @Override
        public HierarchyListItem getListModel(BaseAdapter adapter,
                long capabilities, HierarchyListFilter preferredFilter) {
            final HierarchyListItem item = getItem(capabilities);
            if (item == null)
                return null;
            if (preferredFilter != null && item instanceof HierarchyListItem2)
                ((HierarchyListItem2) item).refresh(preferredFilter);
            return item;
        }

        private HierarchyListItem getItem(long capabilities) {
            for (Map.Entry<Long, HierarchyListItem> entry : _item.entrySet()) {
                if ((entry.getKey() & capabilities) != 0)
                    return entry.getValue();
            }
            return null;
        }
    }
}
