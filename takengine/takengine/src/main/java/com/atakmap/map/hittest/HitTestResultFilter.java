package com.atakmap.map.hittest;

/**
 * Used to filter out results that are not applicable to a given hit test query
 * before performing the hit test
 */
public interface HitTestResultFilter {

    /**
     * Test whether a given result class is accepted by this filter
     *
     * @param cl Class
     * @return True if acceptable
     */
    default boolean acceptClass(Class<?> cl) {
        return true;
    }

    /**
     * Test whether a given result subject is accepted by this filter
     * By default this defers to {@link #acceptClass(Class)}
     *
     * @param subject Subject (object instance)
     * @return True if acceptable
     */
    default boolean acceptSubject(Object subject) {
        return subject != null && acceptClass(subject.getClass());
    }
}
