package plugins.host;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import plugins.core.Activator;
import plugins.core.model.ClassLoaderInfo;
import plugins.core.model.DependencyGraph;
import plugins.core.model.Plugin;
import plugins.core.model.PluginDescriptor;
import plugins.host.context.PluginContext;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

/**
 * A hierachical cache for plugin data local to the {@link Context}
 * This allows us to have differing {@link PluginContext} depending
 * on the host {@link Context}.  This also allows parent caches to
 * do some heavy lifting of finding plugins and dependencies on init.
 * When this finds a plugin in its parent it can simply create a
 * {@link PluginContext} instance based on that rather than walking
 * the dependency hierarchy.
 * 
 * TODO: consider moving singletons here as well...should singletons
 * be context local?
 * 
 * @author mriley
 */
public class PluginCache {

    private static final String TAG = PluginRegistry.TAG;
    
    private final Map<Class<?>, Object> singletons;
    private final Map<String,PluginContext> loadedPlugins;
    private final PluginRegistry pluginRegistry;
    private final Context context;

    private final Uri dependencyGraphUri;
    private final Uri pluginUri;
    
    private final PluginCache parentCache;

    /**
     * Constructor
     * @param registry registry to cache in memory
     */
    public PluginCache( PluginRegistry registry ) {
        this(registry, null);
    }

    /**
     *
     * @param registry
     * @param parent
     */
    public PluginCache( PluginRegistry registry, PluginCache parent ) {
        parentCache = parent;
        pluginRegistry = registry;
        context = registry.getContext();
        singletons = new ConcurrentHashMap<Class<?>, Object>();
        loadedPlugins = new ConcurrentHashMap<String, PluginContext>();
        dependencyGraphUri = DependencyGraph.Fields.getContentUri(registry.getContext());
        pluginUri = Plugin.Fields.getContentUri(registry.getContext());
    }
    
    public PluginRegistry getPluginRegistry() {
        return pluginRegistry;
    }
    
    @SuppressWarnings("unchecked")
    public <T> T getSingleton( Class<T> implClass ) {
        return (T) singletons.get(implClass);
    }
    
    public void addSingleton( Class<?> implClass, Object extension ) {
        // NOTE: this implclass param might be unnecessary.  We could use extension.getClass()
        // as long as we don't proxy extension.
        singletons.put(implClass, extension);
    }
    
    public synchronized boolean containsPlugin( String pluginId ) {
        return loadedPlugins.containsKey(pluginId);
    }
    
    public synchronized void addPlugin( String pluginId, PluginContext context ) {
        loadedPlugins.put(pluginId, context);
    }

    public synchronized PluginContext getPluginContext( String pluginId ) {
        PluginContext pluginContext = null;
        if( parentCache != null ) {
            pluginContext = parentCache.getPluginContext(pluginId);
            
            if( pluginContext != null && pluginContext.getHostContext() != context ) {
                pluginContext = new PluginContext(pluginContext, context);
                loadedPlugins.put(pluginId, pluginContext);
            }
        }
        
        if( pluginContext == null ) {
            pluginContext = loadedPlugins.get(pluginId);
            if( pluginContext == null ) {
                pluginContext = loadAndActivatePlugin(pluginId);
                if( pluginContext != null ) {
                    loadedPlugins.put(pluginId, pluginContext);
                }
            }
        }
        
        return pluginContext;
    }

    private boolean loadAndActivateDependencies( String pluginId ) {

        ContentResolver contentResolver = context.getContentResolver();
        Cursor cursor = contentResolver.query(dependencyGraphUri, 
                new String[] {DependencyGraph.Fields.DEPENDEE_ID}, 
                DependencyGraph.Fields.DEPENDENT_ID + "=?", 
                new String[] {pluginId}, null);

        try {
            while( cursor.moveToNext() ) {
                String dependeeId = cursor.getString(0);
                if( getPluginContext(dependeeId) == null ) {
                    return false;
                }
            }
        } finally {
            cursor.close();
        }

        return true;
    }

    private PluginContext loadAndActivatePlugin( String pluginId ) {

        PluginContext pluginContext = null;

        if( loadAndActivateDependencies(pluginId) ) {
            ContentResolver contentResolver = context.getContentResolver();
            Cursor cursor = contentResolver.query(pluginUri, new String[] {"*"}, 
                    Plugin.Fields.PLUGIN_ID + "=?", 
                    new String[] {String.valueOf(pluginId)}, null);

            String impl = null;
            try {
                int idIdx = cursor.getColumnIndex(Plugin.Fields.ID);
                int pluginIdIdx = cursor.getColumnIndex(Plugin.Fields.PLUGIN_ID);
                int versionIdx = cursor.getColumnIndex(Plugin.Fields.VERSION);
                int implIdx = cursor.getColumnIndex(Plugin.Fields.ACTIVATOR);
                int dexPathIdx = cursor.getColumnIndex(ClassLoaderInfo.Fields.DEX_PATH);
                int libPathIdx = cursor.getColumnIndex(ClassLoaderInfo.Fields.LIB_PATH);

                if( cursor.moveToNext() ) {
                    impl = cursor.getString(implIdx);

                    Plugin plugin = new Plugin();
                    plugin.setId(cursor.getLong(idIdx));
                    plugin.setActivator(cursor.getString(implIdx));
                    plugin.setDescriptor(new PluginDescriptor(cursor.getString(pluginIdIdx), cursor.getString(versionIdx)));
                    plugin.setClassLoaderInfo(new ClassLoaderInfo(cursor.getString(dexPathIdx), "", cursor.getString(libPathIdx)));                    
                    
                    if( !pluginRegistry.getFilter().filterLoad(plugin) ) {
                        pluginContext = new PluginContext(context, pluginRegistry.getHostPlugin(), plugin, getPluginRegistry().getPluginBaseClassLoader());

                        if( impl != null ) {
                            Class<?> activatorClass = Class.forName(impl, true, pluginContext.getClassLoader());
                            Activator activator = (Activator) activatorClass.newInstance();
                            activator.activate(pluginContext);
                        }
                    }
                }
            } catch ( Exception e ) {
                Log.w(TAG, "Failed to load activator " + impl, e);
            } finally {
                cursor.close();
            }

            if( pluginContext == null ) {
                Log.w(TAG, "Failed to load plugin classloader for plugin " + pluginId);
            }

        } else {
            Log.e(TAG, "Not loading " + pluginId + " as its dependencies failed to load");
        }

        return pluginContext;
    }

    synchronized void releaseReferences( )
    {
        singletons.clear( );
        loadedPlugins.clear( );
    }
}
