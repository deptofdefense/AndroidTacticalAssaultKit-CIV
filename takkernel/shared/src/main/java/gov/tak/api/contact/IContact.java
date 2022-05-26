package gov.tak.api.contact;

import gov.tak.api.annotation.NonNull;
import gov.tak.api.util.AttributeSet;

/**
 * Represents a Contact that can be communicated with.
 *
 * @since 0.17.0
 */
public interface IContact {
    /**
     * @return The unique ID of the contact
     */
    @NonNull
    String getUniqueId();

    /**
     * @return The display name of the contact (e.g. call sign, group name, etc.)
     */
    @NonNull
    String getDisplayName();

    /**
     * @return The attributes of the contact, described by a set of key/value tuples
     */
    @NonNull
    AttributeSet getAttributes();
}

