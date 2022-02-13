package gov.tak.api.util;

import gov.tak.api.annotation.NonNull;
import gov.tak.platform.commons.util.ClearablePropertyChangeSupport;

import java.beans.PropertyChangeListener;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Typed collection of key/value tuples.  Nulls are not allowed as keys or as values.
 * <p>
 * Implementation uses a simple {@link java.util.Map} for storage.
 *
 * @since 0.17.0
 */
class AttributeSetBase {

    final Map<String, AttributeValue> attributeMap = new ConcurrentHashMap<>();

    private final ClearablePropertyChangeSupport propertyChangeSupport = new ClearablePropertyChangeSupport(this);

    /**
     * Construct an empty attribute set.
     */
    AttributeSetBase() {
    }

    /**
     * Copy constructor.
     */
    AttributeSetBase(@NonNull AttributeSet source) {
        putAll(source);
    }

    /**
     * Returns the specified <code>boolean</code> attribute.
     *
     * @param name The name of the attribute
     * @return The <code>boolean</code> value of the attribute
     * @throws IllegalArgumentException If the <code>AttributeSet</code> does not contain the specified attribute
     *                                  or if the attribute is not of type <code>boolean</code>.
     */
    public boolean getBooleanAttribute(@NonNull String name) {
        return typedGet(name, AttributeType.BOOLEAN);
    }

    /**
     * Returns the specified <code>boolean</code> attribute, or the default value if the named attribute is not present.
     *
     * @param name         The name of the attribute
     * @param defaultValue Default value to return if the named attribute is not present
     * @return The <code>boolean</code> value of the attribute
     * @throws IllegalArgumentException If the attribute is not of type <code>boolean</code>.
     */
    public boolean getBooleanAttribute(@NonNull String name, boolean defaultValue) {
        return typedGet(name, AttributeType.BOOLEAN, defaultValue);
    }

    /**
     * Returns the specified <code>int</code> attribute.
     *
     * @param name The name of the attribute
     * @return The <code>int</code> value of the attribute
     * @throws IllegalArgumentException If the <code>AttributeSet</code> does not contain the specified attribute
     *                                  or if the attribute is not of type <code>int</code>.
     */
    public int getIntAttribute(@NonNull String name) {
        return typedGet(name, AttributeType.INTEGER);
    }

    /**
     * Returns the specified <code>int</code> attribute, or the default value if the named attribute is not present.
     *
     * @param name         The name of the attribute
     * @param defaultValue Default value to return if the named attribute is not present
     * @return The <code>int</code> value of the attribute
     * @throws IllegalArgumentException If the attribute is not of type <code>int</code>.
     */
    public int getIntAttribute(@NonNull String name, int defaultValue) {
        return typedGet(name, AttributeType.INTEGER, defaultValue);
    }

    /**
     * Returns the specified <code>long</code> attribute.
     *
     * @param name The name of the attribute
     * @return The <code>long</code> value of the attribute
     * @throws IllegalArgumentException If the <code>AttributeSet</code> does not contain the specified attribute
     *                                  or if the attribute is not of type <code>long</code>.
     */
    public long getLongAttribute(@NonNull String name) {
        return typedGet(name, AttributeType.LONG);
    }

    /**
     * Returns the specified <code>long</code> attribute, or the default value if the named attribute is not present.
     *
     * @param name         The name of the attribute
     * @param defaultValue Default value to return if the named attribute is not present
     * @return The <code>long</code> value of the attribute
     * @throws IllegalArgumentException If the attribute is not of type <code>long</code>.
     */
    public long getLongAttribute(@NonNull String name, long defaultValue) {
        return typedGet(name, AttributeType.LONG, defaultValue);
    }

    /**
     * Returns the specified <code>double</code> attribute.
     *
     * @param name The name of the attribute
     * @return The <code>double</code> value of the attribute
     * @throws IllegalArgumentException If the <code>AttributeSet</code> does not contain the specified attribute
     *                                  or if the attribute is not of type <code>double</code>.
     */
    public double getDoubleAttribute(@NonNull String name) {
        return typedGet(name, AttributeType.DOUBLE);
    }

