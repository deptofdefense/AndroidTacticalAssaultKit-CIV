package com.atakmap.map.hittest;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * A hit test filter that contains a whitelist of acceptable result classes
 */
public class ClassResultFilter implements HitTestResultFilter {

    private final Set<Class<?>> classes;

    public ClassResultFilter(Class<?>... classes) {
        this.classes = new HashSet<>(Arrays.asList(classes));
    }

    @Override
    public boolean acceptClass(Class<?> cl) {
        return this.classes.contains(cl);
    }
}
