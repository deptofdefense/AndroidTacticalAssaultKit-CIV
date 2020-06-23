package plugins.host.register;

import java.io.InputStream;

import plugins.core.provider.PluginRegistrationProvider;
import android.content.ContentProviderClient;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

/**
 * Specialization of the Registration InfoSource to load the plugin.xml file through the use of a Content Provider
 * in the plugin application.  This mechanism is necessary to avoid some issues with applications that are not
 * installed correctly will not load correctly when the Pluggable application attempts to load the plugins.
 *
 * By using the ContentProvider, Android will start the application and complete any steps necessary to install
 * the application before the pluggable applications
 *
 */
public class ProviderXmlRegistrationInfoSource extends XmlRegistrationInfoSource {
    
    private static final String TAG = XmlRegistrationInfoSource.class.getName();
    private static final String AUTHORITY_SUFFIX = ".plugin.registration";

    public ProviderXmlRegistrationInfoSource(Context hostContext) {
        super(hostContext);
    }

    /**
     *
     * @param pluginContext context to load the plugin information from
     * @return InputStream input stream that waraps the plugin.xml stream retuened from the content provider.
     * @throws Exception
     */
    @Override
    protected InputStream getXmlInputStream(Context pluginContext) throws Exception {        
        Uri uri = Uri.parse("content://" + pluginContext.getPackageName() + AUTHORITY_SUFFIX + "/" + PluginFileLocator.PLUGIN_FILE);
        ContentProviderClient client = hostContext.getContentResolver().acquireContentProviderClient(uri);
        if( client == null ) {
            Log.w(TAG, "WARNING: If /data/dalvik-cache does not contain the dex for " + packageName + " this plugin may not load!");
            Log.w(TAG, "WARNING: Add <provider authority=\"" + pluginContext.getPackageName() + AUTHORITY_SUFFIX + "\" " +
                    "name=\""+PluginRegistrationProvider.class.getName()+"\"/> to your manifest to ensure this isn't a problem!");
            throw new Exception("Plugin registration provider not defined");
        }
        try {
            return hostContext.getContentResolver().openInputStream(uri);
        } catch (Exception e) {
            Log.e(TAG, "Failed to query provider for " + PluginFileLocator.PLUGIN_FILE, e);
            throw e;
        }
    }
}
