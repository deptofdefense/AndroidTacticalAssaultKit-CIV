package gov.tak.platform.contact;

import gov.tak.api.annotation.DeprecatedApi;
import gov.tak.api.contact.IGroupContact;

/**
 * Builder for group contacts.
 *
 * @see GroupContactBuilderBase2
 * @since 0.55.0
 * @deprecated No replacement
 */
@Deprecated
@DeprecatedApi(since = "0.56.2", forRemoval = true, removeAt = "1.0")
public class GroupContactBuilder extends GroupContactBuilderBase {
    @Override
    public IGroupContact build() {
        generateMissingIdentifiers();

        if (groupMembers.isEmpty()) {
            throw new IllegalArgumentException(
                    "Attempted to create empty group contact; group must contain members.");
        }

        return new GroupContact(uniqueContactId, displayName, attributes, groupMembers,
                parentContact);

    }
}

