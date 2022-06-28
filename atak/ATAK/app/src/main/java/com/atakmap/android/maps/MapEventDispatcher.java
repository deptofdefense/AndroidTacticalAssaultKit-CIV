
package com.atakmap.android.maps;

import com.atakmap.coremap.log.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Central dispatch point for {@link com.atakmap.android.maps.MapEvent}s. The
 * {@link com.atakmap.android.maps.MapView} encapsulates an instance of MapEventDispatcher
 * available through {@link com.atakmap.android.maps.MapView#getMapEventDispatcher()}.
 * 
 * 
 */
public class MapEventDispatcher {

    public static final String TAG = "MapEventDispatcher";

    /**
     * 
     */
    public interface MapEventDispatchListener {
        void onMapEvent(MapEvent event);
    }

    public interface OnMapEventListener {
        void onMapItemMapEvent(MapItem item, MapEvent event);
    }

    private final LinkedList<HashMap<String, _Listeners>> _listenerStack;
    private final Map<Long, ConcurrentLinkedQueue<OnMapEventListener>> itemListeners;

    public MapEventDispatcher() {
        _listenerStack = new LinkedList<>();
        _listenerStack.add(new HashMap<String, _Listeners>());
        this.itemListeners = new HashMap<>();
    }

    private HashMap<String, _Listeners> peekNoSync() {
        return _listenerStack.getLast();
    }

    public void pushListeners() {
        //Log.d(TAG, "call to pushListeners()", new Exception());
        HashMap<String, _Listeners> copy = new HashMap<>();
        Set<Entry<String, _Listeners>> entries = peekNoSync().entrySet();
        for (Entry<String, _Listeners> e : entries) {
            copy.put(e.getKey(), new _Listeners(e.getValue()));
        }
        synchronized (_listenerStack) {
            _listenerStack.add(copy);
        }

    }

    public void popListeners() {
        //Log.d(TAG, "call to popListeners()", new Exception());
        synchronized (_listenerStack) {
            if (_listenerStack.size() > 1) {
                _listenerStack.removeLast();
            }
        }

    }

    /**
     * Only clear listeners that directly manipulate the map.
     * @param clearMapScroll to clear the map scroll behavior
     */
    public void clearUserInteractionListeners(boolean clearMapScroll) {
        //Log.d(TAG, "call to clearUserInteractionListeners", new Exception());
        // clear all the listeners listening for a click
        clearListeners(MapEvent.ITEM_CLICK);
        clearListeners(MapEvent.MAP_CLICK);
        clearListeners(MapEvent.MAP_CONFIRMED_CLICK);

        clearListeners(MapEvent.MAP_LONG_PRESS);
        clearListeners(MapEvent.ITEM_LONG_PRESS);

        clearListeners(MapEvent.ITEM_PRESS);
        clearListeners(MapEvent.ITEM_RELEASE);
        if (clearMapScroll) {
            clearListeners(MapEvent.MAP_SCROLL);
            clearListeners(MapEvent.MAP_SCALE);
        }
    }

    public void clearListeners() {
        //Log.d(TAG, "clearListeners()", new Exception());
        peekNoSync().clear();
    }

    public void clearListeners(String listenerType) {
        //Log.d(TAG, "call to clearListeners(type)", new Exception());
        _Listeners l = peekNoSync().get(listenerType);
        if (l != null && l.list != null) {
            l.list.clear();
        }
    }

    /**
     * Use this if you only if want to listen to all MapEvents fired in ATAK
     * 
     * @param l
     */
    public void addMapEventListener(MapEventDispatchListener l) {
        //Log.d(TAG, "call to addMapEventListener()", new Exception());

        Field[] fields = MapEvent.class.getDeclaredFields();
        for (Field f : fields) {
            if (Modifier.isStatic(f.getModifiers())
                    && Modifier.isFinal(f.getModifiers())) {
                try {
                    addMapEventListener(f.get(f).toString(), l);
                } catch (IllegalArgumentException | IllegalAccessException e) {
                    Log.e(TAG, "error: ", e);
                }

            }
        }
    }

    /**
     * Use this if you only if want to remove a listener for registered using 
     * addMapEventListener
     * 
     * @param l the map event dispatch listener to remove
     */
    public void removeMapEventListener(MapEventDispatchListener l) {
        //Log.d(TAG, "call to addMapEventListener()", new Exception());

        Field[] fields = MapEvent.class.getDeclaredFields();
        for (Field f : fields) {
            if (Modifier.isStatic(f.getModifiers())
                    && Modifier.isFinal(f.getModifiers())) {
                try {
                    removeMapEventListener(f.get(f).toString(), l);
                } catch (IllegalArgumentException | IllegalAccessException e) {
                    Log.e(TAG, "error: ", e);
                }

            }
        }
    }

    /**
     * @param eventType
     * @param l
     */
    public void addMapEventListenerToBase(String eventType,
            MapEventDispatchListener l) {
        //Log.d(TAG, "call to addMapEventListener()", new Exception());
        _Listeners ll;
        synchronized (_listenerStack) {
            ll = _listenerStack.get(0).get(eventType);
            if (ll == null) {
                ll = new _Listeners();
                _listenerStack.get(0).put(eventType, ll);
            }
        }
        if (!ll.list.contains(l)) {
            ll.list.add(l);
        } else {
            Log.e(TAG, "attempt to add a duplicative listener" + l.getClass(),
                    new Exception());
        }
    }

    public void removeMapEventListenerFromBase(String eventType,
            MapEventDispatchListener l) {
        _Listeners ll = null;
        synchronized (_listenerStack) {
            ll = _listenerStack.get(0).get(eventType);
        }
        if (ll != null) {
            ll.list.remove(l);
        }
    }

    /**
     * @param eventType
     * @param l
     */
    public void addMapEventListener(String eventType,
            MapEventDispatchListener l) {
        _Listeners ll = getListenersNoSync(eventType, true);

        if (!ll.list.contains(l)) {
            ll.list.add(l);
        } else {
            Log.e(TAG, "attempt to add a duplicative listener" + l.getClass(),
                    new Exception());
        }
    }

    public void removeMapEventListener(String eventType,
            MapEventDispatchListener l) {
        _Listeners ll = getListenersNoSync(eventType, false);
        if (ll != null) {
            ll.list.remove(l);
        }
    }

    public boolean hasMapEventListener(String eventType) {
        _Listeners ll = getListenersNoSync(eventType, false);
        if (ll != null) { // added by John Thompson - happens when clearlisteners is called
            return ll.list.size() > 0;
        }
        return false;
    }

    public void addMapItemEventListener(MapItem item,
            OnMapEventListener listener) {
        final Long key = item.getSerialId();
        ConcurrentLinkedQueue<OnMapEventListener> listeners = this.itemListeners
                .get(key);
        if (listeners == null)
            this.itemListeners
                    .put(key,
                            listeners = new ConcurrentLinkedQueue<>());
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        } else {
            Log.e(TAG, "attempt to add a duplicative listener"
                    + listener.getClass(), new Exception());
        }
    }

    public void removeMapItemEventListener(MapItem item,
            OnMapEventListener listener) {
        final Long key = item.getSerialId();
        ConcurrentLinkedQueue<OnMapEventListener> listeners = this.itemListeners
                .get(key);
        if (listeners == null)
            return;
        listeners.remove(listener);
        if (listeners.size() < 1)
            this.itemListeners.remove(key);
    }

    public void ignore(String eventType) {
        _Listeners ll = getListenersNoSync(eventType, true);
        ll.ignore = true;
    }

    public void allow(String eventType) {
        _Listeners ll = getListenersNoSync(eventType, true);
        ll.ignore = false;
    }

    private _Listeners getListenersNoSync(String type, boolean createIfNull) {
        Map<String, _Listeners> top = this.peekNoSync();
        _Listeners ll = top.get(type);
        if (ll == null && createIfNull)
            top.put(type, ll = new _Listeners());
        return ll;
    }

    public void dispatch(MapEvent event) {

        // invoke any global listeners
        _Listeners ll = getListenersNoSync(event.getType(), false);
        if (ll != null && !ll.ignore) {
            for (MapEventDispatchListener mapEventDispatchListener : ll.list) {
                MapEventDispatchListener l = null;
                l = mapEventDispatchListener;
                try {

                    l.onMapEvent(event);
                } catch (Exception e) {
                    Log.e(TAG,
                            "A map event listener (" + l.getClass()
                                    + ") has done something bad: ",
                            e);
                }
            }
        }

        // raise the event on the item
        if (event.getItem() != null) {
            Long key = event.getItem().getSerialId();
            ConcurrentLinkedQueue<OnMapEventListener> listeners = this.itemListeners
                    .get(key);
            if (listeners != null)
                for (OnMapEventListener l : listeners)
                    l.onMapItemMapEvent(event.getItem(), event);
        }
    }

    private static class _Listeners {
        boolean ignore;
        final ConcurrentLinkedQueue<MapEventDispatchListener> list;

        _Listeners() {
            this(false, new ConcurrentLinkedQueue<MapEventDispatchListener>());
        }

        _Listeners(_Listeners l) {
            this(l.ignore, new ConcurrentLinkedQueue<>(
                    l.list));
        }

        private _Listeners(boolean ignore,
                ConcurrentLinkedQueue<MapEventDispatchListener> list) {
            this.ignore = ignore;
            this.list = list;
        }
    }
}