    /**
     * Returns the specified <code>double</code> attribute, or the default value if the named attribute is not present.
     *
     * @param name         The name of the attribute
     * @param defaultValue Default value to return if the named attribute is not present
     * @return The <code>double</code> value of the attribute
     * @throws IllegalArgumentException If the attribute is not of type <code>double</code>.
     */
    public double getDoubleAttribute(@NonNull String name, double defaultValue) {
        return typedGet(name, AttributeType.DOUBLE, defaultValue);
    }

    /**
     * Returns the specified {@link String} attribute.
     *
     * @param name The name of the attribute
     * @return The {@link String} value of the attribute
     * @throws IllegalArgumentException If the <code>AttributeSet</code> does not contain the specified attribute
     *                                  or if the attribute is not of type {@link String}.
     */
    @NonNull
    public String getStringAttribute(@NonNull String name) {
        return typedGet(name, AttributeType.STRING);
    }

    /**
     * Returns the specified {@link String} attribute, or the default value if the named attribute is not present.
     *
     * @param name         The name of the attribute
     * @param defaultValue Default value to return if the named attribute is not present
     * @return The <code>String</code> value of the attribute
     * @throws IllegalArgumentException If the attribute is not of type <code>String</code>.
     */
    @NonNull
    public String getStringAttribute(@NonNull String name, @NonNull String defaultValue) {
        return typedGet(name, AttributeType.STRING, defaultValue);
    }

    /**
     * Returns the specified binary attribute.
     *
     * @param name The name of the attribute
     * @return The binary value of the attribute
     * @throws IllegalArgumentException If the <code>AttributeSet</code> does not contain the specified attribute
     *                                  or if the attribute is not of type <code>byte[]</code>.
     */
    @NonNull
    public byte[] getBinaryAttribute(@NonNull String name) {
        return typedGet(name, AttributeType.BLOB);
    }

    /**
     * Returns the specified binary attribute, or the default value if the named attribute is not present.
     *
     * @param name         The name of the attribute
     * @param defaultValue Default value to return if the named attribute is not present
     * @return The binary value of the attribute
     * @throws IllegalArgumentException If the attribute is not of type <code>byte[]</code>.
     */
    @NonNull
    public byte[] getBinaryAttribute(@NonNull String name, @NonNull byte[] defaultValue) {
        return typedGet(name, AttributeType.BLOB, defaultValue);
    }

    /**
     * Returns the specified <code>int[]</code> attribute.
     *
     * @param name The name of the attribute
     * @return The <code>int[]</code> value of the attribute
     * @throws IllegalArgumentException If the <code>AttributeSet</code> does not contain the specified attribute
     *                                  or if the attribute is not of type <code>int[]</code>.
     */
    @NonNull
    public int[] getIntArrayAttribute(@NonNull String name) {
        return typedGet(name, AttributeType.INTEGER_ARRAY);
    }

    /**
     * Returns the specified <code>int[]</code> attribute, or the default value if the named attribute is not present.
     *
     * @param name         The name of the attribute
     * @param defaultValue Default value to return if the named attribute is not present
     * @return The <code>int[]</code> value of the attribute
     * @throws IllegalArgumentException If the attribute is not of type <code>int[]</code>.
     */
    @NonNull
    public int[] getIntArrayAttribute(@NonNull String name, @NonNull int[] defaultValue) {
        return typedGet(name, AttributeType.INTEGER_ARRAY, defaultValue);
    }

    /**
     * Returns the specified <code>long[]</code> attribute.
     *
     * @param name The name of the attribute
     * @return The <code>long[]</code> value of the attribute
     * @throws IllegalArgumentException If the <code>AttributeSet</code> does not contain the specified attribute
     *                                  or if the attribute is not of type <code>long[]</code>.
     */
    @NonNull
    public long[] getLongArrayAttribute(@NonNull String name) {
        return typedGet(name, AttributeType.LONG_ARRAY);
    }

