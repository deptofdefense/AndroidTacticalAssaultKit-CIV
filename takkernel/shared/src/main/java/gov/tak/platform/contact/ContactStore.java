package gov.tak.platform.contact;

import gov.tak.api.annotation.NonNull;
import gov.tak.api.annotation.Nullable;
import gov.tak.api.contact.IContact;
import gov.tak.api.contact.IContactListener;

/**
 * The Contact Store allows core applications and plugins to manage {@link IContact}s through the API, as well as
 * register/unregister {@link IContactListener}s so that listeners can receive notifications when Contacts are added,
 * removed, or updated.
 * <p>
 * Aside from takkernel-implemented components, it is typically expected that plugins will be responsible for managing
 * Contacts as sourced through a specific transport protocol (e.g. XMPP).
 * <p>
 * On the other hand, it is typically expected that core applications will register as Contact Listeners to be notified
 * of changes in Contacts, thereby informing the user of all available Contacts at any given time.
 * <p>
 * An instance of this class can be accessed through the Contacts class, and immutable {@link IContact} instances
 * can be built through the available ContactBuilder classes. For example:
 * <pre>
 *     IContactStore contactStore = Contacts.getDefaultContactStore();
 *     IIndividualContact contact = new IndividualContactBuilder().withUniqueId("uniqueNewYork").build();
 *     contactStore.registerContactListener(contactListener);
 *     contactStore.addContact(contact);
 *     contactStore.unregisterContactListener(contactListener);
 * </pre>
 *
 * @see IContact
 * @see IContactListener
 * @since 0.32.0
 */
public abstract class ContactStore {
    ContactStore() {
        // Prevent API consumers from extending this class
    }

    /**
     * Adds a contact to be stored and maintained by the API. Note that attempting to add a contact that has already been added will
     * cause no effect; no listeners will be notified of the attempt.
     * <p>
     * This method should not be called from an {@link IContactListener} or any form of UI layer that is not directly
     * responsible for managing and communicating with contacts through a chat service of some kind (which should never
     * be the case).
     *
     * @param contact The contact to add
     */
    public abstract void addContact(@NonNull IContact contact);

    /**
     * Updates a contact that is currently being stored by the API. I.e., replaces the contact that has a unique ID matching
     * that of the given contact, as decided by {@link IContact#getUniqueId()}. Note that the contact must have already
     * been added via {@link #addContact(IContact)}.
     * <p>
     * This method should not be called from an {@link IContactListener} or any form of UI layer that is not directly
     * responsible for managing and communicating with contacts through a chat service of some kind (which should never
     * be the case).
     *
     * @param contact The updated contact
     * @throws IllegalArgumentException If there is no existing contact to update
     */
    public abstract void updateContact(@NonNull IContact contact);

    /**
     * Removes a contact so that it is no longer stored by the API. If the contact does not exist, then listeners will
     * not be notified of the removal attempt.
     * <p>
     * This method should not be called from an {@link IContactListener} or any form of UI layer that is not directly
     * responsible for managing and communicating with contacts through a chat service of some kind (which should never
     * be the case).
     *
     * @param uniqueContactId The unique ID of the contact to remove
     */
    public abstract void removeContact(@NonNull String uniqueContactId);

    /**
     * Gets a contact that has a UID matching the given UID string (as decided by {@link String#equals(Object)}).
     *
     * @param contactUid The UID of the contact to get
     * @return The contact with the given UID, or null if no such contact exists in the store
     */
    @Nullable
    public abstract IContact getContact(@NonNull String contactUid);

    /**
     * Registers a contact listener to be notified of changes to all existing contacts. The same listener instance
     * cannot be registered more than once (without first unregistering via {@link #unregisterContactListener(IContactListener)}).
     *
     * @param contactListener The listener to register
     */
    public abstract void registerContactListener(@NonNull IContactListener contactListener);

    /**
     * Unregisters a contact listener so that it no longer receives notifications about contacts.
     *
     * @param contactListener The listener to unregister
     */
    public abstract void unregisterContactListener(@NonNull IContactListener contactListener);
}

