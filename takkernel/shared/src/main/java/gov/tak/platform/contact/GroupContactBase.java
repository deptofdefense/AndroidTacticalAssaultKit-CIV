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
 * <p></p>
 * <em>Developer's note:</em> This class is public strictly to resolve a JDK limitation on "split packages".
 * It is NOT intended for general use.
 *
 * @since 0.17.0
 */
public class GroupContactBase extends Contact implements IGroupContact {
    protected final Set<IContact> groupMembers;

    protected IGroupContact parentContact;

    /**
     * @param uniqueId      The unique ID of this group contact
     * @param displayName   The display name of this group contact
     * @param attributes    The attributes associated with this contact
     * @param groupMembers  The group members associated with this group contact
     * @param parentContact The parent group if this is a nested group (or else null)
     * @since 0.24.0
     */
    public GroupContactBase(@NonNull String uniqueId,
            @NonNull String displayName,
            @NonNull AttributeSet attributes,
            @NonNull Set<IContact> groupMembers,
            @Nullable IGroupContact parentContact) {
        super(uniqueId, displayName, attributes);

        Objects.requireNonNull(groupMembers, "Set of group members cannot be null.");

        this.groupMembers = new HashSet<>(groupMembers);
        this.parentContact = parentContact;
    }

    @NonNull
    @Override
    public Set<IContact> getGroupMembers() {
        return Collections.unmodifiableSet(groupMembers);
    }

    @Override
    public void setGroupMembers(@NonNull Set<IContact> groupMembers) {
        this.groupMembers.clear();
        this.groupMembers.addAll(groupMembers);
    }

    @Override
    @Nullable
    public IGroupContact getParentContact() {
        return parentContact;
    }

    @Override
    public void setParentContact(@Nullable IGroupContact parentContact) {
        this.parentContact = parentContact;
    }
}

