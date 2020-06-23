package com.atakmap.util;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

public final class DirectExecutorService extends AbstractExecutorService {

    private int tasks;
    private boolean shutdown;

    public DirectExecutorService() {
        this.tasks = 0;
        this.shutdown = false;
    }

    @Override
    public synchronized boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        // XXX - interface does not define what the expected behavior is if a
        //       shutdown request has not been made. the working interpretation
        //       of the documentation will be that blocking only occurs AFTER
        //       a shutdown request has been made, in the case where no shutdown
        //       request is made, the method will not block and will immediately
        //       return false as the executor is not terminated.
        if(this.shutdown)
        if(this.tasks > 0)
            this.wait(unit.toMillis(timeout));
        return this.shutdown && (this.tasks<1);
    }

    @Override
    public synchronized boolean isShutdown() {
        return this.shutdown;
    }

    @Override
    public synchronized boolean isTerminated() {
        return this.shutdown && (this.tasks<1);
    }

    @Override
    public synchronized void shutdown() {
        this.shutdown = true;
        this.notify();
    }

    @Override
    public synchronized List<Runnable> shutdownNow() {
        this.shutdown = true;
        this.notify();
        return Collections.<Runnable>emptyList();
    }

    @Override
    public void execute(Runnable command) {
        if(command == null)
            throw new NullPointerException();
        synchronized(this) {
            if(this.shutdown)
                throw new RejectedExecutionException();
            this.tasks++;
        }
        command.run();
        synchronized(this) {
            this.tasks--;
            this.notify();
        }
    }
}
