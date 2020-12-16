package com.atakmap.map.gdal;

import com.atakmap.coremap.io.IOProvider;
import com.atakmap.util.Collections2;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.Set;

/**
 * Interface defining the behavior of the VSI File System Handler
 */
public abstract class VSIJFileFilesystemHandler {
    private final static Set<VSIJFileFilesystemHandler> installedHandlers = Collections2.newIdentityHashSet();

    /**
     * Holds the prefix associated with this handler
     */
    private final String prefix;

    protected VSIJFileFilesystemHandler(String prefix) {
        this.prefix = prefix;
    }

    /**
     * Gets the prefix associated with this handler
     *
     * @return The prefix for this handler
     */
    public String getPrefix(){
        return this.prefix;
    }

    /**
     * Opens the file
     * @param filename path to file to open prefixed with the prefix used in installFileSystemHandler
     * @param access specifies type of access for the file
     * @throws IOException in case of error
     * @return File Handle to the open file or Null if unable to open
     */
    public abstract FileChannel open(String filename, String access) throws IOException;

    /**
     * Gets information about the file
     * @param filename path to file to open prefixed with the prefix used in installFileSystemHandler
     * @param statBuffer The buffer
     * @param flags Contains information about the file
     * @throws IOException in case of error
     * @return 0 if successful, non-zero otherwise
     */
    public abstract int stat(String filename, VSIStatBuf statBuffer, int flags) throws IOException;

    /**
     * Convert access mode string from Linux(JNI) to Java.
     * Linux allows access modes like rb+ but Java just wants modes like r/rw.
     *
     * @param access The original access string that may need to be sanitized.
     * @return The sanitized access string.
     */
    protected static String convertFileAccessMode(String access) {
        if(access.equals("r") || access.equals("rb")) {
            return "r";
        }
        //if it is 'rw' or 'r+', we have to treat it as read-write.
        return "rw";
    }

    /**
     * Installs (registers) VSI file system handler
     * @param handler VSI file system handler
     */
    static public void installFilesystemHandler(VSIJFileFilesystemHandler handler) {
        synchronized(installedHandlers) {
            // only install if not already installed
            if(!installedHandlers.add(handler))
                return;
        }
        // register with GDAL in JNI
        installFilesystemHandler(handler.getPrefix(), handler);
    }

    /**
     * Installs (registers) VSI file system handler
     * @param prefix File path prefix formatted like: "/prefix-string/"
     * @param handler VSI file system handler
     */
    private native static void installFilesystemHandler(String prefix, VSIJFileFilesystemHandler handler);
}
