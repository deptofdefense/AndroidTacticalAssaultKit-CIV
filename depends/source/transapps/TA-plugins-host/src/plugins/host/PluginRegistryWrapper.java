package plugins.host;

import java.util.List;

import plugins.core.model.Extension;
import plugins.core.model.Plugin;
import plugins.core.model.PluginDescriptor;
import android.app.Application;
import android.content.Context;

/**
 * Allows plugins to be managed in a root ({@link Application}) context while overriding
 * the context with which extensions are created.
 * 
 * @author mriley
 */
class PluginRegistryWrapper extends PluginRegistry {

    private final PluginRegistry registry;
    private final Context contextOverride;
    private final PluginCache cacheOverride;
    private ClassLoader pluginBaseClassLoader;

    public PluginRegistryWrapper(PluginRegistry registry) {
        this(registry, null);
    }
    
    public PluginRegistryWrapper(PluginRegistry registry, Context contextOverride) {
        this.contextOverride = contextOverride;
        this.registry = 
                (registry instanceof PluginRegistryWrapper) ? 
                        ((PluginRegistryWrapper)registry).getBaseRegistry() : registry;
        this.cacheOverride = 
                contextOverride == null ? null : new PluginCache(this, registry.getCache());

        //default to the class loader for this class
        pluginBaseClassLoader = this.getClass().getClassLoader();
    }
    
    public PluginRegistry getBaseRegistry() {
        return registry;
    }
    
    
    @Override
    protected PluginCache getCache() {
        return cacheOverride == null ? registry.getCache() : cacheOverride;
    }
    
    @Override
    protected Context getContext() {
        return contextOverride == null ? registry.getContext() : contextOverride;
    }
    
    @Override
    protected Plugin getHostPlugin() {
        return registry.getHostPlugin();
    }
    
    
    
    @Override
    public PluginFilter getFilter() {
        return registry.getFilter();
    }

    public void setFilter(PluginFilter filter) {
        registry.setFilter(filter);
    }

    
    public List<Plugin> getPlugins() {
        return registry.getPlugins();
    }

    @Override
    public List<Plugin> getFilteredPlugins() {
        return registry.getFilteredPlugins();
    }

    public void unregisterPlugin(PluginDescriptor descriptor) {
        registry.unregisterPlugin(descriptor);
    }

    public void registerPlugin(Plugin plugin) {
        registry.registerPlugin(plugin);
    }

    public void registerPlugin(Plugin plugin, List<Extension> extensions) {
        registry.registerPlugin(plugin, extensions);
    }

    public void registerPlugin(Plugin plugin, List<Extension> extensions,
            List<PluginDescriptor> dependencies) {
        registry.registerPlugin(plugin, extensions, dependencies);
    }

    public void preloadClasses() {
        registry.preloadClasses();
    }

    public <T> List<T> getExtensions(Class<T> type) {
        return getExtensions(type, getCache());
    }

    @Override
    protected <T> List<T> getExtensions(Class<T> type, PluginCache cache) {
        return registry.getExtensions(type, cache);
    }

    @Override
    void setPluginBaseClassLoader(ClassLoader loader) {
        this.pluginBaseClassLoader = loader;
    }

    @Override
    ClassLoader getPluginBaseClassLoader() {
        return pluginBaseClassLoader;
    }
}
