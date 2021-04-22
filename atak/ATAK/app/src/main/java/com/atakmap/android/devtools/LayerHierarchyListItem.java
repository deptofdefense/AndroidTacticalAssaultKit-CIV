
package com.atakmap.android.devtools;

import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.map.layer.Layer;
import com.atakmap.map.layer.MultiLayer;
import com.atakmap.map.layer.ProxyLayer;

final class LayerHierarchyListItem extends DevToolToggle {
    private Layer subject;

    public LayerHierarchyListItem(Layer subject) {
        super(subject.getName(),
                "LAYER-" + Integer.toHexString(subject.hashCode()));
        if (subject instanceof ProxyLayer)
            subject = ((ProxyLayer) subject).get();
        this.subject = subject;
    }

    @Override
    public int getChildCount() {
        if (subject instanceof MultiLayer)
            return ((MultiLayer) subject).getNumLayers();
        else
            return 0;
    }

    @Override
    public int getDescendantCount() {
        return getChildCount();
    }

    @Override
    public HierarchyListItem getChildAt(int index) {
        if (!(subject instanceof MultiLayer))
            return null;
        MultiLayer impl = (MultiLayer) subject;
        final int limit = impl.getNumLayers() - 1;
        if (index < 0 || index > limit)
            return null;
        return new LayerHierarchyListItem(impl.getLayer(index));
    }

    @Override
    public boolean isChildSupported() {
        return true;
    }

    @Override
    protected void setEnabled(boolean visible) {
        setVisible(subject, visible);
    }

    @Override
    protected boolean isEnabled() {
        return subject.isVisible();
    }

    private static void setVisible(Layer l, boolean v) {
        if (l instanceof MultiLayer) {
            for (int i = 0; i < ((MultiLayer) l).getNumLayers(); i++)
                setVisible(((MultiLayer) l).getLayer(i), v);
        } else if (l instanceof ProxyLayer) {
            setVisible(((ProxyLayer) l).get(), v);
        }
        if (l != null)
            l.setVisible(v);
    }
}
