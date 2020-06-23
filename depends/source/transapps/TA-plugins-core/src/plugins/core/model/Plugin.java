package plugins.core.model;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.text.TextUtils;
import android.util.Log;
import plugins.core.Provider;

import java.io.File;
import java.lang.reflect.Field;


/**
 * Class that represents a plugin, defines the fields for the SQL table to hold plugins and wrap information about
 * the plugin specific class loader.  For Android versions greater then 9, this also adds package specific paths
 * to the loadLibrary path for native libraries so that they can be found and loaded at runtime.
 */
public class Plugin {

    /**
     * Static class and Fields and URIs for the table and ContentProvider to load and store Plugins in the
     * database Table
     */
    public static class Fields {
        public static final String ID = BaseColumns._ID;
        public static final String PLUGIN_ID = "pluginid";
        public static final String ACTIVATOR = "activator";
        public static final String VERSION = "version";
        public static final String NAME = "name";
        
        /**
         * The content:// style URL for this table
         */        
        public static final Uri getContentUri(Context context) {
            return Uri.withAppendedPath(Provider.getBaseUri(context), "plugins");
        }

        /**
         * The MIME type of {@link #CONTENT_URI} providing a directory of plugins.
         */
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.plugin.plugin";

        /**
         * The MIME type of a {@link #CONTENT_URI} sub-directory of a single plugin.
         */
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.plugin.plugin";

        
        private Fields() {}
    }

    private long id;    
    private String name;
    private String activator;
    private PluginDescriptor descriptor;    
    private ClassLoaderInfo classLoaderInfo;

    /**
     * Default constructor
     */
    public Plugin() {
    }

    /**
     * Constructor that loads itself from a Bundle
     *
     * @param bundle
     */
    public Plugin(Bundle bundle) {
        setName(bundle.getString(Fields.NAME));
        setActivator(bundle.getString(Fields.ACTIVATOR));
        setDescriptor(new PluginDescriptor(bundle));
        setClassLoaderInfo(new ClassLoaderInfo(bundle));
    }

    /**
     * Constructor that loads itself using the provided Context. The context will generally be a PluggableContext.
     * This constructor loads information about the plugin from the PackageManager including metadata and
     * shared library files. This class creates the ClassLoader information it needs to load the plugin classes.
     * It adds the shared library files to the native load library path for this ClassLoader.
     *
     *
     * @param context Context to use to load Package information
     */
    public Plugin(Context context) {
        ApplicationInfo applicationInfo = null;
        PackageManager pm = context.getPackageManager();
        try {
            applicationInfo = pm.getApplicationInfo(context.getPackageName(), 
                    PackageManager.GET_META_DATA | PackageManager.GET_SHARED_LIBRARY_FILES);
        } catch ( Exception stupidExceptionThatWontHappen ) {}
        
        String apk = applicationInfo.sourceDir;
        String[] libPathParts = applicationInfo.sharedLibraryFiles;
        String libPath = "";
        if( libPathParts != null ) {
            libPath = TextUtils.join(File.pathSeparator, libPathParts);
        }

        if( Build.VERSION.SDK_INT >= 9 ) {
            try {
                Field nativeLibraryDirField = applicationInfo.getClass().getField("nativeLibraryDir");
                String nativeLibraryDir = (String) nativeLibraryDirField.get(applicationInfo);
                if( nativeLibraryDir != null ) {
                    if( libPath.length() > 0 && libPath.endsWith(File.pathSeparator) ) {
                        libPath += File.pathSeparator;
                    }
                    libPath += nativeLibraryDir;
                }
            } catch ( Exception ignored ) {
                Log.w(getClass().getName(), "Failed to load nativeLibraryDir from application info", ignored);
            }
        }
        
        ClassLoaderInfo classLoaderInfo = new ClassLoaderInfo();
        classLoaderInfo.setDexOutputDir("/data/dalvik-cache");
        classLoaderInfo.setDexPath(apk);
        classLoaderInfo.setLibPath(libPath);
        
        setDescriptor(new PluginDescriptor(context));
        setClassLoaderInfo(classLoaderInfo);
    }

    /**
     * Returns the ID loaded from AndroidManifest of the plugin
     *
     * @return long id of the plugin
     */
    public long getId() {
        return id;
    }

    /**
     * Sets the id of the plugin
     *
     * @param id long to set the
     */
    public void setId(long id) {
        this.id = id;
    }

    /**
     * Get the {@link plugins.core.model.PluginDescriptor} for the plugin.
     *
     * @return {@link plugins.core.model.PluginDescriptor} for this plugin
     */
    public PluginDescriptor getDescriptor() {
        return descriptor;
    }

    /**
     * Set the {@link plugins.core.model.PluginDescriptor}
     *
     * @param descriptor
     */
    public void setDescriptor(PluginDescriptor descriptor) {
        this.descriptor = descriptor;
    }

    /**
     * Returns the name of the plugin
     *
     * @return String name of the plugin
     */
    public String getName() {
        return name;
    }

    /**
     *
     * Sets the name of the plugin.
     *
     * @param name String name for the plugin
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     *
     * Gets the fully qualified name of the activator class that if specified will be used to activate the plugin
     * before any extensions are loaded.
     *
     * @return
     */
    public String getActivator() {
        return activator;
    }

    /**
     * Set the class name of the activator for this plugin.
     *
     * @param activator String fully qualified class name of the activator
     */
    public void setActivator(String activator) {
        this.activator = activator;
    }

    /**
     * Get the class loader info for this plugin
     *
     * @return {@link plugins.core.model.ClassLoaderInfo} class loader info for this
     */
    public ClassLoaderInfo getClassLoaderInfo() {
        return classLoaderInfo;
    }

    /**
     * Set the class loader info for this plugin.
     *
     * @param classLoaderInfo {@link plugins.core.model.ClassLoaderInfo} The ClassLoader Info to set for this plugin
     *
     */
    public void setClassLoaderInfo(ClassLoaderInfo classLoaderInfo) {
        this.classLoaderInfo = classLoaderInfo;
    }

    /**
     * Gets the plugin specific sdcard directory for this plugin.
     *
     * @return
     */
    public File getDirectory() {
        return getDescriptor().getDirectory();
    }

    /**
     * Conversion function to create a Bundle from this plugin.
     *
     * @return
     */
    public Bundle toBundle() {
        Bundle bundle = new Bundle();
        bundle.putString(Fields.NAME, getName());
        bundle.putString(Fields.ACTIVATOR, getActivator());
        bundle.putAll(getDescriptor().toBundle());
        bundle.putAll(getClassLoaderInfo().toBundle());
        return bundle;
    }
}
