package gov.tak.api.experimental.chat;

import gov.tak.api.annotation.NonNull;

/**
 * Represents a chat message with message content and timestamps indicating the send and receive times.
 *
 * @since 0.21.0
 */
public interface IChatMessage {
    /**
     * @return The message content
     */
    @NonNull
    String getMessage();

    /**
     * @return A timestamp indicating the time the message was sent, in milliseconds since epoch (i.e. UTC)
     */
    long getSendTime();

    /**
     * @return A timestamp indicating the time the message was received, in milliseconds since epoch (i.e. UTC), or
     * a negative number if no valid receive time has been set
     */
    long getReceiveTime();

    /**
     * @return The unique ID of a given message
     */
    @NonNull
    String getMessageId();
}

