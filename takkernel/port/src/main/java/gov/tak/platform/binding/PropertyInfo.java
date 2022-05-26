package gov.tak.platform.binding;

/**
 * Describes a property by name, Class and default value
 */
public class PropertyInfo {

    private Class propertyClass;
    private String name;
    private PropertyValue defaultValue;

    /**
     * Create a named, Classed property descriptor with a default value of null
     *
     * @param name the property name
     * @param propertyClass the property class
     */
    public PropertyInfo(String name, Class propertyClass) {
        this(name, propertyClass, null);
    }

    /**
     * Create a named, Classed property descriptor with a default value
     *
     * @param name the property name
     * @param propertyClass the property class
     * @param defaultValue the default value of the property
     */
    public PropertyInfo(String name, Class propertyClass, Object defaultValue) {
        this(name, propertyClass, PropertyValue.of(defaultValue));
    }

    private PropertyInfo(String name, Class propertyClass, PropertyValue propertyValue) {
        if (name == null || name.isEmpty())
            throw new IllegalArgumentException("property name may not be null or empty");

        this.name = name;
        this.propertyClass = propertyClass;
        this.defaultValue = propertyValue;
    }

    /**
     * Get the property class
     *
     * @return
     */
    public Class getPropertyClass() {
        return this.propertyClass;
    }

    /**
     * Get the property name
     *
     * @return
     */
    public String getName() {
        return this.name;
    }

    /**
     * Get the default value
     *
     * @return
     */
    public PropertyValue getDefaultValue() {
        return this.defaultValue;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof PropertyInfo))
            return false;

        PropertyInfo pi = (PropertyInfo)other;
        return pi == this ||
          (this.hasName(pi.getName()) && this.propertyClass.equals(pi.getPropertyClass()));
    }

    /**
     * Check if a property has a given name
     *
     * @param propertyName
     * @return
     */
    public boolean hasName(String propertyName) {
        if (propertyName == null || propertyName.isEmpty())
            return false;
        return this.name.equals(propertyName);
    }

    /**
     * Test is something with a name and class can be assigned to this property
     *
     * @param propertyName
     * @param clazz
     * @return
     */
    public boolean canAssign(String propertyName, Class clazz) {
        return hasName(propertyName) &&
                this.propertyClass.isAssignableFrom(clazz);
    }

    /**
     * Test if another property is assignable to this one
     *
     * @param other
     * @return
     */
    public boolean canAssign(PropertyInfo other) {
        return canAssign(other.getName(),
                other.getPropertyClass());
    }

    /**
     * Test is something with a name and value can be assigned to this property
     *
     * @param propertyName
     * @param value
     * @return
     */
    public boolean canAssignValue(String propertyName, Object value) {
        return hasName(propertyName) &&
                (value == null || this.propertyClass.isAssignableFrom(value.getClass()));
    }
}
