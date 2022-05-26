package gov.tak.platform.experimental.chat;

import gov.tak.api.annotation.NonNull;
import gov.tak.api.experimental.chat.IChatMessage;

import java.util.Objects;
import java.util.UUID;

/**
 * Builder class to be used for building {@link IChatMessage}s.
 *
 * @since 0.21.0
 */
public class ChatMessageBuilder {
    private final String message;
    private final long sendTime;

    private long receiveTime = -1;
    private String messageId = UUID.randomUUID().toString();

    /**
     * @param message  The message to be used when building a chat message
     * @param sendTime The timestamp representing the send-time of the message. Must be a positive, non-zero {@code long}.
     */
    public ChatMessageBuilder(@NonNull String message, long sendTime) {
        this.message = Objects.requireNonNull(message, "Chat message content cannot be null.");
        this.sendTime = sendTime;
    }

    /**
     * @param receiveTime The timestamp representing the receive-time of the message. Must be a positive, non-zero {@code long}.
     * @return This ChatMessageBuilder instance
     * @throws IllegalArgumentException if the given receive time is less than or equal to zero
     */
    @NonNull
    public ChatMessageBuilder withReceiveTime(long receiveTime) throws IllegalArgumentException {
        if (receiveTime <= 0) {
            throw new IllegalArgumentException("IChatMessage receive-time must be positive and non-zero.");
        }

        this.receiveTime = receiveTime;

        return this;
    }

    @NonNull
    public ChatMessageBuilder withMessageId(@NonNull String messageId) {
        this.messageId = Objects.requireNonNull(messageId, "Chat message ID cannot be null.");

        return this;
    }

    /**
     * @return A newly-constructed chat message instance.
     */
    @NonNull
    public IChatMessage build() {
        return new ChatMessage(message, sendTime, receiveTime, messageId);
    }
}

