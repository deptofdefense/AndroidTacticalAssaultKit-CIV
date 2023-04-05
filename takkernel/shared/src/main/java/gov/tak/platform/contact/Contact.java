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

    protected String uniqueId;
    protected String displayName;
    protected AttributeSet attributes;

    /**
     * @param uniqueId    The unique ID of this contact, cannot be null nor blank (i.e. must contain non-whitespace
     *                    characters)
     * @param displayName The display name of this contact, cannot be null nor blank (i.e. must contain non-whitespace
     *                    characters)
     * @param attributes  The attributes associated with this contact, cannot be null
     */
    protected Contact(@NonNull String uniqueId, @NonNull String displayName, @NonNull AttributeSet attributes) {
        this.uniqueId = Objects.requireNonNull(uniqueId, "Unique Contact ID cannot be null.");
        this.displayName = Objects.requireNonNull(displayName, "Contact display name cannot be null.");
        this.attributes = Objects.requireNonNull(attributes, "Contact attributes cannot be null.");

        if (StringUtils.isBlank(uniqueId)) {
            throw new IllegalArgumentException("Unique Contact ID must not be blank.");
        }
        if (StringUtils.isBlank(displayName)) {
            throw new IllegalArgumentException("Contact display name must not be blank.");
        }
    }

    @NonNull
    @Override
    public String getUniqueId() {
        return uniqueId;
    }

    @NonNull
    @Override
    public String getDisplayName() {
        return displayName;
    }

    @NonNull
    @Override
    public AttributeSet getAttributes() {
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

        return getUniqueId().equals(contact.getUniqueId());
    }

    @Override
    public int hashCode() {
        return getUniqueId().hashCode();
    }

    @Override
    public String toString() {
        return "Contact{" +
                "uniqueId='" + uniqueId + '\'' +
                ", displayName='" + displayName + '\'' +
                ", attributes=" + attributes +
                '}';
    }
}

