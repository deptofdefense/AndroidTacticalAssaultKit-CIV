package gov.tak.platform.contact;

import gov.tak.api.annotation.NonNull;

/**
 * Acts as an entry-point to the Contacts API by providing interface instances.
 *
 * @since 0.17.0
 */
public final class Contacts {
    public static final String PROTOCOL_ATTRIBUTE_KEY = "Contact Protocols";

    private static final class ContactStoreHolder {
        private static final ContactStore STORE_INSTANCE = new DefaultContactStore();
    }

    private Contacts() {
        // Prevent instantiation
    }

    /**
     * @return The contact store used to interact with the Contacts API
     * @since 0.32.0
     */
    @NonNull
    public static ContactStore getDefaultContactStore() {
        return ContactStoreHolder.STORE_INSTANCE;
    }
}

