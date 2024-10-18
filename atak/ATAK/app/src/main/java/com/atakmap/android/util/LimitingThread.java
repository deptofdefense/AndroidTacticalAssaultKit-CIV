
package com.atakmap.android.util;

import com.atakmap.coremap.log.Log;
import gov.tak.api.util.Disposable;

/**
 * Provides a processing capability where the action will only be run as fast as it can no matter how many calls to 
 * exec() are performed - but will guarantee the last most call to exec() will execute the run method of the runnable
 * passed in.
 */
public class LimitingThread implements Runnable, Disposable {

    public static final String TAG = "LimitingThread";

    private boolean disposed;
    private int state;
    private final Thread thread;
    private final Runnable r;
    private final String name;

    /**
     * Construct a limiting thread with a name and a runnable which will be the worker code
     * that is executed whenever the exec call is made on the limiting thread.    If the system
     * cannot keep up, intermediate executions are dropped and only the last exec is executed.
     * @param name the name of the limiting thread
     * @param r the runnable to be used that contains the logic to be run on the limiting thread.
     */
    public LimitingThread(final String name, final Runnable r) {
        this.disposed = false;
        this.state = 0;
        this.r = r;
        this.name = name;

        this.thread = new Thread(this);
        this.thread.setPriority(Thread.NORM_PRIORITY);
        this.thread.setName(name);
    }

    /**
     * Called to perform the action as defined in the runnable used during creation.
     * It is guaranteed that the action will be executed at least once of the runnable.
     * If multiple calls are made, the execution of a runnable may be dropped in order for the
     * limting thread to catch up.
     */
    public synchronized void exec() {
        if (state == 0)
            this.thread.start();
        this.state++;
        this.notify();
    }

    /**
     * Perform disposal of the limiting thread, canceling any future calls to execute.
     * @param join if join is true, try to wait for the execution to finish of the last
     *             call to exec.
     */
    public void dispose(boolean join) {
        synchronized (this) {
            this.disposed = true;
            this.notify();
        }
        if (join) {
            try {
                this.thread.join();
            } catch (InterruptedException ignored) {
            }
        }
    }

    @Override
    public void dispose() {
        dispose(true);
    }

    @Override
    public void run() {
        try {
            int compute = 0;
            while (true) {
                synchronized (this) {
                    if (this.disposed)
                        break;
                    if (compute == this.state) {
                        try {
                            this.wait();
                        } catch (InterruptedException ignored) {
                        }
                        continue;
                    }
                    compute = this.state;
                }

                try {
                    r.run();
                } catch (Exception e) {
                    Log.e(TAG, "error occurred during the run method for: "
                            + name, e);
                }

            }
        } finally {

        }
    }
}