    /**
     * Returns the specified <code>long[]</code> attribute, or the default value if the named attribute is not present.
     *
     * @param name         The name of the attribute
     * @param defaultValue Default value to return if the named attribute is not present
     * @return The <code>long[]</code> value of the attribute
     * @throws IllegalArgumentException If the attribute is not of type <code>long[]</code>.
     */
    @NonNull
    public long[] getLongArrayAttribute(@NonNull String name, @NonNull long[] defaultValue) {
        return typedGet(name, AttributeType.LONG_ARRAY, defaultValue);
    }

    /**
     * Returns the specified <code>double[]</code> attribute.
     *
     * @param name The name of the attribute
     * @return The <code>double[]</code> value of the attribute
     * @throws IllegalArgumentException If the <code>AttributeSet</code> does not contain the specified attribute
     *                                  or if the attribute is not of type <code>double[]</code>.
     */
    @NonNull
    public double[] getDoubleArrayAttribute(@NonNull String name) {
        return typedGet(name, AttributeType.DOUBLE_ARRAY);
    }

    /**
     * Returns the specified <code>double[]</code> attribute, or the default value if the named attribute is not present.
     *
     * @param name         The name of the attribute
     * @param defaultValue Default value to return if the named attribute is not present
     * @return The <code>double[]</code> value of the attribute
     * @throws IllegalArgumentException If the attribute is not of type <code>double[]</code>.
     */
    @NonNull
    public double[] getDoubleArrayAttribute(@NonNull String name, @NonNull double[] defaultValue) {
        return typedGet(name, AttributeType.DOUBLE_ARRAY, defaultValue);
    }

    /**
     * Returns the specified {@link String} array attribute.
     *
     * @param name The name of the attribute
     * @return The {@link String} array value of the attribute
     * @throws IllegalArgumentException If the <code>AttributeSet</code> does not contain the specified attribute
     *                                  or if the attribute is not of type <code>String[]</code>.
     */
    @NonNull
    public String[] getStringArrayAttribute(@NonNull String name) {
        return typedGet(name, AttributeType.STRING_ARRAY);
    }

    /**
     * Returns the specified <code>String[]</code> attribute, or the default value if the named attribute is not present.
     *
     * @param name         The name of the attribute
     * @param defaultValue Default value to return if the named attribute is not present
     * @return The <code>String[]</code> value of the attribute
     * @throws IllegalArgumentException If the attribute is not of type <code>String[]</code>.
     */
    @NonNull
    public String[] getStringArrayAttribute(@NonNull String name, @NonNull String[] defaultValue) {
        return typedGet(name, AttributeType.STRING_ARRAY, defaultValue);
    }

    /**
     * Returns the specified binary array attribute.
     *
     * @param name The name of the attribute
     * @return The binary array value of the attribute
     * @throws IllegalArgumentException If the <code>AttributeSet</code> does not contain the specified attribute
     *                                  or if the attribute is not of type <code>byte[][]</code>.
     */
    @NonNull
    public byte[][] getBinaryArrayAttribute(@NonNull String name) {
        return typedGet(name, AttributeType.BLOB_ARRAY);
    }

    /**
     * Returns the specified binary array attribute, or the default value if the named attribute is not present.
     *
     * @param name         The name of the attribute
     * @param defaultValue Default value to return if the named attribute is not present
     * @return The binary array value of the attribute
     * @throws IllegalArgumentException If the attribute is not of type <code>byte[][]</code>.
     */
    @NonNull
    public byte[][] getBinaryArrayAttribute(@NonNull String name, @NonNull byte[][] defaultValue) {
        return typedGet(name, AttributeType.BLOB_ARRAY, defaultValue);
    }

    /**
     * Returns the specified <code>AttributeSet</code> attribute.
     *
     * @param name The name of the attribute
     * @return The <code>AttributeSet</code> value of the attribute
     * @throws IllegalArgumentException If the <code>AttributeSet</code> does not contain the specified attribute
     *                                  or if the attribute is not of type <code>AttributeSet</code>.
     */
    @NonNull
    public AttributeSet getAttributeSetAttribute(@NonNull String name) {
        return typedGet(name, AttributeType.ATTRIBUTE_SET);
    }

