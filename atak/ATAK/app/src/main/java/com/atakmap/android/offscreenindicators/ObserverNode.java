
package com.atakmap.android.offscreenindicators;

import com.atakmap.android.maps.AnchoredMapItem;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.PointMapItem;

import java.util.Collection;
import java.util.HashMap;

public class ObserverNode implements MapGroup.OnItemListChangedListener,
        MapGroup.OnGroupListChangedListener, MapGroup.OnVisibleChangedListener,
        MapItem.OnVisibleChangedListener {

    private final MapGroup _group;
    private final OffscreenIndicatorController _candidates;
    private final HashMap<Long, ObserverNode> _childGroups = new HashMap<>();

    ObserverNode(final MapGroup group,
            final OffscreenIndicatorController candidates) {
        _group = group;
        _candidates = candidates;
        group.addOnGroupListChangedListener(this);
        group.addOnItemListChangedListener(this);
        group.addOnVisibleChangedListener(this);
        _attach();
    }

    void reconsider() {
        _reconsider(_group.getVisible());
    }

    public void dispose() {
        _detach();
    }

    @Override
    public void onItemAdded(MapItem item, MapGroup group) {
        if (item instanceof Marker) {
            item.addOnVisibleChangedListener(this);
            if (item.getVisible() && _allParentsVisible(item)
                    && !item.getMetaBoolean("ignoreOffscreen", false)) {
                Marker marker = (Marker) item;
                _candidates.addMarker(marker);
            }
        } else if (item instanceof AnchoredMapItem) {
            AnchoredMapItem anc = (AnchoredMapItem) item;
            PointMapItem pmi = anc.getAnchorItem();
            if (pmi instanceof Marker)
                onItemAdded(pmi, pmi.getGroup());
        }
    }

    @Override
    public void onItemRemoved(MapItem item, MapGroup group) {
        if (item instanceof Marker) {
            Marker marker = (Marker) item;
            marker.removeOnVisibleChangedListener(this);
            _candidates.removeMarker(marker);
        }
    }

    @Override
    public void onGroupAdded(MapGroup group, MapGroup parent) {
        if (!group.getMetaBoolean("ignoreOffscreen", false)) {
            // _childGroups.add(index, new ObserverNode(group, _candidates));
            _childGroups.put(group.getSerialId(), new ObserverNode(group,
                    _candidates));
        }
        // else {
        // _childGroups.add(index, null);
        // }
    }

    @Override
    public void onGroupRemoved(MapGroup group, MapGroup parent) {
        // ObserverNode observer = _childGroups.remove(index);
        ObserverNode observer = _childGroups.remove(group.getSerialId());
        if (observer != null) {
            observer._detach();
        }
    }

    @Override
    public void onGroupVisibleChanged(MapGroup group) {
        if (!group.getMetaBoolean("ignoreOffscreen", false)) {
            _reconsider(group.getVisible());
        } else {
            _reconsider(false); // hack to remove it if it's there
        }

    }

    @Override
    public void onVisibleChanged(MapItem item) {
        if (item.getVisible() && _allParentsVisible(item)
                && !item.getMetaBoolean("ignoreOffscreen", false)) {
            // return the marker to consideration set
            _candidates.addMarker((Marker) item);
        } else {
            // remove the marker if it's in there
            _candidates.removeMarker((Marker) item);
        }
    }

    private void _attach() {
        Collection<? extends MapItem> items = _group.getItems();
        // for (int i=0; i<_group.getItemCount(); ++i) {
        for (MapItem item : items) {
            // MapItem item = _group.getItemAt(i);
            if (item instanceof Marker) {
                Marker marker = (Marker) item;
                marker.addOnVisibleChangedListener(this);
            }
        }

        Collection<? extends MapGroup> groups = _group.getChildGroups();
        // for (int i=0; i<_group.getGroupCount(); ++i) {
        for (MapGroup child : groups) {
            // MapGroup child = _group.getGroupAt(i);
            if (!child.getMetaBoolean("ignoreOffscreen", false)) {
                // _childGroups.add(new ObserverNode(child, _candidates));
                _childGroups.put(child.getSerialId(), new ObserverNode(child,
                        _candidates));
            }
            // else {
            // place holder
            // _childGroups.add(null);
            // }
        }
    }

    private void _detach() {
        _group.removeOnGroupListChangedListener(this);
        _group.removeOnItemListChangedListener(this);
        _group.removeOnVisibleChangedListener(this);

        Collection<? extends MapItem> items = _group.getItems();
        // for (int i=0; i<_group.getItemCount(); ++i) {
        for (MapItem item : items) {
            // MapItem item = _group.getItemAt(i);
            if (item.getVisible() && item instanceof Marker) {
                Marker marker = (Marker) item;
                _candidates.removeMarker(marker);
            }
        }

        Collection<ObserverNode> values = _childGroups.values();
        for (ObserverNode c : values) {
            if (c != null) {
                c._detach();
            }
        }
        _childGroups.clear();
    }

    public boolean getVisible() {
        return _group.getVisible();
    }

    private void _reconsider(boolean visible) {
        if (visible) {
            // for (int i=0; i<_group.getItemCount(); ++i) {
            for (MapItem item : _group.getItems()) {
                // MapItem item = _group.getItemAt(i);
                if (item.getVisible() && item instanceof Marker) {
                    _candidates.addMarker((Marker) item);
                }
            }
        } else {
            // for (int i=0; i<_group.getItemCount(); ++i) {
            for (MapItem item : _group.getItems()) {
                // MapItem item = _group.getItemAt(i);
                if (item.getVisible() && item instanceof Marker) {
                    _candidates.removeMarker((Marker) item);
                }
            }
        }
        for (ObserverNode obs : _childGroups.values()) {
            if (obs != null && obs.getVisible()) {
                obs._reconsider(visible);
            }
        }
    }

    private static boolean _allParentsVisible(MapItem item) {
        boolean r = true;
        MapGroup g = item.getGroup();
        while (g != null) {
            if (!g.getVisible()) {
                r = false;
                break;
            }
            g = g.getParentGroup();
        }
        return r;
    }

}
