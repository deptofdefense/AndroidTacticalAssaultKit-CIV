
package com.atakmap.commoncommo;

import java.nio.channels.FileChannel;
import java.io.IOException;

/**
 * Interface for Creating a File IO Provider
 */
public interface FileIOProvider {

    /**
     * Open a file
     * 
     * @param filePath The filepath to open
     * @param mode The open mode
     * @return The FileChannel
     */
    FileChannel open(String filePath, String mode) throws IOException;

    /**
     * Gets the size of the file specified by the filepath
     *
     * @param filePath The path to the file
     * @return the length of the file
     */
    long getSize(String filePath) throws IOException;
}
