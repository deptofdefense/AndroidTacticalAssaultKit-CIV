package gov.tak.platform.experimental.chat;

import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import gov.tak.api.annotation.NonNull;
import gov.tak.api.contact.IContact;
import gov.tak.api.contact.IGroupContact;
import gov.tak.api.experimental.chat.IChatMessage;
import gov.tak.api.experimental.chat.IChatService;
import gov.tak.api.experimental.chat.IChatServiceClient;
import gov.tak.api.experimental.chat.IChatServiceClientDelegate;
import gov.tak.api.experimental.chat.IChatServiceProvider;
import gov.tak.api.experimental.chat.IChatServiceProviderDelegate;
import gov.tak.api.experimental.chat.ISendCallback;
import gov.tak.api.experimental.chat.SendStatus;
import gov.tak.platform.contact.Contacts;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Internal implementation of the {@link IChatService} interface that also acts as the entry point to the Chat Service
 * API via the {@link #getChatService()} method.
 *
 * @since 0.21.0
 */
public class ChatService implements IChatService, IChatServiceProviderDelegate, IChatServiceClientDelegate {
    private static final String TAG = "ChatService";

    private final Map<String, IChatServiceProvider> chatServiceProviders = new ConcurrentHashMap<>();
    private final Object chatServiceClientLock = new Object();

    private IChatServiceClient chatServiceClient;

    // Use initialization-on-demand holder to ensure thread safety while maintaining performance.
    private static final class ChatServiceHolder {
        private static final IChatService CHAT_SERVICE_INSTANCE = new ChatService();
    }

    ChatService() {
        // Prevent public instantiation
    }

    /**
     * @return The chat service instance used to interact with the Chat Service API
     */
    public static IChatService getChatService() {
        return ChatServiceHolder.CHAT_SERVICE_INSTANCE;
    }

    @Override
    public void sendMessage(@NonNull IContact targetContact, @NonNull IChatMessage message,
            @NonNull ISendCallback sendCallback) throws IllegalArgumentException {
        Objects.requireNonNull(targetContact, "Attempted to send message to null IContact.");
        Objects.requireNonNull(message, "Attempted to send null IChatMessage.");

        final String[] targetContactProtocols = getContactProtocols(targetContact);

        if (targetContactProtocols.length == 0) {
            throw new IllegalArgumentException(
                    "Unable to send message to Contact with no support protocols declared: "
                            + targetContact.getDisplayName());
        }

        final IChatServiceProvider chatServiceProvider = findChatServiceProviderForProtocols(targetContactProtocols);

        if (chatServiceProvider != null) {
            chatServiceProvider.sendMessage(targetContact, message, sendCallback);
        } else {
            Log.w(TAG, "Could not find chat service provider for contact " + targetContact.getDisplayName()
                    + ", unable to send message.");

            sendCallback.messageSendAttempted(SendStatus.FAIL, null);
        }
    }

    @Override
    public boolean createGroupContact(@NonNull String groupName, @NonNull Set<IContact> groupMembers,
            @NonNull String chatServiceProtocol) throws IllegalArgumentException {
        Objects.requireNonNull(groupName, "Attempted to create group contact with null group name.");
        Objects.requireNonNull(groupMembers, "Attempted to create group contact with null set of group members.");
        Objects.requireNonNull(chatServiceProtocol, "Attempted to create group contact with null protocol.");

        if (groupMembers.isEmpty()) {
            throw new IllegalArgumentException("Cannot create group contact with no group members.");
        }

        final IChatServiceProvider chatServiceProvider = findChatServiceProviderForProtocol(chatServiceProtocol);

        if (chatServiceProvider == null) {
            Log.w(TAG, "Could not find chat service provider for protocol \"" + chatServiceProtocol +
                    "\", unable to create group contact with name " + groupName + ".");

            return false;
        }

        // Verify that all proposed group members have the requested protocol.
        for (IContact groupMember : groupMembers) {
            final String[] groupMemberProtocols = getContactProtocols(groupMember);

            if (!Arrays.asList(groupMemberProtocols).contains(chatServiceProtocol)) {
                throw new IllegalArgumentException("Discovered group member that does not have protocol \"" +
                        chatServiceProtocol + "\" while attempting to create group contact.");
            }
        }

        return chatServiceProvider.createGroupContact(groupName, groupMembers);
    }

    /**
     * Finds the chat service provider responsible for the given protocol.
     *
     * @param protocol The protocol for which to find a chat service provider
     * @return The chat service provider responsible for the given protocol, or null if no such provider has been registered
     */
    private IChatServiceProvider findChatServiceProviderForProtocol(@NonNull String protocol) {
        Objects.requireNonNull(protocol, "Cannot find chat service provider for null protocol.");

        return findChatServiceProviderForProtocols(new String[] { protocol });
    }

    @Override
    public boolean deleteGroupContact(@NonNull IGroupContact groupContact) {
        Objects.requireNonNull(groupContact, "Cannot delete null group contact.");

        final String[] groupContactProtocols = getContactProtocols(groupContact);
        final IChatServiceProvider chatServiceProvider = findChatServiceProviderForProtocols(groupContactProtocols);

        if (chatServiceProvider != null) {
            return chatServiceProvider.deleteGroupContact(groupContact);
        } else {
            Log.w(TAG,
                    "Could not find chat service provider for protocols " + Arrays.toString(groupContactProtocols)
                            + ", unable to delete group contact with name " + groupContact.getDisplayName() + ".");
            return false;
        }
    }

    @Override
    public boolean updateGroupContact(@NonNull IGroupContact groupContact) {
        Objects.requireNonNull(groupContact, "Cannot update null group contact.");

        final String[] groupContactProtocols = getContactProtocols(groupContact);
        final IChatServiceProvider chatServiceProvider = findChatServiceProviderForProtocols(groupContactProtocols);

        if (chatServiceProvider != null) {
            return chatServiceProvider.updateGroupContact(groupContact);
        } else {
            Log.w(TAG,
                    "Could not find chat service provider for protocols " + Arrays.toString(groupContactProtocols)
                            + ", unable to update group contact with name " + groupContact.getDisplayName() + ".");
            return false;
        }
    }

    /**
     * Gets the chat protocols that the given contact supports.
     *
     * @param contact The contact for which to get supported protocols
     * @return An array of the supported chat protocols
     */
    private String[] getContactProtocols(IContact contact) {
        return contact.getAttributes().getStringArrayAttribute(Contacts.PROTOCOL_ATTRIBUTE_KEY);
    }

    /**
     * Finds an appropriate chat service provider given multiple protocols, using a heuristic to decide on the
     * appropriate provider when multiple matching providers exist for the given protocols.
     *
     * @param protocols The protocols for which to find a chat service provider
     * @return The chat service provider to use for the given protocols, or null if no such provider has been registered
     */
    private IChatServiceProvider findChatServiceProviderForProtocols(@NonNull String[] protocols) {
        Objects.requireNonNull(protocols, "Cannot find chat service provider for null set of protocols.");

        // TODO: Prioritize GeoChat once that provider is in place, and use first match if GeoChat not a protocol of the
        // TODO:    contact. We can improve deconfliction algorithm here after TAKX MVP.
        // TODO:
        // TODO: Or, do we always force the calling class to pass the desired protocol in as a parameter (similar to
        // TODO:    createGroupContact())?
        for (String protocol : protocols) {
            if (chatServiceProviders.containsKey(protocol)) {
                return chatServiceProviders.get(protocol);
            }
        }

        return null;
    }

    @Override
    public String getClientUniqueId() {
        synchronized (chatServiceClientLock) {
            if (chatServiceClient != null) {
                return chatServiceClient.getUniqueId();
            }
        }

        Log.w(TAG, "Attempted to get client UID when no client has been registered.");
        return null;
    }

    @Override
    public String getClientDisplayName() {
        synchronized (chatServiceClientLock) {
            if (chatServiceClient != null) {
                return chatServiceClient.getDisplayName();
            }
        }

        Log.w(TAG, "Attempted to get client display name when no client has been registered.");
        return null;
    }

    @Override
    public GeoPoint getClientLocation() {
        synchronized (chatServiceClientLock) {
            if (chatServiceClient != null) {
                return chatServiceClient.getLocation();
            }
        }

        Log.w(TAG, "Attempted to get client location when no client has been registered.");
        return null;
    }

    @Override
    public void receiveMessage(@NonNull IContact contact, @NonNull IChatMessage chatMessage) {
        Objects.requireNonNull(contact, "Cannot receive message for null contact.");
        Objects.requireNonNull(chatMessage, "Cannot receive null chat message.");

        synchronized (chatServiceClientLock) {
            if (chatServiceClient == null) {
                Log.w(TAG, "Attempted to receive message when no client has been registered to receive it.");
                return;
            }

            chatServiceClient.receiveMessage(contact, chatMessage);
        }
    }

    @NonNull
    @Override
    public IChatServiceProviderDelegate registerChatServiceProvider(@NonNull IChatServiceProvider chatServiceProvider) {
        Objects.requireNonNull(chatServiceProvider, "Cannot register null IChatServiceProvider.");

        chatServiceProviders.putIfAbsent(chatServiceProvider.getProviderProtocol(), chatServiceProvider);

        return this;
    }

    @Override
    public void unregisterChatServiceProvider(@NonNull IChatServiceProvider chatServiceProvider) {
        Objects.requireNonNull(chatServiceProvider, "Cannot unregister null IChatServiceProvider.");

        chatServiceProviders.remove(chatServiceProvider.getProviderProtocol());
    }

    @NonNull
    @Override
    public IChatServiceClientDelegate registerChatServiceClient(@NonNull IChatServiceClient chatServiceClient)
            throws IllegalArgumentException {
        Objects.requireNonNull(chatServiceClient, "Cannot register null IChatServiceClient.");

        synchronized (chatServiceClientLock) {
            final boolean isSameConsumerInstance = this.chatServiceClient == chatServiceClient;

            if (isSameConsumerInstance) {
                Log.d(TAG, "Attempted to register chat service client instance that has already been registered.");
            } else if (this.chatServiceClient != null) {
                throw new IllegalArgumentException(
                        "Different IChatServiceClient already registered, only one may be registered at a time.");
            } else {
                this.chatServiceClient = chatServiceClient;
            }
        }

        return this;
    }

    @Override
    public void unregisterChatServiceClient(@NonNull IChatServiceClient chatServiceClient) {
        Objects.requireNonNull(chatServiceClient, "Cannot unregister null IChatServiceClient.");

        synchronized (chatServiceClientLock) {
            if (this.chatServiceClient == chatServiceClient) {
                this.chatServiceClient = null;
            } else {
                Log.d(TAG, "Attempted to unregister chat service client that has not previously been registered.");
            }
        }
    }
}

