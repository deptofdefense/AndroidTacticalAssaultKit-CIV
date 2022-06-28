package gov.tak.platform.contact;

import gov.tak.api.annotation.DeprecatedApi;
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
    private static final class DeprecatedContactStoreHolder {
        private static final IContactStore DEPRECATED_STORE_INSTANCE = new DefaultContactStore();
    }

    private static final class ContactStoreHolder {
        private static final ContactStore STORE_INSTANCE = new DefaultContactStore();
    }

    private Contacts() {
        // Prevent instantiation
    }

    /**
     * @return The contact store used to interact with the Contacts API
     * @deprecated Since 0.32, use {@link #getDefaultContactStore()} instead
     */
    @NonNull
    @DeprecatedApi(since = "0.32", forRemoval = true, removeAt = "1.0")
    @Deprecated
    public static IContactStore getContactStore() {
        return DeprecatedContactStoreHolder.DEPRECATED_STORE_INSTANCE;
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

