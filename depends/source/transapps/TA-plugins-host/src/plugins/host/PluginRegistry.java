package plugins.host;

import java.util.List;

import plugins.core.model.Extension;
import plugins.core.model.Plugin;
import plugins.core.model.PluginDescriptor;
import plugins.host.context.PluginContext;
import plugins.host.register.PackagePluginRegistrar;
import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import android.util.Log;

/**
 * Base thing that manages plugins.
 * 
 * @author mriley
 */
public abstract class PluginRegistry {

    public static final String TAG = "PluginRegistry";
    
    /**
     * Will attempt to find a {@link PluggableContext} that has
     * a global reference, otherwise will create a new instance
     * 
     * @param context
     * @return
     */
    public static PluginRegistry getInstance( Context context ) {
        if( context instanceof PluggableContext ) {
            PluginRegistry pluginRegistry = ((PluggableContext) context).getPluginRegistry();
            if( pluginRegistry != null ) {
                return getContextLocalInstance(pluginRegistry, context);
            }
        }
        if( !(context instanceof Application) ) {
            if( context.getApplicationContext() != null ) {
                return getContextLocalInstance(getInstance(context.getApplicationContext()), context);
            }
            if( context instanceof ContextWrapper ) {
                Context baseContext = ((ContextWrapper) context).getBaseContext();
                if( baseContext != context ) {
                    return getContextLocalInstance(getInstance(baseContext), context);
                }
            }
        }
        Log.d(TAG, "Creating new plugin registry for context " + context.getPackageName() + ":" + context.getClass().getName());
        return new PluginRegistryImpl(context);
    }
    
    /**
     * If the supplied registry does not contain the supplied context, return a context local registry with this new context.
     * This new registry will refer to the old one but use the new context as the base for the creating extensions.  NOTE:
     * this will generally be done for you. 
     * 
     * @param context
     * @return
     */
    public static PluginRegistry getContextLocalInstance( PluginRegistry registry, Context context ) {
        if( registry.getContext() != context ) {
            Log.d(TAG, "Creating new context local registry for context " + context.getPackageName() + ":" + context.getClass().getName());
            return new PluginRegistryWrapper(registry, context);
        }
        return registry;
    }

    /**
     * This needs to be called during the onDestroy work flow, but should never be called by a plugin
     *
     * @since NW SDK 1.1.11.6
     */
    public void cleanUp( )
    {
        PluginCache cache = getCache( );
        cache.releaseReferences( );
    }
    
    /**
     * Get the cache of loaded plugins
     * 
     * @return
     */
    protected abstract PluginCache getCache();
    
    /**
     * Get the plugin that is hosting extensions
     * 
     * @return
     */
    protected abstract Plugin getHostPlugin(); 
    
    /**
     * Get the context associated with this registry
     * 
     * @return
     */
    protected abstract Context getContext();
    
    /**
     * Apply a filter to this registry.  You'll have to make sure this filter is applied before register and and load
     * calls are invoked or this will not be used (you can't filter after the fact).
     * 
     * @param filter
     */
    public abstract void setFilter(PluginFilter filter);
    
    /**
     * get the current {@link PluginFilter}
     * 
     * @return
     */
    public abstract PluginFilter getFilter();
    
    /**
     * Get the list of {@link Plugin}s currently registered.
     * 
     * @return
     */
    public abstract List<Plugin> getPlugins();
    
    /**
     * Get the list of filtered plugins {@link Plugin}s.
     *
     * @return
     *
     * @since NW SDK 1.1.8.6
     */
    public abstract List<Plugin> getFilteredPlugins();

    /**
     * Unregister the plugin and all extensions associated with the {@link PluginDescriptor}
     * 
     * @param descriptor
     */
    public abstract void unregisterPlugin( PluginDescriptor descriptor );

    /**
     * Register the {@link Plugin}.  See {@link PackagePluginRegistrar} for usage
     * 
     * @param plugin
     * @see PackagePluginRegistrar
     */
    public abstract void registerPlugin( Plugin plugin );
    
    /**
     * Register the {@link Plugin} and its {@link Extension}s.  See {@link PackagePluginRegistrar} for usage
     * 
     * @param plugin
     * @param extensions
     * @see PackagePluginRegistrar
     */
    public abstract void registerPlugin( Plugin plugin, List<Extension> extensions );

    /**
     * Register the {@link Plugin} and its {@link Extension}s and its dependencies.  See {@link PackagePluginRegistrar} for usage
     * 
     * @param plugin
     * @param extensions
     * @param dependencies
     */
    public abstract void registerPlugin( Plugin plugin, List<Extension> extensions, List<PluginDescriptor> dependencies );
    
    /**
     * Preload classes and plugins.  This is not required but may be useful to do in your {@link Application}.  This will speed up 
     * extension loading if this is invoked outside of your activities startup thread.
     */
    public abstract void preloadClasses();
    
    
    /**
     * Get the list of extensions that implement type.  The extensions will be given a {@link PluginContext} that wraps the context 
     * with which this plugin registry was created using {@link PluginRegistry#getInstance(Context)}.
     * 
     * @param type
     * @return
     */
    public abstract <T> List<T> getExtensions( Class<T> type );
    
    /**
     * Get the list of extensions that implement type.  The extensions will be given a {@link PluginContext} that wraps the context 
     * passed in.  Use this version if you want your activity to be the base for the {@link PluginContext}.  
     * 
     * @param type
     * @return
     */
    protected abstract <T> List<T> getExtensions( Class<T> type, PluginCache cache );


    /**
     *
     * DO NOT CHANGE THE VISIBILITY OF THE NEXT TWO METHODS
     *
     * Next two methods are intentionally made package private, especially the setter method. We do
     * want anyone with a reference to the PluginRegistry to be able to reset the plugin
     * base class loader since this is the base class loader used for ALL plugins.
     *
     */

    /**
     * This setter method is used by the {@link ApplicationPluggableContext} to set the base class
     * loader for the plugins. This base class loader comes from the RtuntimeClassLoader libraries.
     *
     *
     * @param loader Class loader that is created from the Runtime Class Loader Libraries
     *
     * @since NW SDK 1.1.8.6
     */
    abstract void setPluginBaseClassLoader(ClassLoader loader);

    /**
     * Returns the Plugin Class Loader.
     *
     * @return plugin base class loader.
     *
     * @since NW SDK 1.1.8.6
     */
    abstract ClassLoader getPluginBaseClassLoader();

}
