package gov.tak.platform.contact;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import gov.tak.api.annotation.NonNull;
import gov.tak.api.annotation.Nullable;
import gov.tak.api.contact.IContact;
import gov.tak.api.contact.IGroupContact;

/**
 * Builds instances of {@link IGroupContact}. Note that empty groups are not permitted, i.e. a group contact <em>must</em>
 * contain at least one group member.
 *
 * @since 0.56.2
 */
public abstract class GroupContactBuilderBase2<C extends IGroupContact, B extends GroupContactBuilderBase2<C, B>>
        extends ContactBuilder<C, B> {

    protected Set<IContact> groupMembers = new HashSet<>();
    protected IGroupContact parentContact = null;

    protected GroupContactBuilderBase2() {
        // Prevent public instantiation
    }

    /**
     * Sets the group members that will be associated with an {@link IGroupContact} being built. Can be a combination
     * of individual and group contacts (i.e. supports nested group contacts).
     *
     * @param groupMembers A set containing the group members that will belong to the group
     * @return This GroupContactBuilder instance
     */
    @NonNull
    public B withGroupMembers(@NonNull Set<IContact> groupMembers) {
        Objects.requireNonNull(groupMembers, "'groupMembers' must not be null");
        if (groupMembers.isEmpty()) throw new IllegalArgumentException("'groupMembers' must not be empty");

        this.groupMembers = new HashSet<>(groupMembers);

        //noinspection unchecked
        return (B) this;
    }

    /**
     * Sets the parent group contact that will be associated with an {@link IGroupContact} being built. This should only
     * be used when creating a nested group contact.
     *
     * @param parentContact The parent group contact, or null if attempting to create a non-nested group contact
     * @return This GroupContactBuilder instance
     */
    @NonNull
    public B withParentContact(@Nullable IGroupContact parentContact) {
        this.parentContact = parentContact;

        //noinspection unchecked
        return (B) this;
    }

    @NonNull
    @Override
    public C build() {
        prepareForBuild();

        //noinspection unchecked
        return (C) new GroupContactBase(uniqueContactId, displayName, attributes, groupMembers, parentContact);
    }

    /**
     * Prepare/validate our state prior to a call to {@link #build()}.
     *
     * @since 0.56.2
     */
    protected void prepareForBuild() {
        generateMissingIdentifiers();

        if (groupMembers.isEmpty()) {
            throw new IllegalArgumentException("Attempted to create empty group contact; group must contain members.");
        }
    }
}

