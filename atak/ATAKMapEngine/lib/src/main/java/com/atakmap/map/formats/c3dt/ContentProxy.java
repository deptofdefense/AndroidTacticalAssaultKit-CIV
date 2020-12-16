package com.atakmap.map.formats.c3dt;

import com.atakmap.util.Collections2;

import java.util.LinkedList;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ContentProxy implements ContentSource, Runnable {
    final ContentContainer cache;
    final ContentSource source;

    final LinkedList<String> queue = new LinkedList<>();
    final Set<OnContentChangedListener> listeners = Collections2.newIdentityHashSet();

    ExecutorService executor;

    boolean connected;

    public ContentProxy(ContentSource source, ContentContainer cache) {
        this.source = source;
        this.cache = cache;
        this.connected = false;
    }

    @Override
    public byte[] getData(String uri, long[] version) {
        return this.getData(uri, version, true);
    }

    public byte[] getData(String uri, long[] version, boolean async) {
        // check cache
        final byte[] cached = this.cache.getData(uri, version);
        if(cached != null)
            return cached;
        // if miss, make request against source
        if(async) {
            synchronized (this) {
                if (this.connected) {
                    this.queue.add(uri);
                    this.notifyAll();
                }
            }

            return null;
        } else {
            final byte[] data = this.source.getData(uri, version);
            if(data != null)
                this.cache.put(uri, data, (version != null) ? version[0] : System.currentTimeMillis());
            return data;
        }
    }

    @Override
    public synchronized void addOnContentChangedListener(OnContentChangedListener l) {
        this.listeners.add(l);
    }

    @Override
    public synchronized void removeOnContentChangedListener(OnContentChangedListener l) {
        this.listeners.remove(l);
    }

    @Override
    public synchronized void connect() {
        if(this.connected)
            return;
        this.source.connect();
        this.cache.connect();
        this.connected = true;
        final int numWorkers = 3;
        this.executor = Executors.newFixedThreadPool(numWorkers);
        for(int i = 0; i < numWorkers; i++)
            this.executor.submit(this);
    }

    @Override
    public synchronized void disconnect() {
        if(!this.connected)
            return;
        this.source.disconnect();
        this.cache.disconnect();
        this.connected = false;
        this.notifyAll();

        this.executor.shutdown();
        this.executor = null;
    }

    @Override
    public void run() {
        long[] version = new long[1];
        boolean notify = false;
        while(true) {
            String fetchUri;
            synchronized (this) {
                if(notify) {
                    for(OnContentChangedListener l : this.listeners) {
                        l.onContentChanged(ContentProxy.this);
                    }
                }
                if(!this.connected)
                    break;
                if(this.queue.isEmpty()) {
                    try {
                        this.wait();
                    } catch(InterruptedException ignored) {}
                    continue;
                }

                // popping the last element (FILO)
                fetchUri = this.queue.removeLast();
            }

            final byte[] fetched = this.source.getData(fetchUri, version);
            if(fetched != null) {
                this.cache.put(fetchUri, fetched, version[0]);
                notify = true;
            }
        }
    }
}
