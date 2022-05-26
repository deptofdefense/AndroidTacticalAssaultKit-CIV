package gov.tak.api.experimental.chat;

import com.atakmap.coremap.maps.coords.GeoPoint;
import gov.tak.api.annotation.NonNull;
import gov.tak.api.annotation.Nullable;
import gov.tak.api.contact.IContact;

/**
 * Delegate used by {@link IChatServiceProvider}s to interact with the Chat Service API.
 *
 * @since 0.21.0
 */
public interface IChatServiceProviderDelegate {
    /**
     * @return The unique ID of the client, or null if no client is currently registered with the chat service API
     */
    @Nullable
    String getClientUniqueId();

    /**
     * @return The display name of the client (e.g. a callsign), or null if no client is currently registered with the
     * chat service API. The display name is expected to be shown to other users available through the chat service.
     */
    @Nullable
    String getClientDisplayName();

    /**
     * @return The location of the client as a GeoPoint, or null if no client is currently registered with the chat
     * service API
     */
    @Nullable
    GeoPoint getClientLocation();

    /**
     * Called by a chat service provider when a message has been received and should be passed to the chat client to be
     * shown to the user.
     *
     * @param contact     The contact from whom the message was sent
     * @param chatMessage The chat message being received
     */
    void receiveMessage(@NonNull IContact contact, @NonNull IChatMessage chatMessage);
}

