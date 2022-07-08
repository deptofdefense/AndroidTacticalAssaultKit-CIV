
package com.atakmap.android.update;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.widget.Toast;

import com.atak.plugins.impl.AtakPluginRegistry;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.util.ATAKConstants;
import com.atakmap.android.util.NotificationUtil;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import java.util.HashSet;
import java.util.Set;

/**
 * Support checking for updates
 * 
 * 
 */
public class ApkUpdateReceiver extends BroadcastReceiver {
    private static final String TAG = "ApkUpdateReceiver";

    final public static String DOWNLOAD_APK = "com.atakmap.app.DOWNLOAD_APK";
    final public static String APP_ADDED = "com.atakmap.app.APP_ADDED";
    final public static String APP_REMOVED = "com.atakmap.app.APP_REMOVED";

    private MapView _mapView;
    private ApkDownloader _downloader;

    private final Set<String> _outstandingInstalls = new HashSet<>();

    public ApkUpdateReceiver(MapView mapView) {
        _mapView = mapView;
        _downloader = new ApkDownloader(mapView);
    }

    public void dispose() {
        _mapView = null;
        if (_downloader != null) {
            _downloader.dispose();
            _downloader = null;
        }
    }

    @Override
    public void onReceive(final Context context, Intent intent) {
        Log.d(TAG, "onReceive: " + intent.getAction());

        if (DOWNLOAD_APK.equals(intent.getAction())) {
            final String url = intent.getStringExtra("url");
            final String packageName = intent.getStringExtra("package");
            final String hash = intent.getStringExtra("hash");
            final String filename = intent.getStringExtra("filename");
            final boolean bInstall = intent.getBooleanExtra("install", false);

            if (FileSystemUtils.isEmpty(url)
                    || FileSystemUtils.isEmpty(packageName)
                    || FileSystemUtils.isEmpty(filename)) {
                Log.w(TAG, "Failed to download APK, no URL/filename");
                NotificationUtil.getInstance().postNotification(
                        ApkDownloader.notificationId,
                        R.drawable.ic_network_error_notification_icon,
                        NotificationUtil.RED,
                        _mapView.getContext().getString(R.string.app_name)
                                + " Update Failed",
                        "Download URL not set",
                        "Download URL not set");
                return;
            }

            //start download
            _downloader.downloadAPK(url, packageName, filename, hash, bInstall);
        }

        if (Intent.ACTION_PACKAGE_ADDED.equals(intent.getAction())) {
            final String pkg = getPackageName(intent);
            Log.d(TAG, "ACTION_PACKAGE_ADDED: " + pkg);
            final String appName = AppMgmtUtils.getAppNameOrPackage(context,
                    pkg);

            //first check if this is an unloaded plugin
            if (!AtakPluginRegistry.get().isPluginLoaded(pkg)
                    && AtakPluginRegistry.get().isPlugin(pkg)) {
                //see if ATAK initiated the install
                boolean bWaitingAppInstall = removeInstalling(pkg);
                if (bWaitingAppInstall) {
                    //it is a plugin we were waiting on, go ahead and load into ATAK
                    Log.d(TAG, "Loading plugin into ATAK: " + appName);
                    if (AtakPluginRegistry.get().loadPlugin(pkg)) {
                        Toast.makeText(context, "Loaded plugin: " + appName,
                                Toast.LENGTH_SHORT).show();
                    }
                } else {
                    // if the AppMgmtActivity is showing use the activity context, 
                    // otherwise use the other context
                    final Context c = AppMgmtActivity
                            .getActivityContext() != null
                                    ? AppMgmtActivity.getActivityContext()
                                    : context;

                    //prompt user to load plugin into ATAK
                    AlertDialog.Builder dialog = new AlertDialog.Builder(c)
                            .setTitle(
                                    String.format(context
                                            .getString(R.string.load_plugins),
                                            appName))
                            .setMessage(
                                    String.format(
                                            context.getString(
                                                    R.string.plugin_prompt),
                                            context
                                                    .getString(
                                                            R.string.app_name),
                                            appName))
                            .setPositiveButton(R.string.ok,
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(
                                                DialogInterface dialog,
                                                int which) {
                                            Log.d(TAG,
                                                    "Loading sideloaded plugin into ATAK, per user");
                                            if (AtakPluginRegistry.get()
                                                    .loadPlugin(pkg)) {

                                                AtakBroadcast
                                                        .getInstance()
                                                        .sendBroadcast(
                                                                new Intent(
                                                                        ProductProviderManager.PRODUCT_REPOS_REFRESHED));

                                                Toast.makeText(
                                                        c,
                                                        String.format(
                                                                context.getString(
                                                                        R.string.loaded_plugins),
                                                                appName),
                                                        Toast.LENGTH_SHORT)
                                                        .show();
                                            } else {
                                                incompatiblePluginWarning(c,
                                                        pkg);
                                            }
                                        }
                                    })
                            .setNegativeButton(R.string.cancel, null);

                    Drawable icon = AppMgmtUtils.getAppDrawable(context, pkg);
                    if (icon != null)
                        dialog.setIcon(
                                AppMgmtUtils.getDialogIcon(context, icon));
                    else
                        dialog.setIcon(R.drawable.ic_menu_plugins);
                    dialog.setCancelable(false);
                    dialog.show();
                }
            }

            //whether plugin or app, initiated by ATAK or not. Notify listeners of app so they can refresh
            AtakBroadcast.getInstance().sendBroadcast(new Intent(APP_ADDED)
                    .putExtra("package", pkg));
        } else if (Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction())) {
            final String pkg = getPackageName(intent);
            Log.d(TAG, "ACTION_PACKAGE_REMOVED: " + pkg);

            //if it was a plugin, clear out plugin registry
            if (AtakPluginRegistry.get().unloadPlugin(pkg)) {
                Toast.makeText(
                        context,
                        context.getString(R.string.plugin_uninstalled)
                                + AppMgmtUtils
                                        .getAppNameOrPackage(context, pkg),
                        Toast.LENGTH_LONG).show();
            }

            AtakBroadcast.getInstance().sendBroadcast(new Intent(APP_REMOVED)
                    .putExtra("package", pkg));
        }
    }

    private void incompatiblePluginWarning(final Context c, final String pkg) {

        //now prompt user to fully uninstall
        String label = AppMgmtUtils.getAppNameOrPackage(c,
                pkg);
        if (FileSystemUtils.isEmpty(label)) {
            label = "Plugin";
        }

        final Drawable icon = AppMgmtUtils.getAppDrawable(c, pkg);
        //final int sdk = AppMgmtUtils.getTargetSdkVersion(c, pkg);
        final String api = AtakPluginRegistry.getPluginApiVersion(
                c, pkg, true);
        final boolean sig = AtakPluginRegistry.verifySignature(c, pkg);

        String reason = "";

        final String versionBrand = ATAKConstants.getVersionBrand();
        if (!ATAKConstants.getPluginApi(true).equals(api) &&
                !ATAKConstants.getPluginApi(true).replace(versionBrand, "CIV")
                        .equals(api)) {
            reason = String.format(c.getString(R.string.reason1), api,
                    ATAKConstants.getPluginApi(true));
        }
        if (!sig) {
            if (!FileSystemUtils.isEmpty(reason))
                reason = reason + "\n\n\n";
            reason = reason + c.getString(R.string.reason2) + "\n";
        }
        if (FileSystemUtils.isEmpty(reason)) {
            reason = c.getString(R.string.reason3);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(c)
                .setTitle(String.format(
                        c.getString(R.string.load_failure_title), label))
                .setMessage(reason)
                .setPositiveButton(R.string.uninstall,
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(
                                    DialogInterface dialog,
                                    int which) {
                                dialog.dismiss();
                                AppMgmtUtils.uninstall(
                                        c,
                                        pkg);
                            }
                        })
                .setNegativeButton(R.string.cancel, null);
        if (icon != null)
            builder.setIcon(AppMgmtUtils.getDialogIcon(c, icon));
        else
            builder.setIcon(R.drawable.ic_menu_plugins);
        AlertDialog dialog = builder.create();
        try {
            dialog.show();
        } catch (Exception ignored) {
        }

    }

    private static String getPackageName(Intent intent) {
        Uri uri = intent.getData();
        return uri != null ? uri.getSchemeSpecificPart() : null;
    }

    /**
     * Add to the list of outstanding apps that are being installed
     * @param product the product information to queue up for installation
     */
    public void addInstalling(final ProductInformation product) {
        Log.d(TAG, "addInstalling: " + product.getPackageName());
        synchronized (_outstandingInstalls) {
            _outstandingInstalls.add(product.getPackageName());
        }
    }

    /**
     * Remove the specified app from list of those outstanding to be installed
     * @param pkg the package to remove
     * @return  true if app was in the list
     */
    private boolean removeInstalling(final String pkg) {
        Log.d(TAG, "removeInstalling: " + pkg);
        synchronized (_outstandingInstalls) {
            return _outstandingInstalls.remove(pkg);
        }
    }
}
