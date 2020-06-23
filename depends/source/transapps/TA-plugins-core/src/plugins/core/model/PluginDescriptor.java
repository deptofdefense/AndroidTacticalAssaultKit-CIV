package plugins.core.model;

import java.io.File;

import plugins.core.Paths;
import plugins.core.model.Plugin.Fields;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;

/**
 * Wrapper to represent the information about the plugin. It provides a wrapper around the name,
 * id and version defined in the Android Manifest of the plugin. The PluginDescriptor
 * is created when the plugin is loaded.  It will be used during a scan for plugins
 * and unless the version has changed, it will not reload the plugin.
 */
public class PluginDescriptor {

    private String pluginId;
    private String version;

    /**
     * Constructor
     */
    public PluginDescriptor() {
    }

    /**
     * Constructor that takes the context for the plugin that will be loaded. The constructor
     * takes the context and loads information from the package manager based on this plugin.
     *
     * @param context Context that is usually a Pluggable Context based on the package of the plugin
     */
    public PluginDescriptor( Context context ) {
        PackageManager pm = context.getPackageManager();
        try {
            PackageInfo packageInfo = pm.getPackageInfo(context.getPackageName(), 0);
            setPluginId(packageInfo.packageName);
            setVersion(String.valueOf(packageInfo.versionCode));
        } catch ( Exception stupidExceptionThatWontHappen ) {}
    }

    /**
     * Constructor that loads itself from the Bundle passed into it
     *
     * @param bundle Bundle containing th plugin id and version values to for this descriptor
     */
    public PluginDescriptor( Bundle bundle ) {
        setPluginId(bundle.getString(Fields.PLUGIN_ID));
        setVersion(bundle.getString(Fields.VERSION));
    }

    /**
     * Constructor that takes an id and version number
     * @param id String id
     * @param version String version
     */
    public PluginDescriptor(String id, String version) {
        setPluginId(id);
        setVersion(version);
    }

    /**
     * Get plugin id
     *
     * @return String plugin id
     */
    public String getPluginId() {
        return pluginId;
    }

    /**
     * Get Plugin version
     *
     * @return String plugin version
     */
    public String getVersion() {
        return version;
    }

    /**
     * Set the plugin id
     *
     * @param pluginId value to set the plugin id
     */
    public void setPluginId(String pluginId) {
        this.pluginId = pluginId;
    }

    /**
     * Set the plugin version
     *
     * @param version String version to set plugin version
     */
    public void setVersion(String version) {
        this.version = version;
    }

    /**
     * Get and/or create a directory on the sdcard specific to this plugin
     *
     * @return
     */
    public File getDirectory() {
        return Paths.getPluginDir(this);
    }

    /**
     * Converter method that writes the relevant version and id fields to a Bundle
     * @return Bundle containing the version and id of this plugin
     */
    public Bundle toBundle() {
        Bundle bundle = new Bundle();
        bundle.putString(Fields.PLUGIN_ID, getPluginId());
        bundle.putString(Fields.VERSION, getVersion());
        return bundle;
    }

    /**
     * Generate a human readable string representation of this class
     *
     * @return String representation of this class
     */
    @Override
    public String toString() {
        return pluginId + " v" + version;
    }
}
