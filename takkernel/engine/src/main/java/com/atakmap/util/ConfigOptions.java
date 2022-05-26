package com.atakmap.util;

import com.atakmap.coremap.log.Log;
import com.atakmap.map.EngineLibrary;

/**
 * Runtime storage for runtime configuration options for the map engine. 
 * 
 * @author Developer
 */
public final class ConfigOptions {
    static {
        EngineLibrary.initialize();
    }
    
    private ConfigOptions() {}
    
    public static native void setOption(String key, String value);
    
    public static void setOption(String key, int value) {
        setOption(key, Integer.toString(value));
    }
    
    public static void setOption(String key, long value) {
        setOption(key, Long.toString(value));
    }
    
    public static void setOption(String key, double value) {
        setOption(key, Double.toString(value));
    }
    
    public static native String getOption(String key, String defval);
    
    public static int getOption(String key, int defval) {
        final String retval = getOption(key, null);
        if(retval == null)
            return defval;
        try {
            return Integer.parseInt(retval);
        } catch(NumberFormatException e) {
            Log.w("ConfigOptions", "Option " + key + " value " + retval + " could not be parsed as int");
            return defval;
        }
    }
    
    public static long getOption(String key, long defval) {
        final String retval = getOption(key, null);
        if(retval == null)
            return defval;
        try {
            return Long.parseLong(retval);
        } catch(NumberFormatException e) {
            Log.w("ConfigOptions", "Option " + key + " value " + retval + " could not be parsed as long");
            return defval;
        }
    }
    
    public static double getOption(String key, double defval) {
        final String retval = getOption(key, null);
        if(retval == null)
            return defval;
        try {
            return Double.parseDouble(retval);
        } catch(NumberFormatException e) {
            Log.w("ConfigOptions", "Option " + key + " value " + retval + " could not be parsed as double");
            return defval;
        }
    }
}
