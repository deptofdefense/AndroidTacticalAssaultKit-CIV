package gov.tak.api.experimental.chat;

import com.atakmap.coremap.maps.coords.GeoPoint;
import gov.tak.api.annotation.NonNull;
import gov.tak.api.contact.IContact;

/**
 * Consumer of the {@link IChatService}, capable of receiving messages to display to the end user. Note that, because
 * a chat service consumer effectively represents the end user, only one consumer can be registered with the chat
 * service at any given time.
 * <p>
 * Can be registered with the {@link IChatService} as follows:
 * <pre>
 *      ChatService.getChatService().registerChatServiceConsumer(myConsumer);
 * </pre>
 *
 * @see IChatService
 * @since 0.21.0
 */
public interface IChatServiceClient {
    /**
     * @return The unique ID of the consumer
     */
    @NonNull
    String getUniqueId();

    /**
     * @return The display name of the consumer (e.g. a callsign), shown to all users communicating with this consumer
     */
    @NonNull
    String getDisplayName();

    /**
     * @return The current location of the consumer, represented as a GeoPoint
     */
    @NonNull
    GeoPoint getLocation();

    /**
     * @param contact     The contact from whom the message was sent
     * @param chatMessage The chat message being received
     */
    void receiveMessage(@NonNull IContact contact, @NonNull IChatMessage chatMessage);
}

