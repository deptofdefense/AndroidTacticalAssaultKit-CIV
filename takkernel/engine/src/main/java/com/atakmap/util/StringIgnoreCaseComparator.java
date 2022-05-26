package com.atakmap.util;

import java.util.Comparator;

public final class StringIgnoreCaseComparator implements Comparator<String> {
    public final static Comparator<String> INSTANCE = new StringIgnoreCaseComparator();

    private StringIgnoreCaseComparator() {}
    
    @Override
    public int compare(String lhs, String rhs) {
        return lhs.compareToIgnoreCase(rhs);
    }
}
