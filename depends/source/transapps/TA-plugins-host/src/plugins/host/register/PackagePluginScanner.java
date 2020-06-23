package plugins.host.register;

import java.util.List;

import plugins.host.PluginRegistry;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.util.Log;

/**
 * Helper class to retrieve all the packages for all apps loaded on the device and send them to the Registrar
 * to scan for plugins.
 *
 * Generally this will be done when either a pluggable application has just been installed on the device and it
 * needs to find all installed plugins, OR it will be done when the application has had its database reset and it
 * needs to scan to refind the plugins.  The application should register and InstallReceiver to handle applications
 * installed or removed after the pluggable application is installed.
 *
 * @author mriley
 *
 */
public class PackagePluginScanner {

    public static interface PackageScanMonitor {
        void onPackageScanStart( PackageInfo info );
        void onPackageScanEnd( PackageInfo info );
        boolean isCanceled();
    }

    private final PackagePluginRegistrar registrar;
    private final PackageScanMonitor listener;
    
    public PackagePluginScanner( PackagePluginRegistrar reg, PackageScanMonitor l ) {
        this.registrar = reg;
        this.listener = l;
    }

    /**
     * Function to access the Android Package Manager, retrieve all installed packages and call the
     * PluginRegistrar to register any new plugins for that package.
     *
     */
    public void scan() {
        Context hostContext = registrar.getHostContext();
        PackageManager packageManager = hostContext.getPackageManager();
        List<PackageInfo> installedPackages = packageManager.getInstalledPackages(0);
        for( PackageInfo pi : installedPackages ) {
            if( listener.isCanceled() ) {
                break;
            }
            
            listener.onPackageScanStart(pi);
            try {
                registrar.register(pi.packageName);
            } catch (NameNotFoundException e) {
                Log.e(PluginRegistry.TAG, "Failed to scan package", e);
            }
            listener.onPackageScanEnd(pi);
        }
    }
}
