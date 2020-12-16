
package com.atakmap.android.update;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.AbstractMapComponent;
import com.atakmap.android.maps.MapView;

/**
 * Supports updating ATAK from an update server
 * 
 * 
 */
public class ApkUpdateComponent extends AbstractMapComponent {

    final public static String TAG = "ApkUpdateComponent";

    private ApkUpdateReceiver _apkUpdateReceiver;
    private ProductProviderManager _providerManager;

    private static ApkUpdateComponent _instance;

    @Override
    public void onCreate(Context context, Intent intent, MapView view) {

        _apkUpdateReceiver = new ApkUpdateReceiver(view);
        _providerManager = new ProductProviderManager(
                (Activity) view.getContext());

        DocumentedIntentFilter intentFilter = new DocumentedIntentFilter();
        intentFilter
                .addAction(
                        "com.atakmap.app.COMPONENTS_CREATED",
                        "Once all components are initialized, the configured app repos are scanned, based on configuration");
        AtakBroadcast.getInstance().registerReceiver(
                _componentsCreatedReceiver,
                intentFilter);

        intentFilter = new DocumentedIntentFilter();
        intentFilter
                .addAction(
                        Intent.ACTION_PACKAGE_ADDED,
                        "If an app is installed into Android, ATAK will scan and see if its a compatible plugin");
        intentFilter
                .addAction(
                        Intent.ACTION_PACKAGE_REMOVED,
                        "If an ap is uninstalled from Android, and is an ATAK plugin, ATAK will unloadProduct the plugin");
        intentFilter.addDataScheme("package");
        AtakBroadcast.getInstance().registerSystemReceiver(_apkUpdateReceiver,
                intentFilter);

        intentFilter = new DocumentedIntentFilter();
        intentFilter.addAction(ApkUpdateReceiver.DOWNLOAD_APK,
                "Intent to download an APK from a remote Update Server");
        AtakBroadcast.getInstance().registerReceiver(_apkUpdateReceiver,
                intentFilter);

        intentFilter = new DocumentedIntentFilter();
        intentFilter.addAction(ApkUpdateReceiver.APP_ADDED,
                "Intent to indicate an app or plugin has been added");
        intentFilter.addAction(ApkUpdateReceiver.APP_REMOVED,
                "Intent to indicate an app or plugin has been removed");
        AtakBroadcast.getInstance().registerReceiver(_providerManager,
                intentFilter);
        _instance = this;
    }

    private final BroadcastReceiver _componentsCreatedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            _providerManager.init();
        }
    };

    @Override
    protected void onDestroyImpl(Context context, MapView view) {
        if (_apkUpdateReceiver != null) {
            AtakBroadcast.getInstance().unregisterReceiver(_apkUpdateReceiver);
            AtakBroadcast.getInstance().unregisterSystemReceiver(
                    _apkUpdateReceiver);
            _apkUpdateReceiver.dispose();
        }

        if (_componentsCreatedReceiver != null) {
            AtakBroadcast.getInstance().unregisterReceiver(
                    _componentsCreatedReceiver);
        }

        if (_providerManager != null) {
            AtakBroadcast.getInstance().unregisterReceiver(_providerManager);
            _providerManager.dispose();
        }
    }

    public ApkUpdateReceiver getApkUpdateReceiver() {
        return _apkUpdateReceiver;
    }

    public ProductProviderManager getProviderManager() {
        return _providerManager;
    }

    /**
     * Singleton pattern
     * @return the instance of ApkUpdateComponent
     */
    public static ApkUpdateComponent getInstance() {
        return _instance;
    }
}
