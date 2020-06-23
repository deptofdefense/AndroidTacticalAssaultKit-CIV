
package com.atakmap.android.hierarchy.action;

import com.atakmap.android.importexport.send.SendDialog;

/**
 * Action interface for prompting the user to send an item
 * This action is not meant to be used in bulk operations
 */
public interface Send {

    /**
     * Prepare or prompt to send this item
     * Usually this would bring up a {@link SendDialog}
     *
     * @return True if the item can be sent, false if not
     */
    boolean promptSend();
}
