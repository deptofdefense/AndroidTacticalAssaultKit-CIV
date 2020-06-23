
package com.atakmap.android.raster;

import android.widget.BaseAdapter;

import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.hierarchy.HierarchyListItem.Sort;
import com.atakmap.android.maps.DeepMapItemQuery;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.overlay.MapOverlay;
import com.atakmap.map.layer.raster.RasterLayer2;

public class RasterLayerMapOverlay implements MapOverlay {

    protected final RasterLayer2 subject;
    protected final String iconUri;

    public RasterLayerMapOverlay(RasterLayer2 subject) {
        this(subject, null);
    }

    public RasterLayerMapOverlay(RasterLayer2 subject, String iconUri) {
        this.subject = subject;
        this.iconUri = iconUri;
    }

    @Override
    public String getIdentifier() {
        return this.subject.getName();
    }

    @Override
    public String getName() {
        return this.subject.getName();
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
    public HierarchyListItem getListModel(BaseAdapter adapter,
            long capabilities, Sort preferredSort) {
        // the list model to be added to the Overlay Manager
        return new RasterLayerHierarchyListItem(this.subject, this.iconUri);
    }

}
