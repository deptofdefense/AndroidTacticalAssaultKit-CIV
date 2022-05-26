package gov.tak.platform.contact;

import gov.tak.api.annotation.NonNull;
import gov.tak.api.contact.IIndividualContact;
import gov.tak.api.util.AttributeSet;

/**
 * Internal implementation of an {@link IIndividualContact}.
 *
 * @since 0.17.0
 */
class IndividualContact extends Contact implements IIndividualContact {

    /**
     * @param uniqueContactId The unique ID of this individual contact
     * @param displayName     The display name of this individual contact (e.g. call sign)
     * @param attributes      The attributes associated with this contact
     */
    IndividualContact(@NonNull String uniqueContactId, @NonNull String displayName, @NonNull AttributeSet attributes) {
        super(uniqueContactId, displayName, attributes);
    }
}

