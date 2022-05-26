
package com.atakmap.android.navigation.models;

import android.content.Intent;

import androidx.annotation.NonNull;

/**
 * An intent-based action that is tied to a {@link NavButtonModel}
 */
public class NavButtonIntentAction {

    private Intent intent;
    private boolean dismissMenu = true;

    public NavButtonIntentAction(@NonNull Intent intent) {
        this.intent = intent;
    }

    public NavButtonIntentAction(String action) {
        this(new Intent(action));
    }

    /**
     * Set the intent for this action
     * @param intent Intent
     */
    public void setIntent(@NonNull Intent intent) {
        this.intent = intent;
    }

    /**
     * Get the intent for this action
     * @return Intent
     */
    @NonNull
    public Intent getIntent() {
        return this.intent;
    }

    /**
     * Set whether this action should dismiss its associated menu, if applicable
     * @param dismissMenu True to dismiss associated menu
     */
    public void setDismissMenu(boolean dismissMenu) {
        this.dismissMenu = dismissMenu;
    }

    /**
     * Get whether this action should dismiss its associated menu
     * @return True to dismiss associated menu
     */
    public boolean shouldDismissMenu() {
        return this.dismissMenu;
    }
}