    /**
     * Return the {@link AttributeType} of the specified attribute.
     * <p>
     * Note the difference between this method, which returns an enum, and {@link #getAttributeValueType(String)},
     * which returns a Java class reference for the stored value.
     *
     * @param name The name of the attribute
     * @return The type of the attribute or <code>null</code> if there is no attribute with that name.
     */
    public AttributeType getAttributeType(@NonNull String name) {
        final AttributeValue attributeValue = attributeMap.get(name);
        return attributeValue != null ? attributeValue.type : null;
    }

    /**
     * Returns the value type of the specified attribute. Supported types include:
     * <ul>
     * <li><code>Boolean.class</code></li>
     * <li><code>Integer.class</code></li>
     * <li><code>Long.class</code></li>
     * <li><code>Double.class</code></li>
     * <li><code>String.class</code></li>
     * <li><code>byte[].class</code></li>
     * <li><code>int[].class</code></li>
     * <li><code>long[].class</code></li>
     * <li><code>double[].class</code></li>
     * <li><code>String[].class</code></li>
     * <li><code>byte[][].class</code></li>
     * <li><code>AttributeSet.class</code></li>
     * </ul>
     *
     * @param name The name of the attribute
     * @return The type of the attribute or <code>null</code> if there is no attribute with that name.
     */
    public Class<?> getAttributeValueType(@NonNull String name) {
        final AttributeValue attributeValue = attributeMap.get(name);
        return attributeValue != null ? attributeValue.type.valueType : null;
    }

    /**
     * Sets the value of the specified attribute. Overwrites any existing value.
     *
     * @param name  The attribute name
     * @param value The value
     */
    public void setAttribute(@NonNull String name, boolean value) {
        setAttribute(name, new AttributeValue(AttributeType.BOOLEAN, value));
    }

    /**
     * Sets the value of the specified attribute. Overwrites any existing value.
     *
     * @param name  The attribute name
     * @param value The value
     */
    public void setAttribute(@NonNull String name, int value) {
        setAttribute(name, new AttributeValue(AttributeType.INTEGER, value));
    }

    /**
     * Sets the value of the specified attribute. Overwrites any existing value.
     *
     * @param name  The attribute name
     * @param value The value
     */
    public void setAttribute(@NonNull String name, long value) {
        setAttribute(name, new AttributeValue(AttributeType.LONG, value));
    }

    /**
     * Sets the value of the specified attribute. Overwrites any existing value.
     *
     * @param name  The attribute name
     * @param value The value
     */
    public void setAttribute(@NonNull String name, double value) {
        setAttribute(name, new AttributeValue(AttributeType.DOUBLE, value));
    }

    /**
     * Sets the value of the specified attribute. Overwrites any existing value.
     *
     * @param name  The attribute name
     * @param value The value
     */
    public void setAttribute(@NonNull String name, @NonNull String value) {
        setAttribute(name, new AttributeValue(AttributeType.STRING, value));
    }

    /**
     * Sets the value of the specified attribute. Overwrites any existing value.
     *
     * @param name  The attribute name
     * @param value The value
     */
    public void setAttribute(@NonNull String name, @NonNull byte[] value) {
        setAttribute(name, new AttributeValue(AttributeType.BLOB, value));
    }

    /**
     * Sets the value of the specified attribute. Overwrites any existing value.
     *
     * @param name  The attribute name
     * @param value The value
     */
    public void setAttribute(@NonNull String name, @NonNull int[] value) {
        setAttribute(name, new AttributeValue(AttributeType.INTEGER_ARRAY, value));
    }

    /**
     * Sets the value of the specified attribute. Overwrites any existing value.
     *
     * @param name  The attribute name
     * @param value The value
     */
    public void setAttribute(@NonNull String name, @NonNull long[] value) {
        setAttribute(name, new AttributeValue(AttributeType.LONG_ARRAY, value));
    }

