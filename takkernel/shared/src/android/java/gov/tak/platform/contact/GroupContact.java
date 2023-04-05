package gov.tak.platform.contact;

import gov.tak.api.annotation.NonNull;
import gov.tak.api.annotation.Nullable;
import gov.tak.api.contact.IContact;
import gov.tak.api.contact.IGroupContact;
import gov.tak.api.util.AttributeSet;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Android-specific group contact implementation that does <em>not</em> support persistence as an entity.
 *
 * @since 0.55.0
 */
class GroupContact extends GroupContactBase {
    /**
     * @param uniqueId      The unique ID of this group contact
     * @param displayName   The display name of this group contact
     * @param attributes    The attributes associated with this contact
     * @param groupMembers  The group members associated with this group contact
     * @param parentContact The parent group if this is a nested group (or else null)
     */
    GroupContact(@NonNull String uniqueId, @NonNull String displayName,
            @NonNull AttributeSet attributes, @NonNull Set<IContact> groupMembers,
            @Nullable IGroupContact parentContact) {
        super(uniqueId, displayName, attributes, groupMembers, parentContact);
    }
}
