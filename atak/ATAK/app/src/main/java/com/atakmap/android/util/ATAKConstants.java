
package com.atakmap.android.util;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.atak.plugins.impl.AtakPluginRegistry;
import com.atakmap.android.cot.detail.TakVersionDetailHandler;
import com.atakmap.android.gui.WebViewer;
import com.atakmap.android.location.LocationMapComponent;
import com.atakmap.android.metrics.MetricsApi;
import com.atakmap.android.preference.AtakPreferenceFragment;
import com.atakmap.app.BuildConfig;
import com.atakmap.app.R;
import com.atakmap.app.system.FlavorProvider;
import com.atakmap.app.system.SystemComponentLoader;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

/**
 *
 */
public class ATAKConstants {

    private static final String TAG = "ATAKConstants";

    private static String appName = "";
    private static ComponentName componentName = null;
    private static String versionNameManifest = "";
    private static String versionName = "";
    private static String pluginApi = "";
    private static String pluginApiFull = "";
    private static String versionBrand = "";
    private static String packageName = "";
    private static String deviceManufacturer = "";
    private static String deviceModel = "";
    private static String deviceOS = "";
    private static int versionCode = 1;
    private static int iconId = -1;
    private static Drawable iconDrawable = null;

    /**
     * Public initialization of all of the string constants used within ATAK.
     */
    public synchronized static void init(final Context context) {
        if (componentName == null) {
            Log.d(TAG, "init: " + context.getClass().getName());
            componentName = new ComponentName(
                    context.getPackageName(),
                    context.getString(R.string.atak_activity));
        } else {
            Log.d(TAG, "already initialized");
            return;
        }

        if (FileSystemUtils.isEmpty(versionName)) {

            FlavorProvider fp = SystemComponentLoader.getFlavorProvider();
            if (fp != null && fp.hasMilCapabilities())
                iconId = R.drawable.ic_mil_atak_launcher;
            else
                iconId = R.drawable.ic_atak_launcher;

            iconDrawable = context.getDrawable(iconId);

            appName = context.getString(R.string.app_name);
            packageName = context.getPackageName();
            PackageInfo pInfo;
            try {
                pInfo = context.getPackageManager().getPackageInfo(
                        context.getPackageName(), 0);
                versionNameManifest = pInfo.versionName;
                versionName = context.getString(R.string.app_name) + " v"
                        + versionNameManifest;
                versionCode = pInfo.versionCode;
                versionBrand = context.getString(R.string.app_brand);
                if (fp != null) {
                    try {
                        final String api = SystemComponentLoader
                                .getFlavorAPI(context);
                        if (api != null) {
                            final int index = api.lastIndexOf(".");
                            versionBrand = api.substring(index + 1);
                        }
                    } catch (Exception ignored) {
                    }
                }

                // use the flavors api version check if installed otherwise use the cores version
                // check.
                final String flavorApi = SystemComponentLoader
                        .getFlavorAPI(context);
                if (!FileSystemUtils.isEmpty(flavorApi)) {
                    pluginApi = AtakPluginRegistry
                            .stripPluginApiVersion(flavorApi);
                    pluginApiFull = flavorApi;
                } else {
                    pluginApi = AtakPluginRegistry.getPluginApiVersion(context,
                            context.getPackageName(), true);
                    pluginApiFull = AtakPluginRegistry.getPluginApiVersion(
                            context,
                            context.getPackageName(), false);
                }

                deviceManufacturer = Build.MANUFACTURER;
                deviceModel = Build.MODEL;
                deviceOS = String.valueOf(Build.VERSION.SDK_INT);

            } catch (PackageManager.NameNotFoundException e) {
                versionName = context.getString(R.string.app_name);
                versionCode = 1;
                versionBrand = context.getString(R.string.app_brand);
                Log.w(TAG, "Failed to determine version name", e);
            }
        }
    }

    /**
     * Returns the application name used in places by the launcher and as shown in 
     * Android settings.
     * @return the app name
     */
    public static String getAppName() {
        return appName;
    }

    /**
     * Returns the icon resource identifier that is used by the launcher and as is shown
     * in Android settings.
     * @return the app icon resource identifier
     */
    public static int getIconId() {
        return iconId;
    }

    /**
     * Returns the icon that is used by the launcher and corresponds with the icon 
     * identifier obtained by the call getIconId().
     * @return the icon 
     */
    public static Drawable getIcon() {
        return iconDrawable;
    }

