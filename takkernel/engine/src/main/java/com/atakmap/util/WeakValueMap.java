
package com.atakmap.util;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.lang.reflect.Array;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import com.atakmap.lang.Objects;

public final class WeakValueMap<K, V> implements Map<K, V> {

    Map<K, WeakReference<V>> impl;
    Map<WeakReference<V>, K> refToKey;
    private final ReferenceQueue<V> queue;
    private Thread monitorThread;
    private boolean async;

    public WeakValueMap() {
        this(false);
    }

    public WeakValueMap(boolean async) {
        this(async, new HashMap<K, WeakReference<V>>());
    }

    public WeakValueMap(boolean async, Map<K, WeakReference<V>> impl) {
        this.impl = impl;
        this.refToKey = new IdentityHashMap<WeakReference<V>, K>();
        this.queue = new ReferenceQueue<V>();
        this.async = async;
    }

    private void initMonitorNoSync() {
        if (this.monitorThread == null) {
            this.monitorThread = new Thread(new QueueMonitor<K, V>(this, this.queue));
            this.monitorThread.setName("WeakValueMap$QueueMonitor@"
                    + Integer.toString(this.hashCode(), 16));
            this.monitorThread.setPriority(Thread.MIN_PRIORITY);
            this.monitorThread.setDaemon(true);
            this.monitorThread.start();
        }
    }

    private void validateNoSync() {
        Reference<? extends V> released;
        K key;
        do {
            released = this.queue.poll();
            if(released == null)
                break;
            key = this.refToKey.remove(released);
            if(key != null)
                this.impl.remove(key);
        } while(true);
    }
    @Override
    public synchronized void clear() {
        for (WeakReference<V> ref : this.refToKey.keySet())
            ref.clear();
        this.refToKey.clear();
        this.impl.clear();
    }

    @Override
    public synchronized boolean containsKey(Object key) {
        if(!this.async)
            this.validateNoSync();
        return this.impl.containsKey(key);
    }

    @Override
    public synchronized boolean containsValue(Object value) {
        if (value == null)
            return false;

        if(!this.async)
            this.validateNoSync();

        Iterator<WeakReference<V>> iter = this.impl.values().iterator();
        V obj;
        while (iter.hasNext()) {
            obj = iter.next().get();
            if (obj != null && Objects.equals(value, iter.next()))
                return true;
        }
        return false;
    }

    @Override
    public Set<Map.Entry<K, V>> entrySet() {
        if(!this.async)
            this.validateNoSync();
        return new EntryWrapper();
    }

    @Override
    public synchronized V get(Object key) {
        if(!this.async)
            this.validateNoSync();
        final WeakReference<V> ref = this.impl.get(key);
        if (ref == null)
            return null;
        return ref.get();
    }

    @Override
    public boolean isEmpty() {
        if(!this.async)
            this.validateNoSync();
        return this.impl.isEmpty();
    }

    @Override
    public Set<K> keySet() {
        if(!this.async)
            this.validateNoSync();
        return this.impl.keySet();
    }

    @Override
    public synchronized V put(K key, V value) {
        if (key == null || value == null)
            throw new NullPointerException();

        if(this.async) {
            this.initMonitorNoSync();
        } else {
            this.validateNoSync();
        }

        final WeakReference<V> old = this.impl.put(key, new WeakReference<V>(value, this.queue));
        if (old == null)
            return null;
        return old.get();
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> map) {
        if(this.async) {
            this.initMonitorNoSync();
        } else {
            this.validateNoSync();
        }

        for (Map.Entry<? extends K, ? extends V> entry : map.entrySet())
            if (entry.getValue() != null)
                this.impl.put(entry.getKey(), new WeakReference<V>(entry.getValue(), this.queue));
    }

    @Override
    public synchronized V remove(Object key) {
        if(!this.async)
            this.validateNoSync();
        final WeakReference<V> ref = this.impl.remove(key);
        if (ref == null)
            return null;
        return ref.get();
    }

    @Override
    public int size() {
        if(!this.async)
            this.validateNoSync();
        return this.impl.size();
    }

    @Override
    public Collection<V> values() {
        if(!this.async)
            this.validateNoSync();
        return new ValuesWrapper();
    }

    /**************************************************************************/

