package gov.tak.platform.util;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public final class BidirectionalMapProxy<T, V> implements Map<T, V> {
    private final Map<T, V> _keyToValue;
    private final Map<V, T> _valueToKey;

    private final Map<T, V> _keyToValueViewOnly;
    private final Map<V, T> _valueToKeyViewOnly;

    public BidirectionalMapProxy(Map<T, V> keyToValue, Map<V, T> valueToKey) {
        if(!keyToValue.isEmpty())   throw new IllegalArgumentException();
        if(!valueToKey.isEmpty())   throw new IllegalArgumentException();

        _keyToValue = keyToValue;
        _valueToKey = valueToKey;

        _keyToValueViewOnly = Collections.unmodifiableMap(_keyToValue);
        _valueToKeyViewOnly = Collections.unmodifiableMap(_valueToKey);
    }

    @Override
    public int size() {
        return _keyToValue.size();
    }

    @Override
    public boolean isEmpty() {
        return _keyToValue.isEmpty();
    }

    @Override
    public boolean containsKey(Object o) {
        return _keyToValue.containsKey(o);
    }

    @Override
    public boolean containsValue(Object o) {
        return _valueToKey.containsKey(o);
    }

    @Override
    public V get(Object o) {
        return _keyToValue.get(o);
    }

    @Override
    public V put(T t, V v) {
        _valueToKey.put(v, t);
        return _keyToValue.put(t, v);
    }

    @Override
    public V remove(Object o) {
        final V v = _keyToValue.remove(o);
        _valueToKey.remove(v);
        return v;
    }

    @Override
    public void putAll(Map<? extends T, ? extends V> map) {
        for(Map.Entry<? extends T, ? extends V> entry : map.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public void clear() {
        _keyToValue.clear();
        _valueToKey.clear();
    }

    @Override
    public Set<T> keySet() {
        return _keyToValueViewOnly.keySet();
    }

    @Override
    public Collection<V> values() {
        return _keyToValueViewOnly.values();
    }

    @Override
    public Set<Entry<T, V>> entrySet() {
        return _keyToValueViewOnly.entrySet();
    }

    public Map<T, V> keyToValue() {
        return _keyToValueViewOnly;
    }

    public Map<V, T> valueToKey() {
        return _valueToKeyViewOnly;
    }
}
