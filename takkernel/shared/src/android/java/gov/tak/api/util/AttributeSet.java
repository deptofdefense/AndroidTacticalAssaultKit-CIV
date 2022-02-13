package gov.tak.api.util;

/**
 * Typed collection of key/value tuples.  Nulls are not allowed as keys or as values.
 * <p>
 * Implementation uses a simple {@link java.util.Map} for storage.
 *
 * @since 0.17.0
 */
public class AttributeSet extends AttributeSetBase {
    /**
     * Construct an empty attribute set.
     */
    public AttributeSet() {
    }

    /**
     * Copy constructor.
     */
    @SuppressWarnings("CopyConstructorMissesField") // Not copying id
    public AttributeSet(AttributeSet source) {
        super(source);
    }
}
