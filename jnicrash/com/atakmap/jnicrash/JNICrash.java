package com.atakmap.jnicrash;

import java.io.File;

public class JNICrash {
    private static boolean initialized = false;

    public synchronized static void initialize(File logDir, String systemDetails) {
        if (initialized) return;
        
        String logdirstring = logDir.getAbsolutePath();
        setSystemDetails(logdirstring, systemDetails);
        
        initialized = true;
    }
    private static native void setSystemDetails(String filename, String details);
}
