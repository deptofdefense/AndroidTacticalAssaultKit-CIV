package gov.tak.platform.contact;

import com.atakmap.coremap.log.Log;
import gov.tak.api.annotation.NonNull;
import gov.tak.api.contact.IContact;
import gov.tak.api.contact.IContactListener;
import gov.tak.api.contact.IContactStore;
import org.apache.commons.lang.StringUtils;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Responsible for storing and managing {@link IContact}s for the Contacts API. See {@link IContactStore} for more
 * information.
 *
 * @since 0.17.0
 */
class ContactStore implements IContactStore {
    private static final String TAG = "ContactStore";

    private final Set<IContactListener> contactListeners = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<String, IContact> allContacts = new ConcurrentHashMap<>();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    @Override
    public void addContact(@NonNull IContact contact) {
        Objects.requireNonNull(contact, "Attempted to add null IContact, must be non-null.");

        final String contactUid = contact.getUniqueId();

        if (allContacts.containsKey(contactUid)) {
            Log.w(TAG, "Attempted to add contact that has already been added to Contact API: " + contactUid);
            return;
        }

        allContacts.put(contactUid, contact);

        Log.d(TAG, "Added contact with UID \"" + contactUid + "\".");

        // Notify listeners on separate threads to prevent a cyclic call-stack.
        for (IContactListener listener : contactListeners) {
            executorService.submit(new ContactNotification(
                    listener,
                    contact,
                    ContactNotification.ContactNotificationType.ADD));
        }
    }

    @Override
    public void updateContact(@NonNull IContact contact) {
        Objects.requireNonNull(contact, "Attempted to update null IContact, must be non-null");

        final String contactUid = contact.getUniqueId();

        if (!allContacts.containsKey(contactUid)) {
            throw new IllegalArgumentException(
                    "Cannot update Contact that has never been added. Use addContact(IContact) instead.");
        }

        allContacts.replace(contactUid, contact);

        Log.d(TAG, "Updated contact with UID \"" + contactUid + "\".");

        // Notify listeners on separate threads to prevent a cyclic call-stack.
        for (IContactListener listener : contactListeners) {
            executorService.submit(new ContactNotification(
                    listener,
                    contact,
                    ContactNotification.ContactNotificationType.UPDATE));
        }
    }

    @Override
    public void removeContact(@NonNull String uniqueContactId) {
        Objects.requireNonNull(uniqueContactId, "Unique Contact ID cannot be null.");

        if (StringUtils.isBlank(uniqueContactId)) {
            throw new IllegalArgumentException("Unique Contact ID must not be blank.");
        }

        final IContact removedContact = allContacts.remove(uniqueContactId);

        if (removedContact == null) {
            Log.d(TAG, "Attempted to remove IContact that does not currently exist in the contact store.");
            return;
        }

        Log.d(TAG, "Removed contact with UID \"" + uniqueContactId + "\".");

        // Notify listeners on separate threads to prevent a cyclic call-stack.
        for (IContactListener listener : contactListeners) {
            executorService.submit(new ContactNotification(
                    listener,
                    removedContact,
                    ContactNotification.ContactNotificationType.REMOVE));
        }
    }

    @Override
    public boolean containsContact(@NonNull IContact contact) {
        Objects.requireNonNull(contact, "Attempted to check for existence of null Contact, must be non-null.");

        return allContacts.containsKey(contact.getUniqueId());
    }

    @Override
    public void registerContactListener(@NonNull IContactListener contactListener) {
        Objects.requireNonNull(contactListener, "Cannot register null IContactListener.");

        if (contactListeners.contains(contactListener)) {
            Log.w(TAG, "Attempted to register IContactListener that has already been registered, ignoring...");
            return;
        }

        contactListeners.add(contactListener);

        // Back-fill the new listener with existing Contacts, and do so on a separate thread to prevent loop-back.
        for (IContact contact : allContacts.values()) {
            executorService.submit(new ContactNotification(
                    contactListener,
                    contact,
                    ContactNotification.ContactNotificationType.ADD
            ));
        }
    }

    @Override
    public void unregisterContactListener(@NonNull IContactListener contactListener) {
        Objects.requireNonNull(contactListener, "Cannot unregister null IContactListener.");

        if (!containsListener(contactListener)) {
            Log.w(TAG, "Attempted to unregister IContactListener that is not currently registered, ignoring...");
            return;
        }

        contactListeners.remove(contactListener);
    }

    /**
     * Checks if a contact listener is currently registered.
     *
     * @param contactListener The listener to check for registration
     * @return {@code true} if the listener is currently registered
     */
    public boolean containsListener(@NonNull IContactListener contactListener) {
        Objects.requireNonNull(contactListener, "Attempted to check for null Contact Listener.");

        return contactListeners.contains(contactListener);
    }
}

