
package com.atakmap.app;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import androidx.annotation.NonNull;

import com.atakmap.comms.CommsMapComponent;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;

import org.acra.ACRA;
import org.acra.ACRAConfiguration;
import org.acra.ReportField;
import org.acra.annotation.ReportsCrashes;

import java.io.File;
import com.atakmap.jnicrash.JNICrash;

@ReportsCrashes
public class ATAKApplication extends Application {

    static final String TAG = "ATAKApplication";

    @Override
    public void onCreate() {
        super.onCreate();

        // important first step, initialize the native loader
        // otherwise as classes are loader, the static blocks
        // are run which might load native libraries.
        com.atakmap.coremap.loader.NativeLoader.init(this);

        // Turn on ACRA defaults, plus enable STACK_HASH
        ReportField[] fields = new ReportField[] {
                ReportField.REPORT_ID,
                ReportField.APP_VERSION_CODE,
                ReportField.APP_VERSION_NAME,
                ReportField.PACKAGE_NAME,
                ReportField.FILE_PATH,
                ReportField.PHONE_MODEL,
                ReportField.ANDROID_VERSION,
                ReportField.BUILD,
                ReportField.BRAND,
                ReportField.PRODUCT,
                ReportField.TOTAL_MEM_SIZE,
                ReportField.AVAILABLE_MEM_SIZE,
                ReportField.BUILD_CONFIG,
                ReportField.CUSTOM_DATA,
                ReportField.STACK_TRACE,
                ReportField.STACK_TRACE_HASH,
                ReportField.INITIAL_CONFIGURATION,
                ReportField.CRASH_CONFIGURATION,
                ReportField.DISPLAY,
                ReportField.USER_COMMENT,
                ReportField.USER_APP_START_DATE,
                ReportField.USER_CRASH_DATE,
                ReportField.DUMPSYS_MEMINFO,
                ReportField.LOGCAT,
                ReportField.IS_SILENT,
                ReportField.INSTALLATION_ID,
                ReportField.USER_EMAIL,
                ReportField.DEVICE_FEATURES,
                ReportField.ENVIRONMENT,
                ReportField.SHARED_PREFERENCES,
                ReportField.BUILD_CONFIG
        };
        ACRAConfiguration config = new ACRAConfiguration()
                .setCustomReportContent(fields);

        // start listening for any crash to report it
        ACRA.init(this, config);
        ACRA.getErrorReporter()
                .setReportSender(ATAKCrashHandler.instance());

        this.registerActivityLifecycleCallbacks(new ActivityLifecycleHandler());

        Thread.setDefaultUncaughtExceptionHandler(new CustomExceptionHandler());

        try {
            com.atakmap.coremap.loader.NativeLoader
                    .loadLibrary("jnicrash");

            initializeJNICrash(IOProviderFactory.isDefault());
        } catch (UnsatisfiedLinkError ignored) {
            // the rk3399 is unable to load the jni crash libraries.
            // and I have no further information from the playstore ATAK-14232
        }
    }

    /**
     * Package protected capability to notify when there is a switch of encrypted IO during the
     * initialization.   This mechanism ensures that the JNI Crash capability is initialized
     * correctly.
     */
    public void notifyEncryptionChanged() {
        initializeJNICrash(IOProviderFactory.isDefault());
    }

    private void initializeJNICrash(boolean enable) {
        File logsDir = ATAKCrashHandler.getLogsDir();
        String hdr = ATAKCrashHandler.getHeaderText(null, null, null, this);
        try {
            String gpu = ATAKCrashHandler.getGPUInfo();
            hdr += ",\n" + gpu;
        } catch (Exception ignore) {
        }

        File providerLogsDir = null;
        if (enable) {
            // use toURI to get a "resolved" path to pass to IOProvider unaware JNICrash.initialize
            providerLogsDir = new File(
                    IOProviderFactory.toURI(logsDir).getPath());
        }
        JNICrash.initialize(providerLogsDir, hdr);
    }

    /**
     * Custom handler that stops the CommsMapComponent prior to actually crashing through which
     * should prevent a native crash when the callbacks are no longer valid.
     */
    public static class CustomExceptionHandler implements
            Thread.UncaughtExceptionHandler {

        private final Thread.UncaughtExceptionHandler defaultUEH;

        CustomExceptionHandler() {
            this.defaultUEH = Thread.getDefaultUncaughtExceptionHandler();
        }

        @Override
        public void uncaughtException(@NonNull Thread t, @NonNull Throwable e) {
            //Log.e(TAG, "uncaughtException", e);

            // Stop commo first to prevent native callbacks once JVM starts
            // to tear down.  ACRA is idiotic and threads the uncaught exception handler work off
            // so we cannot do it in a callback from ACRA as JVM will have already begun to
            // shut down and native callbacks will cause hard crashes
            // (see https://jira.pargovernment.net/browse/ATAK-8098?filter=15300)
            CommsMapComponent cmc = CommsMapComponent.getInstance();
            if (cmc != null)
                cmc.onCrash();

            // I believe that the background service not getting stopped is probably a big reason
            // for the occurance of secondary crash logs.
            try {
                Log.d(TAG, "shutting down services");
                BackgroundServices.stopService();
            } catch (Exception err) {
                Log.d(TAG, "error occurred: " + err);
            }

            // Chain to ACRA
            defaultUEH.uncaughtException(t, e);
        }
    }

    /**
     * This Callback receives callbacks for each type of Activity lifecycle change that occurs
     * within this Application.
     */
    private static class ActivityLifecycleHandler implements
            ActivityLifecycleCallbacks {

        @Override
        public void onActivityCreated(@NonNull Activity activity,
                Bundle savedInstanceState) {
        }

        @Override
        public void onActivityDestroyed(Activity activity) {
            Log.e(TAG, "activity destroyed = " + activity.getClass());
            try {
                if (activity.getClass().equals(ATAKActivity.class)) {
                    Log.d(TAG, "turning off the oreo gps service");
                    BackgroundServices.stopService();
                    Log.d(TAG, "successfully turned off the oreo gps service");
                }
            } catch (Exception e) {
                Log.d(TAG, "error", e);
            }
        }

        @Override
        public void onActivityPaused(@NonNull Activity activity) {
        }

        @Override
        public void onActivityResumed(@NonNull Activity activity) {
        }

        @Override
        public void onActivitySaveInstanceState(@NonNull Activity activity,
                @NonNull Bundle savedInstanceState) {
        }

        @Override
        public void onActivityStarted(@NonNull Activity activity) {
        }

        @Override
        public void onActivityStopped(@NonNull Activity activity) {
        }
    }

    public static void addCrashListener(CrashListener listener) {
        ATAKCrashHandler.instance().addListener(listener);
    }

    public static void removeCrashListener(CrashListener listener) {
        ATAKCrashHandler.instance().removeListener(listener);
    }
}