    /**
     * Returns the appropriate server connection icon based on the current configuration of
     * the application.
     * @param connected true if the connection icon should be connected.
     * @return the drawable associated with the server reflecting the connected state
     */
    public static int getServerConnection(final boolean connected) {
        FlavorProvider fp = SystemComponentLoader.getFlavorProvider();
        if (fp != null && fp.hasMilCapabilities()) {
            return connected ? R.drawable.ic_mil_server_success
                    : R.drawable.ic_mil_server_error;
        }

        return connected ? R.drawable.ic_server_success
                : R.drawable.ic_server_error;
    }

    /**
     * Gets the brand for the software.
     * @return the brand.
     */
    public static String getBrand() {
        String brand = TakVersionDetailHandler.Platform.ATAK.toString();
        String temp = ATAKConstants.getVersionBrand();
        if (!FileSystemUtils.isEmpty(temp)
                && !FileSystemUtils.isEquals(temp, "MIL")) {
            brand += "-" + temp;
        }

        return brand;
    }

    /**
     * Returns the android api target in the full form or in the stripped form 
     * which only shows the value after the @ symbol.
     * @param bStrip true returns the shortened API value, false returns the full value.
     */
    public static String getPluginApi(final boolean bStrip) {
        return bStrip ? pluginApi : pluginApiFull;
    }

    /**
     * Returns the package name for the software.
     * @return the package name
     */
    public static String getPackageName() {
        return packageName;
    }

    /**
     * Returns the version name of the software which contains a human readable 
     * representation of the version code such as 4.2.1
     * @return the human readable string
     */
    public static String getVersionName() {
        return versionName;
    }

    public static int getVersionCode() {
        return versionCode;
    }

    public static ComponentName getComponentName() {
        return componentName;
    }

    public static String getVersionNameManifest() {
        return versionNameManifest;
    }

    /**
     * Returns the app branding which is the CIV, MIL, etc.
     * @return the String that describes the brand.
     */
    public static String getVersionBrand() {
        return versionBrand;
    }

    public static String getDeviceOS() {
        return deviceOS;
    }

    public static String getDeviceModel() {
        return deviceModel;
    }

    public static String getDeviceManufacturer() {
        return deviceManufacturer;
    }

    /**
     * The full version name including the version name, version code and version brand.
     * @return a string in the form: versionname.versioncode.
     */
    public static String getFullVersionName() {
        String v = versionName;

        if (versionCode != 1
                && !v.contains(String.valueOf(versionCode))) {
            v += "." + versionCode;
        }

        //v += " (" + versionBrand + ")";
        return v;
    }