    private class EntryWrapper implements Set<Map.Entry<K, V>> {

        private final Set<Map.Entry<K, WeakReference<V>>> impl;

        public EntryWrapper() {
            this.impl = WeakValueMap.this.impl.entrySet();
        }

        @Override
        public boolean add(Map.Entry<K, V> object) {
            if (object.getValue() == null)
                return false;

            WeakValueMap.this.impl.put(object.getKey(), new WeakReference<V>(object.getValue(),
                    WeakValueMap.this.queue));
            return true;
        }

        @Override
        public boolean addAll(Collection<? extends Map.Entry<K, V>> collection) {
            boolean retval = false;
            for (Map.Entry<K, V> object : collection)
                retval |= this.add(object);
            return retval;
        }

        @Override
        public void clear() {
            WeakValueMap.this.clear();
        }

        @Override
        public boolean contains(Object object) {
            if (!(object instanceof Map.Entry))
                return false;
            @SuppressWarnings("rawtypes")
            final Map.Entry entry = (Map.Entry) object;
            if (entry.getValue() == null)
                return false;
            return (Objects.equals(entry.getValue(), WeakValueMap.this.get(entry.getKey())));
        }

        @Override
        public boolean containsAll(Collection<?> collection) {
            boolean retval = true;
            for (Object object : collection)
                retval &= this.contains(object);
            return retval;
        }

        @Override
        public boolean isEmpty() {
            return WeakValueMap.this.isEmpty();
        }

        @Override
        public Iterator<java.util.Map.Entry<K, V>> iterator() {
            return new EntryIteratorWrapper(this.impl.iterator());
        }

        @Override
        public boolean remove(Object object) {
            if (!(object instanceof Map.Entry))
                return false;
            @SuppressWarnings("rawtypes")
            final Map.Entry entry = (Map.Entry) object;
            if (entry.getValue() == null)
                return false;
            synchronized (WeakValueMap.this) {
                if (Objects.equals(entry.getValue(), WeakValueMap.this.get(entry.getKey()))) {
                    WeakValueMap.this.remove(entry.getKey());
                    return true;
                } else {
                    return false;
                }
            }
        }

        @Override
        public boolean removeAll(Collection<?> collection) {
            boolean retval = false;
            Iterator<Map.Entry<K, V>> iter = this.iterator();
            synchronized (WeakValueMap.this) {
                while (iter.hasNext()) {
                    if (collection.contains(iter.next())) {
                        iter.remove();
                        retval = true;
                    }
                }
            }
            return retval;
        }

        @Override
        public boolean retainAll(Collection<?> collection) {
            boolean retval = false;
            Iterator<Map.Entry<K, V>> iter = this.iterator();
            synchronized (WeakValueMap.this) {
                while (iter.hasNext()) {
                    if (!collection.contains(iter.next())) {
                        iter.remove();
                        retval = true;
                    }
                }
            }
            return retval;
        }

        @Override
        public int size() {
            return WeakValueMap.this.size();
        }

        @Override
        public Object[] toArray() {
            final Object[] retval;
            Iterator<Map.Entry<K, V>> iter = this.iterator();
            synchronized (WeakValueMap.this) {
                retval = new Object[this.size()];
                int idx = 0;
                while (iter.hasNext())
                    retval[idx++] = iter.next();
            }
            return retval;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T[] toArray(T[] array) {
            Iterator<Map.Entry<K, V>> iter = this.iterator();
            synchronized (WeakValueMap.this) {
                final int size = this.size();
                if (array.length < size)
                    array = (T[]) Array.newInstance(array.getClass().getComponentType(), size);

                Object[] retval = array;
                int idx = 0;
                while (iter.hasNext())
                    retval[idx++] = iter.next();
            }
            return array;
        }
    }

    private class ValuesWrapper implements Collection<V> {

        private final Collection<WeakReference<V>> impl;

        public ValuesWrapper() {
            this.impl = WeakValueMap.this.impl.values();
        }

        @Override
        public boolean add(V object) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean addAll(Collection<? extends V> collection) {
            Iterator<? extends V> iter = collection.iterator();
            boolean retval = false;
            while (iter.hasNext())
                retval |= this.add(iter.next());
            return retval;
        }

        @Override
        public void clear() {
            WeakValueMap.this.clear();
        }

