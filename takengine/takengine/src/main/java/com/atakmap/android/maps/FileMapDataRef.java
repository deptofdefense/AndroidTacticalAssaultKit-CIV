
package com.atakmap.android.maps;

import java.io.File;

import android.net.Uri;

/**
 * Engine resource reference that points to a file
 *
 */
public class FileMapDataRef extends MapDataRef {

    /**
     * Create the reference given a file path
     * 
     * @param filePath the file path
     */
    public FileMapDataRef(String filePath) {
        _filePath = filePath;
    }

    /**
     * Get the file path
     * 
     * @return the file path
     */
    public String getFilePath() {
        return _filePath;
    }

    public String toString() {
        return "file: " + _filePath;
    }

    protected String _filePath;

    @Override
    public String toUri() {
        Uri uri = Uri.fromFile(new File(_filePath));
        return uri.toString();
    }
}
