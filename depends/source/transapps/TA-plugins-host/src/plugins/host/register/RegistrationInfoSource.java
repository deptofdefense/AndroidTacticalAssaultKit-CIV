package plugins.host.register;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import plugins.core.model.Extension;
import plugins.core.model.Plugin;
import plugins.core.model.PluginDescriptor;
import android.content.Context;
import android.content.res.Resources;

/**
 * Abstract class wrapper for the data in the plugin.xml file
 *
 * @author mriley
 *
 */
public abstract class RegistrationInfoSource {

    protected Context hostContext;
    protected Plugin plugin;
    protected String packageName;
    protected Context pluginContext;

    protected final List<Extension> extensions = new ArrayList<Extension>(1);
    protected final List<PluginDescriptor> deps = new ArrayList<PluginDescriptor>(1);

    public RegistrationInfoSource(Context hostContext) {
        this.hostContext = hostContext;
    }

    /**
     * Utility method to check if a plugin.xml file exists in this package.
     *
     * @return boolean true if contains plugins, false otherwise.
     */
    protected boolean packageContainsPlugins() {
        InputStream in = null;
        try {
            in = pluginContext.getAssets().open(PluginFileLocator.PLUGIN_FILE);
            return true;
        } catch ( Exception e ) {
            Resources resources = pluginContext.getResources();
            return resources.getIdentifier(PluginFileLocator.RAW_PLUGIN_FILE, "raw", pluginContext.getPackageName()) > 0;
        } finally {
            if( in != null ) try { in.close(); } catch ( Exception e ) {}
        }
    }

    /**
     * Loads the info about the plugin, its extensions and any dependencies.
     *
     * @param pluginContext context to use in order to search for the extensions and dependencies
     * @throws Exception
     */
    public final void loadInfo( Context pluginContext ) throws Exception {
        this.pluginContext = pluginContext;
        this.plugin = new Plugin(pluginContext);
        this.packageName = pluginContext.getPackageName();

        extensions.clear();
        deps.clear();

        if( packageContainsPlugins() ) {
            doLoadInfo();
        }
    }

    /**
     * Returns the Plugin loaded from the plugin.xml
     *
     * @return Plugin loaded plugin
     */
    public Plugin getPlugin() {
        return plugin;
    }

    /**
     * Dependencies that this plugin requires in order for it to be loaded correctly.
     *
     * @return List of PluginDescriptors that are the dependencies
     */
    public List<PluginDescriptor> getDependencies() {
        return deps;
    }

    /**
     * The extensions defined in the plugin.xml file
     *
     * @return List of extensions defined in the plugin
     */
    public List<Extension> getExtensions() {
        return extensions;
    }

    /**
     * Abstract method to be overloaded by the classes to load from its source.
     *
     * @throws Exception
     */
    protected abstract void doLoadInfo() throws Exception;
}
