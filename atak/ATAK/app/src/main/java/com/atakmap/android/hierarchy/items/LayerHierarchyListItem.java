
package com.atakmap.android.hierarchy.items;

import android.view.View;

import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.hierarchy.action.Action;
import com.atakmap.android.hierarchy.action.Visibility;
import com.atakmap.android.hierarchy.action.Visibility2;
import com.atakmap.map.layer.Layer;
import com.atakmap.map.layer.MultiLayer;
import com.atakmap.map.layer.ProxyLayer;

public final class LayerHierarchyListItem
        implements HierarchyListItem, Visibility2, Visibility {

    private final String _title;
    private final String _uid;
    private final Layer _subject;
    private final String _iconUri;
    private final boolean _unwrapProxy;

    public LayerHierarchyListItem(Layer subject) {
        this(subject, null, true);
    }

    public LayerHierarchyListItem(Layer subject, String iconUri,
            boolean unwrapProxy) {
        _title = subject.getName();
        _uid = "LAYER-" + Integer.toHexString(subject.hashCode());
        _iconUri = iconUri;
        _unwrapProxy = unwrapProxy;

        if (_unwrapProxy && subject instanceof ProxyLayer)
            subject = ((ProxyLayer) subject).get();
        _subject = subject;
    }

    @Override
    public int getChildCount() {
        if (_subject instanceof MultiLayer)
            return ((MultiLayer) _subject).getNumLayers();
        else
            return 0;
    }

    @Override
    public int getDescendantCount() {
        return getChildCount();
    }

    @Override
    public HierarchyListItem getChildAt(int index) {
        if (!(_subject instanceof MultiLayer))
            return null;
        MultiLayer impl = (MultiLayer) _subject;
        final int limit = impl.getNumLayers() - 1;
        if (index < 0 || index > limit)
            return null;
        return new LayerHierarchyListItem(impl.getLayer(index), null,
                _unwrapProxy);
    }

    @Override
    public boolean isChildSupported() {
        return _subject instanceof MultiLayer;
    }

    @Override
    public final String getUID() {
        return _uid;
    }

    @Override
    public final String getTitle() {
        return _title;
    }

    @Override
    public int getPreferredListIndex() {
        return -1;
    }

    @Override
    public String getIconUri() {
        return _iconUri;
    }

    @Override
    public int getIconColor() {
        return -1;
    }

    @Override
    public Object setLocalData(String s, Object o) {
        return null;
    }

    @Override
    public Object getLocalData(String s) {
        return null;
    }

    @Override
    public <T> T getLocalData(String s, Class<T> clazz) {
        return null;
    }

    @Override
    public <T extends Action> T getAction(Class<T> clazz) {
        if (clazz.isAssignableFrom(this.getClass()))
            return (T) this;
        return null;
    }

    @Override
    public Object getUserObject() {
        return null;
    }

    @Override
    public View getExtraView() {
        return null;
    }

    @Override
    public HierarchyListItem.Sort refresh(HierarchyListItem.Sort sortHint) {
        return null;
    }

    @Override
    public int getVisibility() {
        return isVisible() ? Visibility2.VISIBLE : Visibility2.INVISIBLE;
    }

    @Override
    public final boolean setVisible(boolean visible) {
        setVisible(_subject, visible);
        return true;
    }

    @Override
    public final boolean isVisible() {
        return _subject.isVisible();
    }

    private static void setVisible(Layer l, boolean v) {
        if (l instanceof MultiLayer) {
            for (int i = 0; i < ((MultiLayer) l).getNumLayers(); i++)
                setVisible(((MultiLayer) l).getLayer(i), v);
        }
        if (l != null)
            l.setVisible(v);
    }
}
