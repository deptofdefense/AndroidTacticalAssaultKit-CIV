
package com.atakmap.android.ipc;

/**
 * Instead of using IntentFilters, ATAK makes use of DocumentedIntentFilter to provide for
 * automatic extraction of documentation supplied by the developer.   As part of the
 * DocumentedIntent filter, the is DocumentedAction that can be supplied in order to produce
 * more robust documentation as well as in the future allow for validation of intents being
 * passed into broadcast.
 */
public class DocumentedAction {
    public final String action;
    public final String description;
    public final DocumentedExtra[] extras;

    /**
     * Basic action without any defined extras.
     * @param action the action.
     * @param description the description of the action.
     */
    public DocumentedAction(final String action, final String description) {
        this(action, description, new DocumentedExtra[0]);
    }

    /**
     * Action with defined extras (either optional or required).
     * @param action the action.
     * @param description the description of the action.
     * @param extras the list of extras,
     */
    public DocumentedAction(final String action, final String description,
            final DocumentedExtra[] extras) {
        this.action = action;
        this.description = description;
        if (extras != null)
            this.extras = extras;
        else
            this.extras = new DocumentedExtra[0];
    }
}
