
package com.atakmap.android.maps;

import android.os.Parcelable;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Map;

public interface MetaDataHolder {

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
    String getMetaString(String key, String defaultValue);

    /**
     * Inserts a String value into the mapping of this Bundle, replacing
     * any existing value for the given key.  Either key or value may be null.
     *
     * @param key a String, or null
     * @param value a String, or null
     */
    void setMetaString(String key, String value);

    /**
     * Returns the value associated with the given key, or defaultValue if
     * no mapping of the desired type exists for the given key.
     *
     * @param key a String
     * @param defaultValue Value to return if key does not exist
     * @return an int value
     */
    int getMetaInteger(String key, int defaultValue);

    /**
     * Inserts an int value into the mapping of this Bundle, replacing
     * any existing value for the given key.
     *
     * @param key a String, or null
     * @param value an int
     */
    void setMetaInteger(String key, int value);

    /**
     * Returns the value associated with the given key, or defaultValue if
     * no mapping of the desired type exists for the given key.
     *
     * @param key a String
     * @param defaultValue Value to return if key does not exist
     * @return a double value
     */
    double getMetaDouble(String key, double defaultValue);

    /**
     * Inserts a double value into the mapping of this Bundle, replacing
     * any existing value for the given key.
     *
     * @param key a String, or null
     * @param value a double
     */
    void setMetaDouble(String key, double value);

    /**
     * Returns the value associated with the given key, or defaultValue if
     * no mapping of the desired type exists for the given key.
     *
     * @param key a String
     * @param defaultValue Value to return if key does not exist
     * @return a long value
     */
    long getMetaLong(String key, long defaultValue);

    /**
     * Inserts a long value into the mapping of this Bundle, replacing
     * any existing value for the given key.
     *
     * @param key a String, or null
     * @param value a long
     */
    void setMetaLong(String key, long value);

    /**
     * Returns the value associated with the given key, or defaultValue if
     * no mapping of the desired type exists for the given key.
     *
     * @param key a String
     * @param defaultValue Value to return if key does not exist
     * @return a boolean value
     */
    boolean getMetaBoolean(String key, boolean defaultValue);

    /**
     * Inserts a Boolean value into the mapping of this Bundle, replacing
     * any existing value for the given key.  Either key or value may be null.
     *
     * @param key a String, or null
     * @param value a boolean
     */
    void setMetaBoolean(String key, boolean value);

    /**
     * Returns the value associated with the given key, or null if
     * no mapping of the desired type exists for the given key or a null
     * value is explicitly associated with the key.
     *
     * @param key a String, or null
     * @return an ArrayList<String> value, or null
     */
    ArrayList<String> getMetaStringArrayList(String key);

    /**
     * Inserts an ArrayList<String> value into the mapping of this Bundle, replacing
     * any existing value for the given key.  Either key or value may be null.
     *
     * @param key a String, or null
     * @param value an ArrayList<String> object, or null
     */
    void setMetaStringArrayList(String key, ArrayList<String> value);

    /**
     * Returns the value associated with the given key, or null if
     * no mapping of the desired type exists for the given key or a null
     * value is explicitly associated with the key.
     *
     * @param key a String, or null
     * @return an int[] value, or null
     */
    int[] getMetaIntArray(String key);

    /**
     * Inserts an int array value into the mapping of this Bundle, replacing
     * any existing value for the given key.  Either key or value may be null.
     *
     * @param key a String, or null
     * @param value an int array object, or null
     */
    void setMetaIntArray(String key, int[] value);

    /**
     * Returns the value associated with the given key, or null if
     * no mapping of the desired type exists for the given key or a null
     * value is explicitly associated with the key.
     *
     * @param key a String, or null
     * @return a Serializable value, or null
     */
    Serializable getMetaSerializable(String key);

    /**
     * Inserts a Serializable value into the mapping of this Bundle, replacing
     * any existing value for the given key.  Either key or value may be null.
     *
     * @param key a String, or null
     * @param value a Serializable object, or null
     */
    void setMetaSerializable(String key, Serializable value);

    /**
     * Returns the value associated with the given key, or null if
     * no mapping of the desired type exists for the given key or a null
     * value is explicitly associated with the key.
     *
     * @param key a String, or null
     * @return a Parcelable value, or null
     */
    <T extends Parcelable> T getMetaParcelable(String key);

    /**
     * Inserts a Parcelable value into the mapping of this Bundle, replacing
     * any existing value for the given key.  Either key or value may be null.
     *
     * @param key a String, or null
     * @param value a Parcelable object, or null
     */
    void setMetaParcelable(String key, Parcelable value);

    /**
     * Returns the value associated with the given key, or null if
     * no mapping of the desired type exists for the given key or a null
     * value is explicitly associated with the key.
     *
     * @param key a String, or null
     * @return a value, or null
     */
    <T extends Object> T get(String key);

    /**
     * Returns the bundle associated with the specified key. The returned bundle is a copy and
     * changes made to it will not be reflected in the underlying metadata.
     * 
     * @param key
     * @return
     */
    Map<String, Object> getMetaMap(String key);

    /**
     * Sets the bundle associated with the specified key. The stored bundle is a copy of the
     * provided bundle; changes made to <code>bundle</code> after the method returns will not be
     * reflected in the underlying metadata.
     * 
     * @param key
     * @param bundle
     */
    void setMetaMap(String key, Map<String, Object> bundle);

    /**
     * Returns <code>true</code> if there is a metadata value associated with the specified key,
     * <code>false</code> otherwise.
     * 
     * @param key
     * @return
     */
    boolean hasMetaValue(String key);

    /**
     * Sets this metadata to the metadata in the specified bundle.
     * 
     * @param bundle
     */
    void setMetaData(Map<String, Object> bundle);

    /**
     * Copies the metadata in the specified bundle into this metadata.
     * 
     * @param bundle
     */
    void copyMetaData(Map<String, Object> bundle);

    /**
     * Returns a copy of the current metadata in the specified bundle.
     * 
     * @param bundle
     */
    void getMetaData(Map<String, Object> bundle);

    /**
     * Removes the metadata associated with the specified key.
     * 
     * @param key
     */
    void removeMetaData(String key);
}
