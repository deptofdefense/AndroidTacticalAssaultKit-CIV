package gov.tak.platform.contact;

import gov.tak.api.annotation.NonNull;
import gov.tak.api.contact.IIndividualContact;

/**
 * Builds instances of {@link IIndividualContact}.
 *
 * @since 0.17.0
 */
public class IndividualContactBuilder extends ContactBuilder<IIndividualContact, IndividualContactBuilder> {
    @NonNull
    @Override
    public IIndividualContact build() {
        generateMissingIdentifiers();
        return new IndividualContact(uniqueContactId, displayName, attributes);
    }
}
