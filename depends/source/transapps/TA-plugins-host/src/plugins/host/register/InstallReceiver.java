package plugins.host.register;

import android.content.ActivityNotFoundException;
import plugins.host.PluginRegistry;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import plugins.host.ProcessKillActivity;

/**
 * Broadcast Receiver to receive the Android broadcasts when apps/packages are installed, replace or removed.
 *
 * This class will then initiate the process of adding, removing or updating the plugin registry when apps are added
 * or removed from the device.
 *
 * @author mriley
 *
 */
public class InstallReceiver extends BroadcastReceiver {
    
    private static final int PACKAGE_PROTO_LEN = "package:".length();

    /**
     * Receive method called when the broadcast that this class is registered to receive is sent by the system. In
     * particular, this method will notify the plugin registrar of these changes.  This will in turn cause plugins
     * to get added, removed or updated in pluginRegistry.
     *
     * @param context a context for this method
     * @param intent the Intent that contains the data in the Broadcast, specifically the package information of
     *               the added, removed or updated application.
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Uri data = intent.getData();
        String packageName = data.toString().substring(PACKAGE_PROTO_LEN);

        if( packageName.equals(context.getPackageName()) )
            return;
        
        Log.e(PluginRegistry.TAG, "Checking " + packageName + " for plugins...");
        try {
            PackagePluginRegistrar registrar = new PackagePluginRegistrar(context);
            registrar.setForceRegister(true);
            if( Intent.ACTION_PACKAGE_ADDED.equals(action) ) {
                registrar.register(packageName);

            } else if( Intent.ACTION_PACKAGE_REPLACED.equals(action) ) {
                registrar.register(packageName);
                
            } else if( Intent.ACTION_PACKAGE_REMOVED.equals(action) ) {
                registrar.unregister(packageName);
            }

            // if things were changed, restart
            try {
                context.startActivity(new Intent(context, ProcessKillActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch ( ActivityNotFoundException e ) {
                // this can happen if this activity isn't registered
            }

        } catch ( Exception e ) {
            Log.e(PluginRegistry.TAG, "error", e);
        }
    }
}