        @Override
        public boolean contains(Object object) {
            if (object == null)
                return false;

            Iterator<V> iter = this.iterator();
            synchronized (WeakValueMap.this) {
                while (iter.hasNext())
                    if (Objects.equals(object, iter.next()))
                        return true;
            }
            return true;
        }

        @Override
        public boolean containsAll(Collection<?> collection) {
            for (Object object : collection)
                if (!this.contains(object))
                    return false;
            return true;
        }

        @Override
        public boolean isEmpty() {
            return WeakValueMap.this.isEmpty();
        }

        @Override
        public Iterator<V> iterator() {
            return new DereferencingIterator<V>(this.impl.iterator());
        }

        @Override
        public boolean remove(Object object) {
            if (object == null)
                return false;

            Iterator<V> iter = this.iterator();
            synchronized (WeakValueMap.this) {
                while (iter.hasNext()) {
                    if (Objects.equals(object, iter.next())) {
                        iter.remove();
                        return true;
                    }
                }
            }
            return false;
        }

        @Override
        public boolean removeAll(Collection<?> collection) {
            boolean retval = false;
            Iterator<V> iter = this.iterator();
            synchronized (WeakValueMap.this) {
                V obj;
                while (iter.hasNext()) {
                    obj = iter.next();
                    if (obj != null && collection.contains(obj)) {
                        iter.remove();
                        retval = true;
                    }
                }
            }
            return retval;
        }

        @Override
        public boolean retainAll(Collection<?> collection) {
            boolean retval = false;
            Iterator<V> iter = this.iterator();
            synchronized (WeakValueMap.this) {
                V obj;
                while (iter.hasNext()) {
                    obj = iter.next();
                    if (obj != null && !collection.contains(obj)) {
                        iter.remove();
                        retval = true;
                    }
                }
            }
            return retval;
        }

        @Override
        public int size() {
            return WeakValueMap.this.size();
        }

        @Override
        public Object[] toArray() {
            final Object[] retval;
            Iterator<V> iter = this.iterator();
            synchronized (WeakValueMap.this) {
                retval = new Object[this.size()];
                int idx = 0;
                while (iter.hasNext())
                    retval[idx++] = iter.next();
            }
            return retval;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T[] toArray(T[] array) {
            Iterator<V> iter = this.iterator();
            synchronized (WeakValueMap.this) {
                final int size = this.size();
                if (array.length < size)
                    array = (T[]) Array.newInstance(array.getClass().getComponentType(), size);

                Object[] retval = array;
                int idx = 0;
                while (iter.hasNext())
                    retval[idx++] = iter.next();
            }
            return array;
        }

    }

    private class EntryIteratorWrapper implements Iterator<Map.Entry<K, V>> {

        private final Iterator<Map.Entry<K, WeakReference<V>>> impl;

        public EntryIteratorWrapper(Iterator<Map.Entry<K, WeakReference<V>>> impl) {
            this.impl = impl;
        }

        @Override
        public boolean hasNext() {
            return this.impl.hasNext();
        }

        @Override
        public Map.Entry<K, V> next() {
            final Map.Entry<K, WeakReference<V>> entry = this.impl.next();
            return new AbstractMap.SimpleEntry<K, V>(entry.getKey(), entry.getValue().get());
        }

        @Override
        public void remove() {
            this.impl.remove();
        }
    }

    private static class QueueMonitor<K, V> implements Runnable {
        private final WeakReference<WeakValueMap<K, V>> mapRef;
        private final ReferenceQueue<V> queue;

        public QueueMonitor(WeakValueMap<K, V> map, ReferenceQueue<V> queue) {
            this.mapRef = new WeakReference<WeakValueMap<K, V>>(map);
            this.queue = queue;
        }

        @Override
        public void run() {
            Reference<? extends V> ref;
            while (true) {
                // XXX -
                try {
                    ref = this.queue.remove();
                } catch (InterruptedException e) {
                    continue;
                }

                {
                    WeakValueMap<K, V> map = this.mapRef.get();
                    if (map == null)
                        break;
                    synchronized (map) {
                        final K key = map.refToKey.remove(ref);
                        if (key != null)
                            map.impl.remove(key);
                    }
                    map = null;
                }
            }
        }
    }
}
