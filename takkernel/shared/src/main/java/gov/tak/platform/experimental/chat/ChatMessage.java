package gov.tak.platform.experimental.chat;

import gov.tak.api.annotation.NonNull;
import gov.tak.api.experimental.chat.IChatMessage;

/**
 * Internal implementation of an {@link IChatMessage}.
 *
 * @since 0.21.0
 */
class ChatMessage implements IChatMessage {
    private final String message;
    private final long sendTime;
    private final long receiveTime;
    private final String messageId;

    ChatMessage(@NonNull String message, long sendTime, long receiveTime, @NonNull String messageId) {
        this.message = message;
        this.sendTime = sendTime;
        this.receiveTime = receiveTime;
        this.messageId = messageId;
    }

    @Override
    @NonNull
    public String getMessage() {
        return message;
    }

    @Override
    public long getSendTime() {
        return sendTime;
    }

    @Override
    public long getReceiveTime() {
        return receiveTime;
    }

    @Override
    @NonNull
    public String getMessageId() {
        return messageId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ChatMessage that = (ChatMessage) o;

        if (sendTime != that.sendTime) {
            return false;
        }
        if (receiveTime != that.receiveTime) {
            return false;
        }
        if (!message.equals(that.message)) {
            return false;
        }
        return messageId.equals(that.messageId);
    }

    @Override
    public int hashCode() {
        int result = message.hashCode();
        result = 31 * result + (int) (sendTime ^ (sendTime >>> 32));
        result = 31 * result + (int) (receiveTime ^ (receiveTime >>> 32));
        result = 31 * result + messageId.hashCode();
        return result;
    }
}

