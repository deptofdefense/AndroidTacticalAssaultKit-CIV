package gov.tak.platform.contact;

import gov.tak.api.annotation.NonNull;
import gov.tak.api.annotation.Nullable;
import gov.tak.api.contact.IContact;
import gov.tak.api.contact.IGroupContact;
import gov.tak.api.util.AttributeSet;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Internal implementation of an {@link IGroupContact}.
 * @since 0.17.0
 */
class GroupContact extends Contact implements IGroupContact {
    private final Set<IContact> groupMembers;
    private IGroupContact parent;

    /**
     * @param uniqueContactId The unique ID of this group contact
     * @param displayName     The display name of this group contact
     * @param attributes      The attributes associated with this contact
     * @param groupMembers    The group members associated with this group contact
     * @param parent          The parent group if this is a nested group (or else null)
     * @since 0.24.0
     */
    GroupContact(@NonNull String uniqueContactId,
                 @NonNull String displayName,
                 @NonNull AttributeSet attributes,
                 @NonNull Set<IContact> groupMembers,
                 @Nullable IGroupContact parent) {
        super(uniqueContactId, displayName, attributes);

        Objects.requireNonNull(groupMembers, "Set of group members cannot be null.");
        if (groupMembers.isEmpty()) {
            throw new IllegalArgumentException("Attempted to create empty group contact; group must contain members.");
        }

        this.groupMembers = new HashSet<>(groupMembers);
        this.parent = parent;
    }

    /**
     * Constructor for creating a root-level group that doesn't have a parent.
     * @param uniqueContactId The unique ID of this group contact
     * @param displayName     The display name of this group contact
     * @param attributes      The attributes associated with this contact
     * @param groupMembers    The group members associated with this group contact
     */
    GroupContact(@NonNull String uniqueContactId,
                 @NonNull String displayName,
                 @NonNull AttributeSet attributes,
                 @NonNull Set<IContact> groupMembers) {
        this(uniqueContactId, displayName, attributes, groupMembers, null);
    }

    @NonNull
    @Override
    public Set<IContact> getGroupMembers() {
        return Collections.unmodifiableSet(groupMembers);
    }

    @Override
    public IGroupContact getParentContact() {
        return parent;
    }
}