    /**
     * Sets the value of the specified attribute. Overwrites any existing value.
     *
     * @param name  The attribute name
     * @param value The value
     */
    public void setAttribute(@NonNull String name, @NonNull double[] value) {
        setAttribute(name, new AttributeValue(AttributeType.DOUBLE_ARRAY, value));
    }

    /**
     * Sets the value of the specified attribute. Overwrites any existing value.
     *
     * @param name  The attribute name
     * @param value The value
     */
    public void setAttribute(@NonNull String name, @NonNull String[] value) {
        setAttribute(name, new AttributeValue(AttributeType.STRING_ARRAY, value));
    }

    /**
     * Sets the value of the specified attribute. Overwrites any existing value.
     *
     * @param name  The attribute name
     * @param value The value
     */
    public void setAttribute(@NonNull String name, @NonNull byte[][] value) {
        setAttribute(name, new AttributeValue(AttributeType.BLOB_ARRAY, value));
    }

    /**
     * Sets the value of the specified attribute. Overwrites any existing value.
     *
     * @param name  The attribute name
     * @param value The value
     */
    public void setAttribute(@NonNull String name, @NonNull AttributeSet value) {
        setAttribute(name, new AttributeValue(AttributeType.ATTRIBUTE_SET, value));
    }

    /**
     * Set a value in the attribute map, firing a {@link java.beans.PropertyChangeEvent} if the value changed.
     *
     * @param name              The attribute name
     * @param newAttributeValue The new attribute value to store in the map
     */
    final void setAttribute(@NonNull String name, @NonNull AttributeValue newAttributeValue) {
        final AttributeValue oldAttributeValue = attributeMap.put(name, newAttributeValue);

        final Object oldValue = oldAttributeValue == null ? null : oldAttributeValue.value;
        final Object newValue = newAttributeValue.value;

        if (!Objects.deepEquals(oldValue, newValue)) {
            propertyChangeSupport.firePropertyChange(name, oldValue, newValue);
        }
    }

    /**
     * Removes the specified attribute.
     *
     * @param name The name of the attribute to be removed.
     */
    public void removeAttribute(@NonNull String name) {
        final AttributeValue oldAttributeValue = attributeMap.remove(name);

        final Object oldValue = oldAttributeValue == null ? null : oldAttributeValue.value;
        propertyChangeSupport.firePropertyChange(name, oldValue, null);
    }

    /**
     * @return A {@link Set Set} of the names of attributes contained in this <code>AttributeSet</code>.
     */
    @NonNull
    public Set<String> getAttributeNames() {
        return Collections.unmodifiableSet(attributeMap.keySet());
    }

    /**
     * @return <code>true</code> if this <code>AttributeSet</code> contains an
     * attribute with the specified name, <code>false</code> otherwise.
     */
    public boolean containsAttribute(@NonNull String name) {
        return attributeMap.containsKey(name);
    }

    /**
     * Clears all attributes.
     */
    public void clear() {
        attributeMap.clear();
    }

    /**
     * Dispose of this object, clearing all values and removing all listeners.
     */
    public void dispose() {
        clear();
        propertyChangeSupport.removeAllListeners();
    }

    /**
     * Copy all the attributes from the specified AttributeSet into this one.  Existing attributes with the
     * same name will be overwritten.
     *
     * @param source Source of attributes to copy
     */
    public void putAll(AttributeSet source) {
        attributeMap.putAll(source.attributeMap);
    }

