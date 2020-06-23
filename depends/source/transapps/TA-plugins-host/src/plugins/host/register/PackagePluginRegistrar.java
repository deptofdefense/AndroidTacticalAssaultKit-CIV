package plugins.host.register;

import plugins.core.model.PluginDescriptor;
import plugins.core.provider.PluginRegistrationProvider;
import plugins.host.PluginRegistry;
import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.util.Log;

/**
 * Responsible for peeking into a package and registering any extensions with the PluginRegistry
 * 
 * @author mriley
 */
public class PackagePluginRegistrar {

    private final Context hostContext;
    private final PluginRegistry registry;

    /**
     * First check to see if there is a provider to give us the plugin descriptor file.
     * @see PluginRegistrationProvider
     */
    private final RegistrationInfoSource firstSource;
    /**
     * Otherwise, fallback on the old method
     */
    private final RegistrationInfoSource secondSource;
    /**
     * Force the registration
     */
    private boolean forceRegister;

    public PackagePluginRegistrar( Context hostContext ) {
        this(hostContext, PluginRegistry.getInstance(hostContext));
    }

    public PackagePluginRegistrar( Context hostContext, PluginRegistry registry ) {
        this.hostContext = hostContext;
        this.registry = registry;
        this.firstSource = new ProviderXmlRegistrationInfoSource(hostContext);
        this.secondSource =  new XmlRegistrationInfoSource(hostContext);
    }

    /**
     * Forces the {@link PluginRegistry} to re-register discovered plugins even if
     * they are the same version
     * 
     * @param forceRegister
     */
    public void setForceRegister(boolean forceRegister) {
        this.forceRegister = forceRegister;
    }

    public PluginRegistry getRegistry() {
        return registry;
    }

    public Context getHostContext() {
        return hostContext;
    }

    /**
     * Removes all plugins that were registered for this package name from the registry. Usually, this will be
     * called by the InstallReceiver when an application is removed from the device.
     *
     * @param packageName package name of the plugin(s) to be removed
     */
    public void unregister( String packageName ) {
        PluginDescriptor descriptor = new PluginDescriptor();
        descriptor.setPluginId(packageName);
        registry.unregisterPlugin(descriptor);
    }

    /**
     * For a given package, it creates a pluginContext, then it looks into the package to see if there are any
     * plugins defined.  If there are plugins defined, it then registers each of these in the pluginRegistry.
     *
     * @param packageName name of the package to search for plugins.
     * @throws NameNotFoundException
     */
    public void register( String packageName ) throws NameNotFoundException {
        if( forceRegister ) {
            unregister(packageName);
        }

        Context pluginContext = null;
        if( packageName.equals(hostContext.getPackageName()) ) {
            pluginContext = hostContext;
        } else {
            pluginContext = hostContext.createPackageContext(packageName, 0);
        }

        RegistrationInfoSource source = null;
        try {
            firstSource.loadInfo(pluginContext);
            source = firstSource;
        } catch ( Exception e ) {
            try {
                secondSource.loadInfo(pluginContext);
                source = secondSource;
            } catch (Exception e1) {
                Log.e(PluginRegistry.TAG, "Failed to load registration info from " + packageName, e1);
            }
        }

        if( source != null && !source.extensions.isEmpty() ) {
            registry.registerPlugin(source.getPlugin(), source.getExtensions(), source.getDependencies());
        }
    }
}
