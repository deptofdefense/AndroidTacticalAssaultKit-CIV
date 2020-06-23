package plugins.host;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import plugins.core.Provider;
import plugins.core.model.ClassLoaderInfo;
import plugins.core.model.DependencyGraph;
import plugins.core.model.Extension;
import plugins.core.model.Plugin;
import plugins.core.model.PluginDescriptor;
import plugins.host.PluginFilter.PassFilter;
import plugins.host.context.PluginContext;
import plugins.host.register.PackagePluginRegistrar;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

/**
 *
 *
 * Implementation of the PluginRegistry interface
 *
 * TODO: move most of the db code somewhere else
 * TODO: hierarchical plugins may not work
 * 
 * @author mriley
 *
 */
class PluginRegistryImpl extends PluginRegistry {


    private final Context context;
    private final Plugin hostPlugin;
    private final PluginCache cache;
    private PluginFilter filter;

    private final Uri extensionUri;
    private final Uri dependencyGraphUri;
    private final Uri pluginUri;
    
    private ClassLoader pluginBaseClassLoader;
    
    /**
     * You sure you don't want {@link #getInstance(Context)}?
     * @param context
     */
    PluginRegistryImpl(Context context) {
        this( context, PluginFilter.PASS );
    }

    /**
     * You sure you don't want {@link #getInstance(Context)}?
     * @param context
     * @param filter
     */
    PluginRegistryImpl(Context context, PluginFilter filter) {
        this.context = context;
        this.filter = filter;
        this.hostPlugin = new Plugin(context);
        this.cache = new PluginCache(this);        

        extensionUri = Extension.Fields.getContentUri(context);
        dependencyGraphUri = DependencyGraph.Fields.getContentUri(context);
        pluginUri = Plugin.Fields.getContentUri(context);

        pluginBaseClassLoader = this.getClass().getClassLoader();
    }

    @Override
    protected PluginCache getCache() {
        return cache;
    }

    @Override
    protected Context getContext() {
        return context;
    }

    @Override
    protected Plugin getHostPlugin() {
        return hostPlugin;
    }

    @Override
    public PluginFilter getFilter() {
        return filter;
    }

    @Override
    public void setFilter(PluginFilter filter) {
        this.filter = filter == null ? PassFilter.PASS : filter;
    }

    @Override
    public void unregisterPlugin( PluginDescriptor descriptor ) {
        Log.i(TAG, "Unregistering " + descriptor);
        long time = System.currentTimeMillis();

        String pluginId = descriptor.getPluginId();
        ContentResolver contentResolver = context.getContentResolver();
        ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();        

        unregisterPlugin(pluginId, ops);

        try {
            contentResolver.applyBatch(Provider.getAuthority(context), ops);
            Log.i(TAG, "Unregistered " + descriptor + " in " + (System.currentTimeMillis() - time));
        } catch (Exception e) {
            Log.e(TAG, "Failed to unregister " + descriptor, e);
        }
    }

    private void unregisterPlugin(String pluginId, ArrayList<ContentProviderOperation> ops) {
        ops.add(ContentProviderOperation.newDelete(extensionUri).withSelection(Plugin.Fields.PLUGIN_ID + "=?", new String[] {pluginId}).build());
        ops.add(ContentProviderOperation.newDelete(dependencyGraphUri).withSelection(DependencyGraph.Fields.DEPENDENT_ID + "=?", new String[] {pluginId}).build());
        ops.add(ContentProviderOperation.newDelete(pluginUri).withSelection(Plugin.Fields.PLUGIN_ID + "=?", new String[] {pluginId}).build());
    }

    @Override
    public void registerPlugin( Plugin plugin ) {
        // if this is a host plugin, do this
        if( !registerHostPlugin(plugin) ) {

            // otherwise, do this
            registerPlugin(plugin, null, null);
        }
    }

    @Override
    public void registerPlugin( Plugin plugin, List<Extension> extensions ) {
        registerPlugin(plugin, extensions, null);
    }

