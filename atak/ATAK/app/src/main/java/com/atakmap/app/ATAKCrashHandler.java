
package com.atakmap.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.preference.PreferenceManager;

import com.atak.plugins.impl.AtakPluginRegistry;
import com.atakmap.android.util.ATAKConstants;
import com.atakmap.app.system.SystemComponentLoader;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.time.CoordinatedTime;

import com.atakmap.util.zip.IoUtils;
import org.acra.ReportField;
import org.acra.collector.CrashReportData;
import org.acra.sender.ReportSender;
import org.acra.util.JSONReportBuilder;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

/**
 * Singleton which can allow components to be notified and take action upon crash, also writes hard
 * crash stack traces to a file on the SD card.
 * 
 * 
 */
class ATAKCrashHandler implements ReportSender {

    private static final String TAG = "ATAKCrashHandler";

    private static final int MAX_ERROR_SUMMARY_LENGTH = 150;
    private static final String STACK_DELIMITER = "\n\tat";

    private final List<CrashListener> _listeners = new ArrayList<>();
    // Singleton management
    private static ATAKCrashHandler sInstance;

    public static synchronized ATAKCrashHandler instance() {
        if (sInstance == null) {
            sInstance = new ATAKCrashHandler();
        }

        return sInstance;
    }

    private ATAKCrashHandler() {
    }

    synchronized public void addListener(CrashListener listener) {
        if (listener == null || _listeners.contains(listener))
            return;

        Log.d(TAG, "Registering crash listener: "
                + listener.getClass().getName());
        _listeners.add(listener);
    }

    synchronized void removeListener(CrashListener listener) {
        if (listener == null || !_listeners.contains(listener))
            return;

        Log.d(TAG, "Removing crash listener: "
                + listener.getClass().getName());
        _listeners.remove(listener);
    }

    public static File getLogsDir() {
        File directory = FileSystemUtils
                .getItem(FileSystemUtils.SUPPORT_DIRECTORY + File.separatorChar
                        + "logs");
        if (!IOProviderFactory.exists(directory)) {
            if (!IOProviderFactory.mkdir(directory))
                Log.d(TAG, "could not make: " + directory);
        }
        return directory;
    }

    @Override
    public void send(Context context, CrashReportData crashData) {

        // write out crash details to disk
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss",
                LocaleUtil.getCurrent());
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        Date now = CoordinatedTime.currentDate();
        String filename_timeStamp = sdf.format(now);

        sdf = new SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss'Z'", LocaleUtil.getCurrent());
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        String header_timstamp = sdf.format(now);

        File directory = getLogsDir();

        File f = new File(directory.getPath() + File.separatorChar
                + "ATAKCRASH_" + filename_timeStamp + ".json");
        FileSystemUtils.deleteFile(f);

