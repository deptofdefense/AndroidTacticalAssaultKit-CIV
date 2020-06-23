package plugins.core;

import java.io.File;

import plugins.core.model.PluginDescriptor;

/**
 * Utility class containing some static constant path names
 */
public final class Paths {

    public static final String PLUGINS = "/mnt/sdcard/support/plugins";
    public static final String DB = "registry.db";
    
    public static final File getPluginDir( PluginDescriptor desc ) {
        File path = new File(PLUGINS + "/" + desc.getPluginId() + "/");
        if( !path.exists() ) {
            path.mkdirs();
        }
        return path;
    }
    
    private Paths() {}
}
