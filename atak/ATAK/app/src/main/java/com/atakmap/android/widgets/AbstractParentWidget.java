
package com.atakmap.android.widgets;

import android.view.MotionEvent;

import com.atakmap.android.config.ConfigEnvironment;
import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.map.AtakMapView;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public abstract class AbstractParentWidget extends MapWidget2 implements
        AtakMapView.OnActionBarToggledListener {

    // Lock to prevent weird stuff like doing the same undo twice
    private final WidgetList _childWidgets = new WidgetList();
    private final ConcurrentLinkedQueue<OnWidgetListChangedListener> _onWidgetListChanged = new ConcurrentLinkedQueue<>();

    public interface OnWidgetListChangedListener {
        void onWidgetAdded(AbstractParentWidget parent, int index,
                MapWidget child);

        void onWidgetRemoved(AbstractParentWidget parent, int index,
                MapWidget child);
    }

    // Comparator for visual draw order of widgets
    // MUST only be used in a sync block on _childWidgets
    private final Comparator<MapWidget> _visualComparator = new Comparator<MapWidget>() {
        @Override
        public int compare(MapWidget o1, MapWidget o2) {
            // First sort by Z-order
            int zComp = Double.compare(o2.getZOrder(), o1.getZOrder());
            if (zComp != 0)
                return zComp;

            // Then fallback to child position in list
            int c1 = _childWidgets.indexOf(o1);
            int c2 = _childWidgets.indexOf(o2);
            if (c1 != -1 && c2 != -1)
                return Integer.compare(c1, c2);

            // No child index on either
            if (c1 == -1 && c2 == -1)
                return 0;
            else if (c1 == -1)
                return -1;
            return 1;
        }
    };

    public AbstractParentWidget() {

    }

    public static class Factory extends MapWidget.Factory {
        @Override
        public MapWidget createFromElem(ConfigEnvironment config,
                Node defNode) {
            return null;
        }

        @Override
        protected void configAttributes(MapWidget widget,
                ConfigEnvironment config,
                NamedNodeMap attrs) {
            super.configAttributes(widget, config, attrs);
        }
    }

    public int getChildCount() {
        synchronized (_childWidgets) {
            return _childWidgets.size();
        }
    }

    public MapWidget getChildAt(int index) {
        synchronized (_childWidgets) {
            return _childWidgets.get(index);
        }
    }

    /**
     * Gets the child widgets.
     * @return Defensive list of child widgets
     */
    public List<MapWidget> getChildWidgets() {
        synchronized (_childWidgets) {
            return new ArrayList<>(_childWidgets);
        }
    }

    /**
     * Get the list of child widgets sorted by their draw order
     * Sort priority: z-order -> list order
     * @return
     */
    public List<MapWidget> getSortedWidgets() {
        synchronized (_childWidgets) {
            List<MapWidget> sorted = new ArrayList<>(_childWidgets);
            Collections.sort(sorted, _visualComparator);
            return sorted;
        }
    }

    public void addWidget(MapWidget widget) {
        synchronized (_childWidgets) {
            addWidgetAt(_childWidgets.size(), widget);
        }
    }

    public void addWidgetAt(int index, MapWidget widget) {
        if (widget == this)
            return;
        if (this.onWidgetCanBeAdded(index, widget)) {
            synchronized (_childWidgets) {
                _childWidgets.add(index, widget);
                if (widget.parent != null) {
                    widget.parent.removeWidget(widget);
                }
                widget.setParent(this);
                onWidgetAdded(index, widget);
            }
        }
    }

    public MapWidget removeWidgetAt(int index) {
        synchronized (_childWidgets) {
            MapWidget w = _childWidgets.remove(index);
            if (w != null) {
                w.setParent(null);
                onWidgetRemoved(index, w);
            }
            return w;
        }
    }

    public boolean removeWidget(MapWidget widget) {
        synchronized (_childWidgets) {
            int index = _childWidgets.indexOf(widget);
            boolean r = index != -1;
            if (r)
                removeWidgetAt(index);
            return r;
        }
    }

    /**
     * Find a widget by name within either:
     * - this parent's immediate children list
     * - this entire parent widget's hierarchy
     * @param name Widget unique name
     * @param deep True to perform a deep find, false to perform a shallow find
     * @return Widget that matches or null if not found
     */
    public MapWidget findWidget(String name, boolean deep) {
        if (FileSystemUtils.isEmpty(name))
            return null;
        synchronized (_childWidgets) {
            // First perform a shallow find
            for (MapWidget w : _childWidgets) {
                if (FileSystemUtils.isEquals(w.getName(), name))
                    return w;
            }
            if (!deep)
                return null;
            // Now perform a deep find
            for (MapWidget w : _childWidgets) {
                if (w instanceof AbstractParentWidget) {
                    MapWidget found = ((AbstractParentWidget) w)
                            .findWidget(name, true);
                    if (found != null)
                        return found;
                }
            }
        }
        return null;
    }

    // Maintain deltas between parent and children z-orders
    @Override
    public void setZOrder(double zOrder) {
        double oldZ = getZOrder();
        if (oldZ != zOrder) {
            super.setZOrder(zOrder);
            List<MapWidget> children = getChildWidgets();
            for (MapWidget w : children)
                w.setZOrder(zOrder + (w.getZOrder() - oldZ));
        }
    }

    public void addOnWidgetListChangedListener(OnWidgetListChangedListener l) {
        _onWidgetListChanged.add(l);
    }

    public void removeOnWidgetListChangedListener(
            OnWidgetListChangedListener l) {
        _onWidgetListChanged.remove(l);
    }

    /**
     * Uses the touch bounds and zOrder to determine the top most item within the MapWidget
     * list to determine what item is hit.
     */
    @Override
    public MapWidget seekHit(MotionEvent event, float x, float y) {
        if (!isVisible() || !isTouchable())
            return null;

        // Iterate through visually-sorted list of widgets backwards so we hit
        // detect the stuff on top first
        List<MapWidget> widgets = getSortedWidgets();
        Collections.reverse(widgets);

        MapWidget hit = null;
        for (MapWidget w : widgets) {
            if (!w.isVisible())
                continue;
            float lx = x - w.getPointX();
            float ly = y - w.getPointY();
            MapWidget c;
            if (w instanceof MapWidget2)
                c = ((MapWidget2) w).seekHit(event, lx, ly);
            else
                c = w.seekHit(lx, ly);
            if (hit == null)
                hit = c;
            else if (c != null && c.getZOrder() < hit.getZOrder()) {
                hit = c;
            }
        }
        return hit;
    }

    /**
     * @deprecated use {@link #seekHit(MotionEvent, float, float)}
     * @param x
     * @param y
     * @return
     */
    @Deprecated
    @DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.4")
    @Override
    public MapWidget seekHit(float x, float y) {
        return seekHit(null, x, y);
    }

    protected boolean onWidgetCanBeAdded(int index, MapWidget widget) {
        return true;
    }

    protected void onWidgetAdded(int index, MapWidget widget) {
        for (OnWidgetListChangedListener l : _onWidgetListChanged) {
            l.onWidgetAdded(this, index, widget);
        }
    }

    protected void onWidgetRemoved(int index, MapWidget widget) {
        for (OnWidgetListChangedListener l : _onWidgetListChanged) {
            l.onWidgetRemoved(this, index, widget);
        }
    }

    @Override
    public void orientationChanged() {
        Collection<MapWidget> children = getChildWidgets();
        for (MapWidget child : children)
            child.orientationChanged();
    }

    /**
     * Note individual MapWidgets do not need to register for action bar events. They simply
     * need to implement AtakMapView.OnActionBarToggledListener amd they will be notified via
     * thier parent MapWidget/container
     *
     * @param showing true if the action bar is showing
     */
    @Override
    public void onActionBarToggled(boolean showing) {
        synchronized (_childWidgets) {
            for (MapWidget child : _childWidgets) {
                if (child instanceof AtakMapView.OnActionBarToggledListener) {
                    ((AtakMapView.OnActionBarToggledListener) child)
                            .onActionBarToggled(showing);
                }
            }
        }
    }
}
