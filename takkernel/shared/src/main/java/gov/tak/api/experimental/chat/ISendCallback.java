package gov.tak.api.experimental.chat;

import gov.tak.api.annotation.NonNull;
import gov.tak.api.annotation.Nullable;

/**
 * Callback interface used by the chat service to inform implementing classes of message-send status.
 *
 * @since 0.21.0
 */
public interface ISendCallback {

    /**
     * Called by the chat service after a message-send has been attempted.
     *
     * @param sendStatus      Status of send attempt
     * @param sendEventObject Optional object associated with send attempt
     */
    void messageSendAttempted(@NonNull SendStatus sendStatus, @Nullable Object sendEventObject);
}

