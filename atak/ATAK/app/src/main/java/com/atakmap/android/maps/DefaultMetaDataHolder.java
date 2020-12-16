
package com.atakmap.android.maps;

import android.os.Bundle;
import android.os.Parcelable;
import android.util.SparseArray;

import com.atakmap.coremap.log.Log;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class DefaultMetaDataHolder implements MetaDataHolder {

    private static final String TAG = "DefaultMetaDataHolder";

    public DefaultMetaDataHolder() {
        this(new HashMap<String, Object>());
    }

    public DefaultMetaDataHolder(final Map<String, Object> bundle) {
        _data = bundle;
    }

    @Override
    public final String getMetaString(final String key,
            final String fallbackValue) {
        return typedGet(_data, key, String.class, fallbackValue);
    }

    @Override
    public final void setMetaString(final String key, final String value) {
        if (value == null)
            _data.remove(key);
        else
            _data.put(key, value);
    }

    @Override
    public final int getMetaInteger(final String key, final int fallbackValue) {
        Integer r = typedGet(_data, key, Integer.class);
        return (r != null) ? r : fallbackValue;
    }

    @Override
    public final void setMetaInteger(final String key, final int value) {
        _data.put(key, value);
    }

    @Override
    public final double getMetaDouble(final String key,
            final double fallbackValue) {
        Double r = typedGet(_data, key, Double.class);
        return (r != null) ? r : fallbackValue;
    }

    @Override
    public final void setMetaDouble(final String key, final double value) {
        _data.put(key, value);
    }

    @Override
    public final boolean getMetaBoolean(final String key,
            final boolean fallbackValue) {
        Boolean r = typedGet(_data, key, Boolean.class);
        return (r != null) ? r : fallbackValue;
    }

    @Override
    public final <T extends Object> T get(final String key) {
        return (T) typedGet(_data, key, Object.class, null);
    }

    @Override
    public final void setMetaBoolean(final String key, final boolean value) {
        _data.put(key, value);
    }

    @Override
    public final boolean hasMetaValue(final String key) {
        return _data != null && _data.containsKey(key);
    }

    @Override
    public final void setMetaData(final Map<String, Object> bundle) {
        _data.clear();
        this.copyMetaData(bundle);
    }

    @Override
    public final void copyMetaData(final Map<String, Object> bundle) {
        _data.putAll(bundle);
    }

    @Override
    public final void getMetaData(final Map<String, Object> bundle) {
        bundle.putAll(_data);
    }

    @Override
    public final long getMetaLong(final String key, final long fallbackValue) {
        Long r = typedGet(_data, key, Long.class);
        return (r != null) ? r : fallbackValue;
    }

    @Override
    public final void setMetaLong(final String key, final long value) {
        _data.put(key, value);
    }

    @Override
    public final void removeMetaData(final String key) {
        _data.remove(key);
    }

    @Override
    public final Map<String, Object> getMetaMap(final String key) {
        Map<String, Object> b = typedGet(_data, key, Map.class, null);
        if (b == null)
            return null;
        return new HashMap<>(b);
    }

    @Override
    public final void setMetaMap(final String key,
            final Map<String, Object> bundle) {
        Map<String, Object> copy = null;
        Map<String, Object> old = (Map<String, Object>) typedGet(_data,
                key, Map.class, null);
        if (old != null) {
            old.clear();
        }
        if (copy == null)
            copy = new HashMap<>(bundle);
        _data.put(key, copy);
    }

    @Override
    public final ArrayList<String> getMetaStringArrayList(final String key) {
        return (ArrayList<String>) typedGet(_data, key, ArrayList.class,
                null);
    }

    @Override
    public final void setMetaStringArrayList(final String key,
            final ArrayList<String> value) {
        _data.put(key, value);
    }

    @Override
    public final int[] getMetaIntArray(final String key) {
        return typedGet(_data, key, int[].class, null);
    }

    @Override
    public final void setMetaIntArray(final String key, final int[] value) {
        _data.put(key, value);
    }

    @Override
    public final Serializable getMetaSerializable(final String key) {
        return typedGet(_data, key, Serializable.class, null);
    }

    @Override
    public final void setMetaSerializable(final String key,
            final Serializable value) {
        _data.put(key, value);
    }

    @Override
    public final <T extends Parcelable> T getMetaParcelable(final String key) {
        return (T) typedGet(_data, key, Parcelable.class, null);
    }

    @Override
    public final void setMetaParcelable(final String key,
            final Parcelable value) {
        _data.put(key, value);
    }

    private static <T> T typedGet(final Map<String, Object> map,
            final String key,
            final Class<T> type, final T defValue) {
        Object o = map.get(key);
        if (o == null)
            return defValue;
        try {
            return (T) o;
        } catch (ClassCastException e) {
            Log.w(TAG, "Wrong type, "
                    + o.getClass().getName() + ", for \""
                    + key + "\".");
            return defValue;
        }
    }

    private static <T> T typedGet(final Map<String, Object> map,
            final String key,
            final Class<T> type) {
        Object o = map.get(key);
        if (o == null)
            return null;
        try {
            return (T) o;
        } catch (ClassCastException e) {
            Log.w(TAG, "Wrong type, "
                    + o.getClass().getName() + ", for \""
                    + key + "\".");
            return null;
        }
    }

    private final Map<String, Object> _data;

    public static void metaMapToBundle(final Map<String, Object> map,
            final Bundle bundle,
            final boolean deep) {
        String key;
        Object value;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            key = entry.getKey();
            value = entry.getValue();
            // XXX - implementation of Bundle has untyped values for backing map
            // so *ANY* putXXX method will store the value. check against
            // Serializable first because it's going to cover most of the
            // cases
            if (deep && (value instanceof Map) && checkBundleMap((Map) value)) {
                Bundle b = new Bundle();
                metaMapToBundle((Map<String, Object>) value, b, deep);
                bundle.putBundle(key, b);
            } else if (value instanceof Serializable) {
                bundle.putSerializable(key, (Serializable) value);
            } else if (value instanceof CharSequence) {
                bundle.putCharSequence(key, (CharSequence) value);
            } else if (value instanceof CharSequence[]) {
                bundle.putCharSequenceArray(key, (CharSequence[]) value);
            } else if (value instanceof Parcelable) {
                bundle.putParcelable(key, (Parcelable) value);
            } else if (value instanceof Parcelable[]) {
                bundle.putParcelableArray(key, (Parcelable[]) value);
            } else if (value instanceof SparseArray) {
                bundle.putSparseParcelableArray(key,
                        (SparseArray<? extends Parcelable>) value);
            } else if (value != null) {
                Log.w(TAG, "Failed to transfer \"" + key
                        + "\" to bundle, type: "
                        + value.getClass());
            }
        }
    }

    private static boolean checkBundleMap(final Map<Object, Object> m) {
        Object key;
        Object value;
        for (Map.Entry entry : m.entrySet()) {
            key = entry.getKey();
            if (!(key instanceof String))
                return false;

            value = entry.getValue();
            // XXX - implementation of Bundle has untyped values for backing map
            // so *ANY* putXXX method will store the value.

            if (!((value instanceof Serializable) ||
                    (value instanceof CharSequence) ||
                    (value instanceof CharSequence[]) ||
                    (value instanceof Parcelable) ||
                    (value instanceof Parcelable[]) ||
                    (value instanceof SparseArray)
                    || (value instanceof Map && checkBundleMap((Map) value)))) {

                return false;
            }
        }
        return true;
    }

    public static void bundleToMetaMap(final Bundle bundle,
            final Map<String, Object> map) {
        Object value;
        for (String key : bundle.keySet()) {
            value = bundle.get(key);

            // XXX - implementation of Bundle has untyped values for backing map
            // so *ANY* putXXX method will store the value. check against
            // Serializable first because it's going to cover most of the
            // cases
            if (value instanceof Bundle) {
                Map<String, Object> m = new HashMap<>();
                bundleToMetaMap((Bundle) value, m);
                map.put(key, m);
            } else if (value instanceof Serializable) {
                bundle.putSerializable(key, (Serializable) value);
            } else if (value != null) {
                Log.w("bundleToMetaMap", "Failed to transfer \"" + key
                        + "\" to map, type: "
                        + value.getClass());
            }
        }
    }
}