    /**
     * Override implementation to register the plugins.  Note that this implementation will only register and load
     * a new plugin for an already loaded plugin IF the version number in the AndroidManifest is increased.
     *
     * @param plugin
     * @param extensions
     * @param dependencies
     */
    @Override
    public void registerPlugin( Plugin plugin, List<Extension> extensions, List<PluginDescriptor> dependencies ) {

        if( !filter.filterRegister(plugin) ) {            
            Log.i(TAG, "Registering " + plugin.getDescriptor());

            boolean register = !filter.filterRegister(plugin);
            ContentResolver contentResolver = context.getContentResolver();
            ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();

            Cursor query = contentResolver.query(pluginUri, 
                    new String[] {Plugin.Fields.ID, Plugin.Fields.PLUGIN_ID, Plugin.Fields.VERSION}, 
                    Plugin.Fields.PLUGIN_ID + "=?", 
                    new String[] {plugin.getDescriptor().getPluginId()}, null);

            if( query != null ) {
                try {
                    if( query.moveToFirst() ) {
                        String currentVersion = query.getString(2);                

                        if( plugin.getDescriptor().getVersion().equals(currentVersion) ) {
                            Log.i(TAG, "Plugin " + plugin.getDescriptor() + " already registered!");
                            register = false;
                        } else {
                            // TODO: update deps?
                            Log.w(TAG, "Plugin " + plugin.getDescriptor() + " already registered with version "+currentVersion
                                    +".  Any dependents may not work properly");
                        }
                        unregisterPlugin(plugin.getDescriptor().getPluginId(), ops);
                    }
                } finally {
                    query.close();
                }
            }

            if( register ) {
                ContentValues values = new ContentValues();
                values.put(Plugin.Fields.NAME, plugin.getName());
                values.put(Plugin.Fields.PLUGIN_ID, plugin.getDescriptor().getPluginId());
                values.put(Plugin.Fields.VERSION, plugin.getDescriptor().getVersion());
                values.put(Plugin.Fields.ACTIVATOR, plugin.getActivator());
                values.put(ClassLoaderInfo.Fields.DEX_PATH, plugin.getClassLoaderInfo().getDexPath());
                values.put(ClassLoaderInfo.Fields.DEX_OUTPUT_DIR, plugin.getClassLoaderInfo().getDexOutputDir());
                values.put(ClassLoaderInfo.Fields.LIB_PATH, plugin.getClassLoaderInfo().getLibPath());
                ops.add(ContentProviderOperation.newInsert(pluginUri).withValues(values).build());

                registerExtensions(plugin, extensions, ops);
                registerDependencies(plugin, dependencies, contentResolver, ops);

                try {
                    contentResolver.applyBatch(Provider.getAuthority(context), ops);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to register " + plugin.getDescriptor(), e);
                }
            }

        } else {
            Log.i(TAG, plugin.getDescriptor() + " skipped based on filter");    
        }
    }

    private boolean registerHostPlugin(Plugin plugin) {
        // if this is the host plugin then go ahead and pre-load it and make sure any extensions it
        // exposes get registered
        String pluginId = plugin.getDescriptor().getPluginId();
        if( !cache.containsPlugin(pluginId) && pluginId.equals(context.getPackageName()) ) {            
            try {
                PluginContext pluginContext = new PluginContext(context, plugin, context.getClassLoader());
                cache.addPlugin(pluginId, pluginContext);
            } catch (NameNotFoundException ignoredCauseThisCantHappen) {
            }

            // also, load any plugins defined in the host
            PackagePluginRegistrar registrar = new PackagePluginRegistrar(context, this);
            try {
                registrar.register(context.getPackageName());
            } catch (NameNotFoundException ignoredCauseThisCantHappen) {
            }

            return true;
        }
        return false;
    }

    private void registerDependencies(Plugin plugin, List<PluginDescriptor> dependencies, ContentResolver contentResolver, ArrayList<ContentProviderOperation> ops) {
        if( dependencies != null ) {
            for( int i = 0; i < dependencies.size(); i++ ) {
                PluginDescriptor dependency = dependencies.get(i);
                String dependencyPluginId = dependency.getPluginId();
                String dependencyVersion = dependency.getVersion();

                Cursor query = contentResolver.query(pluginUri, 
                        new String[] {Plugin.Fields.ID}, 
                        Plugin.Fields.PLUGIN_ID + "=? and " + Plugin.Fields.VERSION + "=?", 
                        new String[] {dependencyPluginId, dependencyVersion}, null);

                if( query != null ) {
                    try {
                        if( !query.moveToFirst() ) {
                            Log.w(TAG, "Dependendee " + dependency + " not found!  Plugin " + 
                                    plugin.getDescriptor() + " may not load properly!");
                        }

                        ContentValues values = new ContentValues();
                        values.put(DependencyGraph.Fields.DEPENDEE_ID, dependencyPluginId);
                        values.put(DependencyGraph.Fields.DEPENDENT_ID, plugin.getDescriptor().getPluginId());
                        ops.add(ContentProviderOperation.newInsert(dependencyGraphUri).withValues(values).build());

                        Log.i(TAG, plugin.getDescriptor() + " -> Registered dependency " + dependency);
                    } finally {
                        query.close();
                    }
                }
            }
        }
    }

