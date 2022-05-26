package com.atakmap.spi;

public interface PriorityServiceProvider<T, V> extends ServiceProvider<T, V> {

    /**
     * The priority for the Service Provider.  Note - the larger the number, the higher priority so a
     * Service with a priority of 5 will be examined prior to a service with the priority of 3.
     * @return the priority of the service
     */
    public int getPriority();
}
