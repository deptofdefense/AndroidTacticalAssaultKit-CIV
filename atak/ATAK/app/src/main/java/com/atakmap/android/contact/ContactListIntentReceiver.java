
package com.atakmap.android.contact;

import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.coremap.log.Log;

import java.util.LinkedList;
import java.util.List;

/**
 * @deprecated Non-functional code. Maintains a list, but does not allow
 *             access nor any external operations
 */
@Deprecated
@DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.4")
public class ContactListIntentReceiver {

    public static final String TAG = "ContactListIntentReceiver";

    // Singleton
    private static ContactListIntentReceiver instance = null;

    synchronized public static ContactListIntentReceiver instance() {
        if (instance == null)
            instance = new ContactListIntentReceiver();
        return instance;
    }

    private ContactListIntentReceiver() {
    }

    private final List<IntentReceiver> receivers = new LinkedList<>();

    /**
     * @deprecated
     */
    @Deprecated
    @DeprecatedApi(since = "4.1")
    public synchronized ContactListIntentReceiver addReceiver(
            IntentReceiver receiver) {
        Log.d(TAG, "Adding contact list intent receiver: " + receiver);
        receivers.add(receiver);
        return this;
    }

}
