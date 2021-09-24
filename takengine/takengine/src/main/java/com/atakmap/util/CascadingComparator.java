package com.atakmap.util;

import java.util.Collection;
import java.util.Comparator;

public class CascadingComparator<T> implements Comparator<T> {

    protected Collection<Comparator<T>> comparators;
    
    public CascadingComparator(Collection<Comparator<T>> comparators) {
        this.comparators = comparators;
    }

    @Override
    public int compare(T lhs, T rhs) {
        int result;
        for(Comparator<T> comp : this.comparators) {
            result = comp.compare(lhs, rhs);
            if(result != 0)
                return result;
        }

        return 0;
    }    
}
