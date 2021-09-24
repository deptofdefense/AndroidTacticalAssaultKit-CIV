
package com.atakmap.util;

public final class ReadWriteLock {
    private int readers;
    private int writers;
    private Thread writeLockHolder;

    public ReadWriteLock() {
        this.readers = 0;
        this.writers = 0;
        this.writeLockHolder = null;
    }

    public synchronized void acquireRead() {
        while (this.writers > 0)
            try {
                this.wait();
            } catch (InterruptedException ignored) {
            }

        this.readers++;
    }

    public synchronized void releaseRead() {
        if (this.readers == 0)
            throw new IllegalStateException();
        this.readers--;
        this.notifyAll();
    }

    public synchronized void acquireWrite() {
        final Thread writeLockRequester = Thread.currentThread();

        while (this.readers > 0 ||
                (this.writeLockHolder != null &&
                        this.writeLockHolder != writeLockRequester &&
                        this.writers > 0)) {

            try {
                this.wait();
            } catch (InterruptedException ignored) {
            }
        }

        this.writers++;
        this.writeLockHolder = writeLockRequester;
    }

    public synchronized void releaseWrite() {
        if (this.writers == 0)
            throw new IllegalStateException();
        this.writers--;
        if (this.writers == 0)
            this.writeLockHolder = null;
        this.notifyAll();
    }
}
