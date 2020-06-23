package transapps.mapi.events;

import transapps.mapi.MapView;

/*
 * Tagging interface for map events
 *
 * @author Theodore Hong
 */
public abstract class MapEvent {
    
    /**
     * @author mriley
     *
     * @param <T>
     */
    protected static abstract class Pool<T extends MapEvent> {
        private static final int MAX_POOL_SIZE = 10;
        private final Object recyclerLock = new Object();
        private int recyclerUsed;
        private T recyclerTop;
        
        @SuppressWarnings("unchecked")
        public T obtain() {
            final T ev;
            synchronized (recyclerLock) {
                ev = recyclerTop;
                if (ev == null) {
                    return create();
                }
                recyclerTop = (T) ev.next;
                recyclerUsed -= 1;
            }
            ev.next = null;
            ev.prepareForReuse();
            return ev;
        }
        
        @SuppressWarnings("unchecked")
        public void recycle( MapEvent event ) {
            synchronized (recyclerLock) {
                if (recyclerUsed < MAX_POOL_SIZE) {
                    recyclerUsed++;
                    event.next = recyclerTop;
                    recyclerTop = (T) event;
                }
            }
        }
        
        protected abstract T create();
    }

    
    private MapView source;
    protected MapEvent next;
    private final Pool<?> pool;

    public MapEvent(Pool<?> pool) {
        this.pool = pool;
    }

    protected void setSource(MapView source) {
        this.source = source;
    }

    public MapView getSource() {
        return source;
    }
    
    protected void prepareForReuse() {
        source = null;
    }
    
    public void recycle() {
        pool.recycle(this);
    }
}
