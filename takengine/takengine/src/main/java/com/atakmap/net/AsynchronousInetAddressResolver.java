
package com.atakmap.net;

import java.io.IOException;
import java.net.InetAddress;

public class AsynchronousInetAddressResolver implements Runnable {

    private final String host;
    private InetAddress address;
    private IOException error;
    private boolean initialized;
    private final Thread thread;

    public AsynchronousInetAddressResolver(String host) {
        this.host = host;
        this.initialized = false;
        this.address = null;
        this.error = null;

        this.thread = new Thread(this);
        this.thread.setPriority(Thread.NORM_PRIORITY);
        this.thread.setName("async-addr-resolver [" + this.host + "]");
        this.thread.start();
    }

    /**
     * @param timeout - in ms
     * @return - the Inet address of the host, or null
     * @throws IOException if a network IO error has occurred
     */
    public synchronized InetAddress get(long timeout) throws IOException {
        if (!this.initialized)
            try {
                this.wait(timeout);
            } catch (InterruptedException ignored) {
            }
        if (this.error != null)
            throw error;
        return this.address;
    }

    @Override
    public void run() {
        if (this.thread != Thread.currentThread())
            throw new IllegalStateException();
        try {

            // This is used to for DNS resolution of the hostname.   This case cannot be 
            // mitigated without breaking functionality.
            this.address = InetAddress.getByName(this.host);
        } catch (IOException e) {
            this.error = e;
        } catch (Throwable t) {
            this.error = new IOException(t);
        } finally {
            synchronized (this) {
                this.initialized = true;
                this.notify();
            }
        }
    }
}