        PrintWriter pw = null;
        try {
            if (IOProviderFactory.createNewFile(f)) {
                pw = new PrintWriter(IOProviderFactory.getFileWriter(f));
                // add custom dynamic content not supported by ACRA, and crash summary to header
                String error = null;
                String st = crashData == null ? null
                        : crashData.getProperty(ReportField.STACK_TRACE);
                int index = st == null ? -1 : st.indexOf(STACK_DELIMITER);
                if (index > 0) {
                    error = st.substring(0, index);
                }

                String sthash = crashData == null ? null
                        : crashData.getProperty(ReportField.STACK_TRACE_HASH);
                pw.println(getHeader(header_timstamp, error, sthash, context));

                try {
                    pw.println(getGPUInfo() + ",");
                } catch (Exception ignore) {
                    // just in case there are any unforseen issues 
                }

                //write the full ACRA log
                if (crashData == null) {
                    pw.println("\"report\":\"ACRA Log empty\"");

                } else {
                    JSONObject json = crashData.toJSON();
                    if (json == null) {
                        pw.println("\"report\":\"ACRA JSON empty\"");
                    } else {
                        pw.println("\"report\":" + json);
                    }
                }

                // notify crash listeners
                synchronized (this) {
                    if (_listeners.size() > 0) {
                        Log.d(TAG, "Notifying " + _listeners.size()
                                + " crash listeners");
                        for (CrashListener listener : _listeners) {
                            try {
                                CrashListener.CrashLogSection section = listener
                                        .onCrash();
                                if (section != null && section.isValid()) {
                                    pw.println("," + section);
                                    pw.println();
                                }
                            } catch (Throwable t) {
                                Log.e(TAG,
                                        "onCrash "
                                                + listener.getClass().getName(),
                                        t);
                            }
                        }
                    }
                }

                pw.println("}");
            }
        } catch (IOException | JSONReportBuilder.JSONReportException e) {
            Log.e(TAG, "error: ", e);
        } finally {
            IoUtils.close(pw, TAG, "PrintWriter failed to close");
        }
    }

    public static String getGPUInfo() {
        String gpuinfo = "\"gpu\": {";
        gpuinfo += com.atakmap.android.util.BundleUtils.bundleToString(
                com.atakmap.android.maps.MapView.getMapView().getGPUInfo())
                + "}";
        return gpuinfo;
    }

    public static String getHeaderText(String timeStamp, String error,
            String stackTraceHash, Context context) {
        if (timeStamp == null)
            timeStamp = "";
        if (error == null)
            error = "";
        if (error.length() > MAX_ERROR_SUMMARY_LENGTH)
            error = error.substring(0, MAX_ERROR_SUMMARY_LENGTH);
        // Remove newlines, tabs, and double quotes
        error = error.replaceAll("\n", "\\\\n\\\\t").replaceAll("\t", "")
                .replaceAll("\"", "'");
        if (stackTraceHash == null)
            stackTraceHash = "";

        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(context);

        String sessionId = prefs.getString("core_sessionid", "not sset");
        String takVersion = null, takRev = null;
        try {
            takVersion = context.getPackageManager().getPackageInfo(
                    context.getPackageName(), 0).versionName;
            takRev = "" + context.getPackageManager().getPackageInfo(
                    context.getPackageName(), 0).versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Failed to lookup version code", e);
        }

        return "{\"TAK.sessionid\":\"" + sessionId + "\",\n" +
                "\"TAK.uid\":\"" + prefs.getString("bestDeviceUID", "unknown")
                + "\",\n" +
                "\"timestamp\":\"" + timeStamp + "\",\n" +
                "\"os.version\":\"" + System.getProperty("os.version") + "\",\n"
                +
                "\"android.release\":\"" + Build.VERSION.RELEASE + "\",\n" +
                "\"android.sdk\":\"" + Build.VERSION.SDK_INT + "\",\n" +
                "\"device.model\":\""
                + Build.MODEL + "\",\n" +
                "\"device.manufacturer\":\""
                + Build.MANUFACTURER + "\",\n" +
                "\"ACRA.version\":\"4.6.1\",\n" +
                "\"TAK.brand\":\"" + ATAKConstants.getVersionBrand()
                + "\",\n" +
                "\"TAK.version\":\"" + takVersion + "\",\n" +
                "\"TAK.revision\":\"" + takRev + "\",\n" +
                "\"TAK.plugin-api\":\"" + ATAKConstants.getPluginApi(false)
                + "\",\n" +
                "\"TAK.error\":\"" + error + "\",\n" +
                "\"TAK.stackHash\":\"" + stackTraceHash + "\",\n" +
                SystemComponentLoader.getCrashLogInfo() + ",\n" +
                "\"plugins\":[" + getPluginList(context, prefs) + "]}";
    }

    private static String getHeader(String timeStamp, String error,
            String stackTraceHash, Context context) {

        return "{\"header\":\n" +
                getHeaderText(timeStamp, error, stackTraceHash, context) +
                ",";
    }

    private static String getPluginList(Context context,
            SharedPreferences prefs) {
        StringBuilder summary = new StringBuilder();

        if (context != null) {
            Log.d(TAG, "get plugins from prefs");
            //first check prefs which includes versionCode
            final Map<String, ?> keys = prefs.getAll();
            boolean first = true;
            for (Map.Entry<String, ?> entry : keys.entrySet()) {
                final String key = entry.getKey();
                if (key.startsWith(AtakPluginRegistry.pluginLoadedBasename)
                        && entry.getValue() != null) {
                    if (!first) {
                        summary.append(", ");
                    }
                    summary.append("{\"plugin\":\"").append(key)
                            .append("\",\"version\":\"")
                            .append(entry.getValue().toString())
                            .append("\"}\n");
                    first = false;
                }
            }
        } else {
            Log.d(TAG, "get plugins from registry");
            //now check plugin registry
            AtakPluginRegistry registry = AtakPluginRegistry.get();
            boolean first = true;
            if (registry != null) {
                Set<String> plugins = registry.getPluginsLoaded();
                if (plugins.size() > 0) {
                    for (String plugin : plugins) {
                        if (!first) {
                            summary.append(", ");
                        }
                        if (plugin != null) {
                            summary.append("{\"plugin\":\"").append(plugin)
                                    .append("\"}\n");
                            first = false;
                        }
                    }
                }
            }
        }

        return summary.toString();
    }
}
