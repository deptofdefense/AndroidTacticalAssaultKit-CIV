package gov.tak.api.experimental.chat;

import gov.tak.api.annotation.NonNull;

/**
 * The primary interface to be used for interacting with the Chat Service API. Allows for registration of
 * {@link IChatServiceProvider}s capable of handling the sending and receiving of messages through an underlying transport
 * protocol, as well as the registration of an {@link IChatServiceClient} that can be notified of received messages.
 * <p>
 * An instance of the chat service can be retrieved through the ChatService.getChatService() static method.
 *
 * @since 0.21.0
 */
public interface IChatService {
    /**
     * Registers a chat service provider capable of handling the sending/receiving of messages through an underlying
     * transport protocol.
     *
     * @param chatServiceProvider The chat service provider to register
     * @return A delegate that can be used by the provider to interact with the Chat Service API
     */
    @NonNull
    IChatServiceProviderDelegate registerChatServiceProvider(@NonNull IChatServiceProvider chatServiceProvider);

    /**
     * Unregisters a chat service provider that was previously registered, or does nothing in the event that the given
     * provider is not registered.
     *
     * @param chatServiceProvider The chat service provider to unregister
     */
    void unregisterChatServiceProvider(@NonNull IChatServiceProvider chatServiceProvider);

    /**
     * Registers the chat service client to be notified of received messages. As a chat service client represents the
     * end user device, only one client can be registered with the chat service at any given time.
     *
     * @param chatServiceClient The chat service client to be registered
     * @return A delegate that can be used by the client to interact with the Chat Service API
     * @throws IllegalArgumentException if a different client instance has already been registered and not yet unregistered
     */
    @NonNull
    IChatServiceClientDelegate registerChatServiceClient(@NonNull IChatServiceClient chatServiceClient)
            throws IllegalArgumentException;

    /**
     * Unregisters a chat service client that was previously registered, or does nothing in the event that the given
     * client is not registered.
     *
     * @param chatServiceClient The chat service client to unregister
     */
    void unregisterChatServiceClient(@NonNull IChatServiceClient chatServiceClient);
}

