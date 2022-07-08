package gov.tak.platform.contact;

import gov.tak.api.contact.IGroupContact;

/**
 * Builder for Android-specific group contacts.
 *
 * @since 0.55.0
 */
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

