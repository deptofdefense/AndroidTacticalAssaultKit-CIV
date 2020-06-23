package plugins.host.context;


import plugins.core.model.Plugin;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.content.res.Resources.Theme;

/**
 * Special context for plugins.  Allows locating resources and loading classes from a
 * plugin while retaining the context of the host for getting access to services and
 * such.  There will be one of these for each loaded plugin.  This class wraps all the
 * normal Context methods to provide a plugin with the answers it would expect if it
 * was running in its own process as well as merge the resource look up to find resources
 * from both the hosting application and the plugin application.
 * 
 * @author mriley
 */
public class PluginContext extends ContextWrapper {
    
    static final String TAG = PluginContext.class.getName();

    private final Plugin plugin;
    
    private final Context hostContext;
    private final Context pluginContext;
    
    private final ClassLoader classLoader;    
    private final PluginResources resources;
    private final PluginLayoutInflator layoutInflator;
    private Theme setTheme = null;

    /**
     * Constructor
     *
     * @param host contexgt for the host application
     * @param hostPlugin host Plugin needed to create the merged ClassLoader for this Context
     * @param plugin plugin this Context is created for
     * @throws NameNotFoundException
     */
    public PluginContext(Context host, Plugin hostPlugin, Plugin plugin) throws NameNotFoundException {
        this( host, plugin, new PluginClassLoader(plugin.getClassLoaderInfo().getDexPath(),
                plugin.getClassLoaderInfo().getLibPath(), host.getClassLoader()));
    }

    /**
     * Constructor
     *
     * @param host
     * @param hostPlugin
     * @param plugin
     * @param parentClassLoader
     * @throws NameNotFoundException
     *
     * @since NW SDK 1.1.8.6
     */
    public PluginContext(Context host, Plugin hostPlugin, Plugin plugin, ClassLoader parentClassLoader) throws NameNotFoundException {
        this( host, plugin, new PluginClassLoader(plugin.getClassLoaderInfo().getDexPath(),
                                                  plugin.getClassLoaderInfo().getLibPath(), parentClassLoader));
    }

    /**
     * Constructor
     *
     * @param host host application context
     * @param p plugin this context is created for
     * @param loader class loader to use
     * @throws NameNotFoundException
     */
    public PluginContext(Context host, Plugin p, ClassLoader loader) throws NameNotFoundException {
        super( host );
        plugin = p;
        hostContext = host;
        classLoader = loader;
        pluginContext = host.createPackageContext(p.getDescriptor().getPluginId(), Context.CONTEXT_IGNORE_SECURITY);
        resources = new PluginResources(this, getAssets(), host.getResources().getDisplayMetrics(), host.getResources().getConfiguration());
        layoutInflator = new PluginLayoutInflator(this);        
    }

    /**
     * Constructor for moving from one context to another, specifically when screen changes occur
     *
     * @param context
     * @param newHost
     */
    public PluginContext(PluginContext context, Context newHost) {
        super( newHost );
        plugin = context.plugin;
        hostContext = newHost;
        classLoader = context.classLoader;
        pluginContext = context.pluginContext;
        resources = new PluginResources(this, getAssets(), newHost.getResources().getDisplayMetrics(), newHost.getResources().getConfiguration());
        layoutInflator = new PluginLayoutInflator(this);
    }

    /**
     * Returns the context specific to the application that is hosting and running the plugins.
     *
     * @return  Context specific to the hosting pluggable application
     */
    public Context getHostContext() {
        return hostContext;
    }


    /**
     * Returns the context specific to the application the plugin is loaded out of.
     *
     * @return Context specific to the application that the plugin was loaded from
     */
    public Context getPluginContext() {
        return pluginContext;
    }


    /**
     * Overrides the getAssets method in order to look in the application the plugin is loaded from.
     *
     * @return AssetManager
     */
    @Override
    public AssetManager getAssets() {
        return pluginContext.getAssets();
    }

    /**
     * Overrides the getResources method in order to look in both the hosting application's context as well
     * as the context from the application the plugin is loaded from.  In this way the plugin can reference
     * both the resources found in the plugin host as well as resources that only exist in its own application.
     *
     * @return the Resources object that provides access to the hosting application and the plugin application
     *         resources
     */
    @Override
    public Resources getResources() {
        return resources;
    }


    /**
     * Overrides the getPackageName method in order to return the package name of the plugin application rather
     * then the package name of the hosting application.
     *
     * @return the Package name of the plugin application
     *
     */
    @Override
    public String getPackageName() {
        return pluginContext.getPackageName();
    }

    /**
     * Overrides the getApplicationInfo method in order to return the applicationInfo of the plugin application rather
     * then that of the hosting application.
     *
     * @return the Package name of the plugin application
     *
     */
    @Override
    public ApplicationInfo getApplicationInfo() {
        return pluginContext.getApplicationInfo();
    }

    /**
     * Overrides the getPackageResourcePath method in order to return the resource path of the plugin application rather
     * then that of the hosting application.
     *
     * @return the Package Resource Path of the plugin application
     *
     */
    @Override
    public String getPackageResourcePath() {
        return pluginContext.getPackageResourcePath();
    }

    /**
     * Overrides the getPackageCodePath method in order to return the package path  path of the plugin application rather
     * then that of the hosting application.
     *
     * @return the Package Resource Path of the plugin application
     *
     */
    @Override
    public String getPackageCodePath() {
        return pluginContext.getPackageCodePath();
    }

    /**
     * Overrides the getSystemService method in order to return the normal system services in all cases except for the
     * layout inflater.  In the case of the Layout inflater it must return the plugin Inflater which know to look in
     * both the hosting application and the plugin application resources in order to inflate the layouts and views.
     *
     * @return the correct System Service based on the correct context or set of contexts
     *
     */
    @Override
    public Object getSystemService(String name) {
        if( name.equals(LAYOUT_INFLATER_SERVICE) ) {
            return layoutInflator;
        }
        return super.getSystemService(name);
    }

    /**
     * Overrides the getClassLoader method in order to merged class loader that will search both the hosting application
     * and the plugin application to load classes as necessary.
     *
     * @return ClassLoader that will have access to both the plugin and the host application's objects
     *
     */
    @Override
    public ClassLoader getClassLoader() {
        return classLoader;
    }


    /**
     * Overrides the getTheme method in order return the Theme from the plugin application rather then the Theme from
     * the host application.  This way any special colors or theme elements expected by the plugin will be accessible.
     *
     * @return ClassLoader that will have access to both the plugin and the host application's objects
     *
     */
    @Override
    public Theme getTheme() {
        return pluginContext.getTheme();
    }

    /**
     * Accessor for the actual plugin this Context supports.
     *
     * @return ClassLoader that will have access to both the plugin and the host application's objects
     *
     */
    public Plugin getPlugin() {
        return plugin;
    }
}
