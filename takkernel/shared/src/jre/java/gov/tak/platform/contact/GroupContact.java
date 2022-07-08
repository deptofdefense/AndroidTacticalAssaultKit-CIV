package gov.tak.platform.contact;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import gov.tak.api.annotation.DeprecatedApi;
import gov.tak.api.annotation.NonNull;
import gov.tak.api.annotation.Nullable;
import gov.tak.api.contact.IContact;
import gov.tak.api.contact.IGroupContact;
import gov.tak.api.util.AttributeSet;
import gov.takx.api.persist.IPersistable;

/**
 * Group contact implementation that supports persistence via javax.persistence annotations.
 *
 * @since 0.55.0
 * @deprecated No replacement
 */
@SuppressWarnings("unused")
@Deprecated
@DeprecatedApi(since = "0.56.2", forRemoval = true, removeAt = "1.0")
public class GroupContact extends GroupContactBase implements IPersistable {

    private Integer id;

    private String uniqueId;

    private String displayName;

    private String parentUid;

    private Set<String> memberUids;

    private Map<String, String> memberDisplayNames;

    private Map<String, String> memberAttributesAsJson;

    private AttributeSet attributes;

    /**
     * No-args constructor to support persistence.
     */
    protected GroupContact() {
        this(UUID.randomUUID().toString(), UUID.randomUUID().toString(), new AttributeSet(),
                Collections.emptySet(), null);
    }

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

        parentUid = parentContact == null ? null : parentContact.getUniqueId();
        memberUids = extractGroupMemberUids(groupMembers);
        memberDisplayNames = extractGroupMemberDisplayNames(groupMembers);
        memberAttributesAsJson = extractGroupMemberAttributesAsJson(groupMembers);
    }

    /**
     * Extracts the group member UIDs from the given set of group members.
     *
     * @param groupMembers The group members from which UIDs are extracted
     * @return A set containing the group member UIDs
     */
    private Set<String> extractGroupMemberUids(Set<IContact> groupMembers) {
        final Set<String> memberUids = new HashSet<>();

        for (IContact groupMember : groupMembers) {
            memberUids.add(groupMember.getUniqueId());
        }

        return memberUids;
    }

    /**
     * Extracts the group member display names from the given set of group members.
     *
     * @param groupMembers The group members from which display names are extracted
     * @return A map containing the group member display names by UID
     */
    private Map<String, String> extractGroupMemberDisplayNames(Set<IContact> groupMembers) {
        final Map<String, String> memberDisplayNames = new HashMap<>();

        for (IContact groupMember : groupMembers) {
            memberDisplayNames.put(groupMember.getUniqueId(), groupMember.getDisplayName());
        }

        return memberDisplayNames;
    }

    /**
     * Extracts the group member attributes (as JSON strings) from the given set of group members.
     *
     * @param groupMembers The group members from which attributes are extracted
     * @return A map containing the group member attributes as JSON strings, by UID
     */
    private Map<String, String> extractGroupMemberAttributesAsJson(Set<IContact> groupMembers) {
        final Map<String, String> memberAttributesAsJson = new HashMap<>();

        for (IContact groupMember : groupMembers) {
            memberAttributesAsJson.put(
                    groupMember.getUniqueId(), groupMember.getAttributes().toJson());
        }

        return memberAttributesAsJson;
    }

    public Integer getId() {
        return id;
    }

    public void setId(@NonNull Integer id) {
        this.id = Objects.requireNonNull(id, "'id' must not be null");
    }

    @NonNull
    public String getUniqueId() {
        return uniqueId != null ? uniqueId : super.getUniqueId();
    }

    public void setUniqueId(@NonNull String uniqueContactId) {
        this.uniqueId =
                Objects.requireNonNull(uniqueContactId, "'uniqueContactId' must not be null");
    }

    @NonNull
    public String getDisplayName() {
        return displayName != null ? displayName : super.getDisplayName();
    }

    public void setDisplayName(@NonNull String displayName) {
        this.displayName = Objects.requireNonNull(displayName, "'displayName' must not be null");
    }

    @Nullable
    public String getParentUid() {
        return parentUid;
    }

    public void setParentUid(@Nullable String parentUid) {
        this.parentUid = parentUid;
    }

    @NonNull
    public Set<String> getMemberUids() {
        return new HashSet<>(memberUids);
    }

    public void setMemberUids(@NonNull Set<String> memberUids) {
        this.memberUids = Objects.requireNonNull(memberUids, "'memberUids' must not be null");
    }

    @NonNull
    public Map<String, String> getMemberDisplayNames() {
        return new HashMap<>(memberDisplayNames);
    }

    public void setMemberDisplayNames(@NonNull Map<String, String> memberDisplayNames) {
        this.memberDisplayNames =
                Objects.requireNonNull(memberDisplayNames, "'memberDisplayNames' must not be null");
    }

    @NonNull
    public Map<String, String> getMemberAttributesAsJson() {
        return new HashMap<>(memberAttributesAsJson);
    }

    public void setMemberAttributesAsJson(@NonNull Map<String, String> memberAttributesAsJson) {
        this.memberAttributesAsJson = Objects.requireNonNull(memberAttributesAsJson,
                "'memberAttributesAsJson' must not be null");
    }

    public AttributeSet getAttributes() {
        return attributes != null ? attributes : super.getAttributes();
    }

    public void setAttributes(@NonNull AttributeSet attributes) {
        this.attributes = Objects.requireNonNull(attributes, "'attributes' must not be null");
    }
}

