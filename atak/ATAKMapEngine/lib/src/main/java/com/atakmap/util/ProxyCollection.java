
package com.atakmap.util;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;

public class ProxyCollection<T> implements Collection<T> {

    private Collection<T> filter;

    public ProxyCollection() {
        this(Collections.<T> emptySet());
    }

    public ProxyCollection(Collection<T> filter) {
        this.filter = filter;
    }

    public void setFilter(Collection<T> filter) {
        if (filter == null)
            filter = Collections.<T> emptySet();
        this.filter = filter;
    }

    @Override
    public boolean add(T arg0) {
        return this.filter.add(arg0);
    }

    @Override
    public boolean addAll(Collection<? extends T> arg0) {
        return this.filter.addAll(arg0);
    }

    @Override
    public void clear() {
        this.filter.clear();
    }

    @Override
    public boolean contains(Object object) {
        return this.filter.contains(object);
    }

    @Override
    public boolean containsAll(Collection<?> arg0) {
        return this.filter.containsAll(arg0);
    }

    @Override
    public boolean isEmpty() {
        return this.filter.isEmpty();
    }

    @Override
    public Iterator<T> iterator() {
        return this.filter.iterator();
    }

    @Override
    public boolean remove(Object object) {
        return this.filter.remove(object);
    }

    @Override
    public boolean removeAll(Collection<?> arg0) {
        return this.filter.removeAll(arg0);
    }

    @Override
    public boolean retainAll(Collection<?> arg0) {
        return this.filter.retainAll(arg0);
    }

    @Override
    public int size() {
        return this.filter.size();
    }

    @Override
    public Object[] toArray() {
        return this.filter.toArray();
    }

    @Override
    public <T> T[] toArray(T[] array) {
        return this.filter.toArray(array);
    }
}
