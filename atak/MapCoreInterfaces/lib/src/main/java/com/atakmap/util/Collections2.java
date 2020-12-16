
package com.atakmap.util;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

public final class Collections2 {
    @SuppressWarnings("rawtypes")
    public final static Iterator EMPTY_ITERATOR = new EmptyIterator<Object>();

    private Collections2() {
    }

    /**
     * Returns the first element in the specified {@link Collection}. If <code>c</code> is empty,
     * {@link NoSuchElementException} will be raised.
     *
     * @param c A <code>Collection</code>
     * @return  The first element in <code>c</code>
     * @throws NoSuchElementException   If the
     */
    public static <T> T first(Collection<T> c) {
        return c.iterator().next();
    }

    public static boolean containsAny(Collection<?> c0, Collection<?> c1) {
        for (Object o : c1)
            if (c0.contains(o))
                return true;
        return false;
    }

    @SuppressWarnings("unchecked")
    public static <T> Iterator<T> emptyIterator() {
        return (Iterator<T>) EMPTY_ITERATOR;
    }

    public static boolean containsIgnoreCase(Collection<String> c, String s) {
        for (String e : c) {
            if ((e == null && s == null) ||
                    (e != null && s != null && e.equalsIgnoreCase(s))) {

                return true;
            }
        }
        return false;
    }

    public static <T> Set<T> newIdentityHashSet() {
        return Collections.<T> newSetFromMap(new IdentityHashMap<T, Boolean>());
    }

    public static <T> Set<T> newIdentityHashSet(Collection<? extends T> items) {
        Set<T> retval = newIdentityHashSet();
        retval.addAll(items);
        return retval;
    }

    /**
     * <P>NOTE: Consideration should be given to the nuances between {@link List#equals(Object)} and {@link Set#equals(Object)}, and the implications on comparing <code>Set</code>, <code>List</code>, and custom <code>Collection</code> instances when invoking this method
     *
     * @param a the first collection
     * @param b the second collection
     * @param <T> they type for each collection
     * @return true if they are equal
     */
    public static <T> boolean equals(Collection<T> a, Collection<T> b) {
        if (a == null && b == null)
            return true;
        else if (a == null)
            return false;
        else if (b == null)
            return false;
        else if (a == b)
            return true;
        else
            return a.equals(b);
    }

    /**************************************************************************/

    private final static class EmptyIterator<T> implements Iterator<T> {
        @Override
        public boolean hasNext() {
            return false;
        }

        @Override
        public T next() {
            throw new NoSuchElementException();
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

    }
}
