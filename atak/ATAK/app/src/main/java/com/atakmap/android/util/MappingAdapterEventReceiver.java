
package com.atakmap.android.util;

/**
 * Used to receive events from an instance of {@link MappingVH}
 * @param <T> Class that implements {@link MappingVM}
 */
public interface MappingAdapterEventReceiver<T> {
    void eventReceived(T event);
}
