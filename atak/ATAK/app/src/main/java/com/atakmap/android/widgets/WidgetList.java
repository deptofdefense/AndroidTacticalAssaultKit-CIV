
package com.atakmap.android.widgets;

import com.atakmap.annotations.DeprecatedApi;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Array list that has a quick child index lookup for each child
 * Also prevents the same child from being inserted twice in order to simplify
 * the child -> index mapping
 *
 * @deprecated use {@link gov.tak.platform.widgets.WidgetList}
 */
@Deprecated
@DeprecatedApi(since = "4.4", forRemoval = true, removeAt = "4.7")
public class WidgetList extends ArrayList<MapWidget> {

    private final gov.tak.platform.widgets.WidgetList impl = new gov.tak.platform.widgets.WidgetList();

    @Override
    public int indexOf(Object o) {
        return impl.indexOf(o);
    }

    @Override
    public int lastIndexOf(Object o) {
        return impl.lastIndexOf(o);
    }

    @Override
    public void add(int index, MapWidget element) {
        impl.add(index, element);
    }

    @Override
    public boolean addAll(Collection<? extends MapWidget> c) {
        return impl.addAll(c);
    }

    @Override
    public boolean addAll(int index, Collection<? extends MapWidget> c) {
        return impl.addAll(index, c);
    }

    @Override
    public MapWidget set(int index, MapWidget element) {
        return (MapWidget) impl.set(index, element);
    }

    @Override
    public boolean remove(Object o) {
        return impl.remove(o);
    }

    @Override
    public MapWidget remove(int index) {
        return (MapWidget) impl.remove(index);
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return impl.removeAll(c);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return impl.retainAll(c);
    }

    @Override
    public void clear() {
        impl.clear();
    }
}
