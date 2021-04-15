
package com.atakmap.android.update;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import com.atakmap.android.util.FileProviderHelper;

import android.content.pm.Signature;

import java.security.cert.CertificateFactory;
import java.io.ByteArrayInputStream;
import java.security.cert.X509Certificate;
import android.annotation.SuppressLint;

import android.graphics.drawable.BitmapDrawable;
import android.util.DisplayMetrics;

import com.atakmap.android.util.NotificationUtil;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.log.Log;

import java.io.File;
import java.io.FilenameFilter;
import java.util.List;

/**
 * Supports mgmt of ATAK plugins and auxillary apps. Currently supports
 *
 *
 */
public class AppMgmtUtils {

    private static final String TAG = "AppMgmtUtils";

    /**
     * Version code not available, app not installed
     */
    static final int APP_NOT_INSTALLED = -1;

    /**
     * Name of repo index file
     */
    public static final String REPO_INDEX_FILENAME = "product.inf";

    /**
     * Name of compressed index file
     */
    public static final String REPOZ_INDEX_FILENAME = "product.infz";

    static final String APK_DIR = FileSystemUtils.SUPPORT_DIRECTORY
            + File.separatorChar + "apks";

    public static final FilenameFilter APK_FilenameFilter = new FilenameFilter() {
        @Override
        public boolean accept(File dir,
                String filename) {
            filename = filename.toLowerCase(LocaleUtil.getCurrent());
            return filename.endsWith(".apk");
        }
    };

    public static boolean install(Context context, File apk) {
        //Use java.io.File operations for Android installation purposes
        
        if (apk == null || !apk.isFile()) {
            String message = (apk == null ? "" : apk.getAbsolutePath());
            Log.w(TAG,
                    "Failed to install APK, file does not exist: " + message);

            NotificationUtil.getInstance().postNotification(
                    ApkDownloader.notificationId,
                    R.drawable.ic_network_error_notification_icon,
                    NotificationUtil.RED,
                    context.getString(R.string.app_name)
                            + " installProduct Failed",
                    "APK not found: " + message,
                    "APK not found: " + message);
            return false;
        }

        try {
            Log.d(TAG, "Installing: " + apk.getAbsolutePath());

            Intent intent = new Intent(Intent.ACTION_VIEW);
            FileProviderHelper.setDataAndType(context, intent, apk,
                    "application/vnd.android.package-archive");
            context.startActivity(intent);
            //TODO does not really mean installed, just that the install Activity was found
            return true;

        } catch (Exception e) {
            Log.e(TAG,
                    "Failed to install: "
                            + (apk == null ? "" : apk.getAbsolutePath()),
                    e);
            NotificationUtil.getInstance().postNotification(
                    ApkDownloader.notificationId,
                    R.drawable.ic_network_error_notification_icon,
                    NotificationUtil.RED,
                    context.getString(R.string.app_name)
                            + " installProduct Failed",
                    "Error during install",
                    "Error during install");
            return false;
        }
    }

    public static String getAppNameOrPackage(final Context context,
            final String packageName) {
        final PackageManager pm = context.getPackageManager();
        ApplicationInfo ai;
        try {
            ai = pm.getApplicationInfo(packageName, 0);
            if (ai != null)
                return (String) pm.getApplicationLabel(ai);
        } catch (PackageManager.NameNotFoundException e) {
            Log.d(TAG, "could not look up the package name: " + packageName);
        }
        return packageName;
    }

    public static Drawable getAppDrawable(final Context context,
            final String packageName) {
        try {
            return context.getPackageManager().getApplicationIcon(packageName);
        } catch (PackageManager.NameNotFoundException e) {
            Log.d(TAG,
                    "could not look up the package drawable: " + packageName);
        }
        return null;
    }

    /**
     * Scale image to a specified size - only works for bitmap drawables
     * Used to display alert dialog icons at a reasonable, consistent size.
     * @param image Bitmap drawable
     * @param size Desired size in pixels
     *             It's recommended to pass in a dimension resource here rather
     *             than a hardcoded size
     * @return Scaled drawable
     */
    public static Drawable getDialogIcon(Drawable image, float size) {
        // Scaling method only works for bitmap-based drawables
        if (!(image instanceof BitmapDrawable))
            return image;

        // Mutate so we don't modify the primary instance
        BitmapDrawable b = (BitmapDrawable) image.mutate();

        // Set density to a baseline we can check against for scaling
        b.setTargetDensity(DisplayMetrics.DENSITY_HIGH);

        // Get the width and height of the icon at the given density
        float srcPixels = Math.max(b.getIntrinsicWidth(),
                b.getIntrinsicHeight());

        // Determine the scale we need to apply to the density to get the
        // desired icon size
        float scale = size / srcPixels;

        // Set target density to scale icon
        b.setTargetDensity(Math.round(scale * DisplayMetrics.DENSITY_HIGH));

        return image;
    }

    public static Drawable getDialogIcon(Context context, Drawable image) {
        return getDialogIcon(image, context.getResources()
                .getDimension(R.dimen.button_small));
    }

