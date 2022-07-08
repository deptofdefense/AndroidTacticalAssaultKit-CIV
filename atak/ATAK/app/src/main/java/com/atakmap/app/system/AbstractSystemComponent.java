
package com.atakmap.app.system;

import android.content.Context;

import com.atakmap.annotations.DeprecatedApi;

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

    public interface Callback {
        int FAILED_STOP = 2;
        int FAILED_CONTINUE = 1;
        int SUCCESSFUL = 0;

        /**
         * Called when setup is complete.
         * @param condition SUCCESSFUL if setup of the encryption plugin was successful.
         */
        void setupComplete(int condition);
    }

    /**
     * Sets the plugin context for this component.
     * @param pluginContext the plugin context
     */
    public void setPluginContext(Context pluginContext) {
        this.pluginContext = pluginContext;
    }

    /**
     * Sets the application context for this component.
     * @param appContext the application context
     */
    public void setAppContext(Context appContext) {
        this.appContext = appContext;
    }

    /**
     * Allows for an implementation to be notified when the app enters the state of PAUSED, RESUMED
     * and when the app is being destroyed.
     * @param state the state
     */
    public abstract void notify(SystemState state);

    /**
     * Called as an async activity used during the setup of the component.
     * @param callback when the task is complete, the callback is made
     * @return true if the setupAsync has already completed and any further calls will not
     * perform utilize the callback.   If it returns false, it is guaranteed to call the callback in
     * all cases.
     */
    public abstract boolean load(Callback callback);
}
