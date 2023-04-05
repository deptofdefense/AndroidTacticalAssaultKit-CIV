
package com.atakmap.android.widgets;

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
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import gov.tak.api.widgets.IParentWidget;
import gov.tak.api.widgets.IMapWidget;
import gov.tak.platform.ui.MotionEvent;
import gov.tak.platform.widgets.WidgetList;

@Deprecated
@DeprecatedApi(since = "4.4")
public abstract class AbstractParentWidget extends MapWidget2
        implements IParentWidget,
        AtakMapView.OnActionBarToggledListener {

    // Lock to prevent weird stuff like doing the same undo twice
    private final WidgetList _childWidgets = new WidgetList();
    private final ConcurrentLinkedQueue<IParentWidget.OnWidgetListChangedListener> _onWidgetListChanged = new ConcurrentLinkedQueue<>();

    private final Map<OnWidgetListChangedListener, IParentWidget.OnWidgetListChangedListener> _onWidgetListChangedForwarders = new IdentityHashMap<>();

    public interface OnWidgetListChangedListener {
        void onWidgetAdded(AbstractParentWidget parent, int index,
                MapWidget child);

        void onWidgetRemoved(AbstractParentWidget parent, int index,
                MapWidget child);
    }

    // Comparator for visual draw order of widgets
    // MUST only be used in a sync block on _childWidgets
    private final Comparator<IMapWidget> _visualComparator = new Comparator<IMapWidget>() {
        @Override
        public int compare(IMapWidget o1, IMapWidget o2) {
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
        protected void configAttributes(IMapWidget widget,
                ConfigEnvironment config,
                NamedNodeMap attrs) {
            super.configAttributes(widget, config, attrs);
        }
    }

    public int getChildCount() {
        synchronized (_childWidgets) {
            int count = 0;
            for (IMapWidget widget : _childWidgets) {
                if (widget instanceof MapWidget)
                    ++count;
            }
            return count;
        }
    }

    public MapWidget getChildAt(int index) {
        synchronized (_childWidgets) {
            int count = 0;
            for (IMapWidget widget : _childWidgets) {
                if (widget instanceof MapWidget) {
                    if (count++ == index)
                        return (MapWidget) widget;
                }
            }
            throw new IndexOutOfBoundsException();
        }
    }

    @Override
    public AbstractParentWidget getParent() {
        return parent;
    }

    /**
     * Get the list of child widgets sorted by their draw order
     * Sort priority: z-order -> list order
     * @return
     */
    public List<MapWidget> getSortedWidgets() {
        List<MapWidget> sorted = getChildWidgets();
        Collections.sort(sorted, _visualComparator);
        return sorted;
    }

    public List<IMapWidget> getChildren() {
        synchronized (_childWidgets) {
            return new ArrayList<>(_childWidgets);
        }
    }

    @Override
    public void addChildWidget(IMapWidget widget) {
        synchronized (_childWidgets) {
            addChildWidgetAt(_childWidgets.size(), widget);
        }
    }

    public void addWidget(MapWidget widget) {
        addChildWidget(widget);
    }

    @Override
    public void addChildWidgetAt(int index, IMapWidget widget) {
        if (widget == this)
            return;
        if (this.onChildWidgetCanBeAdded(index, widget)) {
            synchronized (_childWidgets) {
                _childWidgets.add(index, widget);
                if (widget.getParent() != null) {
                    widget.getParent().removeChildWidget(widget);
                }
                widget.setParent(this);
                if (widget instanceof MapWidget)
                    onWidgetAdded(index, (MapWidget) widget);
                else
                    onWidgetAdded(index, widget);
            }
        }
    }

    public void addWidgetAt(int index, MapWidget widget) {
        addChildWidgetAt(index, widget);
    }

    @Override
    public IMapWidget removeChildWidgetAt(int index) {
        synchronized (_childWidgets) {
            IMapWidget w = _childWidgets.remove(index);
            if (w != null) {
                w.setParent(null);
                if (w instanceof MapWidget)
                    onWidgetRemoved(index, (MapWidget) w);
                else
                    onWidgetRemoved(index, w);
            }
            return w;
        }

    }

    public MapWidget removeWidgetAt(int index) {
        synchronized (_childWidgets) {
            int count = 0;
            for (IMapWidget iMapWidget : _childWidgets) {
                if (!(iMapWidget instanceof MapWidget))
                    continue;

                if (count++ != index)
                    continue;

                MapWidget w = (MapWidget) _childWidgets.remove(index);
                if (w != null) {
                    w.setParent(null);
                    onWidgetRemoved(index, w);
                }
                return w;
            }
        }
        throw new IndexOutOfBoundsException();
    }

    @Override
    public boolean removeChildWidget(IMapWidget widget) {
        synchronized (_childWidgets) {
            int index = _childWidgets.indexOf(widget);
            boolean r = index != -1;
            if (r)
                removeChildWidgetAt(index);
            return r;
        }
    }

    public boolean removeWidget(MapWidget widget) {
        return removeChildWidget(widget);
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
            for (IMapWidget w : _childWidgets) {
                if (w instanceof MapWidget
                        && FileSystemUtils.isEquals(w.getName(), name))
                    return (MapWidget) w;
            }
            if (!deep)
                return null;
            // Now perform a deep find
            for (IMapWidget w : _childWidgets) {
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
            List<IMapWidget> children = getChildren();
            for (IMapWidget w : children)
                w.setZOrder(zOrder + (w.getZOrder() - oldZ));
        }
    }

    public List<MapWidget> getChildWidgets() {
        List<MapWidget> ret = new ArrayList<>();
        synchronized (_childWidgets) {
            for (IMapWidget child : _childWidgets) {
                if (child instanceof MapWidget)
                    ret.add((MapWidget) child);
            }
        }
        return ret;
    }

    @Override
    public void addOnWidgetListChangedListener(
            IParentWidget.OnWidgetListChangedListener l) {
        _onWidgetListChanged.add(l);
    }

    public void addOnWidgetListChangedListener(
            AbstractParentWidget.OnWidgetListChangedListener legacy) {
        final IParentWidget.OnWidgetListChangedListener l;
        synchronized (_onWidgetListChangedForwarders) {
            if (_onWidgetListChangedForwarders.containsKey(legacy))
                return;
            l = new WidgetListChangedForwarder(legacy);
            _onWidgetListChangedForwarders.put(legacy, l);
        }
        addOnWidgetListChangedListener(l);
    }

    @Override
    public void removeOnWidgetListChangedListener(
            IParentWidget.OnWidgetListChangedListener l) {
        _onWidgetListChanged.remove(l);
    }

    public void removeOnWidgetListChangedListener(
            AbstractParentWidget.OnWidgetListChangedListener legacy) {
        final IParentWidget.OnWidgetListChangedListener l;
        synchronized (_onWidgetListChangedForwarders) {
            l = _onWidgetListChangedForwarders.remove(legacy);
            if (l == null)
                return;
        }
        removeOnWidgetListChangedListener(l);
    }

    /**
     * Uses the touch bounds and zOrder to determine the top most item within the MapWidget
     * list to determine what item is hit.
     */
    @Override
    public IMapWidget seekWidgetHit(gov.tak.platform.ui.MotionEvent event,
            float x, float y) {
        if (!isVisible() || !isTouchable())
            return null;

        // Iterate through visually-sorted list of widgets backwards so we hit
        // detect the stuff on top first
        List<IMapWidget> widgets = getSortedChildrenWidgets();
        Collections.reverse(widgets);

        IMapWidget hit = null;
        for (IMapWidget w : widgets) {
            if (!w.isVisible())
                continue;
            float lx = x - w.getPointX();
            float ly = y - w.getPointY();
            IMapWidget c = w.seekWidgetHit(event, lx, ly);
            if (hit == null)
                hit = c;
            else if (c != null && c.getZOrder() < hit.getZOrder()) {
                hit = c;
            }
        }
        return hit;
    }

    @Override
    public boolean onChildWidgetCanBeAdded(int index, IMapWidget widget) {
        return true;
    }

    public boolean onWidgetCanBeAdded(int index, MapWidget widget) {
        return onChildWidgetCanBeAdded(index, widget);
    }

    protected void onWidgetAdded(int index, MapWidget widget) {
        onWidgetAdded(index, (IMapWidget) widget);
    }

    protected void onWidgetRemoved(int index, MapWidget widget) {
        onWidgetRemoved(index, (IMapWidget) widget);
    }

    protected void onWidgetAdded(int index, IMapWidget widget) {
        for (IParentWidget.OnWidgetListChangedListener l : _onWidgetListChanged) {
            l.onWidgetAdded(this, index, widget);
        }
    }

    protected void onWidgetRemoved(int index, IMapWidget widget) {
        for (IParentWidget.OnWidgetListChangedListener l : _onWidgetListChanged) {
            l.onWidgetRemoved(this, index, widget);
        }
    }

    @Override
    public void orientationChanged() {
        Collection<IMapWidget> children = getChildren();
        for (IMapWidget child : children)
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
            for (IMapWidget child : _childWidgets) {
                if (child instanceof AtakMapView.OnActionBarToggledListener) {
                    ((AtakMapView.OnActionBarToggledListener) child)
                            .onActionBarToggled(showing);
                }
            }
        }
    }

    @Override
    public int getChildWidgetCount() {
        synchronized (_childWidgets) {
            return _childWidgets.size();
        }
    }

    @Override
    public IMapWidget getChildWidgetAt(int index) {
        synchronized (_childWidgets) {
            return _childWidgets.get(index);
        }
    }

    @Override
    public List<IMapWidget> getSortedChildrenWidgets() {
        List<IMapWidget> sorted = getChildren();
        Collections.sort(sorted, _visualComparator);
        return sorted;
    }
}
