package android.util;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public class LruCache<K, V> {
    private Map<K, V> impl = new LinkedHashMap<K, V>(16, 0.75f, true);
    private int maxSize;
    private int size;

    public LruCache(int maxSize) {
        this.maxSize = maxSize;
        this.size = 0;
    }

    public V get(K key) {
        return impl.get(key);
    }

    public V put(K key, V value) {
        final V retval = impl.put(key, value);
        if(retval != null) {
            entryRemoved(false, key, retval, value);
        }
        if(retval != null) {
            size -= sizeOf(key, retval);
        }
        size += sizeOf(key, value);
        trimToSize(maxSize);
        return retval;
    }

    public void trimToSize(int maxSize) {
        Iterator<Map.Entry<K, V>> it = impl.entrySet().iterator();
        int retainedSize = 0;
        while(size > maxSize && it.hasNext()) {
            Map.Entry<K, V> e = it.next();
            final int entrySize = sizeOf(e.getKey(), e.getValue());
            if(retainedSize+entrySize <= maxSize) {
                retainedSize += entrySize;
            } else {
                // the retained size plus the entry is over the limit, evict
                it.remove();
            }
        }
        size = retainedSize;
    }

    public int maxSize() {
        return maxSize;
    }
    protected int sizeOf(K key, V value) {
        return 1;
    }
    protected void entryRemoved(boolean evicted, K key, V oldValue, V newValue) {

    }
}
