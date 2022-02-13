
package gov.tak.platform.widgets;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import gov.tak.api.widgets.IMapWidget;

/**
 * Array list that has a quick child index lookup for each child
 * Also prevents the same child from being inserted twice in order to simplify
 * the child -> index mapping
 */
public final class WidgetList extends ArrayList<IMapWidget> {

    private final Map<IMapWidget, Integer> _childToIndex = new HashMap<>();

    @Override
    public int indexOf(Object o) {
        if (!(o instanceof IMapWidget))
            return -1;
        synchronized (_childToIndex) {
            Integer idx = _childToIndex.get(o);
            if (idx != null)
                return idx;
        }
        return -1;
    }

    @Override
    public int lastIndexOf(Object o) {
        // Can only have 1 occurrence per item
        return indexOf(o);
    }

    @Override
    public void add(int index, IMapWidget element) {
        int reIdx = index;
        synchronized (_childToIndex) {
            // Re-index elements elements after the one we just added
            // Also prevent same element from being added twice so the index map
            // is maintained
            Integer oldIndex = _childToIndex.get(element);
            if (oldIndex != null) {
                super.remove(oldIndex);
                reIdx = oldIndex;
            }
        }
        super.add(index, element);
        reIndex(Math.min(reIdx, index));
    }

    @Override
    public boolean addAll(Collection<? extends IMapWidget> c) {
        // Remove existing elements from old index
        int reIdx = removeOldEntries(c);

        // Add new children and re-index
        if (super.addAll(c)) {
            reIndex(reIdx);
            return true;
        }
        return false;
    }

    @Override
    public boolean addAll(int index, Collection<? extends IMapWidget> c) {
        int reIdx = removeOldEntries(c);
        if (super.addAll(index, c)) {
            reIndex(Math.min(reIdx, index));
            return true;
        }
        return false;
    }

    @Override
    public IMapWidget set(int index, IMapWidget element) {
        IMapWidget removed = super.set(index, element);
        synchronized (_childToIndex) {
            if (removed != null)
                _childToIndex.remove(removed);
            _childToIndex.put(element, index);
        }
        return removed;
    }

    @Override
    public boolean remove(Object o) {
        if (super.remove(o) && o instanceof IMapWidget) {
            // Re-index elements after the one we just removed
            synchronized (_childToIndex) {
                Integer oldIndex = _childToIndex.remove(o);
                if (oldIndex != null)
                    reIndex(oldIndex);
            }
            return true;
        }
        return false;
    }

    @Override
    public IMapWidget remove(int index) {
        IMapWidget w = super.remove(index);
        synchronized (_childToIndex) {
            _childToIndex.remove(w);
            reIndex(index);
        }
        return w;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        if (super.removeAll(c)) {
            reIndex();
            return true;
        }
        return false;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        if (super.retainAll(c)) {
            reIndex();
            return true;
        }
        return false;
    }

    @Override
    public void clear() {
        super.clear();
        reIndex();
    }

    private int removeOldEntries(Collection<? extends IMapWidget> c) {
        int ret = size();
        synchronized (_childToIndex) {
            for (IMapWidget w : c) {
                Integer oldIndex = _childToIndex.get(w);
                if (oldIndex != null) {
                    super.remove(oldIndex);
                    ret = Math.min(ret, oldIndex);
                }
            }
        }
        return ret;
    }

    private void reIndex(int startIdx) {
        synchronized (_childToIndex) {
            for (int i = startIdx; i < size(); i++)
                _childToIndex.put(get(i), i);
        }
    }

    private void reIndex() {
        synchronized (_childToIndex) {
            _childToIndex.clear();
            reIndex(0);
        }
    }
}
