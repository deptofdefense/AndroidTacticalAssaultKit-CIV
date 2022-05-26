package gov.tak.platform.contact;

import gov.tak.api.annotation.NonNull;
import gov.tak.api.contact.IContact;
import gov.tak.api.util.AttributeSet;
import org.apache.commons.lang.StringUtils;

import java.util.Objects;
import java.util.UUID;

/**
 * Builder class used to construct {@link IContact} instances, thus allowing them to be immutable.
 * <p>
 * It's assumed that every Contact built will have different unique IDs as well as different display names. Thus, a unique
 * ID is automatically generated for every Contact via the {@link #build()} method, and if no display name has explicitly
 * been set since {@link #build()} was last called, then the unique Contact ID will be used as the display name.
 *
 * @since 0.17.0
 */
public abstract class ContactBuilder<C extends IContact, B extends ContactBuilder<C, B>> {
    private boolean isUniqueContactIdManuallySet = false;
    private boolean isDisplayNameManuallySet = false;

    protected String uniqueContactId = UUID.randomUUID().toString();
    protected String displayName;
    protected AttributeSet attributes = new AttributeSet();

    /**
     * Generates a random UUID and assigns it to {@link #uniqueContactId} as a String if the unique contact ID has not
     * been set via {@link #withUniqueContactId(String)} since {@link #build()} was last called.
     * <p>
     * Also assigns the value of {@link #uniqueContactId} to {@link #displayName} if no display name has been set since
     * {@link #build()} was last called.
     * <p>
     * Should be called in every derived class's {@link #build()} method.
     */
    protected void generateMissingIdentifiers() {
        if (isUniqueContactIdManuallySet) {
            isUniqueContactIdManuallySet = false;
        } else {
            uniqueContactId = UUID.randomUUID().toString();
        }

        if (isDisplayNameManuallySet) {
            isDisplayNameManuallySet = false;
        } else {
            displayName = uniqueContactId;
        }
    }

    /**
     * Sets the unique contact ID to be used when building an {@link IContact} instance.
     *
     * @param uniqueContactId The unique contact ID to use when building a contact
     * @return This ContactBuilder instance
     */
    @NonNull
    public B withUniqueContactId(@NonNull String uniqueContactId) {
        Objects.requireNonNull(uniqueContactId, "Cannot use null contact ID when building an IContact instance.");

        if (StringUtils.isBlank(uniqueContactId)) {
            throw new IllegalArgumentException("Cannot use blank contact ID when building an IContact instance.");
        }

        this.uniqueContactId = uniqueContactId;
        isUniqueContactIdManuallySet = true;

        // noinspection unchecked
        return (B) this;
    }

    /**
     * Sets the display name to be used when building an {@link IContact} instance.
     *
     * @param displayName The display name to use when building a contact
     * @return This ContactBuilder instance
     */
    @NonNull
    public B withDisplayName(@NonNull String displayName) {
        Objects.requireNonNull(displayName, "Cannot use null display name when building an IContact instance.");

        if (StringUtils.isBlank(displayName)) {
            throw new IllegalArgumentException("Cannot use blank display name when building an IContact instance.");
        }

        this.displayName = displayName;
        isDisplayNameManuallySet = true;

        // noinspection unchecked
        return (B) this;
    }

    /**
     * Sets the attributes to be used when building an {@link IContact} instance.
     *
     * @param attributes The attributes to use when building a contact
     * @return This ContactBuilder instance
     */
    @NonNull
    public B withAttributes(@NonNull AttributeSet attributes) {
        Objects.requireNonNull(attributes, "Cannot use null set of attributes when building an IContact instance.");

        this.attributes = attributes;

        // noinspection unchecked
        return (B) this;
    }

    /**
     * Builds a contact using the currently set field values.
     * <p>
     * Derived classes implementing this method should call {@link #generateMissingIdentifiers()} so that a unique ID is generated
     * for every new contact instance that gets built.
     *
     * @return A newly constructed instance of a Contact
     */
    @NonNull
    public abstract C build();
}

