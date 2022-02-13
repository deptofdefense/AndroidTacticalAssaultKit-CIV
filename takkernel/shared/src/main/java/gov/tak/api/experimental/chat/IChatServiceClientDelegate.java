package gov.tak.api.experimental.chat;

import gov.tak.api.annotation.NonNull;
import gov.tak.api.contact.IContact;
import gov.tak.api.contact.IGroupContact;

import java.util.Set;

/**
 * Delegate to be used by {@link IChatServiceClient}s for sending {@link IChatMessage}s and requesting
 * {@link IGroupContact} changes.
 *
 * @since 0.21.0
 */
public interface IChatServiceClientDelegate {
    /**
     * Attempts to send a chat message to the given target contact.
     *
     * @param targetContact The contact to whom the chat message should be sent
     * @param message       The message to send
     * @param sendCallback  Callback that notifies the implementor of the message-send status.
     * @throws IllegalArgumentException if the given contact does not have any supported protocols declared as an attribute
     */
    void sendMessage(@NonNull IContact targetContact, @NonNull IChatMessage message,
            @NonNull ISendCallback sendCallback) throws IllegalArgumentException;

    /**
     * Requests that a new group contact be created for the given chat service protocol (e.g. GeoChat, XMPP, etc.). All
     * requested group members must support the given chat service protocol, and group members cannot be empty.
     *
     * @param groupName           The name of the group to create (i.e. the display name of the IGroupContact that will be created)
     * @param groupMembers        The group members that should be affiliated with the created group contact
     * @param chatServiceProtocol The chat service protocol that should be used to create the group contact
     * @return {@code true} if the group contact was successfully created
     * @throws IllegalArgumentException if group members is empty or a given group member does not support the requested protocol
     */
    boolean createGroupContact(@NonNull String groupName, @NonNull Set<IContact> groupMembers,
            @NonNull String chatServiceProtocol) throws IllegalArgumentException;

    /**
     * Requests that a group contact be deleted.
     *
     * @param groupContact The group contact to delete
     * @return {@code true} if the group contact was successfully deleted
     */
    boolean deleteGroupContact(@NonNull IGroupContact groupContact);

    /**
     * Requests that a group contact be updated.
     *
     * @param groupContact The group contact to update
     * @return {@code true} if the group contact was successfully updated
     */
    boolean updateGroupContact(@NonNull IGroupContact groupContact);
}

