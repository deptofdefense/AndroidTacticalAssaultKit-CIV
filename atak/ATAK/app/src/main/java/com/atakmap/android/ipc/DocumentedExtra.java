
package com.atakmap.android.ipc;

/**
 * Instead of using IntentFilters, ATAK makes use of DocumentedIntentFilter to provide for
 * automatic extraction of documentation supplied by the developer.   As part of the
 * DocumentedIntent filter, the is DocumentedExtra that can be supplied in order to produce
 * more robust documentation as well as in the future allow for validation of intents being
 * passed into broadcast.
 */
public class DocumentedExtra {
    public final String name;
    public final String description;
    public final boolean optional;
    public final Class<?> type;

    /**
     * A documented extra that is associated with the action that is required to exist.
     * @param name the name of the extra
     * @param description the description of the extra
     */
    public DocumentedExtra(final String name, final String description) {
        this(name, description, false, Object.class);
    }

    /**
    
    /**
     * A documented extra that is associated with the action.
     * @param name the name of the extra
     * @param description the description of the extra
     * @param optional if the extra is optional when the action is performed.
     */
    public DocumentedExtra(final String name, final String description,
            final boolean optional) {
        this(name, description, optional, Object.class);
    }

    /**
     * A documented extra that is associated with the action.
     * @param name the name of the extra
     * @param description the description of the extra
     * @param optional if the extra is optional when the action is performed.
     * @param type the class that needs to be placed into the extra.
     */
    public DocumentedExtra(final String name, final String description,
            final boolean optional, final Class<?> type) {
        this.name = name;
        this.description = description;
        this.optional = optional;
        this.type = type;
    }

}
