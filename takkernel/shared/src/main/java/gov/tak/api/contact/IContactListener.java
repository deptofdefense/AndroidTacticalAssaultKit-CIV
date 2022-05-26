package gov.tak.api.contact;

import gov.tak.api.annotation.NonNull;

/**
 * Listener interface that can be registered for notification of changes to contacts. See {@link IContactStore} for
 * registration methods.
 *
 * @see IContactStore
 * @since 0.17.0
 */
public interface IContactListener {
    /**
     * Called when a contact has been added to the API.
     *
     * @param addedContact The contact that was added
     */
    void contactAdded(@NonNull IContact addedContact);

    /**
     * Called when a contact has been removed from the API.
     *
     * @param removedContact The contact that was removed
     */
    void contactRemoved(@NonNull IContact removedContact);

    /**
     * Called when a contact has been updated in the API.
     *
     * @param updatedContact The contact that was updated
     */
    void contactUpdated(@NonNull IContact updatedContact);
}

