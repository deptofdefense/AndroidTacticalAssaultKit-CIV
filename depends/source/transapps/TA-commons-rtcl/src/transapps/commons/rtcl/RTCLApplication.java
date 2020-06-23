package transapps.commons.rtcl;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;
import dalvik.system.PathClassLoader;
import plugins.core.model.Plugin;
import plugins.host.ApplicationPluggableContext;
import plugins.host.PluginRegistry;


/**
 * Created by fhodum on 6/20/14.
 *
 * Base class implemented by Pluggable Applications thats sole purpose is to load dynamic libraries
 * at runtime. Creates a classloader that is aware of any dynamic libraries that need to be loaded
 * at runtime. Sets this classloader in the PluginRegistry for implementing Application.
 *
 */
public abstract class RTCLApplication extends ApplicationPluggableContext
{
    /**
     * The tag to be used for logging.
     */
    private static final String LOG_TAG = RTCLApplication.class.getSimpleName( );

    /**
     * The plugin registry.
     */
    protected PluginRegistry plugins;

    @Override
    public void onCreate( )
    {
        super.onCreate( );
        this.plugins = getRegistry( this );
    }

    /**
     * Gets the plugin registry for the given context or creates one if it does not exist.
     * 
     * @param context
     *            The context.
     * @return The plugin registry for the given context.
     */
    final protected static PluginRegistry getRegistry( Context context )
    {
        return PluginRegistry.getInstance( context );
    }

    /**
     * Called by implementing application class after plugins have been registered, but before
     * getExtensions is called. This method will gather the list of plugins for the application,
     * check if they have dynamic libraries, and then add them to the PathClassLoader. This
     * PathClassLoader is used when instantiating the impl class for the extensions.
     */
    protected void loadLibraries( )
    {
        List<Plugin> filteredPlugins = plugins.getFilteredPlugins( );
        List<String> sourceDirs = new ArrayList<String>( );

        for( Plugin plugin : filteredPlugins )
        {
            String name = plugin.getDescriptor( ).getPluginId( );
            loadLibraryDependencies( name, sourceDirs, false );
        }

        setPluginBaseClassLoader( createRTCLClassLoader( sourceDirs ) );
        plugins.getExtensions( FactoryExtension.class );
    }

    private void loadLibraryDependencies( String packageName, List<String> sourceDirs,
                                          boolean include )
    {
        try
        {
            PackageManager packageManager = getPackageManager( );
            ApplicationInfo applicationInfo =
                packageManager.getApplicationInfo( packageName, PackageManager.GET_META_DATA );

            if( include && !sourceDirs.contains( applicationInfo.sourceDir ) )
            {
                sourceDirs.add( applicationInfo.sourceDir );
            }
            if( applicationInfo.metaData != null )
            {
                String dependenciesStr = applicationInfo.metaData.getString( "dependencies" );

                if( dependenciesStr != null )
                {
                    String [] dependencies = dependenciesStr.split( "," );

                    for( String d : dependencies )
                    {
                        if( !d.trim( ).isEmpty( ) )
                        {
                            loadLibraryDependencies( d, sourceDirs, true );
                        }
                    }
                }
            }
        }
        catch( PackageManager.NameNotFoundException e )
        {
            Log.e( LOG_TAG, "RTCL – Unable to find package: " + packageName );
        }
    }

    private ClassLoader createRTCLClassLoader( List<String> paths )
    {
        StringBuffer pathBuffer = new StringBuffer( );

        int size = paths.size( );
        for( String path : paths )
        {
            pathBuffer.append( path );
            size--;
            if( size != 0 )
            {
                pathBuffer.append( ":" );
            }
        }

        ClassLoader classLoader = new PathClassLoader( pathBuffer.toString( ), getClassLoader( ) );
        return classLoader;
    }

    /**
     * Gets the plugin registry for this application.
     * 
     * @return The plugin registry.
     */
    @Override
    public PluginRegistry getPluginRegistry( )
    {
        return plugins;
    }

    /**
     * This will return the key used for extension types for this application. This will return null
     * if not overridden.
     * 
     * @return The key used for extension types for this application, or null if no extension types
     *         are used.
     * 
     * @since NW SDK 1.1.8.6
     */
    protected String getExtensionsMetaDataKey( )
    {
        return null;
    }

    /**
     * This will return any extension types from the meta data of any package installed on the
     * device. The type of extensions returned will be based on the key provided in
     * getExtensionsMetaData( )
     * 
     * @return Any extension types that have been defined using an appropriate meta-data tag for
     *         this type of application.
     * 
     * @since NW SDK 1.1.8.6
     */
    protected List<String> getExtensionTypes( )
    {
        return getMetaData( getExtensionsMetaDataKey( ), "," );
    }

    /**
     * This is a helper method for getting meta data from installed packages.
     * 
     * @param key
     *            The key for the meta data
     * @param separator
     *            A separator used in the value of the meta data. If null, no separator is used and
     *            only 1 value can be provided per meta-data tag.
     * @return The values for the meta-data key that was provided.
     * 
     * @since NW SDK 1.1.8.6
     */
    protected List<String> getMetaData( String key, String separator )
    {
        List<String> metaData = new ArrayList<String>( );

        if( key != null )
        {
            PackageManager packageManager = getPackageManager( );
            List<PackageInfo> installedPackages =
                packageManager.getInstalledPackages( PackageManager.GET_META_DATA );

            for( PackageInfo packageInfo : installedPackages )
            {
                Bundle bundle = packageInfo.applicationInfo.metaData;

                if( bundle != null )
                {
                    String value = bundle.getString( key );

                    if( value != null )
                    {
                        if( separator != null && !separator.isEmpty( ) )
                        {
                            String [] values = value.split( separator );

                            for( String v : values )
                            {
                                metaData.add( v );
                            }
                        }
                        else
                        {
                            metaData.add( value );
                        }
                    }
                }
            }
        }

        return metaData;
    }
}
