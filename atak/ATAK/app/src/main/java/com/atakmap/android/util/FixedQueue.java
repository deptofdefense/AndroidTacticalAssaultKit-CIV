
package com.atakmap.android.util;

import java.util.ArrayList;
import java.util.Objects;

/**
 * FIFO Queue of fixed size. When full bumps old items
 * @param <E>
 */
public class FixedQueue<E> extends ArrayList<E> {

    private final int limit;

    public FixedQueue(int limit) {
        this.limit = limit;
    }

    @Override
    public boolean add(E o) {
        boolean added = super.add(o);
        while (added && size() > limit) {
            super.remove(0);
        }
        return added;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        if (!super.equals(o))
            return false;
        FixedQueue<?> that = (FixedQueue<?>) o;
        return limit == that.limit;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), limit);
    }
}
