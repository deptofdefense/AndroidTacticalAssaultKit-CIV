
package com.atakmap.app;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.FileIOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.util.ConfigOptions;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;

public final class DeveloperOptions {

    private static String TAG = "DeveloperOptions";

    private final static File DEVOPTS_FILE = FileSystemUtils
            .getItem("devopts.properties");

    private static final Properties opts = new Properties();
    static {
        InputStream in = null;
        try {
            if (FileIOProviderFactory.exists(DEVOPTS_FILE)) {
                in = FileIOProviderFactory.getInputStream(DEVOPTS_FILE);
                opts.load(in);
            }
        } catch (IOException e) {
            Log.w(TAG, "Failed to load options", e);
        } finally {
            if (in != null)
                try {
                    in.close();
                } catch (IOException ignored) {
                }
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
}
