package gov.tak.platform.contact;

import gov.tak.api.annotation.NonNull;
import gov.tak.api.contact.IContactStore;

/**
 * Acts as an entry-point to the Contacts API by providing interface instances.
 *
 * @since 0.17.0
 */
public final class Contacts {
    public static final String PROTOCOL_ATTRIBUTE_KEY = "Contact Protocols";

    // Use initialization-on-demand holder to ensure thread safety while maintaining performance.
    private static final class ContactStoreHolder {
        private static final IContactStore STORE_INSTANCE = new ContactStore();
    }

    private Contacts() {
        // Prevent instantiation
    }

    /**
     * @return The contact store used to interact with the Contacts API
     */
    @NonNull
    public static IContactStore getContactStore() {
        return ContactStoreHolder.STORE_INSTANCE;
    }
}