    private void registerExtensions(Plugin plugin, List<Extension> extensions, ArrayList<ContentProviderOperation> ops) {
        if( extensions != null ) {
            ContentValues values = new ContentValues();
            for( int i = 0; i < extensions.size(); i++ ) {
                values.clear();
                Extension extension = extensions.get(i);
                if( !filter.filterRegister(extension) ) {
                    values.put(Plugin.Fields.PLUGIN_ID, plugin.getDescriptor().getPluginId());
                    values.put(Extension.Fields.TYPE, extension.getType());
                    values.put(Extension.Fields.IMPL, extension.getImpl());
                    values.put(Extension.Fields.SINGLETON, extension.isSingleton());
                    ops.add(ContentProviderOperation.newInsert(extensionUri).withValues(values).build());
                    Log.i(TAG, plugin.getDescriptor() + " -> Registered extension " + extension);
                } else {
                    Log.i(TAG, plugin.getDescriptor() + " -> Filtered extension " + extension);
                }
            }
        }
    }

    /**
     * For faster startup times you may want to invoke this to preload some classes
     */
    @Override
    public void preloadClasses() {
        Set<String> seen = new HashSet<String>();
        ContentResolver contentResolver = context.getContentResolver();

        Cursor cursor = contentResolver.query(extensionUri, 
                new String[] {Extension.Fields.IMPL,Plugin.Fields.PLUGIN_ID}, null, null, null);


        if( cursor != null ) {
            try {
                int implIdx = cursor.getColumnIndex(Extension.Fields.IMPL);
                int pluginIdIdx = cursor.getColumnIndex(Plugin.Fields.PLUGIN_ID);


                boolean moveToNext = cursor.moveToNext();
                while( moveToNext ) {

                    String pluginId = cursor.getString(pluginIdIdx);
                    String impl = cursor.getString(implIdx);

                    boolean notalreadyseen = !seen.contains(pluginId);

                    if( notalreadyseen ) {

                        PluginContext pluginContext = cache.getPluginContext(pluginId);

                        if( pluginContext != null ) {

                            try {
                                pluginContext.getClassLoader().loadClass(impl);
                                seen.add(pluginId);
                            }
                            catch (Exception e) {
                                Log.e(TAG, "Failed to load extension " + impl, e);
                            }
                            catch (LinkageError e) {
                                Log.e(TAG, "Failed to load extension " + impl, e);
                            }

                        }
                    }

                    moveToNext = cursor.moveToNext();

                }

            } finally {
                cursor.close();
            }
        }

    }

    @Override
    public <T> List<T> getExtensions( Class<T> type ) {
        return getExtensions(type, cache);
    }

    @Override
    public <T> List<T> getExtensions( Class<T> type, PluginCache cache ) {
        List<T> extensions = new ArrayList<T>();

        if( !filter.filterLoad(type) ) {
            ContentResolver contentResolver = context.getContentResolver();
            Cursor cursor = contentResolver.query(extensionUri, new String[] {"*"}, 
                    Extension.Fields.TYPE + "=?", 
                    new String[] {type.getName()}, null);

            if( cursor != null ) {
                try {
                    int implIdx = cursor.getColumnIndex(Extension.Fields.IMPL);
                    int singletonIdx = cursor.getColumnIndex(Extension.Fields.SINGLETON);
                    int pluginIdIdx = cursor.getColumnIndex(Plugin.Fields.PLUGIN_ID);

                    while( cursor.moveToNext() ) {
                        String pluginId = cursor.getString(pluginIdIdx);
                        String impl = cursor.getString(implIdx);
                        boolean singleton = cursor.getInt(singletonIdx) == 1;

                        T extension = loadExtension(pluginId, impl, singleton, cache);
                        if( extension == null ) {
                            continue;
                        }
                        if(!type.isAssignableFrom(extension.getClass())) {
                            Log.e(TAG, "Extension is not of advertised type: " + extension.getClass() + " is not an " + type);
                            continue;
                        }
                        if( filter.filterLoad(extension) ) {
                            Log.d(TAG, "Extension " + impl + " filtered");
                            continue;
                        }
                        extensions.add(extension);
                    }
                } finally {
                    cursor.close();
                }
            }
        }

        return extensions;
    }

    @Override
    void setPluginBaseClassLoader(ClassLoader loader) {
        this.pluginBaseClassLoader = loader;
    }

    @Override
    ClassLoader getPluginBaseClassLoader() {
        return pluginBaseClassLoader;
    }

