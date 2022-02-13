
package gov.tak.platform.widgets;

import gov.tak.platform.ui.MotionEvent;

import com.atakmap.coremap.filesystem.FileSystemUtils;

import gov.tak.api.widgets.IParentWidget;
import gov.tak.api.widgets.IMapWidget;
import gov.tak.platform.config.ConfigEnvironment;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public abstract class AbstractParentWidget extends MapWidget implements
        IParentWidget {

    // Lock to prevent weird stuff like doing the same undo twice
    private final WidgetList _childWidgets = new WidgetList();
    private final ConcurrentLinkedQueue<IParentWidget.OnWidgetListChangedListener> _onWidgetListChanged = new ConcurrentLinkedQueue<>();

    public interface OnWidgetListChangedListener extends IParentWidget.OnWidgetListChangedListener  {

        void onWidgetAdded(AbstractParentWidget parent, int index,
                           IMapWidget child);

        default void onWidgetAdded(IParentWidget parent, int index,
                                   IMapWidget child) {
            if(parent instanceof AbstractParentWidget)
                onWidgetAdded((AbstractParentWidget)parent, index, child);
        }
        void onWidgetRemoved(AbstractParentWidget parent, int index,
                             IMapWidget child);

        default void onWidgetRemoved(IParentWidget parent, int index,
                                     IMapWidget child) {
            if(parent instanceof AbstractParentWidget)
                onWidgetRemoved((AbstractParentWidget)parent, index, child);
        }
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

    @Deprecated
    public static class Factory extends MapWidget.Factory {
        @Override
        public MapWidget createFromElem(ConfigEnvironment config,
                Node defNode) {
            return null;
        }
    }

    @Override
    public IMapWidget getChildWidgetAt(int index) {
        synchronized (_childWidgets) {
            return _childWidgets.get(index);
        }
    }

    @Override
    public int getChildWidgetCount() {
        synchronized (_childWidgets) {
            return _childWidgets.size();
        }
    }

    /**
     * Gets the child widgets.
     * @return Defensive list of child widgets
     */

    @Override
    public List<IMapWidget> getChildren() {
        synchronized (_childWidgets) {
            return new ArrayList<>(_childWidgets);
        }
    }

    /**
     * Get the list of child widgets sorted by their draw order
     * Sort priority: z-order -> list order
     * @return
     */
    @Override
    public List<IMapWidget> getSortedChildrenWidgets() {
        synchronized (_childWidgets) {
            List<IMapWidget> sorted = new ArrayList<>(_childWidgets);
            Collections.sort(sorted, _visualComparator);
            return sorted;
        }
    }

    @Override
    public void addChildWidget(IMapWidget widget) {
        synchronized (_childWidgets) {
            addChildWidgetAt(_childWidgets.size(), widget);
        }
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
                onWidgetAdded(index, widget);
            }
        }
    }

    @Override
    public IMapWidget removeChildWidgetAt(int index) {
        synchronized (_childWidgets) {
            IMapWidget w = _childWidgets.remove(index);
            if (w != null) {
                w.setParent(null);
                onWidgetRemoved(index, w);
            }
            return w;
        }
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

    /**
     * Find a widget by name within either:
     * - this parent's immediate children list
     * - this entire parent widget's hierarchy
     * @param name Widget unique name
     * @param deep True to perform a deep find, false to perform a shallow find
     * @return Widget that matches or null if not found
     */
    public IMapWidget findWidget(String name, boolean deep) {
        if (FileSystemUtils.isEmpty(name))
            return null;
        synchronized (_childWidgets) {
            // First perform a shallow find
            for (IMapWidget w : _childWidgets) {
                if (FileSystemUtils.isEquals(w.getName(), name))
                    return w;
            }
            if (!deep)
                return null;
            // Now perform a deep find
            for (IMapWidget w : _childWidgets) {
                if (w instanceof AbstractParentWidget) {
                    IMapWidget found = ((AbstractParentWidget) w)
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

    @Override
    public void addOnWidgetListChangedListener(IParentWidget.OnWidgetListChangedListener l) {
        _onWidgetListChanged.add(l);
    }

    @Override
    public void removeOnWidgetListChangedListener(IParentWidget.OnWidgetListChangedListener l) {
        _onWidgetListChanged.remove(l);
    }


    /**
     * Uses the touch bounds and zOrder to determine the top most item within the MapWidget
     * list to determine what item is hit.
     */
    @Override
    public IMapWidget seekWidgetHit(MotionEvent event, float x, float y) {
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

    public void onWidgetAdded(int index, IMapWidget widget) {
        for (IParentWidget.OnWidgetListChangedListener l : _onWidgetListChanged) {
            l.onWidgetAdded(this, index, widget);
        }
    }

    public void onWidgetRemoved(int index, IMapWidget widget) {
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
}
