
package com.atakmap.android.data;

/**
 * Allows for priority-based sorting with resolvers, providers, etc.
 */
public interface URIContentPriority {

    // Default priority levels
    int LOWEST = 0, VERY_LOW = 10, LOW = 25, DEFAULT = 50, HIGH = 75,
            VERY_HIGH = 90, HIGHEST = 100;

    /**
     * Get the priority of this URI content manager
     * @return Priority from 0-100 (outer values will be clamped)
     */
    int getPriority();
}
