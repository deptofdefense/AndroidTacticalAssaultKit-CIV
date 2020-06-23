
package com.atakmap.android.hierarchy.action;

public interface AsynchronousAction extends Action {
    /**
     * Returns a token that uniquely identifies the associated item for the service provider. This
     * token must be valid across invocations of the software.
     * 
     * @return
     */
    String getToken();

    /**
     * The identifier for the service provider that can perform the desired action at some future
     * time.
     * 
     * @return
     */
    String getServiceProvider();

    /**
     * Returns a command to be issued to the service provider to instantiate the appropriate action
     * sometime in the future.
     * 
     * @param clazz
     * @return
     */
    String getCommand(Class<? extends Action> clazz);
}
