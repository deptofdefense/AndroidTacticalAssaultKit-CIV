package com.atakmap.jnicrash;

import java.io.File;

public class JNICrash {
    public synchronized static void initialize(File logDir, String systemDetails) {
    	String logdirstring = null;
    	if (logDir != null)
        	logdirstring = logDir.getAbsolutePath();
        setSystemDetails(logdirstring, systemDetails);
    }
    private static native void setSystemDetails(String filename, String details);
}