    /**
     * Gets the app information for a specific package name.
     */
    public static ApplicationInfo getAppInfo(final Context context,
            final String packageName) {
        PackageManager pm = context.getPackageManager();
        try {
            return pm.getApplicationInfo(packageName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            Log.d(TAG, "could not look up the package info: " + packageName);
        }
        return null;
    }

    /**
     * Obtains the app version code from a given package name.
     */
    public static int getAppVersionCode(Context context, String pkg) {
        PackageManager manager = context.getPackageManager();
        try {
            PackageInfo pInfo = manager.getPackageInfo(pkg,
                    PackageManager.GET_ACTIVITIES);
            return pInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Version code not found: " + pkg);
        }
        return APP_NOT_INSTALLED;

    }

    static int getTargetSdkVersion(Context context, String pkg) {
        PackageManager manager = context.getPackageManager();
        try {
            return manager.getApplicationInfo(pkg, 0).targetSdkVersion;
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Target SDK not found: " + pkg);
        }
        return APP_NOT_INSTALLED;

    }

    /**
     * Gets the version name of a package.
     */
    public static String getAppVersionName(Context context, String pkg) {
        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(pkg,
                    PackageManager.GET_ACTIVITIES);
            return pInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Version name not found: " + pkg);
        }
        return "";

    }

    @SuppressWarnings("NewApi")
    public boolean isRunning(Context ctx) {
        ActivityManager activityManager = (ActivityManager) ctx
                .getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningTaskInfo> tasks = activityManager
                .getRunningTasks(Integer.MAX_VALUE);

        if (tasks == null)
            return false;

        for (ActivityManager.RunningTaskInfo task : tasks) {
            // the task.baseActivity field has been available since at least Android 21.
            final ComponentName cn = task.baseActivity;
            if (cn != null) {
                if (ctx.getPackageName().equalsIgnoreCase(
                        cn.getPackageName()))
                    return true;
            }
        }

        return false;
    }

    /**
     * Given a package name, request android to start uninstalling.
     */
    public static void uninstall(final Context ctx, final String packageName) {
        try {
            if (isInstalled(ctx, packageName)) {
                Intent intent = new Intent(Intent.ACTION_DELETE, Uri.fromParts(
                        "package", packageName, null));
                ctx.startActivity(intent);
            }
        } catch (Exception e) {
            Log.w(TAG, "Error while uninstalling: " + packageName, e);
        }
    }

    /**
     * Checks is the package name is registered in the device package manager ie installed apk
     * @param pkg the manifest package name that is associated with the application
     */
    public static boolean isInstalled(Context context, String pkg) {
        PackageManager pm = context.getPackageManager();
        try {
            pm.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            Log.d(TAG, "Package not installed: " + pkg);
        }
        return false;
    }

    public static long getInstalledDate(final Context context,
            final String pkgname) {
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo pi = pm.getPackageInfo(pkgname, 0);
            return pi.firstInstallTime;
        } catch (Exception e) {
            return 0;
        }
    }

    public static long getUpdateDate(final Context context,
            final String pkgname) {
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo pi = pm.getPackageInfo(pkgname, 0);
            return pi.lastUpdateTime;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Obtain the Signatures information for the package.
     * @return the array of human reabable easily extract key information.  empty if no keys are
     * extractable.
     */
    @SuppressLint("PackageManagerGetSignatures")
    public static String[] getSignatureInfo(final Context context,
            final String pkgname) {
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo pi = pm.getPackageInfo(pkgname,
                    PackageManager.GET_SIGNATURES);
            String[] retval = new String[pi.signatures.length];
            for (int i = 0; i < pi.signatures.length; ++i) {
                Signature sig = pi.signatures[i];
                try {
                    CertificateFactory cf = CertificateFactory
                            .getInstance("X.509");
                    X509Certificate cert = (X509Certificate) cf
                            .generateCertificate(new ByteArrayInputStream(
                                    sig.toByteArray()));
                    retval[i] = cert.toString();
                } catch (Exception e) {
                    Log.e(TAG, "error" + e);
                }
            }
            return retval;
        } catch (Exception e) {
            return new String[0];
        }
    }

    /**
     * pings the activity processes registered in the currently running manifest
     * if night vision app is registered it is currently running
     */
    public static boolean isAppRunning(Context context, String processName) {
        ActivityManager activityManager = (ActivityManager) context
                .getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager != null) {
            List<ActivityManager.RunningAppProcessInfo> procInfos = activityManager
                    .getRunningAppProcesses();
            if (procInfos != null) {
                for (int i = 0; i < procInfos.size(); i++) {
                    if (procInfos.get(i).processName.equals(processName)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @SuppressWarnings("NewApi")
    public static Boolean isActivityRunning(Class<?> activityClass,
            Context context) {
        ActivityManager activityManager = (ActivityManager) context
                .getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningTaskInfo> tasks;
        try {
            tasks = activityManager.getRunningTasks(Integer.MAX_VALUE);
        } catch (SecurityException e) {
            Log.w(TAG, "Failed to determine if ATAK is running", e);
            return false;
        }

        if (FileSystemUtils.isEmpty(tasks))
            return false;

        //see if Activity is running
        for (ActivityManager.RunningTaskInfo task : tasks) {
            String canonicalName = activityClass.getCanonicalName();
            if (FileSystemUtils.isEmpty(canonicalName))
                continue;

            // the task.baseActivity field has been available since at least Android 21.
            final ComponentName cn = task.baseActivity;
            if (cn != null) {
                if (canonicalName.equalsIgnoreCase(
                        cn.getClassName()))
                    return true;
            }
        }

        return false;
    }

    public static String getAppDescription(final Context context,
            final String packageName) {
        try {
            final PackageManager pm = context.getPackageManager();
            ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
            CharSequence cs = appInfo.loadDescription(pm);
            if (cs != null)
                return cs.toString();
        } catch (PackageManager.NameNotFoundException e) {
            Log.d(TAG, "could not look up the package name: " + packageName);
        }
        return null;
    }

}
