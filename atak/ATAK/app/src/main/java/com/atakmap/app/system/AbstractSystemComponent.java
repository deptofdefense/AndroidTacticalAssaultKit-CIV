
package com.atakmap.app.system;

import android.content.Context;

/**
 * Provides the abstract class for the System Components that modify behavior for the core capability.
 */
public abstract class AbstractSystemComponent {

    protected Context pluginContext;
    protected Context appContext;

    /**
     * Notification for application state changes that may be relevent to the system component.
     */
    public enum SystemState {
        PAUSE,
        RESUME,
        DESTROY
    }

    /**
     * Sets the plugin context for this component.
     * @param pluginContext the plugin context
     */
    public void setPluginContext(Context pluginContext) {
        this.pluginContext = pluginContext;
    }

    public void setAppContext(Context appContext) {
        this.appContext = appContext;
    }

    /**
     * Entry point to load the capabilities for the encryption.
     */
    public abstract void load();

    /**
     * Allows for an implementation to be notified when the app enters the state of PAUSED, RESUMED
     * and when the app is being destroyed.
     * @param state the state
     */
    public abstract void notify(SystemState state);

}
