package gov.tak.api.contact;

import gov.tak.api.annotation.NonNull;
import gov.tak.api.annotation.Nullable;

import java.util.Set;

/**
 * Represents a group contact, i.e. a group of contacts that can be communicated with via a single group. A group contact
 * can contain a mixture of individual contacts and other group contacts (i.e. nested group contacts).
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
}