    /**
     * Helper method for displaying the about dialog with an optional capability to display the view
     * eula button.
     * @param context the activity to use when displaying the dialog.
     * @param bDisplayEULA show the eula button.
     */
    public static void displayAbout(final Context context,
            final boolean bDisplayEULA) {
        final AlertDialog.Builder build = new AlertDialog.Builder(context);
        LayoutInflater inflater = LayoutInflater.from(context);

        SharedPreferences _controlPrefs = PreferenceManager
                .getDefaultSharedPreferences(context);

        View v;
        if (_controlPrefs.getBoolean("atakControlForcePortrait",
                false)) {
            v = inflater.inflate(R.layout.atak_splash_port, null);
        } else {
            v = inflater.inflate(R.layout.atak_splash, null);
        }
        FlavorProvider fp = SystemComponentLoader.getFlavorProvider();
        if (fp != null)
            fp.installCustomSplashScreen(v,
                    AtakPreferenceFragment.getOrientation(context));

        final String encryptorName = SystemComponentLoader
                .getEncryptionComponentName();
        if (encryptorName != null) {
            final TextView encryptionText = v
                    .findViewById(R.id.encryption);
            encryptionText.setText(context
                    .getString(R.string.dar_encryptor_message, encryptorName));
            encryptionText.setVisibility(View.VISIBLE);
        }

        TextView tv = v.findViewById(R.id.revision);
        TextView pa = v.findViewById(R.id.pluginapi);
        TextView mm = v.findViewById(R.id.makemodel);
        pa.setVisibility(View.VISIBLE);
        mm.setVisibility(View.VISIBLE);

        final int level = Build.VERSION.SDK_INT;

        mm.setText(String.format(
                context.getString(R.string.about_screen_make_model),
                deviceModel, level));

        pa.setText(context.getString(R.string.preferences_text414)
                + ATAKConstants.getPluginApi(true));

        tv.setText(ATAKConstants.getFullVersionName());
        File splash = FileSystemUtils
                .getItem(FileSystemUtils.SUPPORT_DIRECTORY
                        + File.separatorChar + "atak_splash.png");
        if (FileSystemUtils.isFile(splash)) {
            Bitmap bmp;
            try (FileInputStream fis = IOProviderFactory
                    .getInputStream(splash)) {
                bmp = BitmapFactory.decodeStream(fis);
            } catch (IOException e) {
                bmp = null;
            }
            if (bmp != null) {
                Log.d(TAG, "Loading custom splash screen");
                ImageView atak_splash_imgView = v
                        .findViewById(R.id.atak_splash_imgView);
                atak_splash_imgView.setImageBitmap(bmp);
            }
        }

        build.setView(v);
        build.setCancelable(false);
        build.setPositiveButton(R.string.ok, null);
        if (bDisplayEULA) {
            build.setNeutralButton(R.string.preferences_text415,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            try {
                                WebViewer.show(
                                        "file:///android_asset/support/license/LICENSE.txt",
                                        context, 250);
                            } catch (Exception e) {
                                Log.e(TAG, "error loading license.txt", e);
                            }

                        }
                    });
        }

        final AlertDialog dialog = build.create();
        dialog.show();

        if (MetricsApi.shouldRecordMetric()) {
            Bundle b = new Bundle();
            b.putString("eula", String.valueOf(bDisplayEULA));
            MetricsApi.record("about", b);
        }
    }

    /**
     * Returns general information about the system.
     * @param context the context to be used
     * @return a map with the keys guaranteed to be sorted.
     */
    public static Map<String, String> getGeneralInformation(Context context) {
        final SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(context);

        Map<String, String> retVal = new TreeMap<>();
        retVal.put("id.wifi",
                LocationMapComponent.fetchWifiMacAddress(context));
        retVal.put("id.telephonyid",
                LocationMapComponent.fetchTelephonyDeviceId(context));
        retVal.put("id.serialno",
                LocationMapComponent.fetchSerialNumber(context));
        try {
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                retVal.put("id.app",
                        Settings.Secure.getString(context.getContentResolver(),
                                Settings.Secure.ANDROID_ID));
            }
        } catch (Exception ignored) {
        }

        retVal.put("android.sdk",
                Integer.toString(Build.VERSION.SDK_INT));
        retVal.put("android.version",
                System.getProperty("os.version"));
        retVal.put("android.release", Build.VERSION.RELEASE);

        retVal.put("device.model", Build.MODEL);
        retVal.put("device.manufacturer", Build.MANUFACTURER);
        retVal.put("device.hardware", Build.HARDWARE);
        retVal.put("device.id", Build.ID);
        retVal.put("device.brand", Build.BRAND);
        retVal.put("device.host", Build.HOST);
        retVal.put("device.display", Build.DISPLAY);
        retVal.put("device.fingerprint", Build.FINGERPRINT);
        retVal.put("device.build_time", Long.toString(Build.TIME));

        retVal.put("tak.uid",
                prefs.getString("bestDeviceUID", "unknown"));
        retVal.put("tak.brand", ATAKConstants.getVersionBrand());
        retVal.put("tak.flavor", BuildConfig.FLAVOR);
        retVal.put("tak.type", BuildConfig.BUILD_TYPE);
        retVal.put("tak.version", ATAKConstants.getFullVersionName());

        int count = 0;
        final File[] folders = context.getExternalCacheDirs();
        for (File f : folders) {
            if (f != null)
                retVal.put("filesystem.mount." + count++, f.toString());
        }

        final Map<String, ?> keys = prefs.getAll();
        count = 0;
        for (Map.Entry<String, ?> entry : keys.entrySet()) {
            final String key = entry.getKey();
            if (key.startsWith(AtakPluginRegistry.pluginLoadedBasename)
                    && entry.getValue() != null) {
                retVal.put("plugin." + count++,
                        key.replace("plugin.version.loaded.", "") + " "
                                + entry.getValue().toString());
            }
        }

        return retVal;
    }

}
