
package com.atakmap.comms;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.time.CoordinatedTime;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.util.zip.IoUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Arrays;

class OutboundLogger implements CommsLogger,
        SharedPreferences.OnSharedPreferenceChangeListener {

    public static final String TAG = "OutboundLogger";

    private static final String DELIMITER = "\t";
    private static final String LINE_SEPARATOR = System
            .getProperty("line.separator");
    private boolean log;
    private boolean shuttingDown = false;
    private Writer writer = null;

    private final SharedPreferences prefs;

    OutboundLogger(final Context context) {

        prefs = PreferenceManager.getDefaultSharedPreferences(context);
        log = prefs.getBoolean("lognettraffictofile",
                false);

        prefs.registerOnSharedPreferenceChangeListener(this);
        File f = FileSystemUtils.getItem(FileSystemUtils.SUPPORT_DIRECTORY
                + File.separatorChar + "logs");
        if (!IOProviderFactory.exists(f))
            if (!IOProviderFactory.mkdir(f))
                Log.d(TAG, "could not create the support/logs directory");

        if (log)
            writer = getLogFileWriter();

    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {

        if (key == null)
            return;

        if (key.compareTo("lognettraffictofile") == 0) {
            log = prefs.getBoolean("lognettraffictofile", false);

            synchronized (this) {
                if (!shuttingDown) {
                    // close the previous log, 
                    try {
                        if (writer != null)
                            writer.close();
                    } catch (Exception ignored) {
                    }
                    writer = null;

                    // if logging is enabled - create a new log
                    if (log)
                        writer = getLogFileWriter();
                }
            }
        }
    }

    @Override
    public void logSend(CotEvent msg, String destination) {
        if (log) {
            logToFile("send", msg, destination);
        }
    }

    @Override
    public void logSend(CotEvent msg, String[] toUIDs) {
        if (log) {
            logToFile("send", msg, Arrays.toString(toUIDs));
        }
    }

    @Override
    public void logReceive(CotEvent msg, String rxid, String server) {
        if (log) {
            final String inc = rxid + ((server == null) ? "" : "," + server);
            logToFile("received", msg, inc);
        }
    }

    private Writer getLogFileWriter() {
        File f = new File(FileSystemUtils.getItem("support/logs/"), "network-"
                + new CoordinatedTime().getMilliseconds() + "-log.csv");

        Writer fw = null;
        FileOutputStream fos = null;
        try {
            fw = new OutputStreamWriter(
                    fos = IOProviderFactory.getOutputStream(f),
                    FileSystemUtils.UTF8_CHARSET.newEncoder());
        } catch (Exception e) {
            Log.w(TAG, "Could not open log file: " + f, e);
            IoUtils.close(fos);
        }
        return fw;
    }

    // synchronized so that we don't get mangled lines
    private synchronized void logToFile(String direction, CotEvent event,
            String destination) {
        if (event == null)
            return;

        try {
            // check if the logging is enabled
            // write it out to file (if possible)
            StringBuilder toWrite = new StringBuilder(
                    new CoordinatedTime() + DELIMITER + direction + DELIMITER
                            + event);
            toWrite.append(DELIMITER)
                    .append(destination);
            if (writer != null) {
                writer.write(toWrite + LINE_SEPARATOR);
                writer.flush();

            }
        } catch (Exception e) {
            Log.w("serious problem writing to network log file", e);
            try {
                if (writer != null)
                    writer.close();
            } catch (Exception ignore) {
            }
            writer = null;
        }

    }

    @Override
    public void dispose() {
        prefs.unregisterOnSharedPreferenceChangeListener(this);
        synchronized (this) {
            shuttingDown = true;
            try {
                if (writer != null)
                    writer.close();
            } catch (Exception ignored) {
            }
        }
    }

}
