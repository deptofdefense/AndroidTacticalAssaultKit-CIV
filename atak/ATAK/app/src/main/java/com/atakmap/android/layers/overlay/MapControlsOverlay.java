
package com.atakmap.android.layers.overlay;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.widget.BaseAdapter;

import com.atakmap.android.hierarchy.HierarchyListFilter;
import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.hierarchy.items.AbstractHierarchyListItem2;
import com.atakmap.android.maps.DeepMapItemQuery;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.overlay.AbstractMapOverlay2;
import com.atakmap.app.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Overlay parent for map controls such as changing transparency of base imagery
 */
public class MapControlsOverlay extends AbstractMapOverlay2 {

    private final MapView _mapView;
    private final Context _context;
    private final ListModel _listModel;
    private final List<HierarchyListItem> _items = new ArrayList<>();

    public MapControlsOverlay(MapView mapView) {
        _mapView = mapView;
        _context = mapView.getContext();

        _listModel = new ListModel();

        // Items list
        _items.add(new ImageryTransparencyListItem(_mapView));
        _items.add(new LollipopListItem(_mapView));
        _items.add(new LegacyAltitudeRenderingListItem(_mapView));
        _items.add(new IlluminationListItem(_mapView));

        _mapView.getMapOverlayManager().addOverlay(this);
    }

    public void dispose() {
        _mapView.getMapOverlayManager().removeOverlay(this);
    }

    @Override
    public String getIdentifier() {
        return "mapControls";
    }

    @Override
    public String getName() {
        return _context.getString(R.string.map_controls);
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
    public HierarchyListItem getListModel(BaseAdapter listener, long actions,
            HierarchyListFilter filter) {
        _listModel.syncRefresh(listener, filter);
        return _listModel;
    }

    private class ListModel extends AbstractHierarchyListItem2 {

        ListModel() {
            this.asyncRefresh = true;
            this.reusable = true;
        }

        @Override
        public String getTitle() {
            return getName();
        }

        @Override
        public Drawable getIconDrawable() {
            return _context.getDrawable(R.drawable.ic_menu_maps);
        }

        @Override
        public int getDescendantCount() {
            return getChildCount();
        }

        @Override
        public Object getUserObject() {
            return this;
        }

        @Override
        public int getPreferredListIndex() {
            return 98;
        }

        @Override
        public boolean isMultiSelectSupported() {
            return false;
        }

        @Override
        protected void refreshImpl() {
            List<HierarchyListItem> filtered = new ArrayList<>();

            for (HierarchyListItem item : _items) {
                if (filter.accept(item))
                    filtered.add(item);
            }

            sortItems(filtered);
            updateChildren(filtered);
        }

        @Override
        public boolean hideIfEmpty() {
            return true;
        }
    }
}
