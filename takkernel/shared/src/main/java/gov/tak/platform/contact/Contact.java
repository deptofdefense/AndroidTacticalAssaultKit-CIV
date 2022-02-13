package gov.tak.platform.contact;

import gov.tak.api.annotation.NonNull;
import gov.tak.api.contact.IContact;
import gov.tak.api.util.AttributeSet;
import org.apache.commons.lang.StringUtils;

import java.util.Objects;

/**
 * Base class for internal implementations of an {@link IContact}.
 *
 * @since 0.17.0
 */
abstract class Contact implements IContact {
    private final String uniqueContactId;
    private final String displayName;
    private final AttributeSet attributes;

    /**
     * @param uniqueContactId The unique ID of this contact, cannot be null nor blank (i.e. must contain non-whitespace
     *                        characters)
     * @param displayName     The display name of this contact, cannot be null nor blank (i.e. must contain non-whitespace
     *                        characters)
     * @param attributes      The attributes associated with this contact, cannot be null
     */
    protected Contact(@NonNull String uniqueContactId, @NonNull String displayName, @NonNull AttributeSet attributes) {
        this.uniqueContactId = Objects.requireNonNull(uniqueContactId, "Unique Contact ID cannot be null.");
        this.displayName = Objects.requireNonNull(displayName, "Contact display name cannot be null.");
        this.attributes = Objects.requireNonNull(attributes, "Contact attributes cannot be null.");

        if (StringUtils.isBlank(uniqueContactId)) {
            throw new IllegalArgumentException("Unique Contact ID must not be blank.");
        }
        if (StringUtils.isBlank(displayName)) {
            throw new IllegalArgumentException("Contact display name must not be blank.");
        }
    }

    @NonNull
    @Override
    public String getUniqueId() {
        return uniqueContactId;
    }

    @NonNull
    @Override
    public String getDisplayName() {
        return displayName;
    }

    @NonNull
    @Override
    public AttributeSet getAttributes() {
        // TODO: Return a read-only copy of the attributes for full immutability
        return attributes;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }

        Contact contact = (Contact) object;

        return uniqueContactId.equals(contact.uniqueContactId);
    }

    @Override
    public int hashCode() {
        return uniqueContactId.hashCode();
    }
}