    @Override
    public List<Plugin> getPlugins() {
        List<Plugin> ret = new ArrayList<Plugin>();
        ContentResolver contentResolver = context.getContentResolver();
        Cursor cursor = contentResolver.query(pluginUri, new String[] {"*"}, 
                null,  null, null);

        if( cursor != null ) {
            try {
                int pluginIdIdx = cursor.getColumnIndex(Plugin.Fields.PLUGIN_ID);
                int versionIdx = cursor.getColumnIndex(Plugin.Fields.VERSION);
                int nameIdx = cursor.getColumnIndex(Plugin.Fields.NAME);

                while( cursor.moveToNext() ) {
                    String pluginId = cursor.getString(pluginIdIdx);
                    String name = cursor.getString(nameIdx);
                    String version = cursor.getString(versionIdx);
                    Plugin plugin = new Plugin();
                    plugin.setName(name);
                    plugin.setDescriptor(new PluginDescriptor(pluginId, version));
                    ret.add(plugin);
                }
            } finally {
                cursor.close();
            }
        }
        return ret;
    }

    @SuppressWarnings("unchecked")
    private <T> T loadExtension(String pluginId, String impl, boolean singleton, PluginCache cache) {

        PluginContext pluginContext = cache.getPluginContext(pluginId);
        if( pluginContext != null ) {
            try {
                ClassLoader classLoader = pluginContext.getClassLoader();
                Class<?> implClass = classLoader.loadClass(impl);

                T extension = null;

                // first, make sure that if this guy is a singleton, we don't
                // create it again.  Singletons allow us to have a single extension
                // implement multiple extension points
                if( singleton ) {
                    Object object = cache.getSingleton(implClass);
                    if( object != null ) {
                        extension = (T) object;
                    }
                }

                if( extension == null ) {
                    Constructor<?> contextCtor = null;
                    try {
                        contextCtor = implClass.getConstructor(Context.class, PluginRegistry.class);
                    } catch ( Exception ignored ) {
                    }

                    if (contextCtor != null){
                        // if we give the extension a context, make sure it doesn't have to know
                        // about which context to use...
                        PluginRegistry registry = cache.getPluginRegistry();
                        extension = (T) contextCtor.newInstance(pluginContext, registry);
                    } else {
                        try {
                            contextCtor = implClass.getConstructor(Context.class);
                        } catch ( Exception ignored ) {
                        }
                        if( contextCtor != null ) {
                            extension = (T) contextCtor.newInstance(pluginContext);
                        }
                    }

                    if (extension == null) {
                        extension = (T) implClass.newInstance();
                    }
                }

                if( extension != null  && singleton) {
                    cache.addSingleton(implClass, extension);
                }

                return extension;
            }
            catch ( Exception e ) 
            {
                if( e.getCause( ) instanceof PluginUnsupportedException )
                {
                    e = (PluginUnsupportedException) e.getCause( );
                }

                if( e instanceof PluginUnsupportedException )
                {
                    Log.i( TAG, "The Plugin Extension \"" + impl + "\" is not supported." );
                    if( e.getCause( ) != null )
                    {
                        Log.v( TAG, "Reason the extension is not supported: ", e );
                    }
                    else
                    {
                        Log.v( TAG, "Reason the extension is not supported: " + e.getMessage( ) );
                    }
                }
                else
                {
                    Log.e( TAG, "Failed to load extension " + impl, e );
                }
             }
            catch ( LinkageError e ) {
                Log.e(TAG, "Failed to load extension " + impl, e);
            }
        } else {
            Log.e(TAG, "Failed to load extension " + impl);
        }

        return null;
    }
    
    /**
     * Returns a list of the plugins for use in this host application.
     * @return
     *
     * @since NW SDK 1.1.8.6
     */
    @Override
    public List<Plugin> getFilteredPlugins() {
        List<Plugin> ret = new ArrayList<Plugin>();
        ContentResolver contentResolver = context.getContentResolver();
        Cursor cursor = contentResolver.query(pluginUri, new String[] {"*"},
                                              null,  null, null);
        
        if( cursor != null ) {
            try {
                int pluginIdIdx = cursor.getColumnIndex(Plugin.Fields.PLUGIN_ID);
                int versionIdx = cursor.getColumnIndex(Plugin.Fields.VERSION);
                int nameIdx = cursor.getColumnIndex(Plugin.Fields.NAME);
                
                while( cursor.moveToNext() ) {
                    String pluginId = cursor.getString(pluginIdIdx);
                    String name = cursor.getString(nameIdx);
                    String version = cursor.getString(versionIdx);
                    Plugin plugin = new Plugin();
                    plugin.setName(name);
                    plugin.setDescriptor(new PluginDescriptor(pluginId, version));
                    
                    if(!getFilter().filterRegister(plugin)) {
                        ret.add(plugin);
                    }
                    
                }
            } finally {
                cursor.close();
            }
        }
        return ret;
    }
}
