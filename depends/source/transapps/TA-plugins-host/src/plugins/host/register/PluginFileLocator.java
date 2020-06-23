package plugins.host.register;

import java.io.InputStream;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.util.Log;

/**
 * Utility class to find the plugin.xml file for parsing.  It searches the assets directory and the
 * resources/raw directories for the plugin.xml file.
 *
 * @author mriley
 */
public final class PluginFileLocator {
    
    public static final String PLUGIN_FILE = "plugin-nw.xml";
    public static final String RAW_PLUGIN_FILE = "plugin-nw";
    private static final String TAG = PluginFileLocator.class.getName();

    /**
     * Function that takes the plugin context and searches the asset and resource directories for the plugin.xml file
     *
     * @param pluginContext context for the plugin to search for plugin.xml file
     * @return InputStream an input stream that references the plugin file or null if not found
     */
    public static InputStream locatePluginFile( Context pluginContext ) {
        InputStream in = null;
        try {
            try {
                in = pluginContext.getAssets().open(PLUGIN_FILE);
            } catch ( Exception e ) {
                Log.d(TAG, "Failed to load " + PLUGIN_FILE
                        + " from assets dir of package " + pluginContext.getPackageName() + ".  Checking res/raw");
                Resources resources = pluginContext.getResources();
                int identifier = resources.getIdentifier(RAW_PLUGIN_FILE, "raw", pluginContext.getPackageName());
                in = resources.openRawResource(identifier);
            }

        } catch ( NotFoundException notFound ) {
            Log.i(TAG, "Ignoring " + pluginContext.getPackageName() + ".");
        } catch ( Exception e ) {
            Log.e(TAG, "Failed to load " + PLUGIN_FILE + " from package " + pluginContext.getPackageName());
            Log.d(TAG, "TRACE", e);
        }
        return in;
    }
    
    private PluginFileLocator() {}
}
