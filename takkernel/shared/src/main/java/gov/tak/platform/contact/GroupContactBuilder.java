package gov.tak.platform.contact;

import gov.tak.api.annotation.NonNull;
import gov.tak.api.annotation.Nullable;
import gov.tak.api.contact.IContact;
import gov.tak.api.contact.IGroupContact;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Builds instances of {@link IGroupContact}. Note that empty groups are not permitted, i.e. a group contact <em>must</em>
 * contain at least one group member.
 *
 * @since 0.17.0
 */
public class GroupContactBuilder extends ContactBuilder<IGroupContact, GroupContactBuilder> {
    private Set<IContact> groupMembers = new HashSet<>();
    private IGroupContact parentContact = null;

    /**
     * Sets the group members that will be associated with an {@link IGroupContact} being built. Can be a combination
     * of individual and group contacts (i.e. supports nested group contacts).
     *
     * @param groupMembers A set containing the group members that will belong to the group
     * @return This GroupContactBuilder instance
     */
    @NonNull
    public GroupContactBuilder withGroupMembers(@NonNull Set<IContact> groupMembers) {
        Objects.requireNonNull(groupMembers,
                "Cannot use null group members when building IGroupContact instance.");

        if (groupMembers.isEmpty()) {
            throw new IllegalArgumentException(
                    "Cannot use empty set of group members when building IGroupContact instance.");
        }

        this.groupMembers = new HashSet<>(groupMembers);
        return this;
    }

    /**
     * Sets the parent group contact that will be associated with an {@link IGroupContact} being built. This should only
     * be used when creating a nested group contact.
     *
     * @param parentContact The parent group contact, or null if attempting to create a non-nested group contact
     * @return This GroupContactBuilder instance
     * @since 0.27.0
     */
    @NonNull
    public GroupContactBuilder withParentContact(@Nullable IGroupContact parentContact) {
        this.parentContact = parentContact;
        return this;
    }

    @NonNull
    @Override
    public IGroupContact build() {
        generateMissingIdentifiers();
        return new GroupContact(uniqueContactId, displayName, attributes, groupMembers, parentContact);
    }
}

