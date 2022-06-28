
package com.atakmap.android.cot;

import java.util.Map;
import java.util.HashMap;
import com.atakmap.coremap.cot.event.CotEvent;
import android.os.Bundle;

/**
 * Replace a current tight coupling within ATAK that allows for specialized behavior to 
 * occur when a MapItem is received and requires a user action before it can continue.
 *    The existing capability locks the current processing pipe until the user 
 *    is able to respond.    In the next version of this, the processing will not be 
 *    blocked.
 */
public class CotModificationManager {

    private static final Map<String, ModificationAction> table = new HashMap<>();

    static abstract public class ModificationAction {
        /**
         * Prompts the user with the appropriate user screens.
         * @return true if user interaction is needed.
         */
        public abstract boolean checkAndPrompt(CotEvent event, Bundle bundle);

        /** 
         * Temporarily expose the mechanism for simulating previous behavior.
         */
        protected void approved(CotEvent event, Bundle extra) {
            extra.putBoolean("overwrite.value", true);
            CotMapComponent.getInstance().processCotEvent(event, extra);
        }
    }

    /**
     * Allows the client to register an action for when a specific item with the
     * uid specified is received.
     * @param uid the uid of the map item.
     * @param callback the callback to see.
     */
    static synchronized public void register(final String uid,
            final ModificationAction callback) {
        table.put(uid, callback);
    }

    /**
     * Allows the client to unregister an action for when a specific item with the
     * uid specified is received.
     * @param uid the uid of the map item.
     */
    static synchronized public void unregister(final String uid) {
        table.remove(uid);
    }

    static synchronized public boolean process(CotEvent event, Bundle bundle) {

        // do not allow for internal processing to prompt
        final String fromExtra = bundle.getString("from");
        if (fromExtra != null && !fromExtra.contentEquals("internal")) {
            ModificationAction cb = table.get(event.getUID());
            if (cb != null && !bundle.getBoolean("overwrite.value", false))
                return cb.checkAndPrompt(event, bundle);
        }
        return false;
    }

}
