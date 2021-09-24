
package com.atakmap.util;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;

public class MultiCollection<T> implements Collection<T> {

    protected Collection<Collection<? extends T>> collections;

    public MultiCollection(Collection<Collection<? extends T>> collections) {
        this.collections = collections;
    }

    @Override
    public boolean add(T object) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean addAll(Collection<? extends T> arg0) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        Iterator<Collection<? extends T>> iter = this.collections.iterator();
        while (iter.hasNext())
            iter.next().clear();
    }

    @Override
    public boolean contains(Object object) {
        Iterator<Collection<? extends T>> iter = this.collections.iterator();
        while (iter.hasNext())
            if (iter.next().contains(object))
                return true;
        return false;
    }

    @Override
    public boolean containsAll(Collection<?> arg0) {
        for(Object o : arg0)
            if(!this.contains(o))
                return false;
        return true;
    }

    @Override
    public boolean isEmpty() {
        for(Collection<? extends T> c : this.collections)
            if(!c.isEmpty())
                return false;
        return true;
    }

    @Override
    public Iterator<T> iterator() {
        return new MultiIterator();
    }

    @Override
    public boolean remove(Object object) {
        Iterator<Collection<? extends T>> iter = this.collections.iterator();
        while (iter.hasNext())
            if (iter.next().remove(object))
                return true;
        return false;
    }

    @Override
    public boolean removeAll(Collection<?> arg0) {
        boolean retval = false;
        Iterator<Collection<? extends T>> iter = this.collections.iterator();
        while (iter.hasNext())
            retval |= iter.next().removeAll(arg0);
        return retval;
    }

    @Override
    public boolean retainAll(Collection<?> arg0) {
        boolean retval = false;
        for(Collection<? extends T> c : this.collections)
            retval |= c.retainAll(arg0);
        return retval;
    }

    @Override
    public int size() {
        int retval = 0;
        Iterator<Collection<? extends T>> iter = this.collections.iterator();
        while (iter.hasNext())
            retval += iter.next().size();
        return retval;
    }

    @Override
    public Object[] toArray() {
        Object[] array = new Object[this.size()];
        int idx = 0;
        Iterator<T> iter = this.iterator();
        while (iter.hasNext())
            array[idx++] = iter.next();
        return array;
    }

    @Override
    public <A> A[] toArray(A[] array) {
        final int size = this.size();
        if (array.length < size)
            array = (A[]) Array.newInstance(array.getClass().getComponentType(), size);
        int idx = 0;
        Iterator<T> iter = this.iterator();
        while (iter.hasNext())
            array[idx++] = (A) iter.next();
        return array;
    }

    protected class MultiIterator implements Iterator<T> {

        protected Iterator<Collection<? extends T>> collectionIter;
        protected Iterator<? extends T> elemIter;

        public MultiIterator() {
            this.collectionIter = MultiCollection.this.collections.iterator();
            if (this.collectionIter.hasNext())
                this.elemIter = this.collectionIter.next().iterator();
            else
                this.elemIter = null;
        }

        @Override
        public boolean hasNext() {
            if (this.elemIter == null)
                return false;
            while (!this.elemIter.hasNext() && this.collectionIter.hasNext())
                this.elemIter = this.collectionIter.next().iterator();
            return this.elemIter.hasNext();
        }

        @Override
        public T next() {
            if (this.elemIter == null)
                throw new NoSuchElementException();
            while (!this.elemIter.hasNext() && this.collectionIter.hasNext())
                this.elemIter = this.collectionIter.next().iterator();
            return this.elemIter.next();
        }

        @Override
        public void remove() {
            if (this.elemIter == null)
                throw new NoSuchElementException();
            this.elemIter.remove();
        }

    }

}
