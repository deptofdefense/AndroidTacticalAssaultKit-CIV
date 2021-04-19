
package com.atakmap.comms;

import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;

import org.apache.commons.lang.StringUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.channels.FileChannel;

public class FileIOProvider implements com.atakmap.commoncommo.FileIOProvider {

    private static final String TAG = "FileIOProvider";

    /**
     * Opens a file and returns the FileChannel for that file
     * 
     * @param filePath The filepath of the file
     * @param mode The mode to open the file
     * @return The FileChannel for that file or null if the file cannot be found
     */
    @Override
    public FileChannel open(String filePath, String mode) {
        try {
            mode = convertFileAccessMode(mode);
            return IOProviderFactory.getChannel(new File(filePath), mode);
        } catch (FileNotFoundException e) {
            Log.e(TAG, "error encountered", e);
            return null;
        }
    }

    /**
     * Gets the size of a file based on the filepath
     * 
     * @param filePath - The path to the file
     * @return The size in bytes of the file, or 0 if the filepath is invalid or null
     */
    @Override
    public long getSize(String filePath) {
        if (!StringUtils.isBlank(filePath)) {
            return IOProviderFactory.length(new File(filePath));
        }
        return 0L;
    }

    /**
     * Convert access mode string from Linux(JNI) to Java.
     * Linux allows access modes like rb+ but Java just wants modes like r/rw.
     *
     * @param access The original access string that may need to be sanitized.
     * @return The sanitized access string.
     */
    private static String convertFileAccessMode(String access) {
        if (access.equals("r") || access.equals("rb")) {
            return "r";
        }
        //if it is 'rw' or 'r+', we have to treat it as read-write.
        return "rw";
    }
}
