
package com.atakmap.coremap.concurrent;

import java.util.concurrent.ThreadFactory;

public class NamedThreadFactory implements ThreadFactory {

    private int count = 0;
    private final String name;

    /**
     * Constructs a Thread factory with a specific name and count for each thread created to assist
     * in debugging.
     * .
     * @param name the name of the threads created by this factory.
     */
    public NamedThreadFactory(final String name) {
        this.name = name;
    }

    @Override
    public Thread newThread(final Runnable r) {
        return new Thread(r, name + "-" + count++);
    }
}
