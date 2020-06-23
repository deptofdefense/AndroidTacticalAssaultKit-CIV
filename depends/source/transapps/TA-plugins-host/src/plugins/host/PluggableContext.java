package plugins.host;

import android.app.Application;

/**
 * A thing that acts as a container for a plugin registry.  This will normally
 * be an {@link Application} or another thing that has a reference to a global
 * {@link PluginRegistry} reference.  This isn't explicitly required but it helps
 * with the {@link PluginListActivity}.
 * 
 * @author mriley
 */
public interface PluggableContext {

    /**
     * @return The global reference to the plugin registry for this app
     */
    PluginRegistry getPluginRegistry();
}
