
package com.atakmap.android.gui;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import android.util.AttributeSet;
import android.widget.Spinner;
import android.view.Display;
import android.content.res.Configuration;
import android.os.UserHandle;

import com.atakmap.android.maps.MapView;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import com.atakmap.coremap.log.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class PluginSpinner extends Spinner {

    /**
     * This is a facade for a Context and as such, the methods implemented might 
     * not actually be used by the system.   Please note that this is private access
     * and only used by PluginSpinner
     */
    private static class ContextSensitiveContext extends Context {
        final Context origContext;
        final Context appContext;
        Context c;

        public ContextSensitiveContext(final Context origContext) {
            this.appContext = MapView.getMapView().getContext();
            this.c = origContext;
            this.origContext = origContext;
        }

        public void useAppContext(boolean e) {
            if (e)
                c = appContext;
            else
                c = origContext;

        }

        @TargetApi(24)
        public boolean isDeviceProtectedStorage() {
            return c.isDeviceProtectedStorage();
        }

        @TargetApi(24)
        public Context createDeviceProtectedStorageContext() {
            return c.createDeviceProtectedStorageContext();
        }

        @TargetApi(26)
        public Context createContextForSplit(String splitName)
                throws android.content.pm.PackageManager.NameNotFoundException {
            return c.createContextForSplit(splitName);
        }

        @TargetApi(26)
        public void revokeUriPermission(String toPackage, Uri uri,
                int modeFlags) {
            c.revokeUriPermission(toPackage, uri, modeFlags);
        }

        @TargetApi(24)
        public int checkSelfPermission(@NonNull String s) {
            return c.checkSelfPermission(s);
        }

        @TargetApi(26)
        public ComponentName startForegroundService(Intent service) {
            return c.startForegroundService(service);
        }

        @TargetApi(26)
        public Intent registerReceiver(BroadcastReceiver receiver,
                IntentFilter filter, int flags) {
            return c.registerReceiver(receiver, filter, flags);
        }

        @TargetApi(26)
        public Intent registerReceiver(BroadcastReceiver receiver,
                IntentFilter filter, String broadcastPermission,
                Handler scheduler, int flags) {
            return c.registerReceiver(receiver, filter, broadcastPermission,
                    scheduler, flags);
        }

        @TargetApi(24)
        public boolean moveDatabaseFrom(Context sourceContext, String name) {
            return c.moveDatabaseFrom(sourceContext, name);
        }

        @TargetApi(24)
        public boolean deleteSharedPreferences(String name) {
            return c.deleteSharedPreferences(name);
        }

        @TargetApi(24)
        public boolean moveSharedPreferencesFrom(Context sourceContext,
                String name) {
            return c.moveSharedPreferencesFrom(sourceContext, name);
        }

        public boolean isUiContext() {
            Method m;
            try {
                m = c.getClass().getMethod("isUIContext");
                return (Boolean) m.invoke(c, new Object[] {});
            } catch (SecurityException | NoSuchMethodException
                    | IllegalArgumentException | IllegalAccessException
                    | InvocationTargetException e) {
                Log.e(TAG, "isUIContext failed", e);
            }
            return false;
        }

        public void updateDisplay(int displayId) {
            Method m;
            try {
                m = c.getClass().getMethod("updateDisplay");
                m.invoke(c, new Object[] {
                        displayId
                });
            } catch (SecurityException | NoSuchMethodException
                    | IllegalArgumentException | IllegalAccessException
                    | InvocationTargetException e) {
                Log.e(TAG, "getExternalMediaDirs failed", e);
            }
        }

        public String getSystemServiceName(@NonNull Class<?> clazz) {
            Method m;
            try {
                m = c.getClass().getMethod("getSystemServiceName", Class.class);
                return (String) m.invoke(c, new Object[] {
                        clazz
                });
            } catch (SecurityException | NoSuchMethodException
                    | IllegalArgumentException | IllegalAccessException
                    | InvocationTargetException e) {
                Log.e(TAG, "getSystemServiceName failed", e);
            }
            return "";
        }

        /**
         * added in to fix an issue with Android 9 on a 960U model phone.
         * note - the original source code is marked unsupported app usage.
         */
        public Display getDisplay() {
            Method m;
            try {
                m = c.getClass().getMethod("getDisplay", Class.class);
                return (Display) m.invoke(c, new Object[] {});
            } catch (SecurityException | NoSuchMethodException
                    | IllegalArgumentException | IllegalAccessException
                    | InvocationTargetException e) {
                Log.e(TAG, "getSystemServiceName failed", e);
            }
            return null;
        }

        /**
         * added in to fix an issue with Android 10 on a SAMSUNG SM-N975U1 model phone.
         * note - the original source code is marked unsupported app usage.
         */
        public int getDisplayId() {
            Method m;
            try {
                m = c.getClass().getMethod("getDisplayId");
                return (Integer) m.invoke(c, new Object[] {});
            } catch (SecurityException | NoSuchMethodException
                    | IllegalArgumentException | IllegalAccessException
                    | InvocationTargetException e) {
                Log.e(TAG, "getUserId failed", e);
            }
            return 0;
        }

        // fix an issue on the OnePlus6T where this method is being used 
        // even though it does not exist publicly.
        public boolean isCredentialProtectedStorage() {
            Method m;
            try {
                m = c.getClass().getMethod("isCredentialProtectedStorage");
                return (Boolean) m.invoke(c, new Object[] {});
            } catch (SecurityException | NoSuchMethodException
                    | IllegalArgumentException | IllegalAccessException
                    | InvocationTargetException e) {
                Log.e(TAG, "isCredentialProtectedStorage failed", e);
            }

            return false;
        }

        public File getDataDir() {
            Method m;
            try {
                m = c.getClass().getMethod("getDataDir");
                return (File) m.invoke(c, new Object[] {});
            } catch (SecurityException | NoSuchMethodException
                    | IllegalArgumentException | IllegalAccessException
                    | InvocationTargetException e) {
                Log.e(TAG, "getExternalMediaDirs failed", e);
            }
            // based on the ContextImpl source code
            return getFilesDir().getParentFile();
        }

        @Override
        public File[] getExternalMediaDirs() {
            Method m;
            try {
                m = c.getClass().getMethod("getExternalMediaDirs");
                return (File[]) m.invoke(c, new Object[] {});
            } catch (SecurityException | NoSuchMethodException
                    | IllegalArgumentException | IllegalAccessException
                    | InvocationTargetException e) {
                Log.e(TAG, "getExternalMediaDirs failed", e);
            }
            return new File[0];
        }

        @Override
        public File getCodeCacheDir() {
            Method m;
            try {
                m = c.getClass().getMethod("getCodeCacheDir");
                return (File) m.invoke(c, new Object[] {});
            } catch (SecurityException | NoSuchMethodException
                    | IllegalArgumentException | IllegalAccessException
                    | InvocationTargetException e) {
                Log.e(TAG, "getCodeCacheDir failed", e);
            }
            return getCacheDir();
        }

        @Override
        public File getNoBackupFilesDir() {
            Method m;
            try {
                m = c.getClass().getMethod("getNoBackupFilesDir");
                return (File) m.invoke(c, new Object[] {});
            } catch (SecurityException | NoSuchMethodException
                    | IllegalArgumentException | IllegalAccessException
                    | InvocationTargetException e) {
                Log.e(TAG, "getNoBackupFilesDir failed", e);
            }
            return getCacheDir();
        }

        public String getOpPackageName() {
            Method m;
            try {
                m = c.getClass().getMethod("getOpPackageName");
                return (String) m.invoke(c, new Object[] {});
            } catch (SecurityException | NoSuchMethodException
                    | IllegalArgumentException | IllegalAccessException
                    | InvocationTargetException e) {
                Log.e(TAG, "getOpPackageName failed", e);
            }
            return getBasePackageName();

        }

        public int getUserId() {
            Method m;
            try {
                m = c.getClass().getMethod("getUserId");
                return (Integer) m.invoke(c, new Object[] {});
            } catch (SecurityException | NoSuchMethodException
                    | IllegalArgumentException | IllegalAccessException
                    | InvocationTargetException e) {
                Log.e(TAG, "getUserId failed", e);
            }
            return 0;
        }

        @Override
        public AssetManager getAssets() {
            return c.getAssets();
        }

        @Override
        public Resources getResources() {
            return c.getResources();
        }

        public String getBasePackageName() {
            return c.getPackageName();
        }

        @Override
        public PackageManager getPackageManager() {
            return c.getPackageManager();
        }

        @Override
        public ContentResolver getContentResolver() {
            return c.getContentResolver();
        }

        @Override
        public Looper getMainLooper() {
            return c.getMainLooper();
        }

        @Override
        public Context getApplicationContext() {
            return c.getApplicationContext();
        }

        @Override
        public void setTheme(int resid) {
            c.setTheme(resid);
        }

        @Override
        public Resources.Theme getTheme() {
            return c.getTheme();
        }

        @Override
        public ClassLoader getClassLoader() {
            return c.getClassLoader();
        }

        @Override
        public String getPackageName() {
            return c.getPackageName();
        }

        @Override
        public ApplicationInfo getApplicationInfo() {
            return c.getApplicationInfo();
        }

        @Override
        public String getPackageResourcePath() {
            return c.getPackageResourcePath();
        }

        @Override
        public String getPackageCodePath() {
            return c.getPackageCodePath();
        }

        @Override
        public SharedPreferences getSharedPreferences(String name, int mode) {
            return c.getSharedPreferences(name, mode);
        }

        @Override
        public FileInputStream openFileInput(String name)
                throws FileNotFoundException {
            return c.openFileInput(name);
        }

        @Override
        public FileOutputStream openFileOutput(String name, int mode)
                throws FileNotFoundException {
            return c.openFileOutput(name, mode);
        }

        @Override
        public boolean deleteFile(String name) {
            return c.deleteFile(name);
        }

        @Override
        public File getFileStreamPath(String name) {
            return c.getFileStreamPath(name);
        }

        @Override
        public File getFilesDir() {
            return c.getFilesDir();
        }

        @Override
        public File getExternalFilesDir(String type) {
            return c.getExternalFilesDir(type);
        }

        @Override
        public File[] getExternalFilesDirs(String type) {
            return c.getExternalFilesDirs(type);
        }

        @Override
        public File getObbDir() {
            return c.getObbDir();
        }

        @Override
        public File[] getObbDirs() {
            return c.getObbDirs();
        }

        @Override
        public File getCacheDir() {
            return c.getCacheDir();
        }

        @Override
        public File getExternalCacheDir() {
            return c.getExternalCacheDir();
        }

        @Override
        public File[] getExternalCacheDirs() {
            return c.getExternalCacheDirs();
        }

        @Override
        public String[] fileList() {
            return c.fileList();
        }

        @Override
        public File getDir(String name, int mode) {
            return c.getDir(name, mode);
        }

        @Override
        public SQLiteDatabase openOrCreateDatabase(String name, int mode,
                SQLiteDatabase.CursorFactory factory) {
            return c.openOrCreateDatabase(name, mode, factory);
        }

        @Override
        public SQLiteDatabase openOrCreateDatabase(String name, int mode,
                SQLiteDatabase.CursorFactory factory,
                DatabaseErrorHandler errorHandler) {
            return c.openOrCreateDatabase(name, mode, factory, errorHandler);
        }

        @Override
        public boolean deleteDatabase(String name) {
            return c.deleteFile(name);
        }

        @Override
        public File getDatabasePath(String name) {
            return c.getDatabasePath(name);
        }

        @Override
        public String[] databaseList() {
            return c.databaseList();
        }

        @Override
        public Drawable getWallpaper() {
            return c.getWallpaper();
        }

        @Override
        public Drawable peekWallpaper() {
            return c.peekWallpaper();
        }

        @Override
        public int getWallpaperDesiredMinimumWidth() {
            return c.getWallpaperDesiredMinimumWidth();
        }

        @Override
        public int getWallpaperDesiredMinimumHeight() {
            return c.getWallpaperDesiredMinimumHeight();
        }

        @Override
        public void setWallpaper(Bitmap bitmap) throws IOException {
            c.setWallpaper(bitmap);
        }

        @Override
        public void setWallpaper(InputStream data) throws IOException {
            c.setWallpaper(data);
        }

        @Override
        public void clearWallpaper() throws IOException {
            c.clearWallpaper();
        }

        @Override
        public void startActivity(Intent intent) {
            c.startActivity(intent);
        }

        @Override
        public void startActivity(Intent intent, Bundle bundle) {
            c.startActivity(intent, bundle);
        }

        @Override
        public void startActivities(Intent[] intents) {
            c.startActivities(intents);
        }

        @Override
        public void startActivities(Intent[] intents, Bundle bundle) {
            c.startActivities(intents, bundle);
        }

        @Override
        public void startIntentSender(IntentSender intent, Intent fillInIntent,
                int flagsMask, int flagsValues, int extraFlags)
                throws IntentSender.SendIntentException {
            c.startIntentSender(intent, fillInIntent, flagsMask, flagsValues,
                    extraFlags);
        }

        @Override
        public void startIntentSender(IntentSender intent, Intent fillInIntent,
                int flagsMask, int flagsValues, int extraFlags, Bundle bundle)
                throws IntentSender.SendIntentException {
            c.startIntentSender(intent, fillInIntent, flagsMask, flagsValues,
                    extraFlags, bundle);
        }

        @Override
        public void sendBroadcast(Intent intent) {
            // Fortify has flagged this as a Android Bad Practice: Missing Receiver Permission
            // This class is a facade for a real context so it must implement 1:1 the methods
            // being wrapped.    The application does not call this directly.
            c.sendBroadcast(intent);
        }

        @Override
        @SuppressWarnings({
                "MissingPermission"
        })
        public void sendBroadcastAsUser(Intent intent, UserHandle user) {
            c.sendBroadcastAsUser(intent, user);
        }

        @Override
        public void sendBroadcast(Intent intent, String receiverPermission) {
            c.sendBroadcast(intent, receiverPermission);
        }

        @Override
        @SuppressWarnings({
                "MissingPermission"
        })
        public void sendBroadcastAsUser(Intent intent, UserHandle user,
                String receiverPermission) {
            c.sendBroadcastAsUser(intent, user, receiverPermission);
        }

        @Override
        public void sendOrderedBroadcast(Intent intent,
                String receiverPermission) {
            c.sendOrderedBroadcast(intent, receiverPermission);
        }

        @Override
        public void sendOrderedBroadcast(@NonNull Intent intent,
                String receiverPermission, BroadcastReceiver resultReceiver,
                Handler scheduler, int initialCode, String initialData,
                Bundle initialExtras) {
            // Fortify has flagged this as a Android Bad Practice: Missing Receiver Permission
            // This class is a facade for a real context so it must implement 1:1 the methods
            // being wrapped.    The application does not call this directly.
            c.sendOrderedBroadcast(intent, receiverPermission, resultReceiver,
                    scheduler, initialCode, initialData, initialExtras);
        }

        @Override
        @SuppressWarnings({
                "MissingPermission"
        })
        public void sendOrderedBroadcastAsUser(Intent intent, UserHandle user,
                String receiverPermission, BroadcastReceiver resultReceiver,
                Handler scheduler, int initialCode, String initialData,
                Bundle initialExtras) {
            c.sendOrderedBroadcastAsUser(intent, user, receiverPermission,
                    resultReceiver,
                    scheduler, initialCode, initialData, initialExtras);
        }

        @Override
        @SuppressWarnings({
                "MissingPermission"
        })
        public void sendStickyBroadcast(Intent intent) {
            // Fortify has flagged this as a Android Bad Practice: Sticky Broadcast
            // This class is a facade for a real context so it must implement 1:1 the methods
            // being wrapped.    The application does not call this directly.
            c.sendStickyBroadcast(intent);
        }

        @Override
        @SuppressWarnings({
                "MissingPermission"
        })
        public void sendStickyBroadcastAsUser(Intent intent, UserHandle user) {
            c.sendStickyBroadcastAsUser(intent, user);
        }

        @Override
        @SuppressWarnings({
                "MissingPermission"
        })
        public void sendStickyOrderedBroadcast(Intent intent,
                BroadcastReceiver resultReceiver, Handler scheduler,
                int initialCode, String initialData, Bundle initialExtras) {
            // Fortify has flagged this as a Android Bad Practice: Sticky Broadcast
            // This class is a facade for a real context so it must implement 1:1 the methods
            // being wrapped.    The application does not call this directly.
            c.sendStickyOrderedBroadcast(intent, resultReceiver, scheduler,
                    initialCode, initialData, initialExtras);
        }

        @Override
        @SuppressWarnings({
                "MissingPermission"
        })
        public void sendStickyOrderedBroadcastAsUser(Intent intent,
                UserHandle user, BroadcastReceiver resultReceiver,
                Handler scheduler,
                int initialCode, String initialData, Bundle initialExtras) {
            c.sendStickyOrderedBroadcastAsUser(intent, user, resultReceiver,
                    scheduler,
                    initialCode, initialData, initialExtras);
        }

        @Override
        @SuppressWarnings({
                "MissingPermission"
        })
        public void removeStickyBroadcast(Intent intent) {
            c.removeStickyBroadcast(intent);
        }

        @Override
        @SuppressWarnings({
                "MissingPermission"
        })
        public void removeStickyBroadcastAsUser(Intent intent,
                UserHandle userHandle) {
            c.removeStickyBroadcastAsUser(intent, userHandle);
        }

        @Override
        public Intent registerReceiver(BroadcastReceiver receiver,
                IntentFilter filter) {
            // Fortify has flagged this as a Android Bad Practice: Missing Broadcaster Permission
            // This class is a facade for a real context so it must implement 1:1 the methods
            // being wrapped.    The application does not call this directly.
            return c.registerReceiver(receiver, filter);
        }

        @Override
        public Intent registerReceiver(BroadcastReceiver receiver,
                IntentFilter filter, String broadcastPermission,
                Handler scheduler) {
            return c.registerReceiver(receiver, filter, broadcastPermission,
                    scheduler);
        }

        @Override
        public void unregisterReceiver(BroadcastReceiver receiver) {
            c.unregisterReceiver(receiver);
        }

        @Override
        public ComponentName startService(Intent service) {
            return c.startService(service);
        }

        @Override
        public boolean stopService(Intent service) {
            return c.stopService(service);
        }

        @Override
        public boolean bindService(Intent service,
                @NonNull ServiceConnection conn,
                int flags) {
            return c.bindService(service, conn, flags);
        }

        @Override
        public void unbindService(@NonNull ServiceConnection conn) {
            c.unbindService(conn);
        }

        @Override
        public boolean startInstrumentation(@NonNull ComponentName className,
                String profileFile, Bundle arguments) {
            return c.startInstrumentation(className, profileFile, arguments);
        }

        @Override
        public Object getSystemService(@NonNull String name) {
            return c.getSystemService(name);
        }

        @Override
        public int checkPermission(@NonNull String permission, int pid,
                int uid) {
            return c.checkPermission(permission, pid, uid);
        }

        @Override
        public int checkCallingPermission(@NonNull String permission) {
            return c.checkCallingPermission(permission);
        }

        @Override
        public int checkCallingOrSelfPermission(@NonNull String permission) {
            // Fortify has flagged this as a Android Bad Practice: Android Permission Check
            // This class is a facade for a real context so it must implement 1:1 the methods
            // being wrapped.    The application does not call this directly.
            return c.checkCallingOrSelfPermission(permission);
        }

        @Override
        public void enforcePermission(@NonNull String permission, int pid,
                int uid,
                String message) {
            c.enforcePermission(permission, pid, uid, message);
        }

        @Override
        public void enforceCallingPermission(@NonNull String permission,
                String message) {
            c.enforceCallingOrSelfPermission(permission, message);
        }

        @Override
        public void enforceCallingOrSelfPermission(@NonNull String permission,
                String message) {
            c.enforceCallingOrSelfPermission(permission, message);
        }

        @Override
        public void grantUriPermission(String toPackage, Uri uri,
                int modeFlags) {
            c.grantUriPermission(toPackage, uri, modeFlags);
        }

        @Override
        public void revokeUriPermission(Uri uri, int modeFlags) {
            c.revokeUriPermission(uri, modeFlags);
        }

        @Override
        public int checkUriPermission(Uri uri, int pid, int uid,
                int modeFlags) {
            return c.checkUriPermission(uri, pid, uid, modeFlags);
        }

        @Override
        public int checkCallingUriPermission(Uri uri, int modeFlags) {
            return c.checkCallingUriPermission(uri, modeFlags);
        }

        @Override
        public int checkCallingOrSelfUriPermission(Uri uri, int modeFlags) {
            // Fortify has flagged this as a Android Bad Practice: Android Permission Check
            // This class is a facade for a real context so it must implement 1:1 the methods
            // being wrapped.    The application does not call this directly.
            return c.checkCallingOrSelfUriPermission(uri, modeFlags);
        }

        @Override
        public int checkUriPermission(Uri uri, String readPermission,
                String writePermission, int pid, int uid, int modeFlags) {
            return c.checkUriPermission(uri, readPermission, writePermission,
                    pid, uid, modeFlags);
        }

        @Override
        public void enforceUriPermission(Uri uri, int pid, int uid,
                int modeFlags, String message) {
            c.enforceUriPermission(uri, pid, uid, modeFlags, message);
        }

        @Override
        public void enforceCallingUriPermission(Uri uri, int modeFlags,
                String message) {
            c.enforceCallingUriPermission(uri, modeFlags, message);
        }

        @Override
        public void enforceCallingOrSelfUriPermission(Uri uri, int modeFlags,
                String message) {
            c.enforceCallingOrSelfUriPermission(uri, modeFlags, message);
        }

        @Override
        public void enforceUriPermission(Uri uri, String readPermission,
                String writePermission, int pid, int uid, int modeFlags,
                String message) {
            c.enforceUriPermission(uri, readPermission, writePermission, pid,
                    uid, modeFlags, message);
        }

        @Override
        public Context createPackageContext(String packageName, int flags)
                throws PackageManager.NameNotFoundException {
            return c.createPackageContext(packageName, flags);
        }

        @Override
        public Context createDisplayContext(@NonNull Display display) {
            return c.createDisplayContext(display);
        }

        @Override
        public Context createConfigurationContext(
                @NonNull Configuration overrideConfiguration) {
            return c.createConfigurationContext(overrideConfiguration);
        }

    }

    public static final String TAG = "PluginSpinner";

    public PluginSpinner(Context context) {
        super(new ContextSensitiveContext(context), null);
    }

    public PluginSpinner(Context context, int mode) {
        super(new ContextSensitiveContext(context), mode);
    }

    public PluginSpinner(Context context, AttributeSet attrs) {
        super(new ContextSensitiveContext(context), attrs);
    }

    public PluginSpinner(Context context, AttributeSet attrs, int mode) {
        super(new ContextSensitiveContext(context), attrs, mode);
    }

    public PluginSpinner(Context context, AttributeSet attrs, int style,
            int mode) {
        super(new ContextSensitiveContext(context), attrs, style, mode);
    }

    @Override
    public boolean performClick() {
        ((ContextSensitiveContext) getContext()).useAppContext(true);
        boolean retval = super.performClick();
        ((ContextSensitiveContext) getContext()).useAppContext(false);
        return retval;

    }

}
