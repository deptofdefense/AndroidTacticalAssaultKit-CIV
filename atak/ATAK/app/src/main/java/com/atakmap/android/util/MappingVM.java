
package com.atakmap.android.util;

public class MappingVM implements Comparable<MappingVM> {

    @Override
    public int compareTo(MappingVM o) {
        return this.getClass().getName().compareTo(o.getClass().getName());
    }
}
