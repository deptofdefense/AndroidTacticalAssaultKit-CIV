package gov.tak.platform.contact;

import com.atakmap.coremap.log.Log;
import gov.tak.api.annotation.NonNull;
import gov.tak.api.contact.IContact;
import gov.tak.api.contact.IContactListener;

/**
 * Used internally by the {@link ContactStore} to notify listeners of changes to {@link IContact}s on a separate thread.
 *
 * @since 0.17.0
 */
class ContactNotification implements Runnable {
    private static final String TAG = "ContactNotification";

    private final IContactListener currentListener;
    private final IContact contact;
    private final ContactNotificationType notificationType;

    enum ContactNotificationType {
        ADD, UPDATE, REMOVE
    }

    /**
     * @param currentListener  The listener to be notified
     * @param contact          The contact that was added/removed/updated
     * @param notificationType The notification type
     */
    ContactNotification(@NonNull IContactListener currentListener,
            @NonNull IContact contact,
            @NonNull ContactNotificationType notificationType) {
        this.currentListener = currentListener;
        this.contact = contact;
        this.notificationType = notificationType;
    }

    @Override
    public void run() {
        switch (notificationType) {
            case ADD:
                currentListener.contactAdded(contact);
                break;
            case UPDATE:
                currentListener.contactUpdated(contact);
                break;
            case REMOVE:
                currentListener.contactRemoved(contact);
                break;
            default:
                Log.d(TAG, String.format("Received unsupported ContactNotificationType: %s", notificationType));
        }
    }
}