    /**
     * Add a PropertyChangeListener to the listener list. The listener is registered for all properties.
     * The same listener object may be added more than once, and will be called as many times as it is added.
     * If <code>listener</code> is null, no exception is thrown and no action is taken.
     *
     * @param listener The listener to be added
     */
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(listener);
    }

    /**
     * Remove a PropertyChangeListener from the listener list. This removes a PropertyChangeListener that was registered
     * for all properties.
     * If <code>listener</code> was added more than once to the same event source, it will be notified one less time
     * after being removed.
     * If <code>listener</code> is null, or was never added, no exception is thrown and no action is taken.
     *
     * @param listener The listener to be removed
     */
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(listener);
    }

    /**
     * Add a PropertyChangeListener for a specific property.
     *
     * @param propertyName The name of the property to listen on
     * @param listener     The PropertyChangeListener to be added
     */
    public void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(propertyName, listener);
    }

    /**
     * Remove a PropertyChangeListener for a specific property.
     *
     * @param propertyName The name of the property that was listened on
     * @param listener     The PropertyChangeListener to be removed
     */
    public void removePropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(propertyName, listener);
    }

    /**
     * Get a value cast to the requested type.
     *
     * @param key  Key of attribute to get
     * @param type Required type of value
     * @param <T>  Inferred type
     * @return attribute value cast to {@code type}
     * @throws IllegalArgumentException If the key is missing or the value is not of the required type
     */
    @NonNull
    private <T> T typedGet(String key, @NonNull AttributeType type) {
        return cast(key, type, attributeMap.get(key));
    }

    /**
     * Get a value cast to the requested type, returning a given default if the key is not present.
     *
     * @param key      Key of attribute to get
     * @param type     Required type of value
     * @param defValue Default value to return if the attribute is not present
     * @param <T>      Inferred type
     * @return attribute value (or default value) cast to {@code type}
     * @throws IllegalArgumentException If the value is not of the required type
     */
    @NonNull
    private <T> T typedGet(String key, @NonNull AttributeType type, @NonNull T defValue) {
        Objects.requireNonNull(defValue, "'defaultValue' must not be null");

        AttributeValue attributeValue = attributeMap.get(key);
        if (attributeValue == null) {
            attributeValue = new AttributeValue(type, defValue);
        }

        return cast(key, type, attributeValue);
    }

    /**
     * Cast the given attributeValue's value to the specified type.
     *
     * @param key            Key of attribute to get
     * @param attributeType  Required type of value
     * @param attributeValue Attribute from which to obtain the actual value
     * @param <T>            Inferred type
     * @return attribute value cast to {@link AttributeType#valueType}
     * @throws IllegalArgumentException If the value is null or is not of the required type
     */
    @NonNull
    private <T> T cast(String key, @NonNull AttributeType attributeType, AttributeValue attributeValue) {
        if (attributeValue == null) {
            throw new IllegalArgumentException("Attribute key not found: '" + key + "'");
        }

        final Object o = attributeValue.value;
        final Class<?> valueType = attributeType.valueType;
        try {
            //noinspection unchecked
            return (T) valueType.cast(o);
        } catch (ClassCastException e) {
            final String message = "Wrong type for '" + key + "'; wanted " + valueType.getSimpleName() + ", got "
                    + o.getClass().getSimpleName();
            throw new IllegalArgumentException(message);
        }
    }

    @NonNull
    @Override
    public String toString() {
        return "AttributeSet{attributes=" + attributeMap + '}';
    }

    /**
     * Enumeration of the allowed attribute types, including a reference to the associated Java type.
     */
    public enum AttributeType {
        BOOLEAN(Boolean.class),
        INTEGER(Integer.class),
        LONG(Long.class),
        DOUBLE(Double.class),
        STRING(String.class),
        BLOB(byte[].class),
        ATTRIBUTE_SET(AttributeSet.class),
        INTEGER_ARRAY(int[].class),
        LONG_ARRAY(long[].class),
        DOUBLE_ARRAY(double[].class),
        STRING_ARRAY(String[].class),
        BLOB_ARRAY(byte[][].class);

        final Class<?> valueType;

        AttributeType(Class<?> valueType) {
            this.valueType = valueType;
        }

        /**
         * @return The java class associated with this attribute type
         */
        public Class<?> getValueType() {
            return valueType;
        }
    }

    /**
     * Type/value tuple for storing values in the attribute set.
     */
    static final class AttributeValue {
        final AttributeType type;
        final Object value;

        AttributeValue(AttributeType type, Object value) {
            this.type = type;
            this.value = Objects.requireNonNull(value, "'value' must not be null");
        }

        @NonNull
        @Override
        public String toString() {
            return "{type=" + type + ", value=" + value + '}';
        }
    }

}
