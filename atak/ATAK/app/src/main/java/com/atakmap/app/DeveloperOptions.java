
package com.atakmap.app;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.util.ConfigOptions;
import com.atakmap.util.zip.IoUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.Properties;

public final class DeveloperOptions {

    private static final String TAG = "DeveloperOptions";

    private final static File DEVOPTS_FILE = FileSystemUtils
            .getItem("devopts.properties");

    private static final Properties opts = new Properties();
    static {

        // first examine the system level properties via getprop
        loadSystemProperties();

        // then load the devopts.properties file allowing a developer
        // to locally override any system level properties
        InputStream in = null;
        try {
            if (IOProviderFactory.exists(DEVOPTS_FILE)) {
                in = IOProviderFactory.getInputStream(DEVOPTS_FILE);
                opts.load(in);
            }
        } catch (IOException e) {
            Log.w(TAG, "Failed to load options", e);
        } finally {
            IoUtils.close(in);
        }

        // populate any map engine config options
        try {
            for (Map.Entry<Object, Object> entry : opts.entrySet()) {
                final Object okey = entry.getKey();
                final Object ovalue = entry.getValue();
                if (!(okey instanceof String) || !(ovalue instanceof String))
                    continue;
                final String key = (String) okey;
                if (!key.startsWith("mapengine."))
                    continue;
                ConfigOptions.setOption(key.substring(10), (String) ovalue);
            }
        } catch (Exception e) {
            Log.w("DeveloperOptions",
                    "Error transferring map engine config options", e);
        }
    }

    private DeveloperOptions() {
    }

    public static String getStringOption(String name, String defVal) {
        return opts.getProperty(name, defVal);
    }

    public static int getIntOption(String name, int defVal) {
        String retval = opts.getProperty(name);
        if (retval == null || !retval.matches("\\-?\\d+"))
            return defVal;
        return Integer.parseInt(retval);
    }

    public static double getDoubleOption(String name, double defVal) {
        String retval = opts.getProperty(name);
        if (retval == null)
            return defVal;
        try {
            return Double.parseDouble(retval);
        } catch (NumberFormatException e) {
            return defVal;
        }
    }

    /**
     * Loads system properties that would be specified by setProp and obtained by getProp.
     * The properties that are loaded are prefaced with "ro.tak." and  
     */
    private static void loadSystemProperties() {
        Process proc = null;
        BufferedReader br = null;

        try {
            proc = new ProcessBuilder().command("/system/bin/getprop")
                    .redirectErrorStream(true).start();
            br = new BufferedReader(
                    new InputStreamReader(proc.getInputStream()));
            String line;
            while ((line = br.readLine()) != null) {
                final String[] split = line.split("\\]: \\[");
                String key = split[0].substring(1);
                String value = split[1].substring(0, split[1].length() - 1);
                if (key.startsWith("ro.tak.")) {
                    key = key.replace("ro.tak.", "");
                    opts.setProperty(key, value);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to read system properties", e);
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException ignored) {
                }
            }
            if (proc != null) {
                proc.destroy();
            }
        }
    }
}
