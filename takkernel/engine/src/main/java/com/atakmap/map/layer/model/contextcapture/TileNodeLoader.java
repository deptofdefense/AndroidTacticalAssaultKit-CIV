package com.atakmap.map.layer.model.contextcapture;

import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.coremap.log.Log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/** @deprecated PROTOTYPE CODE; SUBJECT TO REMOVAL AT ANY TIME; DO NOT CREATE DIRECT DEPENDENCIES */
@Deprecated
@DeprecatedApi(since = "4.1")
final class TileNodeLoader {
    private final static String TAG = "TileNodeLoader";

    private SortableMap<GLTileNode, GLTileNode.LoadContext> queuedNodes = new SortableMap<>();
    private SortableMap<GLTileNode, GLTileNode.LoadContext> prefetchNodes = new SortableMap<>();
    private Map<GLTileNode, AtomicBoolean> executingNodes = new IdentityHashMap<>();

    private boolean queueDirty = false;
    private boolean prefetchDirty = false;

    private LinkedList<Thread> workers = new LinkedList<>();
    private int numWorkerThreads;

    public TileNodeLoader(int numWorkerThreads) {
        this.numWorkerThreads = numWorkerThreads;
    }
    public synchronized void enqueue(GLTileNode node, GLTileNode.LoadContext ctx, boolean prefetch) {
        if(executingNodes.containsKey(node))
            return;
        if(queuedNodes.containsKey(node) && prefetch) {
            queuedNodes.remove(node);
            prefetchNodes.put(node, ctx);
            prefetchDirty = true;
        } else if(prefetchNodes.containsKey(node) && !prefetch) {
            prefetchNodes.remove(node);
            queuedNodes.put(node, ctx);
            queueDirty = true;
        } else if(prefetch) {
            prefetchNodes.put(node, ctx);
            prefetchDirty = true;
        } else { // !prefetch
            queuedNodes.put(node, ctx);
            queueDirty = true;
        }

        if(this.workers.size() < this.numWorkerThreads) {
            final Thread worker = new Thread(new Worker());
            worker.setPriority(Thread.NORM_PRIORITY);
            worker.start();
            this.workers.add(worker);
        }
        this.notify();
    }

    public synchronized void cancel(GLTileNode node) {
        // remove if queued
        if(queuedNodes.remove(node) != null)
            return;
        // remove if prefetch queued
        if(prefetchNodes.remove(node) != null)
            return;
        // if executing, cancel
        AtomicBoolean token = executingNodes.get(node);
        if(token != null)
            token.set(true);
    }

    public synchronized void cancelAll() {
        queuedNodes.clear();
        prefetchNodes.clear();
        for(AtomicBoolean token : executingNodes.values())
            token.set(true);
    }

    public synchronized boolean isQueued(GLTileNode node, boolean prefetch) {
        return this.executingNodes.containsKey(node) ||
                (prefetch ?
                        this.prefetchNodes.containsKey(node) :
                        this.queuedNodes.containsKey(node));
    }

    class Worker implements Runnable {

        @Override
        public void run() {
            SortableMap<GLTileNode, GLTileNode.LoadContext>[] queues = new SortableMap[] {queuedNodes, prefetchNodes};
            boolean[] dirty = new boolean[] {false, false};
            final LoadContextCompatator comp = new LoadContextCompatator();
            while(true) {
                GLTileNode node = null;
                GLTileNode.LoadContext ctx = null;
                AtomicBoolean cancelToken;
                synchronized(TileNodeLoader.this) {
                    if(queuedNodes.isEmpty() && prefetchNodes.isEmpty()) {
                        // no work left to do, exit
                        workers.remove(Thread.currentThread());
                        break;
                    }

                    dirty[0] = queueDirty;
                    dirty[1] = prefetchDirty;

                    // pull the node to load -- first check the queued nodes, then prefetch
                    for(int i = 0; i < queues.length; i++) {
                        if(queues[i].isEmpty())
                            continue;
                        if(dirty[i])
                            queues[i].sort(comp);
                        Iterator<Map.Entry<GLTileNode, GLTileNode.LoadContext>> it = queues[i].entrySet().iterator();
                        Map.Entry<GLTileNode, GLTileNode.LoadContext> entry = it.next();
                        node = entry.getKey();
                        ctx = entry.getValue();
                        it.remove();
                        break;
                    }

                    if(node == null)
                        continue; // illegal state

                    cancelToken = new AtomicBoolean(false);
                    executingNodes.put(node, cancelToken);
                }

                try {
                    node.asyncLoad(ctx, cancelToken);
                } catch(Throwable t) {
                    Log.e(TAG, "Failed to load node " + node, t);
                } finally {
                    synchronized(TileNodeLoader.this) {
                        executingNodes.remove(node);
                    }
                }
            }
        }
    }

    private static class LoadContextCompatator implements Comparator<GLTileNode.LoadContext> {

        @Override
        public int compare(GLTileNode.LoadContext lhs, GLTileNode.LoadContext rhs) {
            // XXX - base on camera distance ???

            if(lhs.gsd > rhs.gsd)
                return -1;
            else if(lhs.gsd < rhs.gsd)
                return 1;
            else
                return lhs.id-rhs.id; // create order
        }
    }

    private static <K, V> Map<K, V> sort(Map<K, V> src, final Comparator<V> comp) {
        ArrayList<Map.Entry<K, V>> entries = new ArrayList<>(src.entrySet());
        Collections.sort(entries, new Comparator<Map.Entry<K, V>>() {
            public int compare(Map.Entry<K, V> lhs, Map.Entry<K, V> rhs) {
                return comp.compare(lhs.getValue(), rhs.getValue());
            }
        });
        LinkedHashMap<K, V> retval = new LinkedHashMap<>(entries.size());
        for(Map.Entry<K, V> entry : entries)
            retval.put(entry.getKey(), entry.getValue());
        return retval;
    }

    private static class SortableMap<K, V> implements Map<K, V> {

        Map<K, V> impl;

        public SortableMap() {
            this(new IdentityHashMap<K, V>());
        }

        public SortableMap(Map<K, V> impl) {
            this.impl = impl;
        }

        public void sort(final Comparator<V> comp) {
            impl = TileNodeLoader.sort(impl, comp);
        }

        @Override
        public void clear() {
            impl.clear();
        }

        @Override
        public boolean containsKey(Object key) {
            return impl.containsKey(key);
        }

        @Override
        public boolean containsValue(Object value) {
            return impl.containsValue(value);
        }

        @Override
        public Set<Entry<K, V>> entrySet() {
            return impl.entrySet();
        }

        @Override
        public V get(Object key) {
            return impl.get(key);
        }

        @Override
        public boolean isEmpty() {
            return impl.isEmpty();
        }

        @Override
        public Set<K> keySet() {
            return impl.keySet();
        }

        @Override
        public V put(K key, V value) {
            return impl.put(key, value);
        }

        @Override
        public void putAll(Map<? extends K, ? extends V> map) {
            impl.putAll(map);
        }

        @Override
        public V remove(Object key) {
            return impl.remove(key);
        }

        @Override
        public int size() {
            return impl.size();
        }

        @Override
        public Collection<V> values() {
            return impl.values();
        }
    }
}
