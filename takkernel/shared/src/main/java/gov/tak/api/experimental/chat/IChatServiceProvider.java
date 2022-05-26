package gov.tak.api.experimental.chat;

import gov.tak.api.annotation.NonNull;
import gov.tak.api.contact.IContact;
import gov.tak.api.contact.IGroupContact;

import java.util.Set;

/**
 * Represents a chat service provider responsible for sending messages and creating group contacts in the underlying
 * service being represented (e.g. XMPP). It's assumed that each chat service provider will be responsible for interacting
 * with an underlying chat protocol.
 * <p>
 * Can be registered with the {@link IChatService} to be notified of message send and group management requests. For
 * example:
 * <pre>
 *     ChatService.getChatService().registerChatServiceProvider(myProvider);
 * </pre>
 *
 * @see IChatService
 * @since 0.21.0
 */
public interface IChatServiceProvider {
    /**
     * @return The protocol that a given provider has (e.g. GeoChat, XMPP, etc.)
     */
    @NonNull
    String getProviderProtocol();

    /**
     * Attempts to send a chat message to the given target contact.
     *
     * @param targetContact The contact to whom the chat message should be sent
     * @param message       The message to send
     * @param sendCallback  Callback that notifies the implementor of the message-send status.
     */
    void sendMessage(@NonNull IContact targetContact, @NonNull IChatMessage message,
            @NonNull ISendCallback sendCallback);

    /**
     * Requests that a new group contact be created through this chat service provider.
     *
     * @param groupName    The name of the group to create (i.e. the display name of the IGroupContact that will be created).
     * @param groupMembers The group members that should be affiliated with the created group contact.
     * @return {@code true} if the group contact was successfully created
     */
    boolean createGroupContact(@NonNull String groupName, @NonNull Set<IContact> groupMembers);

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

