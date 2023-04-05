package gov.tak.api.contact;

import gov.tak.api.annotation.NonNull;
import gov.tak.api.annotation.Nullable;

import java.util.Set;

/**
 * Represents a group contact, i.e. a group of contacts that can be communicated with via a single group. A group contact
 * can contain a mixture of individual contacts and other group contacts (i.e. nested group contacts).
 *
 * @since 0.17.0
 */
public interface IGroupContact extends IContact {
    /**
     * @return A read-only set containing all group members
     */
    @NonNull
    Set<IContact> getGroupMembers();

    /**
     * @return The parent group if this is a nested group, or null if this is a top-level group.
     * @since 0.24.0
     */
    @Nullable
    default IGroupContact getParentContact() {
        return null; // default to null for root nodes
    }

    /**
     * Sets the parent of a group contact. This method allows for avoiding a "chicken and egg" problem
     * in which a group member must contain a parent contact, and the parent contact must contain
     * said group member.
     *
     * @param parentContact The parent contact to set on this group contact
     * @since 0.55.0
     */
    default void setParentContact(@Nullable IGroupContact parentContact) {
        // No-op
    }

    /**
     * Set the collection of group members, replacing existing members.
     * <p>
     * <em>Note:</em> This default implementation is useless/wrong, but is required to avoid an API breaking change
     *
     * @param groupMembers New set of group members
     * @since 0.56.2
     */
    default void setGroupMembers(@NonNull Set<IContact> groupMembers) {
    }
}
