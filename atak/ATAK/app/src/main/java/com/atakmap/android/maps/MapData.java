
package com.atakmap.android.maps;

import android.os.Parcelable;

/**
 * Stub class to help transition away from the use of a Bundle as a backer for the MapView::getMapData class.
 * See bugs: ATAK-8435, ATAK-8745
 */
public class MapData extends DefaultMetaDataHolder {

    /**
     * Returns the value associated with the given key, or defaultValue if
     * no mapping of the desired type exists for the given key or if a null
     * value is explicitly associated with the given key.
     *
     * @param key a String, or null
     * @param defaultValue Value to return if key does not exist or if a null
     *     value is associated with the given key.
     * @return the String value associated with the given key, or defaultValue
     *     if no valid String object is currently mapped to that key.
     */
    public String getString(final String key, final String defaultValue) {
        return getMetaString(key, defaultValue);
    }

    /**
     * Returns the value associated with the given key, or null if
     * no mapping of the desired type exists for the given key or a null
     * value is explicitly associated with the key.
     *
     * @param key a String, or null
     * @return a String value, or null
     */
    public String getString(final String key) {
        return getMetaString(key, null);
    }

    /**
     * Inserts a String value into the mapping of this class, replacing
     * any existing value for the given key.  Either key or value may be null.
     *
     * @param key a String, or null
     * @param defaultValue a String, or null
     */
    public void putString(final String key, final String defaultValue) {
        setMetaString(key, defaultValue);
    }

    /**
     * Returns the value associated with the given key, or defaultValue if
     * no mapping of the desired type exists for the given key.
     *
     * @param key a String
     * @param defaultValue Value to return if key does not exist
     * @return a long value
     */
    public long getLong(final String key, final long defaultValue) {
        return getMetaLong(key, defaultValue);
    }

    /**
     * Returns the value associated with the given key, or 0L if
     * no mapping of the desired type exists for the given key.
     *
     * @param key a String
     * @return a long value
     */
    public long getLong(final String key) {
        return getMetaLong(key, 0);
    }

    /**
     * Inserts a long value into the mapping of this class, replacing
     * any existing value for the given key.
     *
     * @param key a String, or null
     * @param value a long
     */
    public void putLong(final String key, final long value) {
        setMetaLong(key, value);
    }

    /**
     * Returns the value associated with the given key, or defaultValue if
     * no mapping of the desired type exists for the given key.
     *
     * @param key a String
     * @param defaultValue Value to return if key does not exist
     * @return a long value
     */
    public int getInt(final String key, final int defaultValue) {
        return getMetaInteger(key, defaultValue);
    }

    /**
     * Returns the value associated with the given key, or 0L if
     * no mapping of the desired type exists for the given key.
     *
     * @param key a String
     * @return a int value
     */
    public int getInt(final String key) {
        return getMetaInteger(key, 0);
    }

    /**
     * Inserts a long value into the mapping of this class, replacing
     * any existing value for the given key.
     *
     * @param key a String, or null
     * @param value a int
     */
    public void putInt(final String key, final int value) {
        setMetaInteger(key, value);
    }

    /**
     * Returns the value associated with the given key, or defaultValue if
     * no mapping of the desired type exists for the given key.
     *
     * @param key a String
     * @param defaultValue Value to return if key does not exist
     * @return a boolean value
     */
    public boolean getBoolean(final String key, final boolean defaultValue) {
        return getMetaBoolean(key, defaultValue);
    }

    /**
     * Returns the value associated with the given key, or false if
     * no mapping of the desired type exists for the given key.
     *
     * @param key a String
     * @return a boolean value
     */
    public boolean getBoolean(String key) {
        return getBoolean(key, false);
    }

    /**
     * Inserts a Boolean value into the mapping of this class, replacing
     * any existing value for the given key.  Either key or value may be null.
     *
     * @param key a String, or null
     * @param value a Boolean, or null
     */
    public void putBoolean(final String key, final boolean value) {
        setMetaBoolean(key, value);
    }

    /**
     * Retrieves the data referenced by the key and if the key is not found, returns the default.
     * @param key the key that identifies the data
     */
    public <T extends Parcelable> T getParcelable(final String key) {
        return getMetaParcelable(key);
    }

    /**
     * Adds an iten referenced by the key.
     * @param key the key used to reference the data
     * @param value the value to be referenced.
     */
    public void putParcelable(final String key, final Parcelable value) {
        setMetaParcelable(key, value);
    }

    /**
     * Returns the value associated with the given key, or defaultValue if
     * no mapping of the desired type exists for the given key.
     *
     * @param key a String
     * @param defaultValue Value to return if key does not exist
     * @return a float value
     */
    public float getFloat(final String key, final float defaultValue) {
        return (float) getMetaDouble(key, defaultValue);
    }

    /**
     * Returns the value associated with the given key, or 0.0f if
     * no mapping of the desired type exists for the given key.
     *
     * @param key a String
     * @return a float value
     */
    public float getFloat(String key) {
        return getFloat(key, 0.0f);
    }

    /**
     * Inserts a float value into the mapping of this class, replacing
     * any existing value for the given key.
     *
     * @param key a String, or null
     * @param value a float
     */
    public void putFloat(final String key, final float value) {
        setMetaDouble(key, value);
    }

    /**
     * Returns the value associated with the given key, or defaultValue if
     * no mapping of the desired type exists for the given key.
     *
     * @param key a String
     * @param defaultValue Value to return if key does not exist
     * @return a double value
     */
    public double getDouble(final String key, final double defaultValue) {
        return getMetaDouble(key, defaultValue);
    }

    /**
     * Returns the value associated with the given key, or 0.0 if
     * no mapping of the desired type exists for the given key.
     *
     * @param key a String
     * @return a double value
     */
    public double getDouble(String key) {
        return getDouble(key, 0.0);
    }

    /**
     * Inserts a double value into the mapping of this class, replacing
     * any existing value for the given key.
     *
     * @param key a String, or null
     * @param value a double
     */
    public void putDouble(final String key, final double value) {
        setMetaDouble(key, value);
    }

    /**
     * Removes any entry with the given key from the mapping of this class.
     *
     * @param key a String key
     */
    public void remove(final String key) {
        removeMetaData(key);
    }

    /**
     * Returns true if the given key is contained in the mapping
     * of this class.
     *
     * @param key a String key
     * @return true if the key is part of the mapping, false otherwise
     */
    public boolean containsKey(final String key) {
        return hasMetaValue(key);
    }

}
