package gov.tak.platform.binding;

/**
 * Used to box a result from IPropertyValueSpi so that null can be represented as a value
 */
public class PropertyValue {
    private final Object value;

    public PropertyValue(Object value) {
        this.value = value;
    }

    public Object getValue() {
        return value;
    }

    public static final PropertyValue NULL =
            new PropertyValue(null);

    public static PropertyValue of(Object value) {
        return new PropertyValue(value);
    }
}
